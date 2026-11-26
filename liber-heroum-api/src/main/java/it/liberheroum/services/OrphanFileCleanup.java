package it.liberheroum.services;

import io.ebean.Database;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.errors.MinioException;
import io.minio.messages.Item;
import it.liberheroum.config.FeedbackConfig;
import it.liberheroum.config.StorageConfig;
import it.liberheroum.model.Document;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanFileCleanup {

    static final String LOCK_NAME = "orphanFileCleanup";
    private static final Duration LOCK_AT_MOST_FOR = Duration.ofHours(1);
    private static final Duration LOCK_AT_LEAST_FOR = Duration.ofMinutes(5);
    private static final int BATCH_SIZE = 500;

    private final MinioClient minioClient;
    private final Database database;
    private final StorageConfig storageConfig;
    private final FeedbackConfig feedbackConfig;
    private final Clock clock;
    private final LockingTaskExecutor lockingTaskExecutor;

    public boolean run() {
        AtomicBoolean executed = new AtomicBoolean(false);
        lockingTaskExecutor.executeWithLock((Runnable) () -> {
            executed.set(true);
            Instant cutoff = clock.instant().minus(storageConfig.getOrphanMinAge());
            int deleted = deleteOrphansOlderThan(cutoff);
            log.info("Orphan file cleanup finished: {} files deleted (older than {})", deleted, cutoff);
        }, new LockConfiguration(clock.instant(), LOCK_NAME, LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR));
        if (!executed.get()) {
            log.info("Orphan file cleanup skipped: another instance holds the lock");
        }
        return executed.get();
    }

    public int deleteOrphansOlderThan(Instant cutoff) {
        return Stream.of(storageConfig.getBucket(), feedbackConfig.getBucket())
                .distinct()
                .mapToInt(bucket -> deleteOrphansOlderThan(bucket, cutoff))
                .sum();
    }

    private int deleteOrphansOlderThan(String bucket, Instant cutoff) {
        int deleted = 0;
        List<String> candidates = new ArrayList<>();
        try {
            for (Result<Item> result : minioClient.listObjects(ListObjectsArgs.builder().bucket(bucket).recursive(true).build())) {
                Item item = result.get();
                if (!item.isDir() && item.lastModified().toInstant().isBefore(cutoff)) {
                    candidates.add(item.objectName());
                }
                if (candidates.size() == BATCH_SIZE) {
                    deleted += deleteUnreferenced(bucket, candidates);
                    candidates.clear();
                }
            }
            deleted += deleteUnreferenced(bucket, candidates);
        } catch (MinioException | RuntimeException e) {
            log.warn("Orphan file cleanup of bucket {} stopped after {} files", bucket, deleted, e);
        }
        return deleted;
    }

    private int deleteUnreferenced(String bucket, List<String> objectKeys) {
        if (objectKeys.isEmpty()) {
            return 0;
        }
        Set<String> referenced = new HashSet<>(database.find(Document.class)
                .setLabel("findReferencedObjectKeys")
                .select("objectKey")
                .where().eq("bucket", bucket).in("objectKey", objectKeys)
                .findSingleAttributeList());
        int deleted = 0;
        for (String objectKey : objectKeys) {
            if (!referenced.contains(objectKey) && remove(bucket, objectKey)) {
                deleted++;
            }
        }
        return deleted;
    }

    private boolean remove(String bucket, String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
            log.info("Orphan file {} deleted from bucket {}", objectKey, bucket);
            return true;
        } catch (MinioException e) {
            log.warn("Cannot delete the orphan file {} from bucket {}", objectKey, bucket, e);
            return false;
        }
    }
}

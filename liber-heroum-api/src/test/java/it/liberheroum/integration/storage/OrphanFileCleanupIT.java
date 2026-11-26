package it.liberheroum.integration.storage;

import static org.assertj.core.api.Assertions.assertThat;

import io.minio.PutObjectArgs;
import it.liberheroum.integration.support.AbstractIntegrationTest;
import it.liberheroum.integration.support.TestContainers;
import it.liberheroum.integration.support.WithMockJwt;
import it.liberheroum.services.OrphanFileCleanup;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@WithMockJwt
class OrphanFileCleanupIT extends AbstractIntegrationTest {

    @Autowired
    private OrphanFileCleanup cleanup;

    private void putObject(String bucket, String key) throws Exception {
        minioClient.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                .stream(new ByteArrayInputStream(PNG), (long) PNG.length, -1L).contentType("image/png").build());
    }

    private static Instant inOneMinute() {
        return Instant.now().plus(Duration.ofMinutes(1));
    }

    @Test
    void deleteOrphansOlderThan_orphanAndAvatarInStorage_deletesOnlyTheOrphan() throws Exception {
        createAppUser();
        mvc.put().uri("/me/avatar").multipart().file(pngFile("me.png")).exchange();
        List<String> avatar = storedObjectKeys();
        putObject(TestContainers.BUCKET, "orphan.png");

        int deleted = cleanup.deleteOrphansOlderThan(inOneMinute());

        assertThat(deleted).isEqualTo(1);
        assertThat(storedObjectKeys()).isEqualTo(avatar);
        assertThat(countRows("document")).isEqualTo(1);
    }

    @Test
    void deleteOrphansOlderThan_orphanNewerThanCutoff_keepsIt() throws Exception {
        putObject(TestContainers.BUCKET, "uploading.png");

        int deleted = cleanup.deleteOrphansOlderThan(Instant.now().minus(Duration.ofHours(1)));

        assertThat(deleted).isZero();
        assertThat(storedObjectKeys()).containsExactly("uploading.png");
    }

    @Test
    void deleteOrphansOlderThan_orphanInFeedbackBucket_deletesIt() throws Exception {
        putObject(FEEDBACK_BUCKET, "screenshot.png");

        int deleted = cleanup.deleteOrphansOlderThan(inOneMinute());

        assertThat(deleted).isEqualTo(1);
        assertThat(storedObjectKeys(FEEDBACK_BUCKET)).isEmpty();
    }

    @Test
    void run_noOtherInstanceRunning_runsAndRecordsTheLock() {
        boolean executed = cleanup.run();

        assertThat(executed).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from shedlock where name = 'orphanFileCleanup'", Integer.class)).isEqualTo(1);
    }

    @Test
    void run_lockHeldByAnotherInstance_skipsTheCleanup() {
        jdbc.update("insert into shedlock (name, lock_until, locked_at, locked_by) "
                + "values ('orphanFileCleanup', timezone('utc', now()) + interval '1 hour', timezone('utc', now()), 'other-instance')");

        boolean executed = cleanup.run();

        assertThat(executed).isFalse();
    }
}

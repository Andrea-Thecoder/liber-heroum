package it.liberheroum.config;

import it.liberheroum.services.OrphanFileCleanup;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class SchedulingConfig implements SchedulingConfigurer {

    private final StorageConfig storageConfig;
    private final OrphanFileCleanup orphanFileCleanup;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        if (storageConfig.isOrphanCleanupEnabled()) {
            registrar.addCronTask(new CronTask(orphanFileCleanup::run,
                    new CronTrigger(storageConfig.getOrphanCleanupCron(), storageConfig.getOrphanCleanupZone())));
        }
    }
}

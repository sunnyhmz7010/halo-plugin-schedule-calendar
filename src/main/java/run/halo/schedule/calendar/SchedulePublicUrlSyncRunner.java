package run.halo.schedule.calendar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulePublicUrlSyncRunner {
    private final SchedulePublicUrlService schedulePublicUrlService;

    @EventListener(ApplicationReadyEvent.class)
    public void syncAfterStartup() {
        schedulePublicUrlService.syncDisplayOnlySettings()
            .doOnError(error -> log.warn("Failed to sync display-only public calendar URLs", error))
            .subscribe();
    }
}

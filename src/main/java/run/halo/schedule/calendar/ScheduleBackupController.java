package run.halo.schedule.calendar;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/apis/console.api.schedule.calendar.sunny.dev/v1alpha1")
@RequiredArgsConstructor
public class ScheduleBackupController {
    private final ScheduleBackupService scheduleBackupService;

    @GetMapping("/backupexports")
    @PreAuthorize("hasAuthority('plugin:schedule-calendar:manage')")
    public Mono<ScheduleBackupService.ScheduleBackupPayload> exportBackup() {
        return scheduleBackupService.exportBackup();
    }
}

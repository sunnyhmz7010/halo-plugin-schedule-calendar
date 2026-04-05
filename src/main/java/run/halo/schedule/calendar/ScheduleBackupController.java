package run.halo.schedule.calendar;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/apis/api.console.schedule.calendar.sunny.dev/v1alpha1/backups")
@RequiredArgsConstructor
public class ScheduleBackupController {
    private final ScheduleBackupService scheduleBackupService;

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('plugin:schedule-calendar:manage')")
    public Mono<ScheduleBackupService.ScheduleBackupPayload> exportBackup() {
        return scheduleBackupService.exportBackup();
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('plugin:schedule-calendar:manage')")
    public Mono<ScheduleBackupService.ScheduleBackupImportResult> importBackup(
        @RequestBody ScheduleBackupService.ScheduleBackupPayload payload
    ) {
        return scheduleBackupService.importBackup(payload);
    }
}

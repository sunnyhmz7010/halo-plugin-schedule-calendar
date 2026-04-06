package run.halo.schedule.calendar;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/apis/console.api.schedule.calendar.sunny.dev/v1alpha1/permissions")
public class ScheduleConsolePermissionController {

    @GetMapping("/view")
    @PreAuthorize("hasAuthority('plugin:schedule-calendar:view')")
    public Mono<ResponseEntity<Void>> viewPermission() {
        return Mono.just(ResponseEntity.noContent().build());
    }

    @GetMapping("/manage")
    @PreAuthorize("hasAuthority('plugin:schedule-calendar:manage')")
    public Mono<ResponseEntity<Void>> managePermission() {
        return Mono.just(ResponseEntity.noContent().build());
    }
}

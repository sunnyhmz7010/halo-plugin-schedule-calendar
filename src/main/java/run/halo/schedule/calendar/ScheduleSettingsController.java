package run.halo.schedule.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/apis/console.api.schedule.calendar.sunny.dev/v1alpha1/settings")
@RequiredArgsConstructor
public class ScheduleSettingsController {
    private final ScheduleSettingsService scheduleSettingsService;

    @GetMapping
    @PreAuthorize("hasAuthority('plugin:schedule-calendar:manage')")
    public Mono<Map<String, JsonNode>> getRawSettings() {
        return scheduleSettingsService.getRawSettings();
    }

    @PutMapping
    @PreAuthorize("hasAuthority('plugin:schedule-calendar:manage')")
    public Mono<Void> replaceRawSettings(@RequestBody(required = false) Map<String, JsonNode> settings) {
        return scheduleSettingsService.replaceRawSettings(settings);
    }

    @GetMapping("/public-page")
    @PreAuthorize("hasAuthority('plugin:schedule-calendar:manage')")
    public Mono<ScheduleCalendarSetting> getPublicPageSetting() {
        return scheduleSettingsService.getPublicPageSetting();
    }

    @PutMapping("/public-page")
    @PreAuthorize("hasAuthority('plugin:schedule-calendar:manage')")
    public Mono<Void> updatePublicPageSetting(@RequestBody(required = false) ScheduleCalendarSetting setting) {
        return scheduleSettingsService.updatePublicPageSetting(setting);
    }
}

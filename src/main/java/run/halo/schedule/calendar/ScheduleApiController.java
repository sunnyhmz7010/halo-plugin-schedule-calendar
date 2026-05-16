package run.halo.schedule.calendar;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/apis/api.schedule.calendar.sunny.dev/v1alpha1")
@RequiredArgsConstructor
public class ScheduleApiController {
    private final ScheduleQueryService scheduleQueryService;
    private final ExternalCalendarService externalCalendarService;
    private final ScheduleCalendarSettingService settingService;

    @GetMapping("/weeks")
    public Mono<ScheduleQueryService.WeekViewResponse> currentWeek(
        @RequestParam(name = "start", required = false) LocalDate start
    ) {
        return scheduleQueryService.getWeekView(start);
    }

    @GetMapping("/summary")
    public Mono<ScheduleQueryService.SummaryResponse> summary() {
        return scheduleQueryService.getSummary();
    }

    @GetMapping("/days")
    public Mono<ScheduleQueryService.DayView> day(
        @RequestParam(name = "date", required = false) LocalDate date
    ) {
        return scheduleQueryService.getDayView(date);
    }

    @GetMapping("/occurrences")
    public Mono<List<ScheduleQueryService.OccurrenceResponse>> range(
        @RequestParam(name = "start", required = false) LocalDate start,
        @RequestParam(name = "end", required = false) LocalDate end
    ) {
        return scheduleQueryService.listOccurrences(start, end);
    }

    @GetMapping("/upcoming")
    public Mono<List<ScheduleQueryService.OccurrenceResponse>> upcoming(
        @RequestParam(name = "limit", required = false) Integer limit
    ) {
        return scheduleQueryService.listUpcomingOccurrences(limit);
    }

    @GetMapping("/entrycards")
    public Mono<List<ScheduleQueryService.ScheduleCardResponse>> entries() {
        return scheduleQueryService.listEntryCards();
    }

    @GetMapping("/entrycards/{name}")
    public Mono<ScheduleQueryService.ScheduleCardResponse> entry(@PathVariable("name") String name) {
        return scheduleQueryService.getEntryCard(name);
    }

    @GetMapping("/external-debug")
    public Mono<ExternalDebugResponse> externalDebug(
        @RequestParam(name = "start", required = false) LocalDate start,
        @RequestParam(name = "end", required = false) LocalDate end
    ) {
        var zoneId = java.time.ZoneId.systemDefault();
        var rangeStart = start == null ? LocalDate.now(zoneId) : start;
        var rangeEnd = end == null ? rangeStart : end;
        return settingService.getSetting()
            .flatMap(setting -> externalCalendarService.listOccurrences(setting, rangeStart, rangeEnd, zoneId)
                .map(occurrences -> new ExternalDebugResponse(
                    setting.enabledExternalCalendars().stream()
                        .map(source -> new ExternalSourceDebug(
                            source.effectiveName(),
                            source.icsUrl(),
                            source.isEnabled(),
                            source.color()
                        ))
                        .toList(),
                    occurrences.size(),
                    occurrences.stream()
                        .map(occurrence -> new ExternalOccurrenceDebug(
                            occurrence.title(),
                            occurrence.start().toString(),
                            occurrence.end().toString(),
                            occurrence.sourceLabel()
                        ))
                        .toList()
                )));
    }

    @PostMapping("/external-calendar-validations")
    @PreAuthorize("hasAuthority('plugin:schedule-calendar:manage')")
    public Mono<ExternalCalendarService.ExternalCalendarValidationResult> validateExternalCalendar(
        @RequestBody ExternalCalendarValidationRequest request
    ) {
        return externalCalendarService.validateSource(request.name(), request.icsUrl(), request.color());
    }

    public record ExternalCalendarValidationRequest(String name, String icsUrl, String color) {
    }

    public record ExternalDebugResponse(
        List<ExternalSourceDebug> sources,
        int occurrenceCount,
        List<ExternalOccurrenceDebug> occurrences
    ) {
    }

    public record ExternalSourceDebug(String name, String icsUrl, boolean enabled, String color) {
    }

    public record ExternalOccurrenceDebug(String title, String start, String end, String sourceLabel) {
    }
}

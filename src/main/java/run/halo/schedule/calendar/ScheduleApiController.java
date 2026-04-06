package run.halo.schedule.calendar;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/apis/api.schedule.calendar.sunny.dev/v1alpha1")
@RequiredArgsConstructor
public class ScheduleApiController {
    private final ScheduleQueryService scheduleQueryService;

    @GetMapping("/weeks")
    public Mono<ScheduleQueryService.WeekViewResponse> currentWeek(
        @RequestParam(name = "start", required = false) LocalDate start
    ) {
        return scheduleQueryService.getWeekView(start);
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
}

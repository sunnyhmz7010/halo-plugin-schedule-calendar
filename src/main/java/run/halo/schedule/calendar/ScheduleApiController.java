package run.halo.schedule.calendar;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/apis/api.schedule.calendar.sunny.dev/v1alpha1/calendar")
@RequiredArgsConstructor
public class ScheduleApiController {
    private final ScheduleQueryService scheduleQueryService;

    @GetMapping("/week")
    public Mono<ScheduleQueryService.WeekViewResponse> currentWeek(
        @RequestParam(name = "start", required = false) LocalDate start
    ) {
        return scheduleQueryService.getWeekView(start);
    }

    @GetMapping("/entries/{name}")
    public Mono<ScheduleQueryService.ScheduleCardResponse> entry(@PathVariable String name) {
        return scheduleQueryService.getEntryCard(name);
    }
}

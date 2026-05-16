package run.halo.schedule.calendar;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class SchedulePageController {
    private final ScheduleQueryService scheduleQueryService;

    @GetMapping(value = ScheduleCalendarRoutes.DEFAULT_PUBLIC_PAGE_PATH, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public Mono<String> page(@RequestParam(name = "start", required = false) LocalDate start) {
        return scheduleQueryService.buildPublicCalendarPage(start);
    }

    @GetMapping(value = ScheduleCalendarRoutes.DEFAULT_PUBLIC_ICAL_PATH, produces = "text/calendar; charset=UTF-8")
    @ResponseBody
    public Mono<ResponseEntity<String>> ical() {
        return scheduleQueryService.exportPublicIcal()
            .map(body -> ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
                .header("Content-Disposition", "inline; filename=\"schedule-calendar.ics\"")
                .body(body));
    }

    @GetMapping(value = ScheduleCalendarRoutes.PUBLIC_CARD_PATH_PREFIX + "/{name}",
        produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public Mono<String> card(@PathVariable("name") String name) {
        return scheduleQueryService.buildPublicCardPage(name);
    }
}

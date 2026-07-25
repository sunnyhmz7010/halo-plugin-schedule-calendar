package run.halo.schedule.calendar;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.ISpringWebFluxTemplateEngine;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Controller
@RequiredArgsConstructor
public class SchedulePageController {

    private final ScheduleQueryService scheduleQueryService;
    private final ScheduleCalendarSettingService settingService;
    private final ISpringWebFluxTemplateEngine templateEngine;

    @GetMapping(value = ScheduleCalendarRoutes.DEFAULT_PUBLIC_PAGE_PATH, produces = MediaType.TEXT_HTML_VALUE)
    public Mono<ServerResponse> page(@RequestParam(name = "start", required = false) LocalDate start) {
        return settingService.getSetting()
            .filter(ScheduleCalendarSetting::isPublicPageEnabled)
            .flatMap(setting -> scheduleQueryService.buildCalendarModel(start)
                .flatMap(this::renderCalendar)
                .flatMap(html -> ServerResponse.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .bodyValue(html)))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    @GetMapping(value = ScheduleCalendarRoutes.DEFAULT_PUBLIC_ICAL_PATH, produces = "text/calendar; charset=UTF-8")
    public Mono<ServerResponse> ical() {
        return scheduleQueryService.exportPublicIcal()
            .flatMap(body -> ServerResponse.ok()
                .contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
                .header("Content-Disposition", "inline; filename=\"schedule-calendar.ics\"")
                .bodyValue(body));
    }

    @GetMapping(value = ScheduleCalendarRoutes.PUBLIC_CARD_PATH_PREFIX + "/{name}",
        produces = MediaType.TEXT_HTML_VALUE)
    public Mono<ServerResponse> card(@PathVariable("name") String name) {
        return settingService.getSetting()
            .filter(ScheduleCalendarSetting::isPublicPageEnabled)
            .flatMap(setting -> scheduleQueryService.buildCardModel(name)
                .flatMap(this::renderCard)
                .flatMap(html -> ServerResponse.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .bodyValue(html)))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<String> renderCalendar(Map<String, Object> model) {
        return render("public/calendar", model);
    }

    private Mono<String> renderCard(Map<String, Object> model) {
        return render("public/card", model);
    }

    private Mono<String> render(String template, Map<String, Object> model) {
        return Mono.fromCallable(() -> {
            var ctx = new Context(Locale.SIMPLIFIED_CHINESE, model);
            return templateEngine.process(template, ctx);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}

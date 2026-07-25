package run.halo.schedule.calendar;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class SchedulePageRouter {

    private final ScheduleQueryService scheduleQueryService;
    private final ScheduleCalendarSettingService settingService;

    @Bean
    RouterFunction<ServerResponse> schedulePageRouterFunction() {
        return publicPageRoute(
                RequestPredicates.GET(ScheduleCalendarRoutes.DEFAULT_PUBLIC_PAGE_PATH),
                this::page
            )
            .and(publicPageRoute(
                RequestPredicates.GET(ScheduleCalendarRoutes.PUBLIC_CARD_PATH_PREFIX + "/{name}"),
                this::card
            ))
            .andRoute(RequestPredicates.GET(ScheduleCalendarRoutes.DEFAULT_PUBLIC_ICAL_PATH), this::ical);
    }

    private RouterFunction<ServerResponse> publicPageRoute(RequestPredicate predicate,
        HandlerFunction<ServerResponse> handler) {
        return request -> {
            if (!predicate.test(request)) {
                return Mono.empty();
            }
            return settingService.getSetting()
                .filter(ScheduleCalendarSetting::isPublicPageEnabled)
                .map(setting -> handler);
        };
    }

    private Mono<ServerResponse> page(ServerRequest request) {
        LocalDate start;
        try {
            start = request.queryParam("start")
                .map(LocalDate::parse)
                .orElse(null);
        } catch (DateTimeParseException ex) {
            return ServerResponse.badRequest().build();
        }
        return scheduleQueryService.buildPublicCalendarPage(start)
            .flatMap(html -> ServerResponse.ok()
                .contentType(MediaType.TEXT_HTML)
                .bodyValue(html));
    }

    private Mono<ServerResponse> card(ServerRequest request) {
        return scheduleQueryService.buildPublicCardPage(request.pathVariable("name"))
            .flatMap(html -> ServerResponse.ok()
                .contentType(MediaType.TEXT_HTML)
                .bodyValue(html))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> ical(ServerRequest request) {
        return scheduleQueryService.exportPublicIcal()
            .flatMap(body -> ServerResponse.ok()
                .contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
                .header("Content-Disposition", "inline; filename=\"schedule-calendar.ics\"")
                .bodyValue(body));
    }
}

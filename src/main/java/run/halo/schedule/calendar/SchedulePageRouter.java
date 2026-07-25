package run.halo.schedule.calendar;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
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
import run.halo.app.theme.TemplateNameResolver;

@Component
@RequiredArgsConstructor
public class SchedulePageRouter {

    private final ScheduleQueryService scheduleQueryService;
    private final ScheduleCalendarSettingService settingService;
    private final TemplateNameResolver templateNameResolver;

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
        return scheduleQueryService.buildCalendarModel(start)
            .flatMap(model -> render(request, "public/calendar", model));
    }

    private Mono<ServerResponse> card(ServerRequest request) {
        return scheduleQueryService.buildCardModel(request.pathVariable("name"))
            .flatMap(model -> render(request, "public/card", model))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> ical(ServerRequest request) {
        return scheduleQueryService.exportPublicIcal()
            .flatMap(body -> ServerResponse.ok()
                .contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
                .header("Content-Disposition", "inline; filename=\"schedule-calendar.ics\"")
                .bodyValue(body));
    }

    private Mono<ServerResponse> render(ServerRequest request, String template, Map<String, Object> model) {
        return templateNameResolver.resolveTemplateNameOrDefault(request.exchange(), template)
            .flatMap(templateName -> ServerResponse.ok()
                .contentType(MediaType.TEXT_HTML)
                .render(templateName, model));
    }
}

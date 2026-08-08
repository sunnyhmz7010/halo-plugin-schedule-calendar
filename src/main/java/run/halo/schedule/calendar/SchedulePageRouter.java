package run.halo.schedule.calendar;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
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

    private static final String TEMPLATE_ID = "_templateId";

    private final ScheduleQueryService scheduleQueryService;
    private final ScheduleCalendarSettingService settingService;
    private final TemplateNameResolver templateNameResolver;

    @Bean
    RouterFunction<ServerResponse> schedulePageRouterFunction() {
        return RouterFunctions.route(
                RequestPredicates.GET(ScheduleCalendarRoutes.DEFAULT_PUBLIC_PAGE_PATH),
                this::page
            )
            .andRoute(
                RequestPredicates.GET(ScheduleCalendarRoutes.PUBLIC_CARD_PATH_PREFIX + "/{name}"),
                this::card
            )
            .andRoute(RequestPredicates.GET(ScheduleCalendarRoutes.DEFAULT_PUBLIC_ICAL_PATH), this::ical);
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
        return Mono.zip(
                scheduleQueryService.getWeekView(start),
                settingService.getSetting()
            )
            .flatMap(tuple -> {
                var view = tuple.getT1();
                var pageTitle = tuple.getT2().effectiveTitle();
                Map<String, Object> model = new HashMap<>();
                model.put("title", pageTitle);
                model.put("view", view);
                model.put(TEMPLATE_ID, "schedule-calendar");
                return render(request, "schedule-calendar", model);
            });
    }

    private Mono<ServerResponse> card(ServerRequest request) {
        return scheduleQueryService.getEntryCard(request.pathVariable("name"))
            .flatMap(card -> {
                Map<String, Object> model = new HashMap<>();
                model.put("title", card.title());
                model.put("color", card.color() == null || card.color().isBlank()
                    ? "#3b82f6" : card.color());
                model.put("summary", buildCardSummary(card));
                model.put("summaryClass", buildCardSummaryClass(card));
                model.put("nextOccurrenceLabel", blankToNull(card.nextOccurrenceLabel()));
                model.put("sourceLabel", blankToNull(card.sourceLabel()));
                model.put("location", blankToNull(card.location()));
                model.put("description", blankToNull(card.description()));
                model.put(TEMPLATE_ID, "schedule-calendar-card");
                return render(request, "schedule-calendar-card", model);
            })
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
            .flatMap(templateName -> ServerResponse.ok().render(templateName, model));
    }

    private static String buildCardSummary(ScheduleQueryService.ScheduleCardResponse card) {
        if (card.recurrenceDescription() == null || card.recurrenceDescription().isBlank()) {
            return card.startTime() + " - " + card.endTime();
        }
        return card.recurrenceDescription() + " · 首次 " + card.startTime() + " - " + card.endTime();
    }

    private static String buildCardSummaryClass(ScheduleQueryService.ScheduleCardResponse card) {
        return card.recurrenceDescription() == null || card.recurrenceDescription().isBlank()
            ? "entry-meta__item"
            : "entry-meta__item entry-meta__item--wide entry-meta__item--block";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

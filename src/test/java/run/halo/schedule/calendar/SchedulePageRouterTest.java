package run.halo.schedule.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;

@ExtendWith(MockitoExtension.class)
class SchedulePageRouterTest {

    @Mock
    ScheduleQueryService scheduleQueryService;

    @Mock
    ReactiveSettingFetcher settingFetcher;

    @Mock
    ReactiveExtensionClient client;

    private ScheduleCalendarSettingService settingService;

    @BeforeEach
    void setUp() {
        settingService = new ScheduleCalendarSettingService(settingFetcher, client);
    }

    @Test
    void publicPageRouteAlwaysMatches() {
        lenient().when(settingFetcher.fetch(eq(ScheduleCalendarSetting.GROUP), eq(ScheduleCalendarSetting.class)))
            .thenReturn(Mono.just(new ScheduleCalendarSetting("日程日历", null)));
        lenient().when(scheduleQueryService.getWeekView(null))
            .thenReturn(Mono.just(new ScheduleQueryService
                .WeekViewResponse(null, null, null, null, null, List.of(), null, null, null, null, null)));

        var routes = new SchedulePageRouter(scheduleQueryService, settingService)
            .schedulePageRouterFunction();

        var handler = routes.route(get(ScheduleCalendarRoutes.DEFAULT_PUBLIC_PAGE_PATH)).blockOptional();

        assertThat(handler).isPresent();
    }

    @Test
    void publicCardRouteAlwaysMatches() {
        lenient().when(scheduleQueryService.getEntryCard("lesson-1"))
            .thenReturn(Mono.just(new ScheduleQueryService
                .ScheduleCardResponse("lesson-1", "课程", null, null,
                    "09:00", "10:30", null, null, "#4285f4", null)));

        var routes = new SchedulePageRouter(scheduleQueryService, settingService)
            .schedulePageRouterFunction();

        var handler = routes.route(get(ScheduleCalendarRoutes.PUBLIC_CARD_PATH_PREFIX + "/lesson-1"))
            .blockOptional();

        assertThat(handler).isPresent();
    }

    @Test
    void publicIcalRouteAlwaysMatches() {
        lenient().when(scheduleQueryService.exportPublicIcal())
            .thenReturn(Mono.just("BEGIN:VCALENDAR"));

        var routes = new SchedulePageRouter(scheduleQueryService, settingService)
            .schedulePageRouterFunction();

        var handler = routes.route(get(ScheduleCalendarRoutes.DEFAULT_PUBLIC_ICAL_PATH)).blockOptional();

        assertThat(handler).isPresent();
    }

    private static MockServerRequest get(String path) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path));
        return MockServerRequest.builder()
            .method(HttpMethod.GET)
            .uri(URI.create(path))
            .exchange(exchange)
            .build();
    }
}

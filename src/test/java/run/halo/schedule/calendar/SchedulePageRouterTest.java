package run.halo.schedule.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.net.URI;
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
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.app.theme.TemplateNameResolver;

@ExtendWith(MockitoExtension.class)
class SchedulePageRouterTest {

    @Mock
    ScheduleQueryService scheduleQueryService;

    @Mock
    ReactiveSettingFetcher settingFetcher;

    @Mock
    ReactiveExtensionClient client;

    @Mock
    TemplateNameResolver templateNameResolver;

    private ScheduleCalendarSettingService settingService;

    @BeforeEach
    void setUp() {
        settingService = new ScheduleCalendarSettingService(settingFetcher, client);
        lenient().when(settingFetcher.fetch(eq(ScheduleCalendarSetting.GROUP), eq(ScheduleCalendarSetting.class)))
            .thenReturn(Mono.empty());
        lenient().when(client.fetch(eq(ConfigMap.class), eq("schedule-calendar-settings")))
            .thenReturn(Mono.empty());
    }

    @Test
    void publicPageRouteDoesNotMatchWhenPublicPageDisabled() {
        when(settingFetcher.fetch(eq(ScheduleCalendarSetting.GROUP), eq(ScheduleCalendarSetting.class)))
            .thenReturn(Mono.just(new ScheduleCalendarSetting(null, false, null)));

        var routes = new SchedulePageRouter(scheduleQueryService, settingService, templateNameResolver)
            .schedulePageRouterFunction();

        var handler = routes.route(get(ScheduleCalendarRoutes.DEFAULT_PUBLIC_PAGE_PATH)).blockOptional();

        assertThat(handler).isEmpty();
    }

    @Test
    void publicCardRouteDoesNotMatchWhenPublicPageDisabled() {
        when(settingFetcher.fetch(eq(ScheduleCalendarSetting.GROUP), eq(ScheduleCalendarSetting.class)))
            .thenReturn(Mono.just(new ScheduleCalendarSetting(null, false, null)));

        var routes = new SchedulePageRouter(scheduleQueryService, settingService, templateNameResolver)
            .schedulePageRouterFunction();

        var handler = routes.route(get(ScheduleCalendarRoutes.PUBLIC_CARD_PATH_PREFIX + "/lesson-1")).blockOptional();

        assertThat(handler).isEmpty();
    }

    @Test
    void publicPageRouteMatchesWhenPublicPageEnabled() {
        when(settingFetcher.fetch(eq(ScheduleCalendarSetting.GROUP), eq(ScheduleCalendarSetting.class)))
            .thenReturn(Mono.just(new ScheduleCalendarSetting(null, true, null)));

        var routes = new SchedulePageRouter(scheduleQueryService, settingService, templateNameResolver)
            .schedulePageRouterFunction();

        var handler = routes.route(get(ScheduleCalendarRoutes.DEFAULT_PUBLIC_PAGE_PATH)).blockOptional();

        assertThat(handler).isPresent();
    }

    @Test
    void publicIcalRouteStillMatchesWhenPublicPageDisabled() {
        var routes = new SchedulePageRouter(scheduleQueryService, settingService, templateNameResolver)
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

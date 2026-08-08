package run.halo.schedule.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.RenderingResponse;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.SystemInfo;
import run.halo.app.infra.SystemInfoGetter;
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

    @Mock
    SystemInfoGetter systemInfoGetter;

    private ScheduleCalendarSettingService settingService;

    @BeforeEach
    void setUp() {
        settingService = new ScheduleCalendarSettingService(settingFetcher, client);
        lenient().when(client.fetch(eq(ConfigMap.class), eq("schedule-calendar-settings")))
            .thenReturn(Mono.empty());
        lenient().when(systemInfoGetter.get())
            .thenReturn(Mono.just(new SystemInfo().setFavicon("/upload/site-favicon.png")));
    }

    @Test
    void publicPageRouteAlwaysMatches() {
        lenient().when(settingFetcher.fetch(eq(ScheduleCalendarSetting.GROUP), eq(ScheduleCalendarSetting.class)))
            .thenReturn(Mono.just(new ScheduleCalendarSetting("日程日历", null)));
        lenient().when(scheduleQueryService.getWeekView(null))
            .thenReturn(Mono.just(new ScheduleQueryService
                .WeekViewResponse(null, null, null, null, null, List.of(), null, null, null, null, null)));

        var routes = new SchedulePageRouter(scheduleQueryService, settingService, templateNameResolver,
            systemInfoGetter)
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

        var routes = new SchedulePageRouter(scheduleQueryService, settingService, templateNameResolver,
            systemInfoGetter)
            .schedulePageRouterFunction();

        var handler = routes.route(get(ScheduleCalendarRoutes.PUBLIC_CARD_PATH_PREFIX + "/lesson-1"))
            .blockOptional();

        assertThat(handler).isPresent();
    }

    @Test
    void publicIcalRouteAlwaysMatches() {
        lenient().when(scheduleQueryService.exportPublicIcal())
            .thenReturn(Mono.just("BEGIN:VCALENDAR"));

        var routes = new SchedulePageRouter(scheduleQueryService, settingService, templateNameResolver,
            systemInfoGetter)
            .schedulePageRouterFunction();

        var handler = routes.route(get(ScheduleCalendarRoutes.DEFAULT_PUBLIC_ICAL_PATH)).blockOptional();

        assertThat(handler).isPresent();
    }

    @Test
    void publicPageUsesResolvedTemplateNameForPluginFallback() throws Exception {
        var request = get(ScheduleCalendarRoutes.DEFAULT_PUBLIC_PAGE_PATH);
        lenient().when(settingFetcher.fetch(eq(ScheduleCalendarSetting.GROUP), eq(ScheduleCalendarSetting.class)))
            .thenReturn(Mono.just(new ScheduleCalendarSetting("日程日历", null)));
        lenient().when(scheduleQueryService.getWeekView(null))
            .thenReturn(Mono.just(new ScheduleQueryService
                .WeekViewResponse(null, null, null, null, null, List.of(), null, null, null, null, null)));
        when(templateNameResolver.resolveTemplateNameOrDefault(request.exchange(), "schedule-calendar"))
            .thenReturn(Mono.just("plugin:schedule-calendar:schedule-calendar"));

        var routes = new SchedulePageRouter(scheduleQueryService, settingService, templateNameResolver,
            systemInfoGetter)
            .schedulePageRouterFunction();
        var response = routes.route(request)
            .flatMap(handler -> handler.handle(request))
            .block();

        assertThat(response).isInstanceOf(RenderingResponse.class);
        assertThat(((RenderingResponse) response).name())
            .isEqualTo("plugin:schedule-calendar:schedule-calendar");
    }

    @Test
    void publicPageModelUsesSiteFavicon() throws Exception {
        var request = get(ScheduleCalendarRoutes.DEFAULT_PUBLIC_PAGE_PATH);
        lenient().when(settingFetcher.fetch(eq(ScheduleCalendarSetting.GROUP), eq(ScheduleCalendarSetting.class)))
            .thenReturn(Mono.just(new ScheduleCalendarSetting("日程日历", null)));
        lenient().when(scheduleQueryService.getWeekView(null))
            .thenReturn(Mono.just(new ScheduleQueryService
                .WeekViewResponse(null, null, null, null, null, List.of(), null, null, null, null, null)));
        when(templateNameResolver.resolveTemplateNameOrDefault(request.exchange(), "schedule-calendar"))
            .thenReturn(Mono.just("plugin:schedule-calendar:schedule-calendar"));
        when(systemInfoGetter.get())
            .thenReturn(Mono.just(new SystemInfo().setFavicon("/upload/custom-favicon.ico")));

        var routes = new SchedulePageRouter(scheduleQueryService, settingService, templateNameResolver,
            systemInfoGetter)
            .schedulePageRouterFunction();
        var response = routes.route(request)
            .flatMap(handler -> handler.handle(request))
            .block();

        assertThat(response).isInstanceOf(RenderingResponse.class);
        assertThat(((RenderingResponse) response).model())
            .containsEntry("favicon", "/upload/custom-favicon.ico");
    }

    @Test
    void publicCardUsesResolvedTemplateNameForPluginFallback() throws Exception {
        var request = get(ScheduleCalendarRoutes.PUBLIC_CARD_PATH_PREFIX + "/lesson-1",
            Map.of("name", "lesson-1"));
        lenient().when(scheduleQueryService.getEntryCard("lesson-1"))
            .thenReturn(Mono.just(new ScheduleQueryService
                .ScheduleCardResponse("lesson-1", "课程", null, null,
                    "09:00", "10:30", null, null, "#4285f4", null)));
        when(templateNameResolver.resolveTemplateNameOrDefault(request.exchange(), "schedule-calendar-card"))
            .thenReturn(Mono.just("plugin:schedule-calendar:schedule-calendar-card"));

        var routes = new SchedulePageRouter(scheduleQueryService, settingService, templateNameResolver,
            systemInfoGetter)
            .schedulePageRouterFunction();
        var response = routes.route(request)
            .flatMap(handler -> handler.handle(request))
            .block();

        assertThat(response).isInstanceOf(RenderingResponse.class);
        assertThat(((RenderingResponse) response).name())
            .isEqualTo("plugin:schedule-calendar:schedule-calendar-card");
    }

    private static MockServerRequest get(String path) {
        return get(path, Map.of());
    }

    private static MockServerRequest get(String path, Map<String, String> pathVariables) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path));
        return MockServerRequest.builder()
            .method(HttpMethod.GET)
            .uri(URI.create(path))
            .exchange(exchange)
            .pathVariables(pathVariables)
            .build();
    }
}

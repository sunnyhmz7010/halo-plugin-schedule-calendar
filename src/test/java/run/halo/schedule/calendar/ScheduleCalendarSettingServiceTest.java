package run.halo.schedule.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;

@ExtendWith(MockitoExtension.class)
class ScheduleCalendarSettingServiceTest {

    @Mock
    ReactiveSettingFetcher settingFetcher;

    @Mock
    ReactiveExtensionClient client;

    @Test
    void readsLegacyRawSettingWhenRemovedEnablePublicPageBreaksTypedFetch() {
        when(settingFetcher.fetch(eq(ScheduleCalendarSetting.GROUP), eq(ScheduleCalendarSetting.class)))
            .thenReturn(Mono.error(new IllegalArgumentException("Unknown property enablePublicPage")));
        var configMap = new ConfigMap();
        configMap.setData(Map.of(
            ScheduleCalendarSetting.GROUP,
            """
                {
                  "title": "旧配置标题",
                  "enablePublicPage": false,
                  "externalCalendars": [
                    {
                      "name": "节假日",
                      "icsUrl": "https://calendar.example/holiday.ics",
                      "enabled": true,
                      "color": "#4285f4"
                    }
                  ]
                }
                """
        ));
        when(client.fetch(eq(ConfigMap.class), eq("schedule-calendar-settings")))
            .thenReturn(Mono.just(configMap));

        var setting = new ScheduleCalendarSettingService(settingFetcher, client)
            .getSetting()
            .block();

        assertThat(setting).isNotNull();
        assertThat(setting.title()).isEqualTo("旧配置标题");
        assertThat(setting.externalCalendars()).singleElement().satisfies(source -> {
            assertThat(source.name()).isEqualTo("节假日");
            assertThat(source.icsUrl()).isEqualTo("https://calendar.example/holiday.ics");
            assertThat(source.enabled()).isTrue();
            assertThat(source.color()).isEqualTo("#4285f4");
        });
    }
}

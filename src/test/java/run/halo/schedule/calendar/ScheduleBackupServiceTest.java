package run.halo.schedule.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;

@ExtendWith(MockitoExtension.class)
class ScheduleBackupServiceTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ReactiveSettingFetcher settingFetcher;

    private final JsonMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void createsConfigMapWhenImportingSettingsWithoutExistingConfigMap() {
        var service = new ScheduleBackupService(client, settingFetcher);
        var payload = new ScheduleBackupService.ScheduleBackupPayload(
            "schedule.calendar.sunny.dev/v1alpha1",
            "ScheduleBackup",
            1,
            null,
            Map.of("public_page", objectMapper.createObjectNode().put("title", "测试标题")),
            List.of()
        );

        when(client.listAll(eq(ScheduleEntry.class), any(ListOptions.class), any()))
            .thenReturn(Flux.empty());
        when(client.fetch(ConfigMap.class, "schedule-calendar-settings"))
            .thenReturn(Mono.error(new RuntimeException("not found")));
        when(client.create(any(ConfigMap.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        var result = service.importBackup(payload).block();

        assertThat(result).isNotNull();
        assertThat(result.totalEntries()).isZero();
        assertThat(result.createdEntries()).isZero();
        assertThat(result.deletedEntries()).isZero();

        var captor = ArgumentCaptor.forClass(ConfigMap.class);
        verify(client).create(captor.capture());

        var configMap = captor.getValue();
        assertThat(configMap.getApiVersion()).isEqualTo("v1alpha1");
        assertThat(configMap.getKind()).isEqualTo("ConfigMap");
        assertThat(configMap.getMetadata().getName()).isEqualTo("schedule-calendar-settings");
        assertThat(configMap.getData()).containsEntry("public_page", "{\"title\":\"测试标题\"}");
    }

    @Test
    void exportBackupSanitizesLegacyAndNullPluginSettings() {
        var service = new ScheduleBackupService(client, settingFetcher);
        var publicPage = objectMapper.createObjectNode();
        publicPage.putNull("title");
        publicPage.put("publicIcalSubscriptionUrl", "https://example.com/schedule-calendar.ics");
        publicPage.putNull("slots");
        publicPage.put("unusedField", "ignored");

        var externalCalendars = objectMapper.createArrayNode();
        externalCalendars.add(objectMapper.createObjectNode()
            .put("name", "美国节假日")
            .put("icsUrl", "https://calendar.example/holiday.ics")
            .put("enabled", true)
            .put("color", "#4285f4")
            .putNull("slots")
            .putNull("title"));
        externalCalendars.add(objectMapper.createObjectNode()
            .put("name", "空链接")
            .put("icsUrl", "   "));
        publicPage.set("externalCalendars", externalCalendars);

        when(client.listAll(eq(ScheduleEntry.class), any(ListOptions.class), any()))
            .thenReturn(Flux.empty());
        when(settingFetcher.getValues())
            .thenReturn(Mono.just(Map.of(
                "public_page", publicPage,
                "unused_group", objectMapper.createObjectNode().put("value", "legacy")
            )));

        var result = service.exportBackup().block();

        assertThat(result).isNotNull();
        assertThat(result.entries()).isEmpty();

        Map<String, JsonNode> settings = result.settings();
        assertThat(settings).containsOnlyKeys("public_page");
        assertThat(settings.get("public_page")).isEqualTo(objectMapper.createObjectNode()
            .set("externalCalendars", objectMapper.createArrayNode()
                .add(objectMapper.createObjectNode()
                    .put("name", "美国节假日")
                    .put("icsUrl", "https://calendar.example/holiday.ics")
                    .put("enabled", true)
                    .put("color", "#4285f4"))));
    }
}

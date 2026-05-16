package run.halo.schedule.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void createsConfigMapWhenImportingSettingsWithoutExistingConfigMap() {
        var service = new ScheduleBackupService(client, settingFetcher);
        var objectMapper = JsonMapper.builder().findAndAddModules().build();
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
    void excludesNullAndEmptySettingValuesWhenExportingBackup() {
        var service = new ScheduleBackupService(client, settingFetcher);
        var objectMapper = JsonMapper.builder().findAndAddModules().build();
        var publicPage = objectMapper.createObjectNode();
        publicPage.putNull("title");
        publicPage.set("externalCalendars", objectMapper.createArrayNode());
        publicPage.put(SchedulePublicUrlService.DISPLAY_ONLY_PUBLIC_ICAL_URL_KEY, "/schedule-calendar.ics");
        when(settingFetcher.getValues()).thenReturn(Mono.just(Map.of("public_page", publicPage)));
        when(client.listAll(eq(ScheduleEntry.class), any(ListOptions.class), any()))
            .thenReturn(Flux.empty());

        var payload = service.exportBackup().block();

        assertThat(payload).isNotNull();
        assertThat(payload.settings()).doesNotContainKey("public_page");
    }
}

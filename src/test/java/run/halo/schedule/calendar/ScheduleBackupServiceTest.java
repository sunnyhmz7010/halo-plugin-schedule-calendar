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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;

@ExtendWith(MockitoExtension.class)
class ScheduleBackupServiceTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ScheduleSettingsService scheduleSettingsService;

    @Test
    void delegatesRawSettingsReplacementWhenImportingBackup() {
        var service = new ScheduleBackupService(client, scheduleSettingsService);
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
        when(scheduleSettingsService.replaceRawSettings(any())).thenReturn(Mono.empty());

        var result = service.importBackup(payload).block();

        assertThat(result).isNotNull();
        assertThat(result.totalEntries()).isZero();
        assertThat(result.createdEntries()).isZero();
        assertThat(result.deletedEntries()).isZero();
        verify(scheduleSettingsService).replaceRawSettings(any());
    }
}

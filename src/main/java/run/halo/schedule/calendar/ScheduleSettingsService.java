package run.halo.schedule.calendar;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

@Service
public class ScheduleSettingsService {
    static final String CONFIG_MAP_NAME = "schedule-calendar-settings";
    private static final String CORE_API_VERSION = "v1alpha1";
    private static final String CONFIG_MAP_KIND = "ConfigMap";

    private final ReactiveExtensionClient client;
    private final JsonMapper objectMapper;

    public ScheduleSettingsService(ReactiveExtensionClient client) {
        this.client = client;
        this.objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();
    }

    Mono<Map<String, JsonNode>> getRawSettings() {
        return client.fetch(ConfigMap.class, CONFIG_MAP_NAME)
            .map(configMap -> decodeSettings(configMap.getData()))
            .onErrorResume(throwable -> Mono.just(Map.of()));
    }

    Mono<ScheduleCalendarSetting> getPublicPageSetting() {
        return getRawSettings()
            .map(settings -> toPublicPageSetting(settings.get(ScheduleCalendarSetting.GROUP)));
    }

    Mono<Void> updatePublicPageSetting(ScheduleCalendarSetting setting) {
        var safeSetting = setting == null ? new ScheduleCalendarSetting(null) : setting;
        return getRawSettings()
            .flatMap(settings -> {
                var next = new LinkedHashMap<>(settings);
                next.put(ScheduleCalendarSetting.GROUP, objectMapper.valueToTree(safeSetting));
                return replaceRawSettings(next);
            });
    }

    Mono<Void> replaceRawSettings(Map<String, JsonNode> settings) {
        var data = encodeSettings(settings == null ? Map.of() : settings);
        return client.fetch(ConfigMap.class, CONFIG_MAP_NAME)
            .flatMap(configMap -> {
                configMap.setData(data);
                return client.update(configMap).then();
            })
            .onErrorResume(throwable -> {
                var configMap = newConfigMap();
                configMap.setData(data);
                return client.create(configMap).then();
            });
    }

    private ScheduleCalendarSetting toPublicPageSetting(JsonNode node) {
        if (node == null || node.isNull()) {
            return new ScheduleCalendarSetting(null);
        }
        try {
            return objectMapper.treeToValue(node, ScheduleCalendarSetting.class);
        } catch (JsonProcessingException exception) {
            return new ScheduleCalendarSetting(null);
        }
    }

    private Map<String, JsonNode> decodeSettings(Map<String, String> data) {
        if (data == null || data.isEmpty()) {
            return Map.of();
        }

        var decoded = new LinkedHashMap<String, JsonNode>();
        data.forEach((group, value) -> {
            if (group == null || group.isBlank() || value == null || value.isBlank()) {
                return;
            }
            try {
                decoded.put(group, objectMapper.readTree(value));
            } catch (JsonProcessingException ignored) {
                // Ignore malformed setting entries and fall back to defaults.
            }
        });
        return decoded;
    }

    private Map<String, String> encodeSettings(Map<String, JsonNode> settings) {
        var encoded = new LinkedHashMap<String, String>();
        settings.forEach((group, value) -> {
            if (group == null || group.isBlank() || value == null || value.isNull()) {
                return;
            }
            try {
                encoded.put(group, objectMapper.writeValueAsString(value));
            } catch (JsonProcessingException ignored) {
                // Skip invalid setting values instead of failing the whole write.
            }
        });
        return encoded;
    }

    private ConfigMap newConfigMap() {
        var metadata = new Metadata();
        metadata.setName(CONFIG_MAP_NAME);

        var configMap = new ConfigMap();
        configMap.setApiVersion(CORE_API_VERSION);
        configMap.setKind(CONFIG_MAP_KIND);
        configMap.setMetadata(metadata);
        return configMap;
    }
}

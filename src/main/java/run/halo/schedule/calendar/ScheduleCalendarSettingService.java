package run.halo.schedule.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.Map;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;

@Service
public class ScheduleCalendarSettingService {
    private static final String CONFIG_MAP_NAME = "schedule-calendar-settings";
    private final ReactiveSettingFetcher settingFetcher;
    private final ReactiveExtensionClient client;
    private final JsonMapper objectMapper;

    public ScheduleCalendarSettingService(ReactiveSettingFetcher settingFetcher, ReactiveExtensionClient client) {
        this.settingFetcher = settingFetcher;
        this.client = client;
        this.objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();
    }

    Mono<ScheduleCalendarSetting> getSetting() {
        return settingFetcher.fetch(ScheduleCalendarSetting.GROUP, ScheduleCalendarSetting.class)
            .defaultIfEmpty(new ScheduleCalendarSetting(null, null))
            .flatMap(this::mergeLegacyExternalCalendarsIfNeeded);
    }

    private Mono<ScheduleCalendarSetting> mergeLegacyExternalCalendarsIfNeeded(ScheduleCalendarSetting setting) {
        if (setting != null && setting.externalCalendars() != null && !setting.externalCalendars().isEmpty()) {
            return Mono.just(setting);
        }

        return loadRawConfig()
            .map(rawConfig -> mergeFromRawConfig(
                setting,
                rawConfig.get(ScheduleCalendarSetting.GROUP),
                rawConfig
            ))
            .onErrorReturn(setting == null ? new ScheduleCalendarSetting(null, null) : setting);
    }

    private Mono<JsonNode> loadRawConfig() {
        return client.fetch(ConfigMap.class, CONFIG_MAP_NAME)
            .mapNotNull(ConfigMap::getData)
            .map(this::toRawConfigNode)
            .defaultIfEmpty(objectMapper.createObjectNode());
    }

    private JsonNode toRawConfigNode(Map<String, String> rawData) {
        var root = objectMapper.createObjectNode();
        if (rawData == null || rawData.isEmpty()) {
            return root;
        }

        rawData.forEach((key, rawValue) -> {
            if (key == null || key.isBlank() || rawValue == null) {
                return;
            }
            root.set(key, toJsonNode(rawValue));
        });
        return root;
    }

    private JsonNode toJsonNode(String rawValue) {
        var trimmed = rawValue.trim();
        if (trimmed.isEmpty()) {
            return objectMapper.getNodeFactory().textNode(rawValue);
        }

        try {
            return objectMapper.readTree(trimmed);
        } catch (Exception ignored) {
            return objectMapper.getNodeFactory().textNode(rawValue);
        }
    }

    private ScheduleCalendarSetting mergeFromRawConfig(ScheduleCalendarSetting base, JsonNode rawGroup,
        JsonNode rawRoot) {
        var fallback = base == null ? new ScheduleCalendarSetting(null, null) : base;
        var merged = mergeFromNode(fallback, rawGroup);
        return hasExternalCalendars(merged) ? merged : mergeFromNode(merged, rawRoot);
    }

    private ScheduleCalendarSetting mergeFromNode(ScheduleCalendarSetting base, JsonNode rawNode) {
        var fallback = base == null ? new ScheduleCalendarSetting(null, null) : base;
        if (rawNode == null || rawNode.isNull() || !rawNode.isObject()) {
            return fallback;
        }
        try {
            var rawSetting = objectMapper.treeToValue(rawNode, ScheduleCalendarSetting.class);
            if (rawSetting == null) {
                return fallback;
            }

            var title = rawSetting.title() == null || rawSetting.title().isBlank()
                ? fallback.title()
                : rawSetting.title();
            var externalCalendars = rawSetting.externalCalendars() == null || rawSetting.externalCalendars().isEmpty()
                ? fallback.externalCalendars()
                : rawSetting.externalCalendars();
            return new ScheduleCalendarSetting(title, externalCalendars);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private boolean hasExternalCalendars(ScheduleCalendarSetting setting) {
        return setting != null
            && setting.externalCalendars() != null
            && !setting.externalCalendars().isEmpty();
    }
}

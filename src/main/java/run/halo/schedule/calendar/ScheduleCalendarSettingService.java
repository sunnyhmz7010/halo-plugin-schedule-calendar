package run.halo.schedule.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.Map;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;

@Service
public class ScheduleCalendarSettingService {
    private final ReactiveSettingFetcher settingFetcher;
    private final JsonMapper objectMapper;

    public ScheduleCalendarSettingService(ReactiveSettingFetcher settingFetcher) {
        this.settingFetcher = settingFetcher;
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

        return settingFetcher.getValues()
            .defaultIfEmpty(Map.of())
            .map(values -> mergeFromRawConfig(setting, values.get(ScheduleCalendarSetting.GROUP)))
            .onErrorReturn(setting == null ? new ScheduleCalendarSetting(null, null) : setting);
    }

    private ScheduleCalendarSetting mergeFromRawConfig(ScheduleCalendarSetting base, JsonNode rawGroup) {
        var fallback = base == null ? new ScheduleCalendarSetting(null, null) : base;
        if (rawGroup == null || rawGroup.isNull() || !rawGroup.isObject()) {
            return fallback;
        }

        try {
            var rawSetting = objectMapper.treeToValue(rawGroup, ScheduleCalendarSetting.class);
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
}

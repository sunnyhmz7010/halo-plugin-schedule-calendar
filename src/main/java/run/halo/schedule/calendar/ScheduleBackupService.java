package run.halo.schedule.calendar;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;

@Service
public class ScheduleBackupService {
    static final String BACKUP_API_VERSION = "schedule.calendar.sunny.dev/v1alpha1";
    static final String BACKUP_KIND = "ScheduleBackup";
    static final int BACKUP_SCHEMA_VERSION = 1;
    private static final java.util.Set<String> DISPLAY_ONLY_SETTING_KEYS =
        java.util.Set.of(SchedulePublicUrlService.DISPLAY_ONLY_PUBLIC_ICAL_URL_KEY);
    private static final String CONFIG_MAP_NAME = "schedule-calendar-settings";
    private static final String CORE_API_VERSION = "v1alpha1";
    private static final String CONFIG_MAP_KIND = "ConfigMap";

    private final ReactiveExtensionClient client;
    private final ReactiveSettingFetcher settingFetcher;
    private final JsonMapper objectMapper;

    public ScheduleBackupService(ReactiveExtensionClient client, ReactiveSettingFetcher settingFetcher) {
        this.client = client;
        this.settingFetcher = settingFetcher;
        this.objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();
    }

    Mono<ScheduleBackupPayload> exportBackup() {
        return Mono.zip(
                listEntries(),
                settingFetcher.getValues().switchIfEmpty(Mono.just(Map.of()))
            )
            .map(tuple -> new ScheduleBackupPayload(
                BACKUP_API_VERSION,
                BACKUP_KIND,
                BACKUP_SCHEMA_VERSION,
                OffsetDateTime.now(),
                sanitizeSettings(tuple.getT2()),
                tuple.getT1().stream()
                    .map(this::toBackupEntry)
                    .toList()
            ));
    }

    Mono<ScheduleBackupImportResult> importBackup(ScheduleBackupPayload payload) {
        validatePayload(payload);
        var importedEntries = normalizeEntries(payload.entries());
        var importedSettings = payload.settings() == null ? Map.<String, JsonNode>of() : payload.settings();
        return listEntries()
            .flatMap(existingEntries -> upsertEntries(existingEntries, importedEntries)
                .flatMap(result -> importSettings(importedSettings)
                    .thenReturn(result)));
    }

    private Mono<List<ScheduleEntry>> listEntries() {
        return client.listAll(ScheduleEntry.class, ListOptions.builder().build(), Sort.unsorted())
            .collectList();
    }

    private ScheduleBackupEntry toBackupEntry(ScheduleEntry entry) {
        var metadata = entry.getMetadata();
        return new ScheduleBackupEntry(
            metadata == null ? null : metadata.getName(),
            copySpec(entry.getSpec())
        );
    }

    private void validatePayload(ScheduleBackupPayload payload) {
        if (payload == null) {
            throw badRequest("备份内容为空。");
        }
        if (payload.schemaVersion() != BACKUP_SCHEMA_VERSION) {
            throw badRequest("不支持的备份版本。");
        }
        if (payload.entries() == null) {
            throw badRequest("备份文件缺少事项数据。");
        }
    }

    private List<ScheduleBackupEntry> normalizeEntries(List<ScheduleBackupEntry> entries) {
        var seenNames = new LinkedHashMap<String, ScheduleBackupEntry>();
        for (var entry : entries) {
            if (entry == null || entry.name() == null || entry.name().isBlank()) {
                throw badRequest("备份文件中的事项名称无效。");
            }
            if (entry.spec() == null || entry.spec().getTitle() == null || entry.spec().getTitle().isBlank()) {
                throw badRequest("备份文件中的事项缺少标题。");
            }
            if (entry.spec().getStartTime() == null || entry.spec().getEndTime() == null) {
                throw badRequest("备份文件中的事项缺少时间信息。");
            }
            if (seenNames.containsKey(entry.name())) {
                throw badRequest("备份文件中存在重复事项名称：" + entry.name());
            }
            seenNames.put(entry.name(), new ScheduleBackupEntry(entry.name(), copySpec(entry.spec())));
        }
        return new ArrayList<>(seenNames.values());
    }

    private Mono<ScheduleBackupImportResult> upsertEntries(List<ScheduleEntry> existingEntries,
        List<ScheduleBackupEntry> importedEntries) {
        Map<String, ScheduleEntry> existingByName = existingEntries.stream()
            .filter(Objects::nonNull)
            .filter(entry -> entry.getMetadata() != null && entry.getMetadata().getName() != null)
            .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getMetadata().getName(), entry),
                LinkedHashMap::putAll);
        var importedNames = importedEntries.stream()
            .map(ScheduleBackupEntry::name)
            .collect(java.util.stream.Collectors.toSet());

        return Flux.fromIterable(importedEntries)
            .concatMap(importedEntry -> {
                var existing = existingByName.get(importedEntry.name());
                if (existing != null) {
                    existing.setSpec(copySpec(importedEntry.spec()));
                    return client.update(existing).thenReturn(false);
                }

                return client.create(toScheduleEntry(importedEntry)).thenReturn(true);
            })
            .collectList()
            .flatMap(results -> Flux.fromIterable(existingEntries)
                .filter(entry -> entry.getMetadata() != null && entry.getMetadata().getName() != null)
                .filter(entry -> !importedNames.contains(entry.getMetadata().getName()))
                .concatMap(client::delete)
                .count()
                .map(deletedCount -> new ScheduleBackupImportResult(
                    importedEntries.size(),
                    results.stream().filter(Boolean::booleanValue).count(),
                    deletedCount
                )));
    }

    private Mono<Void> importSettings(Map<String, JsonNode> settings) {
        var data = encodeSettings(settings);
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

    private Map<String, JsonNode> sanitizeSettings(Map<String, JsonNode> settings) {
        var sanitized = new LinkedHashMap<String, JsonNode>();
        settings.forEach((group, value) -> {
            var cleaned = sanitizeNode(value);
            if (group == null || group.isBlank() || cleaned == null || cleaned.isNull()) {
                return;
            }
            if (cleaned.isObject() && cleaned.isEmpty()) {
                return;
            }
            if (cleaned.isArray() && cleaned.isEmpty()) {
                return;
            }
            sanitized.put(group, cleaned);
        });
        return sanitized;
    }

    private JsonNode sanitizeNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        if (node.isObject()) {
            var source = (ObjectNode) node;
            var cleaned = objectMapper.createObjectNode();
            source.fields().forEachRemaining(entry -> {
                if (DISPLAY_ONLY_SETTING_KEYS.contains(entry.getKey())) {
                    return;
                }
                var value = sanitizeNode(entry.getValue());
                if (value == null || value.isNull()) {
                    return;
                }
                if (value.isObject() && value.isEmpty()) {
                    return;
                }
                if (value.isArray() && value.isEmpty()) {
                    return;
                }
                cleaned.set(entry.getKey(), value);
            });
            return cleaned;
        }

        if (node.isArray()) {
            var cleaned = objectMapper.createArrayNode();
            for (var item : (ArrayNode) node) {
                var value = sanitizeNode(item);
                if (value == null || value.isNull()) {
                    continue;
                }
                if (value.isObject() && value.isEmpty()) {
                    continue;
                }
                if (value.isArray() && value.isEmpty()) {
                    continue;
                }
                cleaned.add(value);
            }
            return cleaned;
        }

        return node;
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

    private ScheduleEntry toScheduleEntry(ScheduleBackupEntry backupEntry) {
        var metadata = new Metadata();
        metadata.setName(backupEntry.name());

        var entry = new ScheduleEntry();
        entry.setApiVersion(BACKUP_API_VERSION);
        entry.setKind("ScheduleEntry");
        entry.setMetadata(metadata);
        entry.setSpec(copySpec(backupEntry.spec()));
        return entry;
    }

    private ScheduleEntry.Spec copySpec(ScheduleEntry.Spec source) {
        if (source == null) {
            return null;
        }
        var spec = new ScheduleEntry.Spec();
        spec.setTitle(source.getTitle());
        spec.setDescription(source.getDescription());
        spec.setLocation(source.getLocation());
        spec.setStartTime(source.getStartTime());
        spec.setEndTime(source.getEndTime());
        spec.setColor(source.getColor());
        spec.setRecurrence(copyRecurrence(source.getRecurrence()));
        return spec;
    }

    private ScheduleEntry.Recurrence copyRecurrence(ScheduleEntry.Recurrence source) {
        if (source == null) {
            return null;
        }
        var recurrence = new ScheduleEntry.Recurrence();
        recurrence.setFrequency(source.getFrequency());
        recurrence.setInterval(source.getInterval());
        recurrence.setUntil(source.getUntil());
        return recurrence;
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    public record ScheduleBackupPayload(String apiVersion, String kind, int schemaVersion,
                                        OffsetDateTime exportedAt, Map<String, JsonNode> settings,
                                        List<ScheduleBackupEntry> entries) {
    }

    public record ScheduleBackupEntry(String name, ScheduleEntry.Spec spec) {
    }

    public record ScheduleBackupImportResult(long totalEntries, long createdEntries, long deletedEntries) {
    }
}

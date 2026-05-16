package run.halo.schedule.calendar;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.ExternalLinkProcessor;

@Service
public class SchedulePublicUrlService {
    static final String DISPLAY_ONLY_PUBLIC_ICAL_URL_KEY = "publicIcalSubscriptionUrl";
    private static final String CONFIG_MAP_NAME = "schedule-calendar-settings";
    private static final String CORE_API_VERSION = "v1alpha1";
    private static final String CONFIG_MAP_KIND = "ConfigMap";

    private final ExternalLinkProcessor externalLinkProcessor;
    private final ReactiveExtensionClient client;
    private final JsonMapper objectMapper;

    public SchedulePublicUrlService(ExternalLinkProcessor externalLinkProcessor,
        ReactiveExtensionClient client) {
        this.externalLinkProcessor = externalLinkProcessor;
        this.client = client;
        this.objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();
    }

    Mono<PublicUrls> resolvePublicUrls() {
        return Mono.zip(
            externalLinkProcessor.processLink(URI.create(ScheduleCalendarRoutes.DEFAULT_PUBLIC_PAGE_PATH)),
            externalLinkProcessor.processLink(URI.create(ScheduleCalendarRoutes.DEFAULT_PUBLIC_ICAL_PATH))
        ).map(tuple -> new PublicUrls(tuple.getT1().toString(), tuple.getT2().toString()));
    }

    Mono<Void> syncDisplayOnlySettings() {
        return resolvePublicUrls()
            .flatMap(this::upsertDisplayOnlySettings);
    }

    private Mono<Void> upsertDisplayOnlySettings(PublicUrls publicUrls) {
        return client.fetch(ConfigMap.class, CONFIG_MAP_NAME)
            .flatMap(configMap -> updateConfigMap(configMap, publicUrls)
                .flatMap(changed -> changed ? client.update(configMap).then() : Mono.empty()))
            .onErrorResume(throwable -> {
                var configMap = newConfigMap();
                return updateConfigMap(configMap, publicUrls)
                    .flatMap(changed -> changed ? client.create(configMap).then() : Mono.empty());
            });
    }

    private Mono<Boolean> updateConfigMap(ConfigMap configMap, PublicUrls publicUrls) {
        var data = configMap.getData() == null
            ? new LinkedHashMap<String, String>()
            : new LinkedHashMap<>(configMap.getData());
        var publicPage = parsePublicPageSettings(data.get(ScheduleCalendarSetting.GROUP));
        var currentValue = publicPage.path(DISPLAY_ONLY_PUBLIC_ICAL_URL_KEY).asText(null);
        if (publicUrls.publicIcalUrl().equals(currentValue)) {
            return Mono.just(false);
        }

        publicPage.put(DISPLAY_ONLY_PUBLIC_ICAL_URL_KEY, publicUrls.publicIcalUrl());
        try {
            data.put(ScheduleCalendarSetting.GROUP, objectMapper.writeValueAsString(publicPage));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
        configMap.setData(data);
        return Mono.just(true);
    }

    private ObjectNode parsePublicPageSettings(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return objectMapper.createObjectNode();
        }

        try {
            var node = objectMapper.readTree(rawValue);
            if (node instanceof ObjectNode objectNode) {
                return objectNode.deepCopy();
            }
        } catch (JsonProcessingException ignored) {
            // Fall back to a new object when the persisted value is invalid JSON.
        }

        return objectMapper.createObjectNode();
    }

    private ConfigMap newConfigMap() {
        var metadata = new Metadata();
        metadata.setName(CONFIG_MAP_NAME);

        var configMap = new ConfigMap();
        configMap.setApiVersion(CORE_API_VERSION);
        configMap.setKind(CONFIG_MAP_KIND);
        configMap.setMetadata(metadata);
        configMap.setData(new LinkedHashMap<>());
        return configMap;
    }

    public record PublicUrls(String publicPageUrl, String publicIcalUrl) {
    }
}

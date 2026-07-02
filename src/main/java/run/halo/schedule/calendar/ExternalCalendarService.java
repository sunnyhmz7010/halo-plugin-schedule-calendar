package run.halo.schedule.calendar;

import java.io.BufferedReader;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ExternalCalendarService {
    private static final Logger log = LoggerFactory.getLogger(ExternalCalendarService.class);
    private static final DateTimeFormatter LOCAL_DATE_TIME =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter LOCAL_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int MAX_EXPANSION_STEPS = 10000;
    private static final Pattern SAFE_COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    
    private static final Map<String, String> WINDOWS_TO_IANA_TZ_MAP = new HashMap<>();
    static {
        // Windows timezone to IANA timezone mapping (common ones)
        WINDOWS_TO_IANA_TZ_MAP.put("China Standard Time", "Asia/Shanghai");
        WINDOWS_TO_IANA_TZ_MAP.put("Eastern Standard Time", "America/New_York");
        WINDOWS_TO_IANA_TZ_MAP.put("Pacific Standard Time", "America/Los_Angeles");
        WINDOWS_TO_IANA_TZ_MAP.put("GMT Standard Time", "Europe/London");
        WINDOWS_TO_IANA_TZ_MAP.put("Tokyo Standard Time", "Asia/Tokyo");
        WINDOWS_TO_IANA_TZ_MAP.put("Korea Standard Time", "Asia/Seoul");
        WINDOWS_TO_IANA_TZ_MAP.put("India Standard Time", "Asia/Kolkata");
        WINDOWS_TO_IANA_TZ_MAP.put("Central European Standard Time", "Europe/Berlin");
        WINDOWS_TO_IANA_TZ_MAP.put("Mountain Standard Time", "America/Denver");
        WINDOWS_TO_IANA_TZ_MAP.put("Central Standard Time", "America/Chicago");
        WINDOWS_TO_IANA_TZ_MAP.put("Alaskan Standard Time", "America/Anchorage");
        WINDOWS_TO_IANA_TZ_MAP.put("Hawaiian Standard Time", "Pacific/Honolulu");
        WINDOWS_TO_IANA_TZ_MAP.put("Singapore Standard Time", "Asia/Singapore");
        WINDOWS_TO_IANA_TZ_MAP.put("Taipei Standard Time", "Asia/Taipei");
        WINDOWS_TO_IANA_TZ_MAP.put("W. Australia Standard Time", "Australia/Perth");
        WINDOWS_TO_IANA_TZ_MAP.put("AUS Eastern Standard Time", "Australia/Sydney");
        WINDOWS_TO_IANA_TZ_MAP.put("New Zealand Standard Time", "Pacific/Auckland");
        // Add more as needed
    }

    private static final Set<String> ALLOWED_PROTOCOLS = Set.of("https");

    private final HttpClient httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private final Map<String, CachedEvents> eventCache = new ConcurrentHashMap<>();
    private final Set<String> refreshInProgress = ConcurrentHashMap.newKeySet();

    Mono<List<ScheduleEventOccurrence>> listOccurrences(ScheduleCalendarSetting setting, LocalDate rangeStart,
        LocalDate rangeEnd, ZoneId zoneId) {
        var sources = setting.enabledExternalCalendars();
        if (sources.isEmpty()) {
            log.debug("No enabled external calendars found for range {} to {}", rangeStart, rangeEnd);
            return Mono.just(List.of());
        }

        var start = rangeStart.atStartOfDay();
        var end = rangeEnd.plusDays(1).atStartOfDay();
        log.debug("Loading {} external calendars for range {} to {}", sources.size(), rangeStart, rangeEnd);
        return Flux.fromIterable(sources)
            .flatMap(source -> loadEvents(source)
                .map(events -> {
                    log.debug("Fetched {} raw external events from {}", events.size(), source.effectiveName());
                    var expanded = expandEvents(events, source, start, end, zoneId);
                    log.debug("Expanded {} occurrences from {} for range {} to {}",
                        expanded.size(), source.effectiveName(), rangeStart, rangeEnd);
                    return expanded;
                })
                .onErrorResume(error -> {
                    log.warn("Failed to load external calendar {}", source.effectiveName(), error);
                    return Mono.just(List.of());
                }))
            .collectList()
            .map(lists -> lists.stream()
                .flatMap(List::stream)
                .sorted(java.util.Comparator.comparing(ScheduleEventOccurrence::start))
                .collect(Collectors.toList()));
    }

    Mono<ExternalCalendarValidationResult> validateSource(String name, String icsUrl, String color) {
        if (icsUrl == null || icsUrl.isBlank()) {
            return Mono.just(new ExternalCalendarValidationResult(false, "ICS 订阅地址不能为空。", 0));
        }
        var urlError = validateUrl(icsUrl);
        if (urlError != null) {
            return Mono.just(new ExternalCalendarValidationResult(false, urlError, 0));
        }
        var source = new ScheduleCalendarSetting.ExternalCalendarSource(name, icsUrl, true, color);
        return refreshEvents(source)
            .map(events -> new ExternalCalendarValidationResult(true, null, events.size()))
            .onErrorResume(error -> Mono.just(new ExternalCalendarValidationResult(
                false,
                validationMessage(error),
                0
            )));
    }

    Mono<ExternalCalendarRefreshResult> refreshSources(ScheduleCalendarSetting setting) {
        var sources = setting.enabledExternalCalendars();
        if (sources.isEmpty()) {
            return Mono.just(new ExternalCalendarRefreshResult(0, 0, 0, List.of()));
        }

        return Flux.fromIterable(sources)
            .flatMap(source -> refreshEvents(source)
                .map(events -> new ExternalCalendarRefreshItem(
                    source.effectiveName(),
                    source.icsUrl(),
                    true,
                    null,
                    events.size()
                ))
                .onErrorResume(error -> Mono.just(new ExternalCalendarRefreshItem(
                    source.effectiveName(),
                    source.icsUrl(),
                    false,
                    validationMessage(error),
                    0
                ))))
            .collectList()
            .map(items -> new ExternalCalendarRefreshResult(
                sources.size(),
                (int) items.stream().filter(ExternalCalendarRefreshItem::success).count(),
                items.stream().mapToInt(ExternalCalendarRefreshItem::eventCount).sum(),
                items
            ));
    }

    private Mono<List<ExternalCalendarEvent>> loadEvents(ScheduleCalendarSetting.ExternalCalendarSource source) {
        var cacheKey = cacheKey(source);
        var cached = eventCache.get(cacheKey);
        if (cached != null) {
            if (!cached.isExpired()) {
                return Mono.just(cached.events());
            }
            refreshEventsInBackground(source);
            return Mono.just(cached.events());
        }
        return refreshEvents(source);
    }

    private void refreshEventsInBackground(ScheduleCalendarSetting.ExternalCalendarSource source) {
        var cacheKey = cacheKey(source);
        if (!refreshInProgress.add(cacheKey)) {
            return;
        }
        refreshEvents(source)
            .doFinally(signalType -> refreshInProgress.remove(cacheKey))
            .subscribe(
                ignored -> {
                },
                error -> log.warn("Failed to refresh cached external calendar {}", source.effectiveName(), error)
            );
    }

    private Mono<List<ExternalCalendarEvent>> refreshEvents(ScheduleCalendarSetting.ExternalCalendarSource source) {
        var cacheKey = cacheKey(source);
        return fetchEvents(source)
            .doOnNext(events -> eventCache.put(cacheKey, new CachedEvents(events, Instant.now())));
    }

    private Mono<List<ExternalCalendarEvent>> fetchEvents(ScheduleCalendarSetting.ExternalCalendarSource source) {
        var url = source.icsUrl();
        var urlError = validateUrl(url);
        if (urlError != null) {
            return Mono.error(new IllegalStateException(urlError));
        }
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "halo-plugin-schedule-calendar")
                .GET()
                .build();
        } catch (IllegalArgumentException ex) {
            return Mono.error(new IllegalStateException("Invalid ICS URL: " + url, ex));
        }

        return Mono.fromFuture(httpClient.sendAsync(request, BodyHandlers.ofString(StandardCharsets.UTF_8)))
            .flatMap(response -> {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    return Mono.error(new IllegalStateException(
                        "Failed to fetch ICS feed, status " + response.statusCode()));
                }
                return Mono.just(parseEvents(response.body()));
            });
    }

    private String cacheKey(ScheduleCalendarSetting.ExternalCalendarSource source) {
        return source.icsUrl() == null ? "" : source.icsUrl().trim();
    }

    private String validationMessage(Throwable error) {
        var message = error.getMessage();
        if (message == null || message.isBlank()) {
            return "无法拉取或解析该 iCal / ICS 订阅地址，请检查链接是否可公开访问。";
        }
        if (message.contains("Invalid ICS URL")) {
            return "ICS 订阅地址格式无效。";
        }
        if (message.contains("status")) {
            return "订阅地址返回异常：" + message + "。";
        }
        return "无法拉取或解析该 iCal / ICS 订阅地址：" + message;
    }

    private List<ScheduleEventOccurrence> expandEvents(List<ExternalCalendarEvent> events,
        ScheduleCalendarSetting.ExternalCalendarSource source, LocalDateTime rangeStart, LocalDateTime rangeEnd,
        ZoneId zoneId) {
        return events.stream()
            .flatMap(event -> expandEvent(event, source, rangeStart, rangeEnd, zoneId).stream())
            .toList();
    }

    private List<ScheduleEventOccurrence> expandEvent(ExternalCalendarEvent event,
        ScheduleCalendarSetting.ExternalCalendarSource source, LocalDateTime rangeStart, LocalDateTime rangeEnd,
        ZoneId zoneId) {
        var start = toOccurrenceDateTime(event.start(), event.allDay(), zoneId);
        var end = toOccurrenceDateTime(event.end(), event.allDay(), zoneId);
        if (!end.isAfter(start)) {
            return List.of();
        }

        var recurrence = event.recurrence();
        if (recurrence == null || recurrence.frequency() == ScheduleEntry.RecurrenceFrequency.NONE) {
            if (!overlaps(start, end, rangeStart, rangeEnd)) {
                return List.of();
            }
            return List.of(toOccurrence(event, source, start, end));
        }

        var occurrences = new ArrayList<ScheduleEventOccurrence>();
        var duration = Duration.between(start, end);
        var cursor = start;
        var occurrenceCount = 1;
        while (cursor.isBefore(rangeEnd) && occurrenceCount <= MAX_EXPANSION_STEPS) {
            if (recurrence.count() != null && occurrenceCount > recurrence.count()) {
                break;
            }
            if (recurrence.until() != null && cursor.toLocalDate().isAfter(recurrence.until())) {
                break;
            }

            if (!isExcluded(event.excludedStarts(), cursor) && overlaps(cursor, cursor.plus(duration), rangeStart,
                rangeEnd)) {
                occurrences.add(toOccurrence(event, source, cursor, cursor.plus(duration)));
            }

            cursor = advance(cursor, recurrence.frequency(), recurrence.interval());
            occurrenceCount++;
        }
        return occurrences;
    }

    private boolean overlaps(LocalDateTime start, LocalDateTime end, LocalDateTime rangeStart,
        LocalDateTime rangeEnd) {
        return end.isAfter(rangeStart) && start.isBefore(rangeEnd);
    }

    private boolean isExcluded(Set<LocalDateTime> excludedStarts, LocalDateTime start) {
        return excludedStarts.contains(start.truncatedTo(ChronoUnit.MINUTES))
            || excludedStarts.contains(start.truncatedTo(ChronoUnit.SECONDS))
            || excludedStarts.contains(start);
    }

    private ScheduleEventOccurrence toOccurrence(ExternalCalendarEvent event,
        ScheduleCalendarSetting.ExternalCalendarSource source, LocalDateTime start, LocalDateTime end) {
        return new ScheduleEventOccurrence(
            event.uid(),
            event.title(),
            event.description(),
            event.location(),
            recurrenceDescription(event.recurrence()),
            event.effectiveColor(source.color()),
            start,
            end,
            source.effectiveName()
        );
    }

    private List<ExternalCalendarEvent> parseEvents(String body) {
        var unfoldedLines = unfoldLines(body);
        var events = new ArrayList<ExternalCalendarEvent>();
        List<String> currentEventLines = null;
        for (var line : unfoldedLines) {
            if ("BEGIN:VEVENT".equalsIgnoreCase(line)) {
                currentEventLines = new ArrayList<>();
                continue;
            }
            if ("END:VEVENT".equalsIgnoreCase(line)) {
                if (currentEventLines != null) {
                    var event = parseEvent(currentEventLines);
                    if (event != null) {
                        events.add(event);
                    }
                }
                currentEventLines = null;
                continue;
            }
            if (currentEventLines != null) {
                currentEventLines.add(line);
            }
        }
        return events;
    }

    private List<String> unfoldLines(String body) {
        var unfolded = new ArrayList<String>();
        try (var reader = new BufferedReader(new StringReader(body))) {
            String rawLine;
            String current = null;
            while ((rawLine = reader.readLine()) != null) {
                if (rawLine.startsWith(" ") || rawLine.startsWith("\t")) {
                    current = current == null ? rawLine.trim() : current + rawLine.substring(1);
                    continue;
                }
                if (current != null) {
                    unfolded.add(current);
                }
                current = rawLine;
            }
            if (current != null) {
                unfolded.add(current);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read ICS body", ex);
        }
        return unfolded;
    }

    private ExternalCalendarEvent parseEvent(List<String> lines) {
        var values = new HashMap<String, PropertyValue>();
        var exDates = new ArrayList<PropertyValue>();
        for (var line : lines) {
            var separatorIndex = line.indexOf(':');
            if (separatorIndex < 0) {
                continue;
            }
            var left = line.substring(0, separatorIndex);
            var value = line.substring(separatorIndex + 1);
            var leftParts = left.split(";");
            var name = leftParts[0].toUpperCase(Locale.ROOT);
            var params = new HashMap<String, String>();
            for (int index = 1; index < leftParts.length; index++) {
                var param = leftParts[index].split("=", 2);
                if (param.length == 2) {
                    params.put(param[0].toUpperCase(Locale.ROOT), param[1]);
                }
            }
            var propertyValue = new PropertyValue(value, params);
            if ("EXDATE".equals(name)) {
                exDates.add(propertyValue);
            } else {
                values.put(name, propertyValue);
            }
        }

        if (values.containsKey("STATUS")
            && "CANCELLED".equalsIgnoreCase(values.get("STATUS").value())) {
            return null;
        }

        var startValue = values.get("DTSTART");
        if (startValue == null) {
            return null;
        }

        var start = parseTemporal(startValue);
        if (start == null) {
            return null;
        }

        var endValue = values.get("DTEND");
        var end = endValue == null ? inferEnd(start) : parseTemporal(endValue);
        if (end == null || !end.value().isAfter(start.value())) {
            end = inferEnd(start);
        }

        var recurrence = parseRecurrence(values.get("RRULE"), start.value().getZone());
        var excludedStarts = parseExcludedStarts(exDates, start.value().getZone());
        return new ExternalCalendarEvent(
            valueOf(values, "UID", "external-" + Integer.toHexString(lines.hashCode())),
            unescapeText(valueOf(values, "SUMMARY", "外部日程")),
            unescapeText(valueOf(values, "DESCRIPTION", null)),
            unescapeText(valueOf(values, "LOCATION", null)),
            start.value(),
            end.value(),
            start.allDay(),
            recurrence,
            excludedStarts,
            sanitizeColor(valueOf(values, "COLOR", null))
        );
    }

    private TemporalValue inferEnd(TemporalValue start) {
        return start.allDay()
            ? new TemporalValue(start.value().plusDays(1), true)
            : new TemporalValue(start.value().plusHours(1), false);
    }

    private String valueOf(Map<String, PropertyValue> values, String key, String fallback) {
        var property = values.get(key);
        return property == null || property.value() == null || property.value().isBlank()
            ? fallback
            : property.value();
    }

    private ZoneId resolveZoneId(String tzid) {
        if (tzid == null || tzid.isBlank()) {
            return ZoneId.systemDefault();
        }
        var mapped = WINDOWS_TO_IANA_TZ_MAP.getOrDefault(tzid, tzid);
        try {
            return ZoneId.of(mapped);
        } catch (Exception ex) {
            log.warn("Unknown timezone ID '{}', falling back to system default", tzid, ex);
            return ZoneId.systemDefault();
        }
    }

    private TemporalValue parseTemporal(PropertyValue property) {
        var raw = property.value();
        if (raw == null || raw.isBlank()) {
            return null;
        }

        var zoneId = resolveZoneId(property.params().get("TZID"));

        if ("DATE".equalsIgnoreCase(property.params().get("VALUE")) || raw.length() == 8) {
            var date = LocalDate.parse(raw, LOCAL_DATE);
            return new TemporalValue(date.atStartOfDay(zoneId), true);
        }

        try {
            if (raw.endsWith("Z")) {
                return new TemporalValue(
                    OffsetDateTime.of(LocalDateTime.parse(raw.substring(0, raw.length() - 1), LOCAL_DATE_TIME),
                        ZoneOffset.UTC).toZonedDateTime(),
                    false
                );
            }
            return new TemporalValue(LocalDateTime.parse(raw, LOCAL_DATE_TIME).atZone(zoneId), false);
        } catch (DateTimeParseException ex) {
            log.warn("Failed to parse ICS temporal value {}", raw, ex);
            return null;
        }
    }

    private ExternalRecurrence parseRecurrence(PropertyValue property, ZoneId zoneId) {
        if (property == null || property.value() == null || property.value().isBlank()) {
            return null;
        }

        var fields = new HashMap<String, String>();
        for (var part : property.value().split(";")) {
            var keyValue = part.split("=", 2);
            if (keyValue.length == 2) {
                fields.put(keyValue[0].toUpperCase(Locale.ROOT), keyValue[1]);
            }
        }

        var frequency = switch (fields.get("FREQ")) {
            case "DAILY" -> ScheduleEntry.RecurrenceFrequency.DAILY;
            case "WEEKLY" -> ScheduleEntry.RecurrenceFrequency.WEEKLY;
            case "MONTHLY" -> ScheduleEntry.RecurrenceFrequency.MONTHLY;
            case "YEARLY" -> ScheduleEntry.RecurrenceFrequency.YEARLY;
            default -> ScheduleEntry.RecurrenceFrequency.NONE;
        };
        if (frequency == ScheduleEntry.RecurrenceFrequency.NONE) {
            return null;
        }

        var interval = 1;
        if (fields.containsKey("INTERVAL")) {
            try {
                interval = Math.max(Integer.parseInt(fields.get("INTERVAL")), 1);
            } catch (NumberFormatException ignored) {
                interval = 1;
            }
        }

        LocalDate until = null;
        if (fields.containsKey("UNTIL")) {
            var temporal = parseTemporal(new PropertyValue(fields.get("UNTIL"), Map.of()));
            if (temporal != null) {
                until = temporal.allDay()
                    ? temporal.value().toLocalDate()
                    : temporal.value().withZoneSameInstant(zoneId).toLocalDate();
            }
        }

        Integer count = null;
        if (fields.containsKey("COUNT")) {
            try {
                count = Math.max(Integer.parseInt(fields.get("COUNT")), 1);
            } catch (NumberFormatException ignored) {
                count = null;
            }
        }

        return new ExternalRecurrence(frequency, interval, until, count);
    }

    private Set<LocalDateTime> parseExcludedStarts(List<PropertyValue> exDates, ZoneId zoneId) {
        var excluded = new HashSet<LocalDateTime>();
        for (var exDate : exDates) {
            for (var raw : exDate.value().split(",")) {
                var temporal = parseTemporal(new PropertyValue(raw, exDate.params()));
                if (temporal != null) {
                    var start = temporal.allDay()
                        ? temporal.value().toLocalDate().atStartOfDay()
                        : temporal.value().withZoneSameInstant(zoneId).toLocalDateTime();
                    excluded.add(start.truncatedTo(ChronoUnit.SECONDS));
                    excluded.add(start.truncatedTo(ChronoUnit.MINUTES));
                }
            }
        }
        return excluded;
    }

    private LocalDateTime toOccurrenceDateTime(ZonedDateTime value, boolean allDay, ZoneId zoneId) {
        if (allDay) {
            return value.toLocalDate().atStartOfDay();
        }
        return value.withZoneSameInstant(zoneId).toLocalDateTime();
    }

    private LocalDateTime advance(LocalDateTime source, ScheduleEntry.RecurrenceFrequency frequency, int interval) {
        return switch (frequency) {
            case DAILY -> source.plusDays(interval);
            case WEEKLY -> source.plusWeeks(interval);
            case MONTHLY -> source.plusMonths(interval);
            case YEARLY -> source.plusYears(interval);
            case NONE -> source;
        };
    }

    private String recurrenceDescription(ExternalRecurrence recurrence) {
        if (recurrence == null || recurrence.frequency() == ScheduleEntry.RecurrenceFrequency.NONE) {
            return null;
        }

        var label = switch (recurrence.frequency()) {
            case DAILY -> recurrence.interval() == 1 ? "重复：每天" : "重复：每" + recurrence.interval() + "天";
            case WEEKLY -> recurrence.interval() == 1 ? "重复：每周" : "重复：每" + recurrence.interval() + "周";
            case MONTHLY -> recurrence.interval() == 1 ? "重复：每月" : "重复：每" + recurrence.interval() + "个月";
            case YEARLY -> recurrence.interval() == 1 ? "重复：每年" : "重复：每" + recurrence.interval() + "年";
            case NONE -> null;
        };
        if (label == null) {
            return null;
        }
        return recurrence.until() == null ? label : label + "，截止 " + recurrence.until();
    }

    private String unescapeText(String value) {
        if (value == null) {
            return null;
        }
        return value
            .replace("\\n", "\n")
            .replace("\\N", "\n")
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\");
    }

    private String sanitizeColor(String color) {
        if (color != null && SAFE_COLOR_PATTERN.matcher(color).matches()) {
            return color;
        }
        return null;
    }

    private String validateUrl(String url) {
        if (url == null || url.isBlank()) {
            return "ICS 订阅地址不能为空。";
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return "ICS 订阅地址格式无效。";
        }
        var scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_PROTOCOLS.contains(scheme.toLowerCase())) {
            return "仅支持 HTTPS 协议的 ICS 订阅地址。";
        }
        var host = uri.getHost();
        if (host == null || host.isBlank()) {
            return "ICS 订阅地址缺少主机名。";
        }
        try {
            var addr = InetAddress.getByName(host);
            if (isPrivateOrReserved(addr)) {
                return "不允许访问内网地址。";
            }
        } catch (UnknownHostException e) {
            return "无法解析 ICS 订阅地址的主机名。";
        }
        return null;
    }

    private boolean isPrivateOrReserved(InetAddress addr) {
        if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()) {
            return true;
        }
        var host = addr.getHostAddress();
        if (host.startsWith("0.") || host.startsWith("0")) {
            return true;
        }
        if (host.startsWith("224.") || host.startsWith("225.")
            || host.startsWith("226.") || host.startsWith("227.")
            || host.startsWith("228.") || host.startsWith("229.")
            || host.startsWith("230.") || host.startsWith("231.")
            || host.startsWith("232.") || host.startsWith("233.")
            || host.startsWith("234.") || host.startsWith("235.")
            || host.startsWith("236.") || host.startsWith("237.")
            || host.startsWith("238.") || host.startsWith("239.")) {
            return true;
        }
        if (host.startsWith("240.")) {
            return true;
        }
        if (host.contains(":") && (host.startsWith("[fc") || host.startsWith("[fd")
            || host.startsWith("[FF") || host.startsWith("[ff") || host.startsWith("[FC") || host.startsWith("[FD"))) {
            return true;
        }
        return false;
    }

    private record PropertyValue(String value, Map<String, String> params) {
    }

    private record TemporalValue(ZonedDateTime value, boolean allDay) {
    }

    private record CachedEvents(List<ExternalCalendarEvent> events, Instant refreshedAt) {
        private boolean isExpired() {
            return refreshedAt.plus(CACHE_TTL).isBefore(Instant.now());
        }
    }

    public record ExternalCalendarValidationResult(boolean valid, String message, int eventCount) {
    }

    public record ExternalCalendarRefreshResult(int sourceCount, int successCount, int eventCount,
                                                List<ExternalCalendarRefreshItem> items) {
    }

    public record ExternalCalendarRefreshItem(String name, String icsUrl, boolean success, String message,
                                              int eventCount) {
    }

    private record ExternalCalendarEvent(String uid, String title, String description, String location,
                                         ZonedDateTime start, ZonedDateTime end, boolean allDay,
                                         ExternalRecurrence recurrence, Set<LocalDateTime> excludedStarts,
                                         String color) {
        private String effectiveColor(String fallback) {
            return color == null || color.isBlank() ? fallback : color;
        }
    }

    private record ExternalRecurrence(ScheduleEntry.RecurrenceFrequency frequency, int interval, LocalDate until,
                                      Integer count) {
    }
}

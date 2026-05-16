package run.halo.schedule.calendar;

import java.io.BufferedReader;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

    private final HttpClient httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    Mono<List<ScheduleEventOccurrence>> listOccurrences(ScheduleCalendarSetting setting, LocalDate rangeStart,
        LocalDate rangeEnd, ZoneId zoneId) {
        var sources = setting.enabledExternalCalendars();
        if (sources.isEmpty()) {
            log.debug("No enabled external calendars found for range {} to {}", rangeStart, rangeEnd);
            return Mono.just(List.of());
        }

        var start = rangeStart.atStartOfDay();
        var end = rangeEnd.plusDays(1).atStartOfDay();
        log.info("Loading {} external calendars for range {} to {}", sources.size(), rangeStart, rangeEnd);
        return Flux.fromIterable(sources)
            .flatMap(source -> loadEvents(source)
                .map(events -> {
                    log.info("Fetched {} raw external events from {}", events.size(), source.effectiveName());
                    var expanded = expandEvents(events, source, start, end, zoneId);
                    log.info("Expanded {} occurrences from {} for range {} to {}",
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

    private Mono<List<ExternalCalendarEvent>> loadEvents(ScheduleCalendarSetting.ExternalCalendarSource source) {
        var url = source.icsUrl();
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
            valueOf(values, "COLOR", null)
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

    private TemporalValue parseTemporal(PropertyValue property) {
        var raw = property.value();
        if (raw == null || raw.isBlank()) {
            return null;
        }

        var zoneId = property.params().containsKey("TZID")
            ? ZoneId.of(property.params().get("TZID"))
            : ZoneId.systemDefault();

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

    private record PropertyValue(String value, Map<String, String> params) {
    }

    private record TemporalValue(ZonedDateTime value, boolean allDay) {
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

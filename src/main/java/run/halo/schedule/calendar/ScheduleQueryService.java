package run.halo.schedule.calendar;

import static java.util.Comparator.comparing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;

@Service
public class ScheduleQueryService {
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Logger log = LoggerFactory.getLogger(ScheduleQueryService.class);
    private static final DateTimeFormatter TIME_FORMATTER =
        DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter ICAL_DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter ICAL_LOCAL_DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final int CALENDAR_HEADER_HEIGHT = 64;
    private static final int HOUR_HEIGHT = 56;
    private static final Locale ZH_CN = Locale.SIMPLIFIED_CHINESE;
    private static final Pattern SAFE_COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final String EXTERNAL_CARD_NAME_PREFIX = "external-calendar:";
    private static final int EDITOR_EXTERNAL_CARD_LOOKBACK_DAYS = 30;
    private static final int EDITOR_EXTERNAL_CARD_LOOKAHEAD_DAYS = 365;
    private static final int MAX_EXPANSION_STEPS = 10000;
    private static final int MAX_QUERY_RANGE_DAYS = 366;

    private final ReactiveExtensionClient client;
    private final ScheduleCalendarSettingService settingService;
    private final ExternalCalendarService externalCalendarService;
    private final JsonMapper objectMapper;

    public ScheduleQueryService(ReactiveExtensionClient client,
        ScheduleCalendarSettingService settingService,
        ExternalCalendarService externalCalendarService) {
        this.client = client;
        this.settingService = settingService;
        this.externalCalendarService = externalCalendarService;
        this.objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .enable(com.fasterxml.jackson.core.json.JsonWriteFeature.ESCAPE_FORWARD_SLASHES)
            .build();
    }

    Mono<WeekViewResponse> getWeekView(LocalDate requestedStart) {
        var zoneId = ZoneId.systemDefault();
        var serverNow = OffsetDateTime.now(zoneId);
        var weekStart = requestedStart == null
            ? LocalDate.now(zoneId).with(DayOfWeek.MONDAY)
            : requestedStart.with(DayOfWeek.MONDAY);
        var weekEnd = weekStart.plusDays(6);
        return loadCalendarContext(weekStart, weekEnd, zoneId)
            .map(context -> toWeekView(context, weekStart, weekEnd, zoneId, serverNow));
    }

    Mono<SummaryResponse> getSummary() {
        var zoneId = ZoneId.systemDefault();
        var serverNow = OffsetDateTime.now(zoneId);
        var today = LocalDate.now(zoneId);
        return loadCalendarContext(today, today.plusDays(90), zoneId)
            .map(context -> toSummary(context.occurrences(), zoneId, serverNow));
    }

    Mono<DayView> getDayView(LocalDate requestedDate) {
        var zoneId = ZoneId.systemDefault();
        var date = requestedDate == null ? LocalDate.now(zoneId) : requestedDate;
        return loadCalendarContext(date, date, zoneId)
            .map(context -> toDayView(context.occurrences(), date));
    }

    Mono<List<OccurrenceResponse>> listOccurrences(LocalDate requestedStart, LocalDate requestedEnd) {
        var zoneId = ZoneId.systemDefault();
        var start = requestedStart == null ? LocalDate.now(zoneId) : requestedStart;
        var end = requestedEnd == null ? start : requestedEnd;
        if (end.isBefore(start)) {
            var swapped = start;
            start = end;
            end = swapped;
        }

        var rangeStart = start;
        var rangeEnd = end;
        var actualEnd = rangeEnd;
        var maxEnd = rangeStart.plusDays(MAX_QUERY_RANGE_DAYS);
        if (actualEnd.isAfter(maxEnd)) {
            actualEnd = maxEnd;
        }
        return loadCalendarContext(rangeStart, actualEnd, zoneId)
            .map(context -> context.occurrences().stream()
                .map(occurrence -> toOccurrenceResponse(occurrence, zoneId))
                .toList());
    }

    Mono<List<OccurrenceResponse>> listUpcomingOccurrences(Integer requestedLimit) {
        var zoneId = ZoneId.systemDefault();
        var now = LocalDateTime.now(zoneId);
        var start = now.toLocalDate();
        var end = now.plusDays(365).toLocalDate();
        var limit = normalizeLimit(requestedLimit);
        return loadCalendarContext(start, end, zoneId)
            .map(context -> context.occurrences().stream()
                .filter(occurrence -> occurrence.end().isAfter(now))
                .limit(limit)
                .map(occurrence -> toOccurrenceResponse(occurrence, zoneId))
                .toList());
    }

    Mono<ScheduleCardResponse> getEntryCard(String name) {
        var zoneId = ZoneId.systemDefault();
        if (isExternalCardName(name)) {
            return getExternalEntryCard(name, zoneId);
        }
        return client.get(ScheduleEntry.class, name)
            .filter(this::isEntryEnabled)
            .map(entry -> toScheduleCardResponse(entry, zoneId))
            .onErrorResume(throwable -> listEntries()
                .flatMap(entries -> entries.stream()
                    .filter(this::isEntryEnabled)
                    .filter(entry -> entry.getMetadata() != null && name.equals(entry.getMetadata().getName()))
                    .findFirst()
                    .map(entry -> Mono.just(toScheduleCardResponse(entry, zoneId)))
                    .orElseGet(Mono::empty)));
    }

    Mono<List<ScheduleCardResponse>> listEntryCards() {
        var zoneId = ZoneId.systemDefault();
        var today = LocalDate.now(zoneId);
        var rangeStart = today.minusDays(EDITOR_EXTERNAL_CARD_LOOKBACK_DAYS);
        var rangeEnd = today.plusDays(EDITOR_EXTERNAL_CARD_LOOKAHEAD_DAYS);
        return Mono.zip(listEntries(), settingService.getSetting())
            .flatMap(tuple -> externalCalendarService.listOccurrences(tuple.getT2(), rangeStart, rangeEnd, zoneId)
                .map(externalOccurrences -> {
                    var cards = new ArrayList<ScheduleCardResponse>();
                    tuple.getT1().stream()
                        .filter(this::isEntryEnabled)
                        .map(entry -> toScheduleCardResponse(entry, zoneId))
                        .forEach(cards::add);
                    externalOccurrences.stream()
                        .map(occurrence -> toExternalScheduleCardResponse(occurrence, zoneId))
                        .forEach(cards::add);
                    cards.sort(Comparator.comparing(ScheduleCardResponse::startTime));
                    return cards;
                }));
    }

    Mono<String> exportPublicIcal() {
        return Mono.zip(
                listEntries(),
                settingService.getSetting()
            )
            .map(tuple -> toIcalContent(tuple.getT1(), tuple.getT2()));
    }

    Mono<java.util.Map<String, Object>> buildCalendarModel(LocalDate requestedStart) {
        return Mono.zip(
                getWeekView(requestedStart),
                settingService.getSetting()
            )
            .map(tuple -> {
                var view = tuple.getT1();
                var pageTitle = tuple.getT2().effectiveTitle();
                try {
                    var model = new java.util.HashMap<String, Object>();
                    model.put("pageTitle", pageTitle);
                    model.put("payload", objectMapper.writeValueAsString(view));
                    model.put("calendarHeaderHeight", CALENDAR_HEADER_HEIGHT);
                    model.put("hourHeight", HOUR_HEIGHT);
                    return model;
                } catch (JsonProcessingException ex) {
                    throw new IllegalStateException("Failed to serialize schedule calendar data.", ex);
                }
            });
    }
    Mono<java.util.Map<String, Object>> buildCardModel(String name) {
        return getEntryCard(name)
            .flatMap(ScheduleQueryService::toCardModel);
    }

    private static Mono<java.util.Map<String, Object>> toCardModel(ScheduleCardResponse card) {
        var model = new java.util.HashMap<String, Object>();
        model.put("pageTitle", card.title());
        model.put("cardTitle", card.title());
        model.put("summary", buildPublicCardSummary(card));
        model.put("summaryClass", buildPublicCardSummaryClass(card));
        model.put("cardColor", defaultColor(card.color()));
        model.put("nextOccurrenceLabel", card.nextOccurrenceLabel());
        model.put("sourceLabel", card.sourceLabel());
        model.put("cardLocation", card.location());
        model.put("cardDescription", card.description());
        return Mono.just(model);
    }

    private static String buildPublicCardSummary(ScheduleCardResponse card) {
        if (card.recurrenceDescription() == null || card.recurrenceDescription().isBlank()) {
            return card.startTime() + " - " + card.endTime();
        }
        return card.recurrenceDescription() + " · 首次 " + card.startTime() + " - " + card.endTime();
    }

    private static String buildPublicCardSummaryClass(ScheduleCardResponse card) {
        if (card.recurrenceDescription() == null || card.recurrenceDescription().isBlank()) {
            return "entry-meta__item";
        }
        return "entry-meta__item entry-meta__item--wide entry-meta__item--block";
    }

    private Mono<List<ScheduleEntry>> listEntries() {
                return client.listAll(ScheduleEntry.class, ListOptions.builder().build(), Sort.unsorted())
            .collectList()
            .map(entries -> entries.stream()
                .sorted(comparing(entry -> entry.getSpec().getStartTime()))
                .collect(Collectors.toList()));
    }

    private String toIcalContent(List<ScheduleEntry> entries, ScheduleCalendarSetting setting) {
        var zoneId = ZoneId.systemDefault();
        var builder = new StringBuilder()
            .append("BEGIN:VCALENDAR\r\n")
            .append("VERSION:2.0\r\n")
            .append("PRODID:-//sunnyhmz7010//Halo Schedule Calendar//CN\r\n")
            .append("CALSCALE:GREGORIAN\r\n")
            .append("METHOD:PUBLISH\r\n")
            .append("X-WR-TIMEZONE:")
            .append(zoneId.getId())
            .append("\r\n")
            .append("X-WR-CALNAME:")
            .append(escapeIcalText(setting.effectiveTitle()))
            .append("\r\n");

        appendVTimeZone(builder, zoneId);

        for (var entry : entries.stream().filter(this::isEntryEnabled).toList()) {
            appendIcalEvent(builder, entry, zoneId);
        }

        builder.append("END:VCALENDAR\r\n");
        return foldIcalLines(builder.toString());
    }

    private void appendVTimeZone(StringBuilder builder, ZoneId zoneId) {
        var now = OffsetDateTime.now(zoneId);
        var standardOffset = zoneId.getRules().getStandardOffset(now.toInstant());
        builder.append("BEGIN:VTIMEZONE\r\n");
        builder.append("TZID:").append(zoneId.getId()).append("\r\n");
        builder.append("X-LIC-LOCATION:").append(zoneId.getId()).append("\r\n");
        builder.append("BEGIN:STANDARD\r\n");
        builder.append("TZOFFSETFROM:").append(formatIcalOffset(standardOffset)).append("\r\n");
        builder.append("TZOFFSETTO:").append(formatIcalOffset(standardOffset)).append("\r\n");
        builder.append("TZNAME:").append(zoneId.getId()).append("\r\n");
        builder.append("DTSTART:19700101T000000\r\n");
        builder.append("END:STANDARD\r\n");
        builder.append("END:VTIMEZONE\r\n");
    }

    private void appendIcalEvent(StringBuilder builder, ScheduleEntry entry, ZoneId calendarZoneId) {
        if (entry == null || entry.getSpec() == null || entry.getMetadata() == null) {
            return;
        }

        var spec = entry.getSpec();
        if (spec.getTitle() == null || spec.getStartTime() == null || spec.getEndTime() == null) {
            return;
        }

        builder.append("BEGIN:VEVENT\r\n");
        builder.append("UID:")
            .append(escapeIcalText(entry.getMetadata().getName()))
            .append("@schedule-calendar.halo\r\n");
        builder.append("DTSTAMP:")
            .append(toIcalDateTime(OffsetDateTime.now(ZoneOffset.UTC)))
            .append("\r\n");
        builder.append("DTSTART;TZID=")
            .append(calendarZoneId.getId())
            .append(":")
            .append(toIcalLocalDateTime(spec.getStartTime(), calendarZoneId))
            .append("\r\n");
        builder.append("DTEND;TZID=")
            .append(calendarZoneId.getId())
            .append(":")
            .append(toIcalLocalDateTime(spec.getEndTime(), calendarZoneId))
            .append("\r\n");
        builder.append("SUMMARY:")
            .append(escapeIcalText(spec.getTitle()))
            .append("\r\n");

        if (spec.getDescription() != null && !spec.getDescription().isBlank()) {
            builder.append("DESCRIPTION:")
                .append(escapeIcalText(spec.getDescription()))
                .append("\r\n");
        }

        if (spec.getLocation() != null && !spec.getLocation().isBlank()) {
            builder.append("LOCATION:")
                .append(escapeIcalText(spec.getLocation()))
                .append("\r\n");
        }

        var recurrence = spec.getRecurrence();
        if (recurrence != null && recurrence.getFrequency() != null
            && recurrence.getFrequency() != ScheduleEntry.RecurrenceFrequency.NONE) {
            builder.append("RRULE:FREQ=").append(recurrence.getFrequency().name());

            if (recurrence.getInterval() != null && recurrence.getInterval() > 1) {
                builder.append(";INTERVAL=").append(recurrence.getInterval());
            }

            if (recurrence.getUntil() != null) {
                var untilDateTime = recurrence.getUntil().atTime(LocalTime.MAX)
                    .atOffset(spec.getStartTime().getOffset())
                    .withOffsetSameInstant(ZoneOffset.UTC);
                builder.append(";UNTIL=").append(toIcalDateTime(untilDateTime));
            }

            builder.append("\r\n");
        }

        builder.append("END:VEVENT\r\n");
    }

    private String toIcalDateTime(OffsetDateTime value) {
        return value.withOffsetSameInstant(ZoneOffset.UTC).format(ICAL_DATE_TIME_FORMATTER);
    }

    private String toIcalLocalDateTime(OffsetDateTime value, ZoneId zoneId) {
        return value.atZoneSameInstant(zoneId).toLocalDateTime().format(ICAL_LOCAL_DATE_TIME_FORMATTER);
    }

    private String formatIcalOffset(ZoneOffset offset) {
        var totalSeconds = offset.getTotalSeconds();
        var absoluteSeconds = Math.abs(totalSeconds);
        var hours = absoluteSeconds / 3600;
        var minutes = (absoluteSeconds % 3600) / 60;
        return "%s%02d%02d".formatted(totalSeconds >= 0 ? "+" : "-", hours, minutes);
    }

    private String escapeIcalText(String value) {
        return value
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n");
    }

    private String foldIcalLines(String value) {
        var result = new StringBuilder();
        for (var line : value.split("\r\n")) {
            if (line.isEmpty()) {
                result.append("\r\n");
                continue;
            }

            var current = new StringBuilder();
            var currentBytes = 0;
            for (var i = 0; i < line.length(); i++) {
                var ch = line.charAt(i);
                var charBytes = String.valueOf(ch).getBytes(StandardCharsets.UTF_8).length;
                if (currentBytes + charBytes > 75) {
                    result.append(current).append("\r\n ");
                    current.setLength(0);
                    currentBytes = 1;
                }
                current.append(ch);
                currentBytes += charBytes;
            }
            result.append(current).append("\r\n");
        }
        return result.toString();
    }

    private Mono<CalendarContext> loadCalendarContext(LocalDate rangeStart, LocalDate rangeEnd, ZoneId zoneId) {
        return Mono.zip(
                listEntries(),
                settingService.getSetting()
            )
            .flatMap(tuple -> {
                var entries = tuple.getT1();
                var setting = tuple.getT2();
                var localOccurrences = expandOccurrences(entries, rangeStart, rangeEnd, zoneId);
                return externalCalendarService.listOccurrences(setting, rangeStart, rangeEnd, zoneId)
                    .map(externalOccurrences -> {
                        var occurrences = new ArrayList<ScheduleEventOccurrence>(localOccurrences.size()
                            + externalOccurrences.size());
                        occurrences.addAll(localOccurrences);
                        occurrences.addAll(externalOccurrences);
                        occurrences.sort(Comparator.comparing(ScheduleEventOccurrence::start));
                        return new CalendarContext(entries, occurrences, setting);
                    });
            });
    }

    private ScheduleCardResponse toScheduleCardResponse(ScheduleEntry entry, ZoneId zoneId) {
        var spec = entry.getSpec();
        return new ScheduleCardResponse(
            entry.getMetadata().getName(),
            spec.getTitle(),
            spec.getDescription(),
            spec.getLocation(),
            formatDateTime(spec.getStartTime()),
            formatDateTime(spec.getEndTime()),
            recurrenceDescription(spec.getRecurrence()),
            nextOccurrenceLabel(entry, zoneId),
            defaultColor(spec.getColor()),
            null
        );
    }

    private ScheduleCardResponse toExternalScheduleCardResponse(ScheduleEventOccurrence occurrence, ZoneId zoneId) {
        var nextOccurrenceLabel = occurrence.start().isAfter(LocalDateTime.now(zoneId))
            ? DATE_TIME_FORMATTER.format(occurrence.start())
            : null;
        return new ScheduleCardResponse(
            externalCardName(occurrence),
            occurrence.title(),
            occurrence.description(),
            occurrence.location(),
            DATE_TIME_FORMATTER.format(occurrence.start()),
            DATE_TIME_FORMATTER.format(occurrence.end()),
            occurrence.recurrenceDescription(),
            nextOccurrenceLabel,
            defaultColor(occurrence.color()),
            occurrence.sourceLabel()
        );
    }

    private Mono<ScheduleCardResponse> getExternalEntryCard(String name, ZoneId zoneId) {
        var key = parseExternalCardKey(name);
        if (key == null) {
            return Mono.empty();
        }
        return settingService.getSetting()
            .flatMap(setting -> externalCalendarService.listOccurrences(
                    setting,
                    key.start().toLocalDate(),
                    key.end().toLocalDate(),
                    zoneId
                )
                .flatMapIterable(occurrences -> occurrences)
                .filter(occurrence -> externalCardKeyMatches(key, occurrence))
                .next()
                .map(occurrence -> toExternalScheduleCardResponse(occurrence, zoneId)));
    }

    private boolean isExternalCardName(String name) {
        return name != null && name.startsWith(EXTERNAL_CARD_NAME_PREFIX);
    }

    private String externalCardName(ScheduleEventOccurrence occurrence) {
        var payload = String.join("\n",
            nullToEmpty(occurrence.sourceLabel()),
            nullToEmpty(occurrence.name()),
            occurrence.start().toString(),
            occurrence.end().toString()
        );
        return EXTERNAL_CARD_NAME_PREFIX + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private ExternalCardKey parseExternalCardKey(String name) {
        if (!isExternalCardName(name)) {
            return null;
        }
        try {
            var encoded = name.substring(EXTERNAL_CARD_NAME_PREFIX.length());
            var payload = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            var parts = payload.split("\n", -1);
            if (parts.length != 4) {
                return null;
            }
            return new ExternalCardKey(
                parts[0],
                parts[1],
                LocalDateTime.parse(parts[2]),
                LocalDateTime.parse(parts[3])
            );
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private boolean externalCardKeyMatches(ExternalCardKey key, ScheduleEventOccurrence occurrence) {
        return key.sourceLabel().equals(nullToEmpty(occurrence.sourceLabel()))
            && key.uid().equals(nullToEmpty(occurrence.name()))
            && key.start().equals(occurrence.start())
            && key.end().equals(occurrence.end());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private WeekViewResponse toWeekView(CalendarContext context, LocalDate weekStart, LocalDate weekEnd,
        ZoneId zoneId, OffsetDateTime serverNow) {
        var days = new ArrayList<DayView>();
        for (int offset = 0; offset < 7; offset++) {
            var date = weekStart.plusDays(offset);
            days.add(toDayView(context.occurrences(), date));
        }
        var summary = toSummary(context.occurrences(), zoneId, serverNow);
        var nextOccurrence = summary.next();
        return new WeekViewResponse(
            weekStart.toString(),
            weekEnd.toString(),
            LocalDate.now(zoneId).with(DayOfWeek.MONDAY).toString(),
            weekStart.minusWeeks(1).toString(),
            weekStart.plusWeeks(1).toString(),
            days,
            serverNow.toString(),
            zoneId.getId(),
            summary,
            nextOccurrence == null ? null : nextOccurrence.title(),
            nextOccurrence == null ? null : nextOccurrence.startTime()
        );
    }

    private SummaryResponse toSummary(List<ScheduleEventOccurrence> occurrences, ZoneId zoneId,
        OffsetDateTime serverNow) {
        var now = serverNow.toLocalDateTime();
        var activeTitles = currentOccurrenceTitles(occurrences, now);
        var current = new CurrentStatusSummary(
            !activeTitles.isEmpty(),
            formatCurrentStatusText(activeTitles),
            activeTitles
        );
        var nextOccurrence = nextUpcomingOccurrence(occurrences, now);
        var next = nextOccurrence == null
            ? null
            : new NextOccurrenceSummary(
                nextOccurrence.title(),
                DATE_TIME_FORMATTER.format(nextOccurrence.start()),
                Duration.between(now, nextOccurrence.start()).toMinutes(),
                formatCountdownText(Duration.between(now, nextOccurrence.start()), nextOccurrence.title())
            );
        return new SummaryResponse(serverNow.toString(), zoneId.getId(), current, next);
    }

    private DayView toDayView(List<ScheduleEventOccurrence> occurrences, LocalDate date) {
        var dailyOccurrences = occurrences.stream()
            .filter(occurrence -> occurrence.end().isAfter(date.atStartOfDay())
                && occurrence.start().isBefore(date.plusDays(1).atStartOfDay()))
            .sorted(comparing(ScheduleEventOccurrence::start))
            .toList();
        var occupied = toOccupiedBlocks(dailyOccurrences, date);
        var free = toFreeBlocks(occupied);
        return new DayView(
            date.toString(),
            date.getDayOfWeek().getDisplayName(TextStyle.FULL, ZH_CN),
            occupied,
            free
        );
    }

    private List<ScheduleEventOccurrence> expandOccurrences(List<ScheduleEntry> entries, LocalDate weekStart,
        LocalDate weekEnd, ZoneId zoneId) {
        var rangeStart = weekStart.atStartOfDay();
        var rangeEnd = weekEnd.plusDays(1).atStartOfDay();
        return entries.stream()
            .filter(this::isEntryEnabled)
            .flatMap(entry -> occurrencesForRange(entry, rangeStart, rangeEnd, zoneId).stream())
            .sorted(comparing(ScheduleEventOccurrence::start))
            .collect(Collectors.toList());
    }

    private List<ScheduleEventOccurrence> occurrencesForRange(ScheduleEntry entry, LocalDateTime rangeStart,
        LocalDateTime rangeEnd, ZoneId zoneId) {
        var spec = entry.getSpec();
        var start = spec.getStartTime().atZoneSameInstant(zoneId).toLocalDateTime();
        var end = spec.getEndTime().atZoneSameInstant(zoneId).toLocalDateTime();
        if (!end.isAfter(start)) {
            return List.of();
        }
        if (!isRecurring(spec) || spansMultipleDates(start, end)) {
            if (end.isAfter(rangeStart) && start.isBefore(rangeEnd)) {
                return List.of(toOccurrence(entry, start, end, zoneId));
            }
            return List.of();
        }

        var recurrence = spec.getRecurrence();
        var frequency = recurrence.getFrequency();
        var interval = normalizeInterval(recurrence.getInterval());
        var duration = Duration.between(start, end);
        var cursor = alignOccurrenceStart(start, duration, rangeStart, frequency, interval);
        var occurrences = new ArrayList<ScheduleEventOccurrence>();
        var stepCount = 0;
        while (cursor.isBefore(rangeEnd) && stepCount <= MAX_EXPANSION_STEPS) {
            if (isAfterUntil(cursor, recurrence)) {
                break;
            }
            var occurrenceEnd = cursor.plus(duration);
            if (occurrenceEnd.isAfter(rangeStart) && cursor.isBefore(rangeEnd)) {
                occurrences.add(toOccurrence(entry, cursor, occurrenceEnd, zoneId));
            }
            cursor = advanceOccurrence(cursor, frequency, interval);
            stepCount++;
        }
        if (stepCount > MAX_EXPANSION_STEPS) {
            log.warn("日程条目重复展开超出最大步数限制 {}：{}", MAX_EXPANSION_STEPS, entry.getMetadata().getName());
        }
        return occurrences;
    }

    private ScheduleEventOccurrence toOccurrence(ScheduleEntry entry, LocalDateTime start, LocalDateTime end,
        ZoneId zoneId) {
        var spec = entry.getSpec();
        return new ScheduleEventOccurrence(
            entry.getMetadata().getName(),
            spec.getTitle(),
            spec.getDescription(),
            spec.getLocation(),
            recurrenceDescription(spec.getRecurrence()),
            defaultColor(spec.getColor()),
            start,
            end,
            null
        );
    }

    private List<TimeBlock> toOccupiedBlocks(List<ScheduleEventOccurrence> occurrences, LocalDate date) {
        var startOfDay = date.atStartOfDay();
        var endOfDay = date.plusDays(1).atStartOfDay();
        return occurrences.stream()
            .map(occurrence -> toBlock(occurrence, startOfDay, endOfDay))
            .filter(block -> block != null)
            .sorted(comparing(TimeBlock::start))
            .collect(Collectors.toList());
    }

    private TimeBlock toBlock(ScheduleEventOccurrence occurrence, LocalDateTime startOfDay,
        LocalDateTime endOfDay) {
        var start = occurrence.start();
        var end = occurrence.end();
        if (!end.isAfter(startOfDay) || !start.isBefore(endOfDay)) {
            return null;
        }
        var clippedStart = start.isBefore(startOfDay) ? startOfDay : start;
        var clippedEnd = end.isAfter(endOfDay) ? endOfDay : end;
        return new TimeBlock(
            TIME_FORMATTER.format(clippedStart),
            TIME_FORMATTER.format(clippedEnd),
            occurrence.title(),
            buildMetaLines(occurrence),
            buildTooltipMeta(occurrence),
            formatDuration(clippedStart, clippedEnd),
            occurrence.color()
        );
    }

    private List<TimeBlock> toFreeBlocks(List<TimeBlock> occupied) {
        var free = new ArrayList<TimeBlock>();
        var cursor = LocalTime.MIN;
        for (var block : occupied) {
            var blockStart = LocalTime.parse(block.start(), TIME_FORMATTER);
            if (blockStart.isAfter(cursor)) {
                free.add(freeBlock(cursor, blockStart));
            }
            var blockEnd = LocalTime.parse(block.end(), TIME_FORMATTER);
            if (blockEnd.isAfter(cursor)) {
                cursor = blockEnd;
            }
        }
        if (cursor.isBefore(LocalTime.MAX.withSecond(0).withNano(0))) {
            free.add(freeBlock(cursor, LocalTime.MAX.withSecond(0).withNano(0)));
        }
        return free.stream()
            .filter(block -> !block.start().equals(block.end()))
            .collect(Collectors.toList());
    }

    private TimeBlock freeBlock(LocalTime start, LocalTime end) {
        return new TimeBlock(
            TIME_FORMATTER.format(start),
            TIME_FORMATTER.format(end),
            "空闲时间",
            null,
            null,
            formatDuration(start.atDate(LocalDate.now()), end.atDate(LocalDate.now())),
            "#94a3b8"
        );
    }

    private String buildTooltipMeta(ScheduleEventOccurrence occurrence) {
        var meta = buildMetaLines(occurrence);
        return meta.isEmpty() ? null : String.join(" ", meta);
    }

    private List<String> buildMetaLines(ScheduleEventOccurrence occurrence) {
        var meta = new ArrayList<String>();
        if (occurrence.location() != null && !occurrence.location().isBlank()) {
            meta.add("地点：" + occurrence.location());
        }
        if (occurrence.description() != null && !occurrence.description().isBlank()) {
            meta.add("备注：" + occurrence.description());
        }
        if (occurrence.recurrenceDescription() != null && !occurrence.recurrenceDescription().isBlank()) {
            meta.add(occurrence.recurrenceDescription());
        }
        if (occurrence.sourceLabel() != null && !occurrence.sourceLabel().isBlank()) {
            meta.add("来源：" + occurrence.sourceLabel());
        }
        return meta;
    }

    private String sanitizeColor(String color) {
        if (color != null && SAFE_COLOR_PATTERN.matcher(color).matches()) {
            return color;
        }
        return "#0f766e";
    }

    private String defaultColor(String color) {
        return sanitizeColor(color);
    }

    private List<String> currentOccurrenceTitles(List<ScheduleEventOccurrence> occurrences, LocalDateTime now) {
        return occurrences.stream()
            .filter(occurrence -> !occurrence.start().isAfter(now) && occurrence.end().isAfter(now))
            .map(ScheduleEventOccurrence::title)
            .filter(title -> title != null && !title.isBlank())
            .distinct()
            .toList();
    }

    private String formatCurrentStatusText(List<String> titles) {
        if (titles.isEmpty()) {
            return "当前空闲";
        }
        if (titles.size() <= 2) {
            return "进行中：" + String.join("、", titles);
        }
        return "进行中：" + String.join("、", titles.subList(0, 2)) + " 等 " + titles.size() + " 项";
    }

    private int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null || requestedLimit < 1) {
            return 10;
        }
        return Math.min(requestedLimit, 100);
    }

    private String formatDuration(LocalDateTime start, LocalDateTime end) {
        var duration = Duration.between(start, end);
        var hours = duration.toHours();
        var minutes = duration.toMinutesPart();
        if (hours > 0 && minutes > 0) {
            return hours + " 小时 " + minutes + " 分钟";
        }
        if (hours > 0) {
            return hours + " 小时";
        }
        return Math.max(duration.toMinutes(), 0) + " 分钟";
    }

    private String formatCountdownDuration(Duration duration) {
        var totalMinutes = Math.max(duration.toMinutes(), 0);
        var days = totalMinutes / (24 * 60);
        var hours = (totalMinutes % (24 * 60)) / 60;
        var minutes = totalMinutes % 60;
        var parts = new ArrayList<String>();
        if (days > 0) {
            parts.add(days + "天");
        }
        if (hours > 0) {
            parts.add(hours + "小时");
        }
        if (minutes > 0 || parts.isEmpty()) {
            parts.add(minutes + "分钟");
        }
        return String.join("", parts);
    }

    private String formatCountdownText(Duration duration, String title) {
        return formatCountdownDuration(duration) + "后开始：" + title;
    }

    private String formatDateTime(OffsetDateTime value) {
        return value == null ? "" : DATE_TIME_FORMATTER.format(value.atZoneSameInstant(ZoneId.systemDefault()));
    }

    private boolean isRecurring(ScheduleEntry.Spec spec) {
        return spec != null
            && spec.getRecurrence() != null
            && spec.getRecurrence().getFrequency() != null
            && spec.getRecurrence().getFrequency() != ScheduleEntry.RecurrenceFrequency.NONE;
    }

    private boolean isEntryEnabled(ScheduleEntry entry) {
        if (entry == null || entry.getSpec() == null) {
            return false;
        }
        var annotations = entry.getMetadata() == null ? null : entry.getMetadata().getAnnotations();
        if (annotations != null && annotations.containsKey(ScheduleEntry.ENABLED_ANNOTATION)) {
            return Boolean.parseBoolean(annotations.get(ScheduleEntry.ENABLED_ANNOTATION));
        }
        return !Boolean.FALSE.equals(entry.getSpec().getEnabled());
    }

    private int normalizeInterval(Integer interval) {
        return interval == null || interval < 1 ? 1 : interval;
    }

    private boolean isAfterUntil(LocalDateTime occurrenceStart, ScheduleEntry.Recurrence recurrence) {
        return recurrence.getUntil() != null && occurrenceStart.toLocalDate().isAfter(recurrence.getUntil());
    }

    private boolean spansMultipleDates(LocalDateTime start, LocalDateTime end) {
        return !start.toLocalDate().equals(end.toLocalDate());
    }

    private LocalDateTime alignOccurrenceStart(LocalDateTime baseStart, Duration duration,
        LocalDateTime rangeStart, ScheduleEntry.RecurrenceFrequency frequency, int interval) {
        var target = rangeStart.minus(duration);
        if (!baseStart.isBefore(target)) {
            return baseStart;
        }

        var steps = switch (frequency) {
            case DAILY -> Math.max(0, ChronoUnit.DAYS.between(baseStart.toLocalDate(), target.toLocalDate()) / interval);
            case WEEKLY -> Math.max(0, ChronoUnit.WEEKS.between(baseStart.toLocalDate(), target.toLocalDate()) / interval);
            case MONTHLY -> Math.max(0, monthsBetween(baseStart, target) / interval);
            case YEARLY -> Math.max(0, ChronoUnit.YEARS.between(baseStart.toLocalDate(), target.toLocalDate()) / interval);
            case NONE -> 0;
        };

        var cursor = advanceOccurrence(baseStart, frequency, (int) steps * interval);
        while (cursor.plus(duration).isBefore(rangeStart) || cursor.plus(duration).equals(rangeStart)) {
            cursor = advanceOccurrence(cursor, frequency, interval);
        }
        return cursor;
    }

    private long monthsBetween(LocalDateTime start, LocalDateTime target) {
        return (target.getYear() - start.getYear()) * 12L + target.getMonthValue() - start.getMonthValue();
    }

    private LocalDateTime advanceOccurrence(LocalDateTime source,
        ScheduleEntry.RecurrenceFrequency frequency, int interval) {
        if (interval <= 0 || frequency == ScheduleEntry.RecurrenceFrequency.NONE) {
            return source;
        }
        return switch (frequency) {
            case DAILY -> source.plusDays(interval);
            case WEEKLY -> source.plusWeeks(interval);
            case MONTHLY -> source.plusMonths(interval);
            case YEARLY -> source.plusYears(interval);
            case NONE -> source;
        };
    }

    private String recurrenceDescription(ScheduleEntry.Recurrence recurrence) {
        if (recurrence == null || recurrence.getFrequency() == null
            || recurrence.getFrequency() == ScheduleEntry.RecurrenceFrequency.NONE) {
            return null;
        }

        var interval = normalizeInterval(recurrence.getInterval());
        var label = switch (recurrence.getFrequency()) {
            case DAILY -> interval == 1 ? "重复：每天" : "重复：每" + interval + "天";
            case WEEKLY -> interval == 1 ? "重复：每周" : "重复：每" + interval + "周";
            case MONTHLY -> interval == 1 ? "重复：每月" : "重复：每" + interval + "个月";
            case YEARLY -> interval == 1 ? "重复：每年" : "重复：每" + interval + "年";
            case NONE -> null;
        };
        if (label == null) {
            return null;
        }
        if (recurrence.getUntil() == null) {
            return label;
        }
        return label + "，截止 " + recurrence.getUntil();
    }

    private String nextOccurrenceLabel(ScheduleEntry entry, ZoneId zoneId) {
        var spec = entry.getSpec();
        if (!isRecurring(spec)) {
            return null;
        }

        var baseStart = spec.getStartTime().atZoneSameInstant(zoneId).toLocalDateTime();
        var baseEnd = spec.getEndTime().atZoneSameInstant(zoneId).toLocalDateTime();
        if (!baseEnd.isAfter(baseStart)) {
            return null;
        }

        var now = LocalDateTime.now(zoneId);
        var upcomingOccurrences = occurrencesForRange(entry, now, now.plusDays(90), zoneId).stream()
            .filter(occurrence -> occurrence.end().isAfter(now))
            .toList();
        if (upcomingOccurrences.isEmpty()) {
            return null;
        }

        ScheduleEventOccurrence nextOccurrence;
        if (baseStart.isAfter(now)) {
            nextOccurrence = upcomingOccurrences.stream()
                .filter(occurrence -> occurrence.start().isAfter(baseStart))
                .findFirst()
                .orElse(null);
        } else {
            nextOccurrence = upcomingOccurrences.getFirst();
        }

        if (nextOccurrence == null) {
            return null;
        }

        return formatOccurrenceLabel(nextOccurrence.start(), nextOccurrence.end());
    }

    private ScheduleEventOccurrence nextUpcomingOccurrence(List<ScheduleEventOccurrence> occurrences,
        LocalDateTime now) {
        return occurrences.stream()
            .filter(occurrence -> occurrence.start().isAfter(now))
            .min(comparing(ScheduleEventOccurrence::start))
            .orElse(null);
    }

    private String formatOccurrenceLabel(LocalDateTime start, LocalDateTime end) {
        if (start.toLocalDate().equals(end.toLocalDate())) {
            return DATE_FORMATTER.format(start)
                + " "
                + TIME_FORMATTER.format(start)
                + "-"
                + TIME_FORMATTER.format(end);
        }
        return DATE_TIME_FORMATTER.format(start)
            + " - "
            + DATE_TIME_FORMATTER.format(end);
    }

    private OccurrenceResponse toOccurrenceResponse(ScheduleEventOccurrence occurrence, ZoneId zoneId) {
        var start = occurrence.start();
        var end = occurrence.end();
        return new OccurrenceResponse(
            occurrence.name(),
            occurrence.title(),
            occurrence.description(),
            occurrence.location(),
            DATE_TIME_FORMATTER.format(start),
            DATE_TIME_FORMATTER.format(end),
            start.toLocalDate().toString(),
            start.getDayOfWeek().getDisplayName(TextStyle.FULL, ZH_CN),
            occurrence.recurrenceDescription(),
            formatDuration(start, end),
            occurrence.color(),
            occurrence.sourceLabel()
        );
    }

    public record WeekViewResponse(String weekStart, String weekEnd, String currentWeekStart,
                                   String previousWeekStart,
                                   String nextWeekStart,
                                   List<DayView> days,
                                   String serverTime,
                                   String zoneId,
                                   SummaryResponse summary,
                                   String nextOccurrenceTitle,
                                   String nextOccurrenceStart) {
    }

    public record SummaryResponse(String serverTime, String zoneId, CurrentStatusSummary current,
                                  NextOccurrenceSummary next) {
    }

    public record CurrentStatusSummary(boolean busy, String text, List<String> titles) {
    }

    public record NextOccurrenceSummary(String title, String startTime, long minutesUntilStart, String text) {
    }

    public record DayView(String date, String dayLabel, List<TimeBlock> occupied, List<TimeBlock> free) {
    }

    public record TimeBlock(String start, String end, String title, List<String> metaLines, String tooltipMeta,
                            String durationLabel, String color) {
    }

    private record CalendarContext(List<ScheduleEntry> entries, List<ScheduleEventOccurrence> occurrences,
                                   ScheduleCalendarSetting setting) {
    }

    private record ExternalCardKey(String sourceLabel, String uid, LocalDateTime start, LocalDateTime end) {
    }

    public record OccurrenceResponse(String name, String title, String description, String location,
                                     String startTime, String endTime, String date, String dayLabel,
                                     String recurrenceDescription, String durationLabel, String color,
                                     String sourceLabel) {
    }

    public record ScheduleCardResponse(String name, String title, String description, String location,
                                       String startTime, String endTime, String recurrenceDescription,
                                       String nextOccurrenceLabel, String color, String sourceLabel) {
    }
}
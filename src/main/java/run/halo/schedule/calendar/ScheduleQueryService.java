package run.halo.schedule.calendar;

import static java.util.Comparator.comparing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.time.temporal.ChronoUnit;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;

@Service
public class ScheduleQueryService {
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter TIME_FORMATTER =
        DateTimeFormatter.ofPattern("HH:mm");
    private static final int CALENDAR_HEADER_HEIGHT = 64;
    private static final int HOUR_HEIGHT = 56;
    private static final Locale ZH_CN = Locale.SIMPLIFIED_CHINESE;
    private static final String DEFAULT_TITLE = "日程日历";

    private final ReactiveExtensionClient client;
    private final ReactiveSettingFetcher settingFetcher;
    private final JsonMapper objectMapper;

    public ScheduleQueryService(ReactiveExtensionClient client,
        ReactiveSettingFetcher settingFetcher) {
        this.client = client;
        this.settingFetcher = settingFetcher;
        this.objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();
    }

    Mono<WeekViewResponse> getWeekView(LocalDate requestedStart) {
        var zoneId = ZoneId.systemDefault();
        var weekStart = requestedStart == null
            ? LocalDate.now(zoneId).with(DayOfWeek.MONDAY)
            : requestedStart.with(DayOfWeek.MONDAY);
        var weekEnd = weekStart.plusDays(6);
        return listEntries()
            .map(entries -> toWeekView(entries, weekStart, weekEnd, zoneId));
    }

    Mono<ScheduleCardResponse> getEntryCard(String name) {
        return client.get(ScheduleEntry.class, name)
            .map(entry -> {
                return toScheduleCardResponse(entry);
            });
    }

    Mono<List<ScheduleCardResponse>> listEntryCards() {
        return listEntries()
            .map(entries -> entries.stream()
                .map(this::toScheduleCardResponse)
                .toList());
    }

    Mono<String> buildPublicCalendarPage(LocalDate requestedStart) {
        return Mono.zip(
                getWeekView(requestedStart),
                settingFetcher.fetch(ScheduleCalendarSetting.GROUP, ScheduleCalendarSetting.class)
                    .defaultIfEmpty(new ScheduleCalendarSetting(DEFAULT_TITLE))
            )
            .map(tuple -> {
                var view = tuple.getT1();
                var setting = tuple.getT2();
                var pageTitle = setting.effectiveTitle();
                try {
                    return """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                          <head>
                            <meta charset="UTF-8" />
                            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                            <title>%s</title>
                            <style>
                              :root {
                                --bg: #f3efe7;
                                --panel: rgba(255, 255, 255, 0.9);
                                --text: #1f2937;
                                --muted: #6b7280;
                                --line: rgba(15, 23, 42, 0.12);
                                --accent: #0f766e;
                                --accent-soft: rgba(15, 118, 110, 0.12);
                              }
                              * { box-sizing: border-box; }
                              body {
                                margin: 0;
                                min-height: 100vh;
                                font-family: "Segoe UI", "PingFang SC", sans-serif;
                                color: var(--text);
                                background:
                                  radial-gradient(circle at top left, rgba(15,118,110,0.16), transparent 36%%),
                                  linear-gradient(135deg, #f8f5ee 0%%, #eef4f2 100%%);
                              }
                              main {
                                max-width: 1280px;
                                margin: 0 auto;
                                padding: 40px 20px 80px;
                              }
                              .hero {
                                display: flex;
                                justify-content: space-between;
                                gap: 16px;
                                align-items: end;
                                margin-bottom: 24px;
                              }
                              .hero h1 {
                                margin: 0;
                                font-size: clamp(2rem, 4vw, 3.4rem);
                              }
                              .week-nav {
                                display: flex;
                                flex-wrap: wrap;
                                justify-content: flex-end;
                                align-items: center;
                                gap: 10px;
                              }
                              .week-nav a {
                                display: inline-flex;
                                align-items: center;
                                gap: 8px;
                                text-decoration: none;
                                color: var(--accent);
                                background: rgba(255,255,255,0.75);
                                border: 1px solid rgba(15,118,110,0.16);
                                padding: 10px 14px;
                                border-radius: 999px;
                              }
                              .week-nav input {
                                border: 1px solid rgba(15,118,110,0.16);
                                background: rgba(255,255,255,0.75);
                                color: var(--text);
                                padding: 10px 14px;
                                border-radius: 12px;
                                font: inherit;
                              }
                              .calendar {
                                overflow-x: auto;
                                border: 1px solid var(--line);
                                border-radius: 20px;
                                background: var(--panel);
                                backdrop-filter: blur(8px);
                                box-shadow: 0 18px 40px rgba(15, 23, 42, 0.06);
                              }
                              .calendar__grid {
                                display: grid;
                                grid-template-columns: 78px minmax(980px, 1fr);
                              }
                              .time-column__header,
                              .day-column__header {
                                display: flex;
                                justify-content: center;
                                height: %dpx;
                                padding: 10px 12px;
                                background: #f9fafb;
                                border-bottom: 1px solid var(--line);
                                box-sizing: border-box;
                              }
                              .time-column__header {
                                align-items: center;
                                font-size: 0.82rem;
                                color: var(--muted);
                              }
                              .day-column__header {
                                flex-direction: column;
                                align-items: center;
                                text-align: center;
                              }
                              .time-column__body {
                                background: #fff;
                              }
                              .time-column__slot {
                                height: %dpx;
                                padding: 6px 12px 0 0;
                                text-align: right;
                                font-size: 0.8rem;
                                color: #9ca3af;
                                border-top: 1px solid #f3f4f6;
                              }
                              .day-columns {
                                display: grid;
                                grid-template-columns: repeat(7, minmax(120px, 1fr));
                              }
                              .day-column {
                                border-left: 1px solid var(--line);
                              }
                              .day-column__header strong {
                                font-size: 1rem;
                              }
                              .day-column__header span {
                                margin-top: 4px;
                                color: var(--muted);
                                font-size: 0.82rem;
                              }
                              .day-column__body {
                                position: relative;
                                height: 1344px;
                                background: #fff;
                              }
                              .day-column__lines {
                                position: absolute;
                                inset: 0;
                                background-image: repeating-linear-gradient(
                                  to bottom,
                                  transparent,
                                  transparent 55px,
                                  #f3f4f6 55px,
                                  #f3f4f6 56px
                                );
                              }
                              .calendar-block {
                                position: absolute;
                                z-index: 1;
                                display: flex;
                                flex-direction: column;
                                justify-content: center;
                                align-items: center;
                                border-radius: 12px;
                                padding: 8px 10px;
                                color: #fff;
                                box-shadow: 0 10px 18px rgba(15, 23, 42, 0.12);
                                overflow: hidden;
                                text-align: center;
                              }
                              .calendar-block__title {
                                font-weight: 700;
                                line-height: 1.2;
                              }
                              .calendar-block__time,
                              .calendar-block__meta {
                                margin-top: 4px;
                                font-size: 0.76rem;
                                line-height: 1.35;
                                opacity: 0.95;
                              }
                              @media (max-width: 960px) {
                                main {
                                  padding: 24px 12px 48px;
                                }
                                .hero {
                                  flex-direction: column;
                                  align-items: stretch;
                                  gap: 12px;
                                  margin-bottom: 16px;
                                }
                                .hero h1 {
                                  font-size: clamp(1.6rem, 8vw, 2.4rem);
                                }
                                .week-nav {
                                  justify-content: flex-start;
                                  gap: 8px;
                                }
                                .week-nav a,
                                .week-nav input {
                                  padding: 8px 10px;
                                  font-size: 0.9rem;
                                }
                                .calendar__grid {
                                  grid-template-columns: 60px minmax(700px, 1fr);
                                }
                                .time-column__header,
                                .day-column__header {
                                  padding: 8px;
                                }
                                .time-column__slot {
                                  padding-right: 6px;
                                  font-size: 0.72rem;
                                }
                                .calendar-block {
                                  padding: 6px;
                                  border-radius: 8px;
                                }
                                .calendar-block__title {
                                  font-size: 0.8rem;
                                }
                                .calendar-block__time,
                                .calendar-block__meta {
                                  margin-top: 2px;
                                  font-size: 0.66rem;
                                  line-height: 1.25;
                                }
                              }
                              @media (max-width: 640px) {
                                .calendar__grid {
                                  grid-template-columns: 52px minmax(560px, 1fr);
                                }
                                .day-column__header strong {
                                  font-size: 0.75rem;
                                }
                                .day-column__header span {
                                  font-size: 0.65rem;
                                }
                              }
                            </style>
                          </head>
                          <body>
                            <main>
                              <section class="hero">
                                <div>
                                  <h1>%s</h1>
                                </div>
                                <div class="week-nav">
                                  <a id="prev-week" href="#">上一周</a>
                                  <input id="week-picker" type="date" />
                                  <a id="next-week" href="#">下一周</a>
                                  <a id="current-week" href="#">回到本周</a>
                                </div>
                              </section>
                              <section class="calendar">
                                <div class="calendar__grid">
                                  <div class="time-column">
                                    <div class="time-column__header">时间</div>
                                    <div class="time-column__body" id="time-column"></div>
                                  </div>
                                  <div class="day-columns" id="calendar-grid"></div>
                                </div>
                              </section>
                            </main>
                            <script>
                              const payload = %s;
                              const hourHeight = %d;
                              const totalHeight = hourHeight * 24;
                              const buildWeekUrl = (start) => `/schedule-calendar?start=${encodeURIComponent(start)}`;
                              const toMinutes = (value) => {
                                const [hours, minutes] = value.split(":").map(Number);
                                return hours * 60 + minutes;
                              };
                              const assignColumns = (blocks) => {
                                const prepared = blocks.map((block) => ({
                                  ...block,
                                  startMinutes: toMinutes(block.start),
                                  endMinutes: toMinutes(block.end),
                                }));
                                const groups = [];
                                let currentGroup = [];
                                let currentGroupEnd = -1;
                                prepared.forEach((block) => {
                                  if (!currentGroup.length || block.startMinutes < currentGroupEnd) {
                                    currentGroup.push(block);
                                    currentGroupEnd = Math.max(currentGroupEnd, block.endMinutes);
                                    return;
                                  }
                                  groups.push(currentGroup);
                                  currentGroup = [block];
                                  currentGroupEnd = block.endMinutes;
                                });
                                if (currentGroup.length) {
                                  groups.push(currentGroup);
                                }
                                return groups.flatMap((group) => {
                                  const columns = [];
                                  const placements = group.map((block) => {
                                    let columnIndex = 0;
                                    while (columnIndex < columns.length && columns[columnIndex] > block.startMinutes) {
                                      columnIndex += 1;
                                    }
                                    if (columnIndex === columns.length) {
                                      columns.push(block.endMinutes);
                                    } else {
                                      columns[columnIndex] = block.endMinutes;
                                    }
                                    return {
                                      block,
                                      columnIndex,
                                    };
                                  });
                                  const columnCount = Math.max(columns.length, 1);
                                  const gap = 6;
                                  const width = `calc((100%% - ${(columnCount + 1) * gap}px) / ${columnCount})`;
                                  return placements.map(({ block, columnIndex }) => {
                                    const left = `calc(${gap}px + (${width} + ${gap}px) * ${columnIndex})`;
                                    const duration = Math.max(block.endMinutes - block.startMinutes, 30);
                                    const height = Math.max((duration / 60) * hourHeight - 6, 26);
                                    const density =
                                      height < 42 ? "minimal" : height < 76 ? "compact" : "full";
                                    return {
                                      ...block,
                                      left,
                                      width,
                                      height,
                                      top: (block.startMinutes / 60) * hourHeight,
                                      density,
                                    };
                                  });
                                });
                              };
                              document.getElementById("prev-week").href = buildWeekUrl(payload.previousWeekStart);
                              document.getElementById("next-week").href = buildWeekUrl(payload.nextWeekStart);
                              document.getElementById("current-week").href = buildWeekUrl(payload.currentWeekStart);
                              const weekPicker = document.getElementById("week-picker");
                              weekPicker.value = payload.weekStart;
                              weekPicker.addEventListener("change", (event) => {
                                const value = event.target.value;
                                if (!value) {
                                  return;
                                }
                                window.location.href = buildWeekUrl(value);
                              });
                              const timeColumn = document.getElementById("time-column");
                              Array.from({ length: 24 }, (_, hour) => {
                                const slot = document.createElement("div");
                                slot.className = "time-column__slot";
                                slot.textContent = `${String(hour).padStart(2, "0")}:00`;
                                timeColumn.appendChild(slot);
                              });
                              const grid = document.getElementById("calendar-grid");
                              payload.days.forEach((day) => {
                                const section = document.createElement("div");
                                section.className = "day-column";
                                section.innerHTML = `
                                  <header class="day-column__header">
                                    <strong>${day.dayLabel}</strong>
                                    <span>${day.date}</span>
                                  </header>
                                  <div class="day-column__body" style="height:${totalHeight}px;">
                                    <div class="day-column__lines"></div>
                                  </div>
                                `;
                                const body = section.querySelector(".day-column__body");
                                assignColumns(day.occupied).forEach((block) => {
                                  const element = document.createElement("article");
                                  element.className = "calendar-block";
                                  element.title = `${block.title} ${block.start} - ${block.end}${block.meta ? ` ${block.meta}` : ""}`;
                                  element.style.top = `${block.top}px`;
                                  element.style.left = block.left;
                                  element.style.width = block.width;
                                  element.style.height = `${block.height}px`;
                                  element.style.background = block.color;
                                  element.innerHTML = `
                                    <div class="calendar-block__title">${block.title}</div>
                                    ${block.density !== "minimal" ? `<div class="calendar-block__time">${block.start} - ${block.end}</div>` : ""}
                                    ${block.density === "full" ? `<div class="calendar-block__meta">${block.durationLabel}</div>` : ""}
                                    ${block.density === "full" && block.meta ? `<div class="calendar-block__meta">${block.meta}</div>` : ""}
                                  `;
                                  body.appendChild(element);
                                });
                                grid.appendChild(section);
                              });
                            </script>
                          </body>
                        </html>
                        """.formatted(pageTitle, CALENDAR_HEADER_HEIGHT, HOUR_HEIGHT, pageTitle,
                        objectMapper.writeValueAsString(view), HOUR_HEIGHT);
                } catch (JsonProcessingException ex) {
                    throw new IllegalStateException("Failed to render schedule calendar page.", ex);
                }
            });
    }

    private Mono<List<ScheduleEntry>> listEntries() {
        return client.listAll(ScheduleEntry.class, ListOptions.builder().build(), Sort.unsorted())
            .collectList()
            .map(entries -> entries.stream()
                .sorted(comparing(entry -> entry.getSpec().getStartTime()))
                .collect(Collectors.toList()));
    }

    private ScheduleCardResponse toScheduleCardResponse(ScheduleEntry entry) {
        var spec = entry.getSpec();
        return new ScheduleCardResponse(
            entry.getMetadata().getName(),
            spec.getTitle(),
            spec.getDescription(),
            spec.getLocation(),
            formatDateTime(spec.getStartTime()),
            formatDateTime(spec.getEndTime()),
            recurrenceDescription(spec.getRecurrence()),
            defaultColor(spec.getColor())
        );
    }

    private WeekViewResponse toWeekView(List<ScheduleEntry> entries, LocalDate weekStart, LocalDate weekEnd,
        ZoneId zoneId) {
        var occurrences = expandOccurrences(entries, weekStart, weekEnd, zoneId);
        var days = new ArrayList<DayView>();
        for (int offset = 0; offset < 7; offset++) {
            var date = weekStart.plusDays(offset);
            var occupied = toOccupiedBlocks(occurrences, date);
            var free = toFreeBlocks(occupied);
            days.add(new DayView(
                date.toString(),
                date.getDayOfWeek().getDisplayName(TextStyle.FULL, ZH_CN),
                occupied,
                free
            ));
        }
        return new WeekViewResponse(
            weekStart.toString(),
            weekEnd.toString(),
            LocalDate.now(zoneId).with(DayOfWeek.MONDAY).toString(),
            weekStart.minusWeeks(1).toString(),
            weekStart.plusWeeks(1).toString(),
            days
        );
    }

    private List<ScheduleOccurrence> expandOccurrences(List<ScheduleEntry> entries, LocalDate weekStart,
        LocalDate weekEnd, ZoneId zoneId) {
        var rangeStart = weekStart.atStartOfDay();
        var rangeEnd = weekEnd.plusDays(1).atStartOfDay();
        return entries.stream()
            .flatMap(entry -> occurrencesForRange(entry, rangeStart, rangeEnd, zoneId).stream())
            .sorted(comparing(ScheduleOccurrence::start))
            .collect(Collectors.toList());
    }

    private List<ScheduleOccurrence> occurrencesForRange(ScheduleEntry entry, LocalDateTime rangeStart,
        LocalDateTime rangeEnd, ZoneId zoneId) {
        var spec = entry.getSpec();
        var start = spec.getStartTime().atZoneSameInstant(zoneId).toLocalDateTime();
        var end = spec.getEndTime().atZoneSameInstant(zoneId).toLocalDateTime();
        if (!end.isAfter(start)) {
            return List.of();
        }
        if (!isRecurring(spec)) {
            if (end.isAfter(rangeStart) && start.isBefore(rangeEnd)) {
                return List.of(new ScheduleOccurrence(entry, start, end));
            }
            return List.of();
        }

        var recurrence = spec.getRecurrence();
        var frequency = recurrence.getFrequency();
        var interval = normalizeInterval(recurrence.getInterval());
        var duration = Duration.between(start, end);
        var cursor = alignOccurrenceStart(start, duration, rangeStart, frequency, interval);
        var occurrences = new ArrayList<ScheduleOccurrence>();
        while (cursor.isBefore(rangeEnd)) {
            if (isAfterUntil(cursor, recurrence)) {
                break;
            }
            var occurrenceEnd = cursor.plus(duration);
            if (occurrenceEnd.isAfter(rangeStart) && cursor.isBefore(rangeEnd)) {
                occurrences.add(new ScheduleOccurrence(entry, cursor, occurrenceEnd));
            }
            cursor = advanceOccurrence(cursor, frequency, interval);
        }
        return occurrences;
    }

    private List<TimeBlock> toOccupiedBlocks(List<ScheduleOccurrence> occurrences, LocalDate date) {
        var startOfDay = date.atStartOfDay();
        var endOfDay = date.plusDays(1).atStartOfDay();
        return occurrences.stream()
            .map(occurrence -> toBlock(occurrence, startOfDay, endOfDay))
            .filter(block -> block != null)
            .sorted(comparing(TimeBlock::start))
            .collect(Collectors.toList());
    }

    private TimeBlock toBlock(ScheduleOccurrence occurrence, LocalDateTime startOfDay, LocalDateTime endOfDay) {
        var spec = occurrence.entry().getSpec();
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
            spec.getTitle(),
            buildMeta(spec),
            formatDuration(clippedStart, clippedEnd),
            defaultColor(spec.getColor())
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
            formatDuration(start.atDate(LocalDate.now()), end.atDate(LocalDate.now())),
            "#94a3b8"
        );
    }

    private String buildMeta(ScheduleEntry.Spec spec) {
        var meta = new ArrayList<String>();
        if (spec.getDescription() != null && !spec.getDescription().isBlank()) {
            meta.add(spec.getDescription());
        }
        if (spec.getLocation() != null && !spec.getLocation().isBlank()) {
            meta.add("地点：" + spec.getLocation());
        }
        var recurrence = recurrenceDescription(spec.getRecurrence());
        if (recurrence != null) {
            meta.add(recurrence);
        }
        return meta.isEmpty() ? null : String.join(" / ", meta);
    }

    private String defaultColor(String color) {
        return color == null || color.isBlank() ? "#0f766e" : color;
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

    private String formatDateTime(OffsetDateTime value) {
        return value == null ? "" : DATE_TIME_FORMATTER.format(value.atZoneSameInstant(ZoneId.systemDefault()));
    }

    private boolean isRecurring(ScheduleEntry.Spec spec) {
        return spec != null
            && spec.getRecurrence() != null
            && spec.getRecurrence().getFrequency() != null
            && spec.getRecurrence().getFrequency() != ScheduleEntry.RecurrenceFrequency.NONE;
    }

    private int normalizeInterval(Integer interval) {
        return interval == null || interval < 1 ? 1 : interval;
    }

    private boolean isAfterUntil(LocalDateTime occurrenceStart, ScheduleEntry.Recurrence recurrence) {
        return recurrence.getUntil() != null && occurrenceStart.toLocalDate().isAfter(recurrence.getUntil());
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

    public record WeekViewResponse(String weekStart, String weekEnd, String currentWeekStart,
                                   String previousWeekStart,
                                   String nextWeekStart,
                                   List<DayView> days) {
    }

    public record DayView(String date, String dayLabel, List<TimeBlock> occupied, List<TimeBlock> free) {
    }

    public record TimeBlock(String start, String end, String title, String meta, String durationLabel,
                            String color) {
    }

    private record ScheduleOccurrence(ScheduleEntry entry, LocalDateTime start, LocalDateTime end) {
    }

    public record ScheduleCardResponse(String name, String title, String description, String location,
                                       String startTime, String endTime, String recurrenceDescription,
                                       String color) {
    }
}

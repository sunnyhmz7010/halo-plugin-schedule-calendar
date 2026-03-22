package run.halo.schedule.calendar;

import static java.util.Comparator.comparing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;

@Service
@RequiredArgsConstructor
public class ScheduleQueryService {
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter TIME_FORMATTER =
        DateTimeFormatter.ofPattern("HH:mm");
    private static final Locale ZH_CN = Locale.SIMPLIFIED_CHINESE;

    private final ReactiveExtensionClient client;
    private final ObjectMapper objectMapper;

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
                var spec = entry.getSpec();
                return new ScheduleCardResponse(
                    entry.getMetadata().getName(),
                    spec.getTitle(),
                    spec.getDescription(),
                    spec.getLocation(),
                    formatDateTime(spec.getStartTime()),
                    formatDateTime(spec.getEndTime()),
                    defaultColor(spec.getColor())
                );
            });
    }

    Mono<String> buildPublicCalendarPage(LocalDate requestedStart) {
        return getWeekView(requestedStart)
            .map(view -> {
                try {
                    return """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                          <head>
                            <meta charset="UTF-8" />
                            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                            <title>日程日历</title>
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
                                  radial-gradient(circle at top left, rgba(15,118,110,0.16), transparent 36%),
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
                              .hero p {
                                margin: 8px 0 0;
                                color: var(--muted);
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
                              .grid {
                                display: grid;
                                grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
                                gap: 18px;
                              }
                              .day {
                                border: 1px solid var(--line);
                                background: var(--panel);
                                backdrop-filter: blur(8px);
                                border-radius: 20px;
                                padding: 18px;
                                box-shadow: 0 18px 40px rgba(15, 23, 42, 0.06);
                              }
                              .day__header {
                                display: flex;
                                justify-content: space-between;
                                align-items: baseline;
                                margin-bottom: 14px;
                              }
                              .day__label {
                                font-size: 1.1rem;
                                font-weight: 700;
                              }
                              .day__date {
                                color: var(--muted);
                                font-size: 0.9rem;
                              }
                              .day__section-title {
                                margin: 18px 0 10px;
                                font-size: 0.9rem;
                                color: var(--muted);
                              }
                              .block-list {
                                display: flex;
                                flex-direction: column;
                                gap: 10px;
                              }
                              .block {
                                border-radius: 16px;
                                padding: 12px 14px;
                                border: 1px solid rgba(15, 23, 42, 0.06);
                              }
                              .block--occupied {
                                background: var(--accent-soft);
                              }
                              .block--free {
                                background: rgba(148, 163, 184, 0.12);
                              }
                              .block__time {
                                font-size: 0.88rem;
                                color: var(--muted);
                              }
                              .block__title {
                                margin-top: 6px;
                                font-weight: 700;
                              }
                              .block__meta {
                                margin-top: 6px;
                                color: var(--muted);
                                font-size: 0.88rem;
                                line-height: 1.5;
                              }
                              .empty {
                                color: var(--muted);
                                font-size: 0.92rem;
                              }
                            </style>
                          </head>
                          <body>
                            <main>
                              <section class="hero">
                                <div>
                                  <p>公开页面路由</p>
                                  <h1>日程日历</h1>
                                  <p id="week-range"></p>
                                </div>
                                <div class="week-nav">
                                  <a id="prev-week" href="#">查看上一周</a>
                                </div>
                              </section>
                              <section class="grid" id="calendar-grid"></section>
                            </main>
                            <script>
                              const payload = %s;
                              const rangeText = `${payload.weekStart} 至 ${payload.weekEnd}`;
                              document.getElementById("week-range").textContent = `本周范围：${rangeText}`;
                              document.getElementById("prev-week").href = `/schedule-calendar?start=${encodeURIComponent(payload.previousWeekStart)}`;
                              const grid = document.getElementById("calendar-grid");
                              payload.days.forEach((day) => {
                                const occupied = day.occupied.length
                                  ? day.occupied.map((block) => `
                                      <article class="block block--occupied" style="border-left: 4px solid ${block.color}">
                                        <div class="block__time">${block.start} - ${block.end} · ${block.durationLabel}</div>
                                        <div class="block__title">${block.title}</div>
                                        ${block.meta ? `<div class="block__meta">${block.meta}</div>` : ""}
                                      </article>
                                    `).join("")
                                  : '<div class="empty">今天没有已登记事项。</div>';
                                const free = day.free.length
                                  ? day.free.map((block) => `
                                      <article class="block block--free">
                                        <div class="block__time">${block.start} - ${block.end} · ${block.durationLabel}</div>
                                        <div class="block__title">空闲时间</div>
                                      </article>
                                    `).join("")
                                  : '<div class="empty">今天没有空闲时间段。</div>';
                                const section = document.createElement("section");
                                section.className = "day";
                                section.innerHTML = `
                                  <header class="day__header">
                                    <div class="day__label">${day.dayLabel}</div>
                                    <div class="day__date">${day.date}</div>
                                  </header>
                                  <div class="day__section-title">已占用</div>
                                  <div class="block-list">${occupied}</div>
                                  <div class="day__section-title">空闲</div>
                                  <div class="block-list">${free}</div>
                                `;
                                grid.appendChild(section);
                              });
                            </script>
                          </body>
                        </html>
                        """.formatted(objectMapper.writeValueAsString(view));
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

    private WeekViewResponse toWeekView(List<ScheduleEntry> entries, LocalDate weekStart, LocalDate weekEnd,
        ZoneId zoneId) {
        var days = new ArrayList<DayView>();
        for (int offset = 0; offset < 7; offset++) {
            var date = weekStart.plusDays(offset);
            var occupied = toOccupiedBlocks(entries, date, zoneId);
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
            weekStart.minusWeeks(1).toString(),
            days
        );
    }

    private List<TimeBlock> toOccupiedBlocks(List<ScheduleEntry> entries, LocalDate date, ZoneId zoneId) {
        var startOfDay = date.atStartOfDay();
        var endOfDay = date.plusDays(1).atStartOfDay();
        return entries.stream()
            .map(entry -> toBlock(entry, startOfDay, endOfDay, zoneId))
            .filter(block -> block != null)
            .sorted(comparing(TimeBlock::start))
            .collect(Collectors.toList());
    }

    private TimeBlock toBlock(ScheduleEntry entry, LocalDateTime startOfDay, LocalDateTime endOfDay,
        ZoneId zoneId) {
        var spec = entry.getSpec();
        var start = spec.getStartTime().atZoneSameInstant(zoneId).toLocalDateTime();
        var end = spec.getEndTime().atZoneSameInstant(zoneId).toLocalDateTime();
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

    public record WeekViewResponse(String weekStart, String weekEnd, String previousWeekStart,
                                   List<DayView> days) {
    }

    public record DayView(String date, String dayLabel, List<TimeBlock> occupied, List<TimeBlock> free) {
    }

    public record TimeBlock(String start, String end, String title, String meta, String durationLabel,
                            String color) {
    }

    public record ScheduleCardResponse(String name, String title, String description, String location,
                                       String startTime, String endTime, String color) {
    }
}

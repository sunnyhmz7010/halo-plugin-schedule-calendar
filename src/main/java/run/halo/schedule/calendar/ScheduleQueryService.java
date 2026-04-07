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
import org.springframework.web.util.HtmlUtils;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;

@Service
public class ScheduleQueryService {
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");
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

    Mono<DayView> getDayView(LocalDate requestedDate) {
        var zoneId = ZoneId.systemDefault();
        var date = requestedDate == null ? LocalDate.now(zoneId) : requestedDate;
        return listEntries()
            .map(entries -> toDayView(entries, date, zoneId));
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
        return listEntries()
            .map(entries -> expandOccurrences(entries, rangeStart, rangeEnd, zoneId).stream()
                .map(occurrence -> toOccurrenceResponse(occurrence, zoneId))
                .toList());
    }

    Mono<List<OccurrenceResponse>> listUpcomingOccurrences(Integer requestedLimit) {
        var zoneId = ZoneId.systemDefault();
        var now = LocalDateTime.now(zoneId);
        var start = now.toLocalDate();
        var end = now.plusDays(365).toLocalDate();
        var limit = normalizeLimit(requestedLimit);
        return listEntries()
            .map(entries -> expandOccurrences(entries, start, end, zoneId).stream()
                .filter(occurrence -> occurrence.end().isAfter(now))
                .limit(limit)
                .map(occurrence -> toOccurrenceResponse(occurrence, zoneId))
                .toList());
    }

    Mono<ScheduleCardResponse> getEntryCard(String name) {
        var zoneId = ZoneId.systemDefault();
        return client.get(ScheduleEntry.class, name)
            .map(entry -> toScheduleCardResponse(entry, zoneId))
            .onErrorResume(throwable -> listEntries()
                .flatMap(entries -> entries.stream()
                    .filter(entry -> entry.getMetadata() != null && name.equals(entry.getMetadata().getName()))
                    .findFirst()
                    .map(entry -> Mono.just(toScheduleCardResponse(entry, zoneId)))
                    .orElseGet(Mono::empty)));
    }

    Mono<List<ScheduleCardResponse>> listEntryCards() {
        var zoneId = ZoneId.systemDefault();
        return listEntries()
            .map(entries -> entries.stream()
                .map(entry -> toScheduleCardResponse(entry, zoneId))
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
                              .view-mode {
                                display: inline-flex;
                                gap: 8px;
                                padding: 6px;
                                margin-bottom: 16px;
                                border: 1px solid var(--line);
                                border-radius: 16px;
                                background: rgba(255,255,255,0.72);
                                backdrop-filter: blur(8px);
                              }
                              .view-mode__button {
                                border: 0;
                                padding: 10px 18px;
                                border-radius: 12px;
                                font: inherit;
                                color: var(--muted);
                                background: transparent;
                                cursor: pointer;
                              }
                              .view-mode__button.is-active {
                                color: var(--text);
                                background: #fff;
                                box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
                              }
                              .calendar-wrap {
                                display: grid;
                                gap: 16px;
                              }
                              .calendar {
                                border: 1px solid var(--line);
                                border-radius: 20px;
                                background: var(--panel);
                                overflow: hidden;
                                backdrop-filter: blur(8px);
                                box-shadow: 0 18px 40px rgba(15, 23, 42, 0.06);
                              }
                              .calendar[hidden],
                              .agenda[hidden] {
                                display: none !important;
                              }
                              .calendar__scroller {
                                width: 100%%;
                                border-radius: inherit;
                                overflow-x: auto;
                                overflow-y: hidden;
                                -webkit-overflow-scrolling: touch;
                                touch-action: pan-x pan-y pinch-zoom;
                                padding-bottom: 4px;
                              }
                              .calendar__grid {
                                display: grid;
                                grid-template-columns: 78px minmax(0, 1fr);
                                width: max(100%%, 1058px);
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
                                overflow: hidden;
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
                                box-sizing: border-box;
                                min-width: 0;
                                border-radius: 12px;
                                padding: 6px 8px;
                                color: #fff;
                                box-shadow: 0 10px 18px rgba(15, 23, 42, 0.12);
                                overflow: hidden;
                                text-align: center;
                              }
                              .calendar-block--split {
                                border-radius: 8px;
                                padding: 6px;
                              }
                              .calendar-block__title {
                                font-weight: 700;
                                line-height: 1.2;
                                width: 100%%;
                                overflow-wrap: anywhere;
                              }
                              .calendar-block__time,
                              .calendar-block__meta {
                                margin-top: 2px;
                                font-size: 0.76rem;
                                line-height: 1.35;
                                opacity: 0.95;
                                white-space: pre-line;
                                width: 100%%;
                                overflow-wrap: anywhere;
                              }
                              .agenda {
                                display: grid;
                                gap: 12px;
                              }
                              .agenda-day {
                                border: 1px solid var(--line);
                                border-radius: 18px;
                                background: var(--panel);
                                overflow: hidden;
                                backdrop-filter: blur(8px);
                                box-shadow: 0 18px 40px rgba(15, 23, 42, 0.06);
                              }
                              .agenda-day__header {
                                display: flex;
                                align-items: center;
                                justify-content: space-between;
                                gap: 12px;
                                padding: 16px 18px;
                                border-bottom: 1px solid var(--line);
                              }
                              .agenda-day__heading {
                                display: grid;
                                gap: 4px;
                              }
                              .agenda-day__heading strong {
                                font-size: 1rem;
                              }
                              .agenda-day__heading span,
                              .agenda-day__count,
                              .agenda-card__meta {
                                color: var(--muted);
                                font-size: 0.86rem;
                                line-height: 1.5;
                              }
                              .agenda-day__count {
                                white-space: nowrap;
                              }
                              .agenda-day__list {
                                display: grid;
                                gap: 10px;
                                padding: 14px;
                              }
                              .agenda-day__empty {
                                padding: 18px;
                                color: var(--muted);
                                font-size: 0.92rem;
                              }
                              .agenda-card {
                                display: grid;
                                grid-template-columns: 4px minmax(0, 1fr);
                                gap: 12px;
                                padding: 14px;
                                border-radius: 16px;
                                background: #fff;
                              }
                              .agenda-card__accent {
                                width: 4px;
                                border-radius: 999px;
                              }
                              .agenda-card__content {
                                display: grid;
                                gap: 4px;
                                min-width: 0;
                              }
                              .agenda-card__title {
                                font-size: 1rem;
                                font-weight: 700;
                                line-height: 1.5;
                                word-break: break-word;
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
                                  grid-template-columns: 60px minmax(0, 1fr);
                                  width: max(100%%, 900px);
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
                            @media (max-width: 768px) {
                              .calendar__grid {
                                grid-template-columns: 48px minmax(0, 1fr);
                                width: 100%%;
                              }
                              .calendar__scroller {
                                overflow-x: visible;
                              }
                              .day-columns {
                                grid-template-columns: repeat(7, minmax(0, 1fr));
                              }
                              .day-column__header {
                                padding-left: 4px;
                                padding-right: 4px;
                              }
                              .day-column__header strong {
                                font-size: 0.78rem;
                              }
                              .day-column__header span {
                                font-size: 0.68rem;
                              }
                              .time-column__header,
                              .time-column__slot {
                                font-size: 0.68rem;
                              }
                              .time-column__slot {
                                padding-right: 4px;
                              }
                              .calendar-block,
                              .calendar-block--split {
                                justify-content: center;
                                align-items: center;
                                padding: 5px 4px;
                                border-radius: 6px;
                                text-align: center;
                              }
                              .calendar-block__title {
                                font-size: 0.75rem;
                                line-height: 1.2;
                              }
                              .calendar-block__time,
                              .calendar-block__meta {
                                display: none;
                              }
                            }
                            @media (max-width: 640px) {
                              .calendar__grid {
                                grid-template-columns: 44px minmax(0, 1fr);
                              }
                              .day-column__header strong {
                                font-size: 0.75rem;
                              }
                              .day-column__header span {
                                font-size: 0.65rem;
                              }
                              .agenda-day__header {
                                padding: 14px 16px;
                              }
                                .agenda-day__list {
                                  padding: 10px;
                                }
                                .agenda-card {
                                  padding: 12px;
                                }
                              }
                            </style>
                            <style>
                              .now-status {
                                display: inline-flex;
                                align-items: center;
                                gap: 8px;
                                margin-top: 12px;
                                padding: 8px 16px;
                                border-radius: 999px;
                                font-size: 0.9rem;
                              }
                              .now-status--free {
                                background: rgba(5, 150, 105, 0.12);
                                color: #059669;
                                border: 1px solid rgba(5, 150, 105, 0.2);
                              }
                              .now-status--busy {
                                background: rgba(245, 158, 11, 0.12);
                                color: #d97706;
                                border: 1px solid rgba(245, 158, 11, 0.2);
                              }
                              .now-status__dot {
                                width: 8px;
                                height: 8px;
                                border-radius: 50%%;
                                flex-shrink: 0;
                              }
                              .now-status--free .now-status__dot {
                                background: #10b981;
                              }
                              .now-status--busy .now-status__dot {
                                background: #f59e0b;
                              }
                              .now-line {
                                position: absolute;
                                left: 0;
                                right: 0;
                                height: 2px;
                                background: #ef4444;
                                z-index: 2;
                                pointer-events: none;
                              }
                              .now-line::before {
                                content: '';
                                position: absolute;
                                left: 0;
                                top: -4px;
                                width: 10px;
                                height: 10px;
                                border-radius: 50%%;
                                background: #ef4444;
                              }
                            </style>
                          </head>
                          <body>
                            <main>
                              <section class="hero">
                                <div>
                                  <h1>%s</h1>
                                  <div id="now-status-wrap"></div>
                                </div>
                                <div class="week-nav">
                                  <a id="prev-week" href="#">上一周</a>
                                  <input id="week-picker" type="date" />
                                  <a id="next-week" href="#">下一周</a>
                                  <a id="current-week" href="#">回到本周</a>
                                </div>
                              </section>
                              <section class="view-mode" aria-label="视图切换">
                                <button type="button" class="view-mode__button" data-view-mode="calendar">日历布局</button>
                                <button type="button" class="view-mode__button" data-view-mode="agenda">事项布局</button>
                              </section>
                              <section class="calendar-wrap">
                                <section class="calendar" id="calendar-view">
                                  <div class="calendar__scroller">
                                    <div class="calendar__grid">
                                      <div class="time-column">
                                        <div class="time-column__header">时间</div>
                                        <div class="time-column__body" id="time-column"></div>
                                      </div>
                                      <div class="day-columns" id="calendar-grid"></div>
                                    </div>
                                  </div>
                                </section>
                                <section class="agenda" id="agenda-view"></section>
                              </section>
                            </main>
                            <script>
                              const payload = %s;
                              const hourHeight = %d;
                              const totalHeight = hourHeight * 24;
                              const searchParams = new URLSearchParams(window.location.search);
                              const resolveResponsiveView = () => window.innerWidth <= 768 ? "agenda" : "calendar";
                              let manualViewSelection = searchParams.has("view");
                              let currentView = searchParams.get("view") === "agenda"
                                ? "agenda"
                                : (manualViewSelection ? "calendar" : resolveResponsiveView());
                              const buildWeekUrl = (start) => {
                                const params = new URLSearchParams(window.location.search);
                                params.set("start", start);
                                if (currentView === "agenda") {
                                  params.set("view", currentView);
                                } else {
                                  params.delete("view");
                                }
                                return `/schedule-calendar?${params.toString()}`;
                              };
                              const toMinutes = (value) => {
                                const [hours, minutes] = value.split(":").map(Number);
                                return hours * 60 + minutes;
                              };
                              const calendarView = document.getElementById("calendar-view");
                              const agendaView = document.getElementById("agenda-view");
                              const prevWeekLink = document.getElementById("prev-week");
                              const nextWeekLink = document.getElementById("next-week");
                              const currentWeekLink = document.getElementById("current-week");
                              const viewModeButtons = Array.from(document.querySelectorAll("[data-view-mode]"));
                              const syncViewMode = () => {
                                calendarView.hidden = currentView !== "calendar";
                                agendaView.hidden = currentView !== "agenda";
                                viewModeButtons.forEach((button) => {
                                  button.classList.toggle("is-active", button.dataset.viewMode === currentView);
                                });
                                prevWeekLink.href = buildWeekUrl(payload.previousWeekStart);
                                nextWeekLink.href = buildWeekUrl(payload.nextWeekStart);
                                currentWeekLink.href = buildWeekUrl(payload.currentWeekStart);
                                const params = new URLSearchParams(window.location.search);
                                if (currentView === "agenda") {
                                  params.set("view", currentView);
                                } else {
                                  params.delete("view");
                                }
                                const query = params.toString();
                                window.history.replaceState(null, "", query ? `${window.location.pathname}?${query}` : window.location.pathname);
                              };
                              const assignColumns = (blocks) => {
                                const prepared = blocks.map((block) => ({
                                  ...block,
                                  startMinutes: toMinutes(block.start),
                                  endMinutes: toMinutes(block.end),
                                }));
                                const buildVisibleMetaLines = (block) => {
                                  if (block.density !== "full" || !Array.isArray(block.metaLines) || !block.metaLines.length) {
                                    return [];
                                  }
                                  const titleLines = block.title.length > 10 ? 2 : 1;
                                  const contentBudget = Math.max(0, block.height - 12);
                                  const reservedHeight =
                                    titleLines * 18 +
                                    18 +
                                    18 +
                                    (block.isRecurring ? 22 : 0);
                                  const maxMetaLines = Math.max(0, Math.floor((contentBudget - reservedHeight) / 18));
                                  return block.metaLines.slice(0, maxMetaLines);
                                };
                                prepared.forEach((block) => {
                                  if (block.endMinutes <= block.startMinutes) {
                                    block.endMinutes = 24 * 60;
                                    block.endLabel = block.end === "00:00" ? "24:00" : block.end;
                                  } else {
                                    block.endLabel = block.end;
                                  }
                                });
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
                                      isSplit: columnCount > 1,
                                      visibleMetaLines: buildVisibleMetaLines({
                                        ...block,
                                        density,
                                        height,
                                      }),
                                    };
                                  });
                                });
                              };
                              const buildAgendaCard = (block) => {
                                const article = document.createElement("article");
                                article.className = "agenda-card";

                                const accent = document.createElement("div");
                                accent.className = "agenda-card__accent";
                                accent.style.background = block.color;

                                const content = document.createElement("div");
                                content.className = "agenda-card__content";

                                const title = document.createElement("div");
                                title.className = "agenda-card__title";
                                title.textContent = block.title;
                                content.appendChild(title);

                                const time = document.createElement("div");
                                time.className = "agenda-card__meta";
                                time.textContent = `${block.start} - ${block.end}`;
                                content.appendChild(time);

                                const duration = document.createElement("div");
                                duration.className = "agenda-card__meta";
                                duration.textContent = block.durationLabel;
                                content.appendChild(duration);

                                (Array.isArray(block.metaLines) ? block.metaLines : []).forEach((line) => {
                                  const meta = document.createElement("div");
                                  meta.className = "agenda-card__meta";
                                  meta.textContent = line;
                                  content.appendChild(meta);
                                });

                                article.append(accent, content);
                                return article;
                              };
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
                              const agenda = document.getElementById("agenda-view");
                              const grid = document.getElementById("calendar-grid");
                              payload.days.forEach((day) => {
                                const section = document.createElement("div");
                                section.className = "day-column";
                                const header = document.createElement("header");
                                header.className = "day-column__header";
                                const headerTitle = document.createElement("strong");
                                headerTitle.textContent = day.dayLabel;
                                const headerDate = document.createElement("span");
                                headerDate.textContent = day.date;
                                header.append(headerTitle, headerDate);

                                const body = document.createElement("div");
                                body.className = "day-column__body";
                                body.style.height = `${totalHeight}px`;
                                const lines = document.createElement("div");
                                lines.className = "day-column__lines";
                                body.appendChild(lines);
                                section.append(header, body);
                                assignColumns(day.occupied).forEach((block) => {
                                  const element = document.createElement("article");
                                  element.className = block.isSplit ? "calendar-block calendar-block--split" : "calendar-block";
                                  element.title = `${block.title} ${block.start} - ${block.endLabel}${block.tooltipMeta ? ` ${block.tooltipMeta}` : ""}`;
                                  element.style.top = `${block.top}px`;
                                  element.style.left = block.left;
                                  element.style.width = block.width;
                                  element.style.height = `${block.height}px`;
                                  element.style.background = block.color;
                                  const blockTitle = document.createElement("div");
                                  blockTitle.className = "calendar-block__title";
                                  blockTitle.textContent = block.title;
                                  element.appendChild(blockTitle);
                                  if (block.density !== "minimal") {
                                    const blockTime = document.createElement("div");
                                    blockTime.className = "calendar-block__time";
                                    blockTime.textContent = `${block.start} - ${block.endLabel}`;
                                    element.appendChild(blockTime);
                                  }
                                  if (block.density === "full" && !block.isSplit) {
                                    const duration = document.createElement("div");
                                    duration.className = "calendar-block__meta";
                                    duration.textContent = block.durationLabel;
                                    element.appendChild(duration);
                                  }
                                  if (!block.isSplit && Array.isArray(block.visibleMetaLines)) {
                                    block.visibleMetaLines.forEach((line) => {
                                      const meta = document.createElement("div");
                                      meta.className = "calendar-block__meta";
                                      meta.textContent = line;
                                      element.appendChild(meta);
                                    });
                                  }
                                  body.appendChild(element);
                                });
                                grid.appendChild(section);

                                const agendaDay = document.createElement("section");
                                agendaDay.className = "agenda-day";

                                const agendaHeader = document.createElement("header");
                                agendaHeader.className = "agenda-day__header";

                                const agendaHeading = document.createElement("div");
                                agendaHeading.className = "agenda-day__heading";
                                const dayTitle = document.createElement("strong");
                                dayTitle.textContent = day.dayLabel;
                                const dayDate = document.createElement("span");
                                dayDate.textContent = day.date;
                                agendaHeading.append(dayTitle, dayDate);

                                const agendaCount = document.createElement("div");
                                agendaCount.className = "agenda-day__count";
                                agendaCount.textContent = `${day.occupied.length} 项`;
                                agendaHeader.append(agendaHeading, agendaCount);
                                agendaDay.appendChild(agendaHeader);

                                if (day.occupied.length) {
                                  const agendaList = document.createElement("div");
                                  agendaList.className = "agenda-day__list";
                                  day.occupied.forEach((block) => {
                                    agendaList.appendChild(buildAgendaCard(block));
                                  });
                                  agendaDay.appendChild(agendaList);
                                } else {
                                  const empty = document.createElement("div");
                                  empty.className = "agenda-day__empty";
                                  empty.textContent = "当天暂无事项";
                                  agendaDay.appendChild(empty);
                                }

                                agenda.appendChild(agendaDay);
                              });
                              viewModeButtons.forEach((button) => {
                                button.addEventListener("click", () => {
                                  manualViewSelection = true;
                                  currentView = button.dataset.viewMode === "agenda" ? "agenda" : "calendar";
                                  syncViewMode();
                                });
                              });
                              window.addEventListener("resize", () => {
                                if (manualViewSelection) {
                                  return;
                                }
                                const nextView = resolveResponsiveView();
                                if (nextView !== currentView) {
                                  currentView = nextView;
                                  syncViewMode();
                                }
                              });
                              syncViewMode();
                              const nowStatusWrap = document.getElementById("now-status-wrap");
                              const updateNowIndicators = () => {
                                const now = new Date();
                                const todayStr = now.toISOString().split("T")[0];
                                const nowMinutes = now.getHours() * 60 + now.getMinutes();
                                const todayIndex = payload.days.findIndex((day) => day.date === todayStr);
                                if (nowStatusWrap) {
                                  nowStatusWrap.innerHTML = "";
                                  if (todayIndex >= 0) {
                                    const todayOccupied = payload.days[todayIndex].occupied;
                                    const activeTitles = [];
                                    for (const block of todayOccupied) {
                                      const blockStartMins = toMinutes(block.start);
                                      const rawEnd = block.end === "00:00" ? 24 * 60 : toMinutes(block.end);
                                      const blockEndMins = rawEnd <= blockStartMins ? 24 * 60 : rawEnd;
                                      if (nowMinutes >= blockStartMins && nowMinutes < blockEndMins) {
                                        activeTitles.push(block.title);
                                      }
                                    }
                                    const activeLabel = activeTitles.length > 0 ? activeTitles.join("\u3001") : null;
                                    const badge = document.createElement("div");
                                    badge.className = activeLabel ? "now-status now-status--busy" : "now-status now-status--free";
                                    const dot = document.createElement("span");
                                    dot.className = "now-status__dot";
                                    const text = document.createElement("span");
                                    text.textContent = activeLabel ? "\u8fdb\u884c\u4e2d\uff1a" + activeLabel : "\u5f53\u524d\u7a7a\u95f2";
                                    badge.append(dot, text);
                                    nowStatusWrap.appendChild(badge);
                                  }
                                }
                                document.querySelectorAll(".now-line").forEach((el) => el.remove());
                                if (todayIndex >= 0) {
                                  const columns = grid.querySelectorAll(".day-column");
                                  const todayColumn = columns[todayIndex];
                                  if (todayColumn) {
                                    const body = todayColumn.querySelector(".day-column__body");
                                    if (body) {
                                      const nowTop = (nowMinutes / 60) * hourHeight;
                                      const line = document.createElement("div");
                                      line.className = "now-line";
                                      line.style.top = nowTop + "px";
                                      body.appendChild(line);
                                    }
                                  }
                                }
                              };
                              updateNowIndicators();
                              setInterval(updateNowIndicators, 60000);
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

    Mono<String> buildPublicCardPage(String name) {
        return getEntryCard(name)
            .map(card -> {
                var title = escapeHtml(card.title());
                var summary = card.recurrenceDescription() == null || card.recurrenceDescription().isBlank()
                    ? escapeHtml(card.startTime() + " - " + card.endTime())
                    : escapeHtml(card.recurrenceDescription() + " · 首次 " + card.startTime() + " - " + card.endTime());
                var summaryClass = card.recurrenceDescription() == null || card.recurrenceDescription().isBlank()
                    ? "entry-meta__item"
                    : "entry-meta__item entry-meta__item--wide entry-meta__item--block";
                var html = new StringBuilder();
                html.append("""
                    <!DOCTYPE html>
                    <html lang="zh-CN">
                      <head>
                        <meta charset="UTF-8" />
                        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                        <style>
                          * { box-sizing: border-box; }
                          html, body {
                            margin: 0;
                            padding: 0;
                            background: transparent;
                            font-family: "Segoe UI", "PingFang SC", sans-serif;
                          }
                          .schedule-card {
                            border: 1px solid #e5e7eb;
                            border-radius: 12px;
                            background: #ffffff;
                            overflow: hidden;
                          }
                          .schedule-card__inner {
                            display: grid;
                            gap: 10px;
                            padding: 14px 16px;
                          }
                          .entry-start {
                            display: flex;
                            align-items: flex-start;
                            gap: 12px;
                            min-width: 0;
                          }
                          .entry-dot {
                            width: 10px;
                            height: 10px;
                            margin-top: 6px;
                            border-radius: 999px;
                            flex: none;
                          }
                          .entry-main {
                            display: flex;
                            flex: 1;
                            min-width: 0;
                            flex-direction: column;
                            gap: 6px;
                          }
                          .entry-title {
                            min-width: 0;
                            color: #111827;
                            font-size: 14px;
                            font-weight: 600;
                            line-height: 1.5;
                            word-break: break-word;
                          }
                          .entry-meta {
                            display: flex;
                            flex-wrap: wrap;
                            gap: 6px;
                            min-width: 0;
                          }
                          .entry-meta__item {
                            display: inline-flex;
                            align-items: center;
                            flex: 0 1 auto;
                            max-width: 100%;
                            min-width: 0;
                            padding: 4px 10px;
                            border-radius: 999px;
                            background: #f8fafc;
                            color: #6b7280;
                            font-size: 12px;
                            line-height: 1.5;
                            word-break: break-word;
                          }
                          .entry-meta__item--wide {
                            flex: 1 1 320px;
                            max-width: 100%;
                            border-radius: 12px;
                            white-space: normal;
                          }
                          .entry-meta__item--block {
                            flex-basis: 100%;
                          }
                        </style>
                      </head>
                      <body>
                        <div class="schedule-card">
                          <div class="schedule-card__inner">
                            <div class="entry-start">
                    """);
                html.append("                              <span class=\"entry-dot\" style=\"background:");
                html.append(escapeHtml(defaultColor(card.color())));
                html.append("\"></span>\n"
                    + "                              <div class=\"entry-main\">\n"
                    + "                                <div class=\"entry-title\">");
                html.append(title);
                html.append("</div>\n"
                    + "                                <div class=\"entry-meta\">\n"
                    + "                                  <span class=\"");
                html.append(summaryClass);
                html.append("\">");
                html.append(summary);
                html.append("</span>");
                appendCardMetaItem(html, "下一次出现：", card.nextOccurrenceLabel());
                appendCardMetaItem(html, "地点：", card.location());
                appendCardMetaItem(html, "备注：", card.description());
                html.append("""
                                </div>
                              </div>
                            </div>
                          </div>
                        </div>
                      </body>
                    </html>
                    """);
                return html.toString();
            });
    }

    private void appendCardMetaItem(StringBuilder html, String prefix, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        html.append("<span class=\"entry-meta__item entry-meta__item--wide entry-meta__item--block\">")
            .append(prefix)
            .append(escapeHtml(value))
            .append("</span>");
    }

    private Mono<List<ScheduleEntry>> listEntries() {
        return client.listAll(ScheduleEntry.class, ListOptions.builder().build(), Sort.unsorted())
            .collectList()
            .map(entries -> entries.stream()
                .sorted(comparing(entry -> entry.getSpec().getStartTime()))
                .collect(Collectors.toList()));
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
            defaultColor(spec.getColor())
        );
    }

    private WeekViewResponse toWeekView(List<ScheduleEntry> entries, LocalDate weekStart, LocalDate weekEnd,
        ZoneId zoneId) {
        var days = new ArrayList<DayView>();
        for (int offset = 0; offset < 7; offset++) {
            var date = weekStart.plusDays(offset);
            days.add(toDayView(entries, date, zoneId));
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

    private DayView toDayView(List<ScheduleEntry> entries, LocalDate date, ZoneId zoneId) {
        var occurrences = expandOccurrences(entries, date, date, zoneId);
        var occupied = toOccupiedBlocks(occurrences, date);
        var free = toFreeBlocks(occupied);
        return new DayView(
            date.toString(),
            date.getDayOfWeek().getDisplayName(TextStyle.FULL, ZH_CN),
            occupied,
            free
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
        if (!isRecurring(spec) || spansMultipleDates(start, end)) {
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
            buildMetaLines(spec),
            buildTooltipMeta(spec),
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
            null,
            formatDuration(start.atDate(LocalDate.now()), end.atDate(LocalDate.now())),
            "#94a3b8"
        );
    }

    private String buildTooltipMeta(ScheduleEntry.Spec spec) {
        var meta = buildMetaLines(spec);
        return meta.isEmpty() ? null : String.join(" ", meta);
    }

    private List<String> buildMetaLines(ScheduleEntry.Spec spec) {
        var meta = new ArrayList<String>();
        if (spec.getLocation() != null && !spec.getLocation().isBlank()) {
            meta.add("地点：" + spec.getLocation());
        }
        if (spec.getDescription() != null && !spec.getDescription().isBlank()) {
            meta.add("备注：" + spec.getDescription());
        }
        var recurrence = recurrenceDescription(spec.getRecurrence());
        if (recurrence != null) {
            meta.add(recurrence);
        }
        return meta;
    }

    private String defaultColor(String color) {
        return color == null || color.isBlank() ? "#0f766e" : color;
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

        ScheduleOccurrence nextOccurrence;
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

    private OccurrenceResponse toOccurrenceResponse(ScheduleOccurrence occurrence, ZoneId zoneId) {
        var entry = occurrence.entry();
        var spec = entry.getSpec();
        var start = occurrence.start();
        var end = occurrence.end();
        return new OccurrenceResponse(
            entry.getMetadata().getName(),
            spec.getTitle(),
            spec.getDescription(),
            spec.getLocation(),
            DATE_TIME_FORMATTER.format(start),
            DATE_TIME_FORMATTER.format(end),
            start.toLocalDate().toString(),
            start.getDayOfWeek().getDisplayName(TextStyle.FULL, ZH_CN),
            recurrenceDescription(spec.getRecurrence()),
            formatDuration(start, end),
            defaultColor(spec.getColor())
        );
    }

    private String escapeHtml(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }

    public record WeekViewResponse(String weekStart, String weekEnd, String currentWeekStart,
                                   String previousWeekStart,
                                   String nextWeekStart,
                                   List<DayView> days) {
    }

    public record DayView(String date, String dayLabel, List<TimeBlock> occupied, List<TimeBlock> free) {
    }

    public record TimeBlock(String start, String end, String title, List<String> metaLines, String tooltipMeta,
                            String durationLabel, String color) {
    }

    private record ScheduleOccurrence(ScheduleEntry entry, LocalDateTime start, LocalDateTime end) {
    }

    public record OccurrenceResponse(String name, String title, String description, String location,
                                     String startTime, String endTime, String date, String dayLabel,
                                     String recurrenceDescription, String durationLabel, String color) {
    }

    public record ScheduleCardResponse(String name, String title, String description, String location,
                                       String startTime, String endTime, String recurrenceDescription,
                                       String nextOccurrenceLabel, String color) {
    }
}

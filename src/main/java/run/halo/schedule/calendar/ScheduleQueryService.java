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
import java.util.Comparator;
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
    private static final DateTimeFormatter ICAL_DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final int CALENDAR_HEADER_HEIGHT = 64;
    private static final int HOUR_HEIGHT = 56;
    private static final Locale ZH_CN = Locale.SIMPLIFIED_CHINESE;

    private final ReactiveExtensionClient client;
    private final ReactiveSettingFetcher settingFetcher;
    private final ExternalCalendarService externalCalendarService;
    private final JsonMapper objectMapper;

    public ScheduleQueryService(ReactiveExtensionClient client,
        ReactiveSettingFetcher settingFetcher,
        ExternalCalendarService externalCalendarService) {
        this.client = client;
        this.settingFetcher = settingFetcher;
        this.externalCalendarService = externalCalendarService;
        this.objectMapper = JsonMapper.builder()
            .findAndAddModules()
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
        return loadCalendarContext(rangeStart, rangeEnd, zoneId)
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

    Mono<String> exportPublicIcal() {
        return listEntries().map(this::toIcalContent);
    }

    Mono<String> buildPublicCalendarPage(LocalDate requestedStart) {
        return Mono.zip(
                getWeekView(requestedStart),
                settingFetcher.fetch(ScheduleCalendarSetting.GROUP, ScheduleCalendarSetting.class)
                    .defaultIfEmpty(new ScheduleCalendarSetting(null, null))
            )
            .map(tuple -> {
                var view = tuple.getT1();
                var pageTitle = tuple.getT2().effectiveTitle();
                var escapedPageTitle = escapeHtml(pageTitle);
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
                                display: grid;
                                grid-template-columns: minmax(0, 1fr) auto;
                                gap: 24px;
                                align-items: start;
                                margin-bottom: 24px;
                              }
                              .hero__heading {
                                display: grid;
                                gap: 12px;
                              }
                              .hero__controls {
                                display: grid;
                                gap: 14px;
                                justify-items: end;
                                align-content: end;
                                padding-top: 10px;
                              }
                              .hero h1 {
                                margin: 0;
                                font-size: clamp(2rem, 4vw, 3.4rem);
                              }
                              .current-status {
                                display: inline-flex;
                                align-items: center;
                                gap: 10px;
                                width: fit-content;
                                max-width: 100%%;
                                padding: 10px 14px;
                                border-radius: 999px;
                                border: 1px solid rgba(15, 118, 110, 0.14);
                                background: rgba(15, 118, 110, 0.1);
                                color: #0f766e;
                                font-size: 0.95rem;
                                font-weight: 600;
                                line-height: 1.4;
                              }
                              .current-status::before {
                                content: "";
                                width: 10px;
                                height: 10px;
                                border-radius: 999px;
                                background: currentColor;
                                flex: none;
                              }
                              .current-status.is-busy {
                                border-color: rgba(217, 119, 6, 0.18);
                                background: rgba(245, 158, 11, 0.12);
                                color: #b45309;
                              }
                              .next-status {
                                display: inline-flex;
                                align-items: center;
                                gap: 10px;
                                width: fit-content;
                                max-width: 100%%;
                                padding: 10px 14px;
                                border-radius: 999px;
                                border: 1px solid rgba(37, 99, 235, 0.14);
                                background: rgba(59, 130, 246, 0.1);
                                color: #1d4ed8;
                                font-size: 0.95rem;
                                font-weight: 600;
                                line-height: 1.4;
                              }
                              .next-status::before {
                                content: "";
                                width: 10px;
                                height: 10px;
                                border-radius: 999px;
                                background: currentColor;
                                flex: none;
                              }
                              .week-nav {
                                display: flex;
                                flex-wrap: wrap;
                                justify-content: flex-end;
                                align-items: center;
                                gap: 10px;
                                width: fit-content;
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
                                border: 1px solid var(--line);
                                border-radius: 16px;
                                background: rgba(255,255,255,0.72);
                                backdrop-filter: blur(8px);
                                justify-self: end;
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
                                z-index: 0;
                                background-image: repeating-linear-gradient(
                                  to bottom,
                                  transparent,
                                  transparent 55px,
                                  #f3f4f6 55px,
                                  #f3f4f6 56px
                                );
                              }
                              .current-time-line {
                                position: absolute;
                                left: 0;
                                right: 0;
                                height: 2px;
                                background: #ef4444;
                                transform: translateY(-1px);
                                z-index: 4;
                                pointer-events: none;
                              }
                              .current-time-line__dot {
                                position: absolute;
                                left: 0;
                                top: 50%%;
                                width: 10px;
                                height: 10px;
                                border-radius: 999px;
                                background: #ef4444;
                                box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.18);
                                transform: translate(-50%%, -50%%);
                              }
                              .calendar-block {
                                position: absolute;
                                z-index: 2;
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
                              .hero {
                                grid-template-columns: minmax(0, 1fr);
                                gap: 18px;
                              }
                              .hero__controls {
                                justify-items: stretch;
                                padding-top: 0;
                              }
                              .week-nav {
                                justify-content: flex-start;
                              }
                              .view-mode {
                                justify-self: start;
                              }
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
                          </head>
                          <body>
                            <main>
                              <section class="hero">
                                <div class="hero__heading">
                                  <h1>%s</h1>
                                  <div id="current-status" class="current-status" hidden></div>
                                  <div id="next-status" class="next-status" hidden></div>
                                </div>
                                <div class="hero__controls">
                                  <div class="week-nav">
                                    <a id="prev-week" href="#">上一周</a>
                                    <input id="week-picker" type="date" />
                                    <a id="next-week" href="#">下一周</a>
                                    <a id="current-week" href="#">回到本周</a>
                                  </div>
                                  <section class="view-mode" aria-label="视图切换">
                                    <button type="button" class="view-mode__button" data-view-mode="calendar">日历布局</button>
                                    <button type="button" class="view-mode__button" data-view-mode="agenda">事项布局</button>
                                  </section>
                                </div>
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
                              const summaryApiUrl = "/apis/api.schedule.calendar.sunny.dev/v1alpha1/summary";
                              const searchParams = new URLSearchParams(window.location.search);
                              const resolveResponsiveView = () => window.innerWidth <= 768 ? "agenda" : "calendar";
                              let manualViewSelection = searchParams.has("view");
                              let currentView = searchParams.get("view") === "agenda"
                                ? "agenda"
                                : (manualViewSelection ? "calendar" : resolveResponsiveView());
                              let liveSummary = payload.summary ?? null;
                              let serverNowAnchor = payload.serverTime ? new Date(payload.serverTime) : new Date();
                              if (Number.isNaN(serverNowAnchor.getTime())) {
                                serverNowAnchor = new Date();
                              }
                              let clientNowAnchor = Date.now();
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
                              const toDateKey = (date) => {
                                const year = date.getFullYear();
                                const month = String(date.getMonth() + 1).padStart(2, "0");
                                const day = String(date.getDate()).padStart(2, "0");
                                return `${year}-${month}-${day}`;
                              };
                              const zonedDateFormatter = payload.zoneId
                                ? new Intl.DateTimeFormat("en-CA", {
                                    timeZone: payload.zoneId,
                                    year: "numeric",
                                    month: "2-digit",
                                    day: "2-digit",
                                  })
                                : null;
                              const zonedTimeFormatter = payload.zoneId
                                ? new Intl.DateTimeFormat("en-GB", {
                                    timeZone: payload.zoneId,
                                    hour: "2-digit",
                                    minute: "2-digit",
                                    hour: "2-digit",
                                    minute: "2-digit",
                                    hour12: false,
                                  })
                                : null;
                              const partsToRecord = (parts) =>
                                parts.reduce((record, part) => {
                                  if (part.type !== "literal") {
                                    record[part.type] = part.value;
                                  }
                                  return record;
                                }, {});
                              const toDateKeyInZone = (date) => {
                                if (!zonedDateFormatter) {
                                  return toDateKey(date);
                                }
                                const parts = partsToRecord(zonedDateFormatter.formatToParts(date));
                                return `${parts.year}-${parts.month}-${parts.day}`;
                              };
                              const getClockPartsInZone = (date) => {
                                if (!zonedTimeFormatter) {
                                  return {
                                    hours: date.getHours(),
                                    minutes: date.getMinutes(),
                                  };
                                }
                                const parts = partsToRecord(zonedTimeFormatter.formatToParts(date));
                                return {
                                  hours: Number(parts.hour ?? 0),
                                  minutes: Number(parts.minute ?? 0),
                                };
                              };
                              const syncServerClock = (serverTime) => {
                                if (!serverTime) {
                                  return;
                                }
                                const parsed = new Date(serverTime);
                                if (Number.isNaN(parsed.getTime())) {
                                  return;
                                }
                                serverNowAnchor = parsed;
                                clientNowAnchor = Date.now();
                              };
                              const getServerNow = () =>
                                new Date(serverNowAnchor.getTime() + (Date.now() - clientNowAnchor));
                              const toMinutes = (value) => {
                                const [hours, minutes] = value.split(":").map(Number);
                                return hours * 60 + minutes;
                              };
                              const normalizeEndMinutes = (block) => {
                                const startMinutes = toMinutes(block.start);
                                const endMinutes = toMinutes(block.end);
                                return endMinutes <= startMinutes ? 24 * 60 : endMinutes;
                              };
                              const formatCurrentStatusText = (titles) => {
                                const normalized = [...new Set(
                                  titles
                                    .filter(Boolean)
                                    .map((title) => title.trim())
                                    .filter(Boolean)
                                )];
                                if (!normalized.length) {
                                  return "当前空闲";
                                }
                                if (normalized.length <= 2) {
                                  return `进行中：${normalized.join("、")}`;
                                }
                                return `进行中：${normalized.slice(0, 2).join("、")} 等 ${normalized.length} 项`;
                              };
                              const formatCountdownDuration = (target, reference = new Date()) => {
                                const totalMinutes = Math.max(Math.ceil((target.getTime() - reference.getTime()) / 60000), 0);
                                const days = Math.floor(totalMinutes / (24 * 60));
                                const hours = Math.floor((totalMinutes %% (24 * 60)) / 60);
                                const minutes = totalMinutes %% 60;
                                const parts = [];
                                if (days) {
                                  parts.push(`${days}天`);
                                }
                                if (hours) {
                                  parts.push(`${hours}小时`);
                                }
                                if (minutes || !parts.length) {
                                  parts.push(`${minutes}分钟`);
                                }
                                return parts.join("");
                              };
                              const calendarView = document.getElementById("calendar-view");
                              const agendaView = document.getElementById("agenda-view");
                              const prevWeekLink = document.getElementById("prev-week");
                              const nextWeekLink = document.getElementById("next-week");
                              const currentWeekLink = document.getElementById("current-week");
                              const currentStatus = document.getElementById("current-status");
                              const nextStatus = document.getElementById("next-status");
                              const viewModeButtons = Array.from(document.querySelectorAll("[data-view-mode]"));
                              const renderStatusSummary = (referenceNow = getServerNow()) => {
                                if (currentStatus) {
                                  const current = liveSummary?.current;
                                  if (current?.text) {
                                    currentStatus.hidden = false;
                                    currentStatus.textContent = current.text;
                                    currentStatus.classList.toggle("is-busy", Boolean(current.busy));
                                  } else {
                                    currentStatus.hidden = true;
                                  }
                                }
                                if (nextStatus) {
                                  const next = liveSummary?.next;
                                  const nextStart = next?.startTime ? new Date(next.startTime) : null;
                                  if (nextStart && !Number.isNaN(nextStart.getTime()) && nextStart > referenceNow) {
                                    nextStatus.hidden = false;
                                    nextStatus.textContent =
                                      `${formatCountdownDuration(nextStart, referenceNow)}后开始：${next.title}`;
                                  } else {
                                    nextStatus.hidden = true;
                                  }
                                }
                              };
                              const refreshSummary = async () => {
                                try {
                                  const response = await fetch(summaryApiUrl, {
                                    headers: {
                                      Accept: "application/json",
                                    },
                                  });
                                  if (!response.ok) {
                                    return;
                                  }
                                  const nextSummary = await response.json();
                                  liveSummary = nextSummary;
                                  syncServerClock(nextSummary.serverTime);
                                  renderStatusSummary();
                                  updateNowIndicators();
                                } catch (error) {
                                  console.debug("Failed to refresh schedule summary.", error);
                                }
                              };
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
                              let currentTimeLine = null;
                              payload.days.forEach((day) => {
                                const section = document.createElement("div");
                                section.className = "day-column";
                                section.dataset.date = day.date;
                                const header = document.createElement("header");
                                header.className = "day-column__header";
                                const headerTitle = document.createElement("strong");
                                headerTitle.textContent = day.dayLabel;
                                const headerDate = document.createElement("span");
                                headerDate.textContent = day.date;
                                header.append(headerTitle, headerDate);

                                const body = document.createElement("div");
                                body.className = "day-column__body";
                                body.dataset.date = day.date;
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
                              const updateNowIndicators = () => {
                                const now = getServerNow();
                                const todayKey = toDateKeyInZone(now);
                                const today = payload.days.find((day) => day.date === todayKey);
                                const clock = getClockPartsInZone(now);
                                const currentMinutes = clock.hours * 60 + clock.minutes;

                                renderStatusSummary(now);

                                if (!today) {
                                  if (currentTimeLine) {
                                    currentTimeLine.hidden = true;
                                  }
                                  return;
                                }

                                const todayBody = grid.querySelector(`.day-column__body[data-date="${todayKey}"]`);
                                if (!todayBody) {
                                  if (currentTimeLine) {
                                    currentTimeLine.hidden = true;
                                  }
                                  return;
                                }

                                if (!currentTimeLine || currentTimeLine.parentElement !== todayBody) {
                                  if (currentTimeLine) {
                                    currentTimeLine.remove();
                                  }
                                  currentTimeLine = document.createElement("div");
                                  currentTimeLine.className = "current-time-line";
                                  const dot = document.createElement("span");
                                  dot.className = "current-time-line__dot";
                                  currentTimeLine.appendChild(dot);
                                  todayBody.appendChild(currentTimeLine);
                                }

                                currentTimeLine.hidden = false;
                                currentTimeLine.style.top = `${(currentMinutes / 60) * hourHeight}px`;
                              };
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
                              renderStatusSummary();
                              updateNowIndicators();
                              refreshSummary();
                              window.setInterval(() => {
                                updateNowIndicators();
                                refreshSummary();
                              }, 60000);
                              syncViewMode();
                            </script>
                          </body>
                        </html>
                        """.formatted(escapedPageTitle, CALENDAR_HEADER_HEIGHT, HOUR_HEIGHT, escapedPageTitle,
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

    private String toIcalContent(List<ScheduleEntry> entries) {
        var builder = new StringBuilder()
            .append("BEGIN:VCALENDAR\r\n")
            .append("VERSION:2.0\r\n")
            .append("PRODID:-//sunnyhmz7010//Halo Schedule Calendar//CN\r\n")
            .append("CALSCALE:GREGORIAN\r\n")
            .append("METHOD:PUBLISH\r\n")
            .append("X-WR-CALNAME:")
            .append(escapeIcalText(ScheduleCalendarSetting.DEFAULT_TITLE))
            .append("\r\n");

        for (var entry : entries) {
            appendIcalEvent(builder, entry);
        }

        builder.append("END:VCALENDAR\r\n");
        return foldIcalLines(builder.toString());
    }

    private void appendIcalEvent(StringBuilder builder, ScheduleEntry entry) {
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
        builder.append("DTSTART:")
            .append(toIcalDateTime(spec.getStartTime()))
            .append("\r\n");
        builder.append("DTEND:")
            .append(toIcalDateTime(spec.getEndTime()))
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
                settingFetcher.fetch(ScheduleCalendarSetting.GROUP, ScheduleCalendarSetting.class)
                    .defaultIfEmpty(new ScheduleCalendarSetting(null, null))
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
            defaultColor(spec.getColor())
        );
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
        while (cursor.isBefore(rangeEnd)) {
            if (isAfterUntil(cursor, recurrence)) {
                break;
            }
            var occurrenceEnd = cursor.plus(duration);
            if (occurrenceEnd.isAfter(rangeStart) && cursor.isBefore(rangeEnd)) {
                occurrences.add(toOccurrence(entry, cursor, occurrenceEnd, zoneId));
            }
            cursor = advanceOccurrence(cursor, frequency, interval);
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

    private String defaultColor(String color) {
        return color == null || color.isBlank() ? "#0f766e" : color;
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
            occurrence.color()
        );
    }

    private String escapeHtml(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
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

    public record OccurrenceResponse(String name, String title, String description, String location,
                                     String startTime, String endTime, String date, String dayLabel,
                                     String recurrenceDescription, String durationLabel, String color) {
    }

    public record ScheduleCardResponse(String name, String title, String description, String location,
                                       String startTime, String endTime, String recurrenceDescription,
                                       String nextOccurrenceLabel, String color) {
    }
}

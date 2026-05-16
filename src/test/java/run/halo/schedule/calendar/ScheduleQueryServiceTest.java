package run.halo.schedule.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;

@ExtendWith(MockitoExtension.class)
class ScheduleQueryServiceTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ReactiveSettingFetcher settingFetcher;

    @Mock
    ExternalCalendarService externalCalendarService;

    private TimeZone originalTimeZone;
    private ScheduleQueryService service;
    private JsonMapper objectMapper;

    @BeforeEach
    void setUp() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        objectMapper = JsonMapper.builder().findAndAddModules().build();
        service = new ScheduleQueryService(
            client,
            new ScheduleCalendarSettingService(settingFetcher),
            externalCalendarService
        );
        lenient().when(settingFetcher.fetch(eq(ScheduleCalendarSetting.GROUP), eq(ScheduleCalendarSetting.class)))
            .thenReturn(Mono.empty());
        lenient().when(settingFetcher.getValues()).thenReturn(Mono.just(Map.of()));
        lenient().when(externalCalendarService.listOccurrences(any(), any(), any(), any()))
            .thenReturn(Mono.just(List.of()));
    }

    @AfterEach
    void tearDown() {
        TimeZone.setDefault(originalTimeZone);
    }

    @Test
    void expandsWeeklyRecurringEntriesWithinRequestedWeek() {
        var entry = scheduleEntry(
            "team-sync",
            "团队同步",
            OffsetDateTime.parse("2026-03-23T09:00:00+08:00"),
            OffsetDateTime.parse("2026-03-23T10:00:00+08:00"),
            recurrence(ScheduleEntry.RecurrenceFrequency.WEEKLY, 1, null)
        );
        when(client.listAll(eq(ScheduleEntry.class), any(ListOptions.class), any()))
            .thenReturn(Flux.just(entry));

        var view = service.getWeekView(LocalDate.of(2026, 3, 30)).block();

        assertThat(view).isNotNull();
        var monday = view.days().getFirst();
        assertThat(monday.occupied()).singleElement().satisfies(block -> {
            assertThat(block.title()).isEqualTo("团队同步");
            assertThat(block.start()).isEqualTo("09:00");
            assertThat(block.end()).isEqualTo("10:00");
            assertThat(block.metaLines()).contains("重复：每周");
            assertThat(block.tooltipMeta()).contains("重复：每周");
        });
    }

    @Test
    void stopsRecurringEntriesAfterUntilDate() {
        var entry = scheduleEntry(
            "daily-standup",
            "站会",
            OffsetDateTime.parse("2026-03-30T09:00:00+08:00"),
            OffsetDateTime.parse("2026-03-30T09:30:00+08:00"),
            recurrence(ScheduleEntry.RecurrenceFrequency.DAILY, 1, LocalDate.of(2026, 4, 1))
        );
        when(client.listAll(eq(ScheduleEntry.class), any(ListOptions.class), any()))
            .thenReturn(Flux.just(entry));

        var view = service.getWeekView(LocalDate.of(2026, 3, 30)).block();

        assertThat(view).isNotNull();
        assertThat(view.days().get(0).occupied()).hasSize(1);
        assertThat(view.days().get(1).occupied()).hasSize(1);
        assertThat(view.days().get(2).occupied()).hasSize(1);
        assertThat(view.days().get(3).occupied()).isEmpty();
    }

    @Test
    void listsScheduleCardsForEditorInsertion() {
        var entry = scheduleEntry(
            "release-review",
            "版本复盘",
            OffsetDateTime.parse("2026-04-03T14:00:00+08:00"),
            OffsetDateTime.parse("2026-04-03T15:30:00+08:00"),
            recurrence(ScheduleEntry.RecurrenceFrequency.MONTHLY, 1, null)
        );
        entry.getSpec().setLocation("会议室 A");
        entry.getSpec().setDescription("同步版本计划");
        when(client.listAll(eq(ScheduleEntry.class), any(ListOptions.class), any()))
            .thenReturn(Flux.just(entry));

        var cards = service.listEntryCards().block();

        assertThat(cards).singleElement().satisfies(card -> {
            assertThat(card.name()).isEqualTo("release-review");
            assertThat(card.title()).isEqualTo("版本复盘");
            assertThat(card.location()).isEqualTo("会议室 A");
            assertThat(card.description()).isEqualTo("同步版本计划");
            assertThat(card.recurrenceDescription()).isEqualTo("重复：每月");
        });
    }

    @Test
    void returnsSingleDayViewForRequestedDate() {
        var entry = scheduleEntry(
            "office-hours",
            "答疑时间",
            OffsetDateTime.parse("2026-04-02T10:00:00+08:00"),
            OffsetDateTime.parse("2026-04-02T11:00:00+08:00"),
            recurrence(ScheduleEntry.RecurrenceFrequency.NONE, 1, null)
        );
        when(client.listAll(eq(ScheduleEntry.class), any(ListOptions.class), any()))
            .thenReturn(Flux.just(entry));

        var day = service.getDayView(LocalDate.of(2026, 4, 2)).block();

        assertThat(day).isNotNull();
        assertThat(day.date()).isEqualTo("2026-04-02");
        assertThat(day.occupied()).singleElement().satisfies(block -> {
            assertThat(block.title()).isEqualTo("答疑时间");
            assertThat(block.start()).isEqualTo("10:00");
            assertThat(block.end()).isEqualTo("11:00");
        });
    }

    @Test
    void expandsOccurrencesAcrossRequestedRange() {
        var entry = scheduleEntry(
            "daily-standup",
            "站会",
            OffsetDateTime.parse("2026-04-01T09:00:00+08:00"),
            OffsetDateTime.parse("2026-04-01T09:30:00+08:00"),
            recurrence(ScheduleEntry.RecurrenceFrequency.DAILY, 1, LocalDate.of(2026, 4, 3))
        );
        when(client.listAll(eq(ScheduleEntry.class), any(ListOptions.class), any()))
            .thenReturn(Flux.just(entry));

        var occurrences = service.listOccurrences(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 3)).block();

        assertThat(occurrences).hasSize(3);
        assertThat(occurrences)
            .extracting(ScheduleQueryService.OccurrenceResponse::date)
            .containsExactly("2026-04-01", "2026-04-02", "2026-04-03");
    }

    @Test
    void limitsUpcomingOccurrences() {
        var entry = scheduleEntry(
            "training",
            "晨训",
            OffsetDateTime.now().plusDays(1).withHour(8).withMinute(0).withSecond(0).withNano(0),
            OffsetDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0),
            recurrence(ScheduleEntry.RecurrenceFrequency.DAILY, 1, LocalDate.now().plusDays(10))
        );
        when(client.listAll(eq(ScheduleEntry.class), any(ListOptions.class), any()))
            .thenReturn(Flux.just(entry));

        var upcoming = service.listUpcomingOccurrences(2).block();

        assertThat(upcoming).hasSize(2);
        assertThat(upcoming)
            .extracting(ScheduleQueryService.OccurrenceResponse::title)
            .containsOnly("晨训");
    }

    @Test
    void includesNextOccurrenceFieldsInWeekView() {
        var now = OffsetDateTime.now();
        var nearEntry = scheduleEntry(
            "class-soon",
            "上课",
            now.plusHours(2).withMinute(0).withSecond(0).withNano(0),
            now.plusHours(3).withMinute(0).withSecond(0).withNano(0),
            recurrence(ScheduleEntry.RecurrenceFrequency.NONE, 1, null)
        );
        var laterEntry = scheduleEntry(
            "class-later",
            "晚自习",
            now.plusDays(1).withHour(19).withMinute(0).withSecond(0).withNano(0),
            now.plusDays(1).withHour(21).withMinute(0).withSecond(0).withNano(0),
            recurrence(ScheduleEntry.RecurrenceFrequency.NONE, 1, null)
        );
        when(client.listAll(eq(ScheduleEntry.class), any(ListOptions.class), any()))
            .thenReturn(Flux.just(nearEntry, laterEntry));

        var view = service.getWeekView(LocalDate.now()).block();

        assertThat(view).isNotNull();
        assertThat(view.serverTime()).isNotBlank();
        assertThat(view.zoneId()).isEqualTo("Asia/Shanghai");
        assertThat(view.summary()).isNotNull();
        assertThat(view.summary().next()).isNotNull();
        assertThat(view.summary().next().title()).isEqualTo("上课");
        assertThat(view.nextOccurrenceTitle()).isEqualTo("上课");
        assertThat(view.nextOccurrenceStart()).isNotBlank();
    }

    @Test
    void returnsStructuredSummaryForCurrentAndNextOccurrences() {
        var now = OffsetDateTime.now();
        var activeEntry = scheduleEntry(
            "writing-now",
            "写作",
            now.minusMinutes(20),
            now.plusMinutes(40),
            recurrence(ScheduleEntry.RecurrenceFrequency.NONE, 1, null)
        );
        var nextEntry = scheduleEntry(
            "review-next",
            "复盘会",
            now.plusHours(2).withSecond(0).withNano(0),
            now.plusHours(3).withSecond(0).withNano(0),
            recurrence(ScheduleEntry.RecurrenceFrequency.NONE, 1, null)
        );
        when(client.listAll(eq(ScheduleEntry.class), any(ListOptions.class), any()))
            .thenReturn(Flux.just(activeEntry, nextEntry));

        var summary = service.getSummary().block();

        assertThat(summary).isNotNull();
        assertThat(summary.serverTime()).isNotBlank();
        assertThat(summary.zoneId()).isEqualTo("Asia/Shanghai");
        assertThat(summary.current()).isNotNull();
        assertThat(summary.current().busy()).isTrue();
        assertThat(summary.current().titles()).containsExactly("写作");
        assertThat(summary.current().text()).contains("进行中：写作");
        assertThat(summary.next()).isNotNull();
        assertThat(summary.next().title()).isEqualTo("复盘会");
        assertThat(summary.next().startTime()).isNotBlank();
        assertThat(summary.next().minutesUntilStart()).isPositive();
        assertThat(summary.next().text()).contains("后开始：复盘会");
    }

    @Test
    void usesConfiguredPageTitleInPublicCalendarPage() {
        when(client.listAll(eq(ScheduleEntry.class), any(ListOptions.class), any()))
            .thenReturn(Flux.empty());
        when(settingFetcher.fetch(eq(ScheduleCalendarSetting.GROUP), eq(ScheduleCalendarSetting.class)))
            .thenReturn(Mono.just(new ScheduleCalendarSetting("自定义标题", null)));

        var html = service.buildPublicCalendarPage(LocalDate.of(2026, 4, 13)).block();

        assertThat(html).isNotNull();
        assertThat(html).contains("<title>自定义标题</title>");
        assertThat(html).contains("<h1>自定义标题</h1>");
    }

    @Test
    void exportsIcalWithCalendarTimezone() {
        var entry = scheduleEntry(
            "morning-class",
            "晨课",
            OffsetDateTime.parse("2026-05-18T09:00:00+08:00"),
            OffsetDateTime.parse("2026-05-18T10:30:00+08:00"),
            recurrence(ScheduleEntry.RecurrenceFrequency.NONE, 1, null)
        );
        when(client.listAll(eq(ScheduleEntry.class), any(ListOptions.class), any()))
            .thenReturn(Flux.just(entry));
        when(settingFetcher.fetch(eq(ScheduleCalendarSetting.GROUP), eq(ScheduleCalendarSetting.class)))
            .thenReturn(Mono.just(new ScheduleCalendarSetting("课程订阅", null)));

        var ical = service.exportPublicIcal().block();

        assertThat(ical).isNotNull();
        assertThat(ical).contains("X-WR-CALNAME:课程订阅");
        assertThat(ical).contains("X-WR-TIMEZONE:Asia/Shanghai");
        assertThat(ical).contains("BEGIN:VTIMEZONE");
        assertThat(ical).contains("TZID:Asia/Shanghai");
        assertThat(ical).contains("DTSTART;TZID=Asia/Shanghai:20260518T090000");
        assertThat(ical).contains("DTEND;TZID=Asia/Shanghai:20260518T103000");
        assertThat(ical).contains("DTSTAMP:");
    }

    @Test
    void includesExternalOccurrencesInSummaryAndWeekView() {
        when(client.listAll(eq(ScheduleEntry.class), any(ListOptions.class), any()))
            .thenReturn(Flux.empty());

        var zoneId = ZoneId.systemDefault();
        var now = OffsetDateTime.now(zoneId).withSecond(0).withNano(0);
        var eventStart = now.plusHours(1).toLocalDateTime();
        var eventEnd = eventStart.plusHours(1);
        var externalOccurrence = new ScheduleEventOccurrence(
            "google-1",
            "Google 日程",
            "来自手机同步",
            "Google Meet",
            null,
            "#4285f4",
            eventStart,
            eventEnd,
            "Google Calendar"
        );
        when(externalCalendarService.listOccurrences(any(), any(), any(), any()))
            .thenReturn(Mono.just(List.of(externalOccurrence)));

        var summary = service.getSummary().block();
        var view = service.getWeekView(eventStart.toLocalDate()).block();

        assertThat(summary).isNotNull();
        assertThat(summary.next()).isNotNull();
        assertThat(summary.next().title()).isEqualTo("Google 日程");
        assertThat(view).isNotNull();
        assertThat(view.summary()).isNotNull();
        assertThat(view.summary().next()).isNotNull();
        assertThat(view.summary().next().title()).isEqualTo("Google 日程");
        assertThat(view.days().stream()
            .flatMap(day -> day.occupied().stream())
            .anyMatch(block -> block.title().equals("Google 日程")
                && block.metaLines() != null
                && block.metaLines().contains("来源：Google Calendar"))).isTrue();
    }

    @Test
    void fallsBackToRawPluginConfigForExternalCalendars() {
        when(client.listAll(eq(ScheduleEntry.class), any(ListOptions.class), any()))
            .thenReturn(Flux.empty());
        when(settingFetcher.fetch(eq(ScheduleCalendarSetting.GROUP), eq(ScheduleCalendarSetting.class)))
            .thenReturn(Mono.just(new ScheduleCalendarSetting("日程日历", null)));

        var publicPage = objectMapper.createObjectNode();
        publicPage.put("title", "日程日历");
        publicPage.set("externalCalendars", objectMapper.createArrayNode()
            .add(objectMapper.createObjectNode()
                .put("name", "美国节假日")
                .put("icsUrl", "https://calendar.example/holiday.ics")
                .put("enabled", true)
                .put("color", "#4285f4")));
        when(settingFetcher.getValues()).thenReturn(Mono.just(Map.of(
            ScheduleCalendarSetting.GROUP, publicPage
        )));

        var externalOccurrence = new ScheduleEventOccurrence(
            "holiday-1",
            "Memorial Day",
            "Public holiday",
            null,
            null,
            "#4285f4",
            LocalDateTime.of(2026, 5, 25, 0, 0),
            LocalDateTime.of(2026, 5, 26, 0, 0),
            "美国节假日"
        );
        when(externalCalendarService.listOccurrences(any(), any(), any(), any()))
            .thenReturn(Mono.just(List.of(externalOccurrence)));

        var view = service.getWeekView(LocalDate.of(2026, 5, 25)).block();

        assertThat(view).isNotNull();
        assertThat(view.days().stream()
            .flatMap(day -> day.occupied().stream())
            .anyMatch(block -> block.title().equals("Memorial Day")
                && block.metaLines() != null
                && block.metaLines().contains("来源：美国节假日"))).isTrue();
    }

    @Test
    void fallsBackToRootPluginConfigForExternalCalendars() {
        when(client.listAll(eq(ScheduleEntry.class), any(ListOptions.class), any()))
            .thenReturn(Flux.empty());
        when(settingFetcher.fetch(eq(ScheduleCalendarSetting.GROUP), eq(ScheduleCalendarSetting.class)))
            .thenReturn(Mono.just(new ScheduleCalendarSetting("日程日历", null)));

        var publicPage = objectMapper.createObjectNode();
        publicPage.put("title", "日程日历");
        when(settingFetcher.getValues()).thenReturn(Mono.just(Map.of(
            ScheduleCalendarSetting.GROUP, publicPage,
            "title", objectMapper.getNodeFactory().textNode("日程日历335"),
            "externalCalendars", objectMapper.createArrayNode()
                .add(objectMapper.createObjectNode()
                    .put("name", "美国节假日")
                    .put("icsUrl", "https://calendar.example/holiday.ics")
                    .put("enabled", true)
                    .put("color", "#4285f4"))
        )));

        var externalOccurrence = new ScheduleEventOccurrence(
            "holiday-root-1",
            "Memorial Day",
            "Public holiday",
            null,
            null,
            "#4285f4",
            LocalDateTime.of(2026, 5, 25, 0, 0),
            LocalDateTime.of(2026, 5, 26, 0, 0),
            "美国节假日"
        );
        when(externalCalendarService.listOccurrences(any(), any(), any(), any()))
            .thenReturn(Mono.just(List.of(externalOccurrence)));

        var view = service.getWeekView(LocalDate.of(2026, 5, 25)).block();

        assertThat(view).isNotNull();
        assertThat(view.days().stream()
            .flatMap(day -> day.occupied().stream())
            .anyMatch(block -> block.title().equals("Memorial Day")
                && block.metaLines() != null
                && block.metaLines().contains("来源：美国节假日"))).isTrue();
    }

    private ScheduleEntry scheduleEntry(String name, String title, OffsetDateTime startTime,
        OffsetDateTime endTime, ScheduleEntry.Recurrence recurrence) {
        var entry = new ScheduleEntry();
        var metadata = new Metadata();
        metadata.setName(name);
        entry.setMetadata(metadata);

        var spec = new ScheduleEntry.Spec();
        spec.setTitle(title);
        spec.setStartTime(startTime);
        spec.setEndTime(endTime);
        spec.setRecurrence(recurrence);
        entry.setSpec(spec);
        return entry;
    }

    private ScheduleEntry.Recurrence recurrence(ScheduleEntry.RecurrenceFrequency frequency, int interval,
        LocalDate until) {
        var recurrence = new ScheduleEntry.Recurrence();
        recurrence.setFrequency(frequency);
        recurrence.setInterval(interval);
        recurrence.setUntil(until);
        return recurrence;
    }
}

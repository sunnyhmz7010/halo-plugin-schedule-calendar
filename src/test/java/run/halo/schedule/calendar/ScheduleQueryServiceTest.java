package run.halo.schedule.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

@ExtendWith(MockitoExtension.class)
class ScheduleQueryServiceTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ScheduleSettingsService scheduleSettingsService;

    private TimeZone originalTimeZone;
    private ScheduleQueryService service;

    @BeforeEach
    void setUp() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        service = new ScheduleQueryService(client, scheduleSettingsService);
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

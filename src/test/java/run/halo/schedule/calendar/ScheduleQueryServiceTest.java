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
import run.halo.app.plugin.ReactiveSettingFetcher;

@ExtendWith(MockitoExtension.class)
class ScheduleQueryServiceTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ReactiveSettingFetcher settingFetcher;

    private TimeZone originalTimeZone;
    private ScheduleQueryService service;

    @BeforeEach
    void setUp() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        service = new ScheduleQueryService(client, settingFetcher);
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

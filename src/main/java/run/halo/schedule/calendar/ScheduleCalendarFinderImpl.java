package run.halo.schedule.calendar;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import run.halo.app.theme.finders.Finder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Finder("scheduleCalendarFinder")
@RequiredArgsConstructor
public class ScheduleCalendarFinderImpl implements ScheduleCalendarFinder {
    private final ScheduleQueryService scheduleQueryService;

    @Override
    public Mono<ScheduleQueryService.WeekViewResponse> week(String start) {
        return scheduleQueryService.getWeekView(parseDate(start));
    }

    @Override
    public Mono<ScheduleQueryService.SummaryResponse> summary() {
        return scheduleQueryService.getSummary();
    }

    @Override
    public Mono<ScheduleQueryService.DayView> day(String date) {
        return scheduleQueryService.getDayView(parseDate(date));
    }

    @Override
    public Flux<ScheduleQueryService.OccurrenceResponse> range(String start, String end) {
        return scheduleQueryService.listOccurrences(parseDate(start), parseDate(end))
            .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Flux<ScheduleQueryService.OccurrenceResponse> upcoming(Integer limit) {
        return scheduleQueryService.listUpcomingOccurrences(limit)
            .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<ScheduleQueryService.ScheduleCardResponse> get(String name) {
        return scheduleQueryService.getEntryCard(name);
    }

    @Override
    public Flux<ScheduleQueryService.ScheduleCardResponse> listAll() {
        return scheduleQueryService.listEntryCards().flatMapMany(Flux::fromIterable);
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return LocalDate.parse(value);
    }
}

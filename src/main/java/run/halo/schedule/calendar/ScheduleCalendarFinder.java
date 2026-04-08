package run.halo.schedule.calendar;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ScheduleCalendarFinder {
    Mono<ScheduleQueryService.WeekViewResponse> week(String start);

    Mono<ScheduleQueryService.SummaryResponse> summary();

    Mono<ScheduleQueryService.DayView> day(String date);

    Flux<ScheduleQueryService.OccurrenceResponse> range(String start, String end);

    Flux<ScheduleQueryService.OccurrenceResponse> upcoming(Integer limit);

    Mono<ScheduleQueryService.ScheduleCardResponse> get(String name);

    Flux<ScheduleQueryService.ScheduleCardResponse> listAll();
}

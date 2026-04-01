package run.halo.schedule.calendar;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ScheduleCalendarFinder {
    Mono<ScheduleQueryService.WeekViewResponse> week(String start);

    Mono<ScheduleQueryService.ScheduleCardResponse> get(String name);

    Flux<ScheduleQueryService.ScheduleCardResponse> listAll();
}

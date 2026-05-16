package run.halo.schedule.calendar;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class SchedulePublicMetaController {
    @GetMapping(ScheduleCalendarRoutes.PUBLIC_META_API_PATH)
    public Mono<PublicMetaResponse> publicMeta() {
        return Mono.just(new PublicMetaResponse(
            ScheduleCalendarRoutes.DEFAULT_PUBLIC_PAGE_PATH,
            ScheduleCalendarRoutes.DEFAULT_PUBLIC_ICAL_PATH
        ));
    }

    public record PublicMetaResponse(String publicPagePath, String publicIcalPath) {
    }
}

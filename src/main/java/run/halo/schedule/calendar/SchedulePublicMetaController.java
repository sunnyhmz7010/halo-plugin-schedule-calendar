package run.halo.schedule.calendar;

import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import run.halo.app.infra.ExternalLinkProcessor;

@RestController
@RequiredArgsConstructor
public class SchedulePublicMetaController {
    private final ExternalLinkProcessor externalLinkProcessor;

    @GetMapping(ScheduleCalendarRoutes.PUBLIC_META_API_PATH)
    public Mono<PublicMetaResponse> publicMeta() {
        return Mono.zip(
            externalLinkProcessor.processLink(URI.create(ScheduleCalendarRoutes.DEFAULT_PUBLIC_PAGE_PATH)),
            externalLinkProcessor.processLink(URI.create(ScheduleCalendarRoutes.DEFAULT_PUBLIC_ICAL_PATH))
        ).map(tuple -> new PublicMetaResponse(
            tuple.getT1().toString(),
            tuple.getT2().toString()
        ));
    }

    public record PublicMetaResponse(String publicPagePath, String publicIcalPath) {
    }
}

package run.halo.schedule.calendar;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class SchedulePublicMetaController {
    private final SchedulePublicUrlService schedulePublicUrlService;

    @GetMapping(ScheduleCalendarRoutes.PUBLIC_META_API_PATH)
    public Mono<PublicMetaResponse> publicMeta() {
        return schedulePublicUrlService.resolvePublicUrls()
            .flatMap(publicUrls -> schedulePublicUrlService.syncDisplayOnlySettings()
                .onErrorResume(throwable -> Mono.empty())
                .thenReturn(new PublicMetaResponse(
                    publicUrls.publicPageUrl(),
                    publicUrls.publicIcalUrl()
                )));
    }

    public record PublicMetaResponse(String publicPagePath, String publicIcalPath) {
    }
}

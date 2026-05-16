package run.halo.schedule.calendar;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;

@RestController
@RequiredArgsConstructor
public class SchedulePublicMetaController {
    private final ReactiveSettingFetcher settingFetcher;

    @GetMapping(ScheduleCalendarRoutes.PUBLIC_META_API_PATH)
    public Mono<PublicMetaResponse> publicMeta() {
        return settingFetcher.fetch(ScheduleCalendarSetting.GROUP, ScheduleCalendarSetting.class)
            .defaultIfEmpty(new ScheduleCalendarSetting(
                ScheduleCalendarSetting.DEFAULT_TITLE,
                ScheduleCalendarSetting.DEFAULT_PUBLIC_PATH,
                null
            ))
            .map(setting -> new PublicMetaResponse(setting.effectivePublicPath()));
    }

    public record PublicMetaResponse(String publicPagePath) {
    }
}

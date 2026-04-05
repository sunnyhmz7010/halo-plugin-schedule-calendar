package run.halo.schedule.calendar;

import org.springframework.stereotype.Component;
import run.halo.app.extension.SchemeManager;
import run.halo.app.extension.index.IndexSpecs;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

@Component
public class ScheduleCalendarPlugin extends BasePlugin {
    private final SchemeManager schemeManager;

    public ScheduleCalendarPlugin(PluginContext pluginContext, SchemeManager schemeManager) {
        super(pluginContext);
        this.schemeManager = schemeManager;
    }

    @Override
    public void start() {
        schemeManager.register(ScheduleEntry.class, this::configureIndices);
    }

    private void configureIndices(IndexSpecs<ScheduleEntry> indexSpecs) {
        indexSpecs.add(IndexSpecs.<ScheduleEntry, String>single("title", String.class)
            .indexFunc(entry -> entry.getSpec() == null ? null : entry.getSpec().getTitle())
            .nullable(true));
        indexSpecs.add(IndexSpecs.<ScheduleEntry, java.time.OffsetDateTime>single(
                "startTime", java.time.OffsetDateTime.class)
            .indexFunc(entry -> entry.getSpec() == null ? null : entry.getSpec().getStartTime())
            .nullable(true));
        indexSpecs.add(IndexSpecs.<ScheduleEntry, java.time.OffsetDateTime>single(
                "endTime", java.time.OffsetDateTime.class)
            .indexFunc(entry -> entry.getSpec() == null ? null : entry.getSpec().getEndTime())
            .nullable(true));
    }
}

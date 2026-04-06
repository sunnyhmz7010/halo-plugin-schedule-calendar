package run.halo.schedule.calendar;

import run.halo.app.extension.index.IndexSpecs;
import org.springframework.stereotype.Component;
import run.halo.app.extension.SchemeManager;
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
        schemeManager.register(ScheduleEntry.class, this::registerIndices);
    }

    private void registerIndices(IndexSpecs<ScheduleEntry> indexSpecs) {
        indexSpecs.add(IndexSpecs.<ScheduleEntry, String>single("metadata.name", String.class)
            .unique(true)
            .indexFunc(entry -> entry.getMetadata() == null ? null : entry.getMetadata().getName())
        );
        indexSpecs.add(IndexSpecs.<ScheduleEntry, java.time.OffsetDateTime>single("spec.startTime",
                java.time.OffsetDateTime.class)
            .indexFunc(entry -> entry.getSpec() == null ? null : entry.getSpec().getStartTime())
        );
        indexSpecs.add(IndexSpecs.<ScheduleEntry, java.time.OffsetDateTime>single("spec.endTime",
                java.time.OffsetDateTime.class)
            .indexFunc(entry -> entry.getSpec() == null ? null : entry.getSpec().getEndTime())
        );
    }
}

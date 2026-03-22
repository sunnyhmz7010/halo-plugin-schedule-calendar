package run.halo.schedule.calendar;

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
        schemeManager.register(ScheduleEntry.class);
    }
}

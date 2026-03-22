package run.halo.schedule.calendar;

import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

@Component
public class ScheduleCalendarPlugin extends BasePlugin {

    public ScheduleCalendarPlugin(PluginContext pluginContext) {
        super(pluginContext);
    }
}

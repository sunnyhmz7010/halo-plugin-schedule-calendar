package run.halo.schedule.calendar;

import java.util.List;

public record ScheduleCalendarSetting(String title, String publicPath, List<ExternalCalendarSource> externalCalendars) {
    public static final String GROUP = "public_page";
    public static final String DEFAULT_TITLE = "日程日历";
    public static final String DEFAULT_PUBLIC_PATH = "/schedule-calendar";

    public String effectiveTitle() {
        return title == null || title.isBlank() ? DEFAULT_TITLE : title;
    }

    public String effectivePublicPath() {
        if (publicPath == null || publicPath.isBlank()) {
            return DEFAULT_PUBLIC_PATH;
        }

        var normalized = publicPath.startsWith("/") ? publicPath : "/" + publicPath;
        return normalized.length() > 1 && normalized.endsWith("/")
            ? normalized.substring(0, normalized.length() - 1)
            : normalized;
    }

    public List<ExternalCalendarSource> enabledExternalCalendars() {
        return externalCalendars == null ? List.of() : externalCalendars.stream()
            .filter(source -> source != null && source.isEnabled() && source.hasUsableUrl())
            .toList();
    }

    public record ExternalCalendarSource(String name, String icsUrl, Boolean enabled, String color) {
        public boolean isEnabled() {
            return enabled == null || enabled;
        }

        public boolean hasUsableUrl() {
            return icsUrl != null && !icsUrl.isBlank();
        }

        public String effectiveName() {
            return name == null || name.isBlank() ? "Google Calendar" : name;
        }
    }
}

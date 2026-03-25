package run.halo.schedule.calendar;

public record ScheduleCalendarSetting(String title, String icon) {
    public static final String GROUP = "public_page";

    public String effectiveTitle() {
        return title == null || title.isBlank() ? "日程日历" : title;
    }

    public String effectiveIcon() {
        return icon == null || icon.isBlank() ? "📅" : icon;
    }
}

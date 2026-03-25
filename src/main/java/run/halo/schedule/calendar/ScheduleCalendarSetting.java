package run.halo.schedule.calendar;

public record ScheduleCalendarSetting(String title) {
    public static final String GROUP = "public_page";

    public String effectiveTitle() {
        return title == null || title.isBlank() ? "日程日历" : title;
    }
}

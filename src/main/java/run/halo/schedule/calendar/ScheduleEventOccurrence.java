package run.halo.schedule.calendar;

import java.time.LocalDateTime;

record ScheduleEventOccurrence(String name, String title, String description, String location,
                               String recurrenceDescription, String color, LocalDateTime start,
                               LocalDateTime end, String sourceLabel) {
}

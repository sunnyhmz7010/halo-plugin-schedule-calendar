package run.halo.schedule.calendar;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(kind = "ScheduleEntry", group = "schedule.calendar.sunny.dev",
    version = "v1alpha1", singular = "scheduleentry", plural = "scheduleentries")
public class ScheduleEntry extends AbstractExtension {

    @Schema(requiredMode = REQUIRED)
    private Spec spec;

    @Data
    public static class Spec {
        @Schema(requiredMode = REQUIRED, minLength = 1)
        private String title;

        @Schema(description = "Optional detail shown in admin and public schedule views.")
        private String description;

        @Schema(description = "Optional place or meeting link.")
        private String location;

        @Schema(requiredMode = REQUIRED)
        private OffsetDateTime startTime;

        @Schema(requiredMode = REQUIRED)
        private OffsetDateTime endTime;

        @Schema(description = "Hex color used to render this entry.")
        private String color;

        @Schema(description = "Optional recurrence rule used to create repeating schedule entries.")
        private Recurrence recurrence;
    }

    public enum RecurrenceFrequency {
        NONE,
        DAILY,
        WEEKLY,
        MONTHLY,
        YEARLY
    }

    @Data
    public static class Recurrence {
        @Schema(description = "Repeat frequency.", allowableValues = {
            "NONE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY"
        })
        private RecurrenceFrequency frequency;

        @Schema(description = "Repeat every N frequency units. Defaults to 1.")
        private Integer interval;

        @Schema(description = "Last occurrence date in local time, inclusive.")
        private LocalDate until;
    }
}

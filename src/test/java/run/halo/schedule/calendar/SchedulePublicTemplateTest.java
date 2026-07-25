package run.halo.schedule.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SchedulePublicTemplateTest {

    @Test
    void calendarTemplateUsesRawJavascriptInliningForPayload() throws IOException {
        var template = Files.readString(Path.of("src/main/resources/templates/public/calendar.html"));

        assertThat(template)
            .contains("const payload = [(${payload})];");
    }
}

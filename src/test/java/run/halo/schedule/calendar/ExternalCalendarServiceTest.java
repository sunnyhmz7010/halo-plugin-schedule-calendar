package run.halo.schedule.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ExternalCalendarServiceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void includesAllDayGoogleHolidayOccurrencesWithinRange() throws IOException {
        var ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Google Inc//Google Calendar 70.9054//EN
            BEGIN:VEVENT
            DTSTART;VALUE=DATE:20260525
            DTEND;VALUE=DATE:20260526
            UID:20260525_8a7osuvj9moeql74mrs1da4b1k@google.com
            STATUS:CONFIRMED
            SUMMARY:Memorial Day
            DESCRIPTION:Public holiday
            END:VEVENT
            END:VCALENDAR
            """;

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/holiday.ics", exchange -> {
            var body = ics.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/calendar; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();

        var service = new ExternalCalendarService();
        var source = new ScheduleCalendarSetting.ExternalCalendarSource(
            "美国节假日",
            "http://127.0.0.1:" + server.getAddress().getPort() + "/holiday.ics",
            true,
            "#4285f4"
        );
        var setting = new ScheduleCalendarSetting("日程日历", List.of(source));

        var occurrences = service.listOccurrences(
            setting,
            LocalDate.of(2026, 5, 25),
            LocalDate.of(2026, 5, 31),
            ZoneId.of("Asia/Shanghai")
        ).block();

        assertThat(occurrences).isNotNull();
        assertThat(occurrences).singleElement().satisfies(occurrence -> {
            assertThat(occurrence.title()).isEqualTo("Memorial Day");
            assertThat(occurrence.start()).isEqualTo(LocalDateTime.of(2026, 5, 25, 0, 0));
            assertThat(occurrence.end()).isEqualTo(LocalDateTime.of(2026, 5, 26, 0, 0));
            assertThat(occurrence.sourceLabel()).isEqualTo("美国节假日");
        });
    }
}

package run.halo.schedule.calendar;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import run.halo.app.extension.SchemeManager;
import run.halo.app.extension.index.IndexSpecs;
import run.halo.app.plugin.PluginContext;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScheduleCalendarPluginTest {

    @Mock
    PluginContext context;

    @Mock
    SchemeManager schemeManager;

    @InjectMocks
    ScheduleCalendarPlugin plugin;

    @Test
    void contextLoads() {
        plugin.start();
        verify(schemeManager).register(eq(ScheduleEntry.class), any());
    }

    @Test
    void registersIndicesForScheduleEntry() {
        plugin.start();

        var captor = ArgumentCaptor.forClass(Consumer.class);
        verify(schemeManager).register(eq(ScheduleEntry.class), captor.capture());

        @SuppressWarnings("unchecked")
        Consumer<IndexSpecs<ScheduleEntry>> consumer = captor.getValue();
        var specs = new CapturingIndexSpecs<ScheduleEntry>();
        consumer.accept(specs);

        assertThat(specs.getIndexSpecs())
            .extracting(spec -> spec.getName())
            .contains("metadata.name", "spec.startTime", "spec.endTime");
    }

    private static class CapturingIndexSpecs<E extends run.halo.app.extension.Extension> implements IndexSpecs<E> {
        private final java.util.List<run.halo.app.extension.index.ValueIndexSpec<E, ?>> specs =
            new java.util.ArrayList<>();

        @Override
        public <K extends java.lang.Comparable<K>> void add(
            run.halo.app.extension.index.ValueIndexSpec<E, K> indexSpec
        ) {
            specs.add(indexSpec);
        }

        @Override
        public java.util.List<run.halo.app.extension.index.ValueIndexSpec<E, ?>> getIndexSpecs() {
            return specs;
        }
    }
}

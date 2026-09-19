package rpg.platform.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import com.destroystokyo.paper.event.server.ServerTickStartEvent;

import rpg.core.performance.DefaultPerformanceRegistry;
import rpg.core.performance.PerformanceWindow;
import rpg.core.performance.SubsystemId;

class PaperPerformanceListenerTest {

    @Test
    void forwardsACompletedTickOnceAndIgnoresMismatchedEndEvents() {
        DefaultPerformanceRegistry registry =
                new DefaultPerformanceRegistry(() -> 0L, 8, true);
        PaperPerformanceSource source = new PaperPerformanceSource(registry);
        PaperPerformanceListener listener = new PaperPerformanceListener(source);

        listener.onTickStart(new ServerTickStartEvent(7));
        listener.onTickEnd(new ServerTickEndEvent(8, 0.009d, 0L));
        listener.onTickEnd(new ServerTickEndEvent(7, 0.123d, 0L));
        listener.onTickEnd(new ServerTickEndEvent(7, 0.456d, 0L));

        PerformanceWindow overall = registry.snapshot().overall();

        assertThat(overall.sampleCount()).isEqualTo(1);
        assertThat(overall.p50Nanos()).isEqualTo(123_000L);
    }

    @Test
    void sourceRegistrationRemainsBukkitFreeAtTheCoreBoundary() {
        DefaultPerformanceRegistry registry =
                new DefaultPerformanceRegistry(() -> 0L, 8, true);
        registry.register(new SubsystemId("test"), Duration.ofMillis(5), "B15");

        PaperPerformanceSource source = new PaperPerformanceSource(registry);
        source.onTickStart(1);
        source.onTickEnd(1, 50_000L);

        assertThat(registry.snapshot().overall().status())
                .isEqualTo(PerformanceWindow.Status.COMPLETE);
    }

    @Test
    void runsThePopulationSnapshotCallbackAfterACompletedTick() {
        DefaultPerformanceRegistry registry =
                new DefaultPerformanceRegistry(() -> 0L, 8, true);
        AtomicInteger callbacks = new AtomicInteger();
        PaperPerformanceListener listener =
                new PaperPerformanceListener(
                        new PaperPerformanceSource(registry), callbacks::incrementAndGet);

        listener.onTickStart(new ServerTickStartEvent(1));
        listener.onTickEnd(new ServerTickEndEvent(1, 0.025d, 0L));

        assertThat(callbacks).hasValue(1);
    }
}

package rpg.platform.performance;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import com.destroystokyo.paper.event.server.ServerTickStartEvent;

/** Paper event adapter; no Paper type crosses the Core performance boundary. */
public final class PaperPerformanceListener implements Listener {

    private final PaperPerformanceSource source;
    private final Runnable afterTickEnd;

    public PaperPerformanceListener(PaperPerformanceSource source) {
        this(source, () -> {});
    }

    public PaperPerformanceListener(PaperPerformanceSource source, Runnable afterTickEnd) {
        this.source = Objects.requireNonNull(source, "source");
        this.afterTickEnd = Objects.requireNonNull(afterTickEnd, "afterTickEnd");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTickStart(ServerTickStartEvent event) {
        source.onTickStart(event.getTickNumber());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTickEnd(ServerTickEndEvent event) {
        source.onTickEnd(PaperTickMetrics.from(event));
        afterTickEnd.run();
    }
}

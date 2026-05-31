package dev.rew1nd.sableschematicapi.client.frontier;

import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class FrontierLaserStore {
    private static final int RETRACT_TICKS = 8;
    private static final Map<ClientSubLevel, State> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private FrontierLaserStore() {
    }

    public static void start(final ClientSubLevel subLevel, final Vector3dc apexWorld) {
        STATES.put(subLevel, new State(apexWorld));
    }

    public static void rememberBase(final ClientSubLevel subLevel,
                                    final SableFrontierLaserGeometry.BasePolygon base) {
        final State state = STATES.get(subLevel);
        if (state == null || state.retracting) {
            return;
        }
        state.lastBase = copy(base);
    }

    public static void startRetract(final ClientSubLevel subLevel) {
        final State state = STATES.get(subLevel);
        if (state == null) {
            return;
        }
        if (state.lastBase == null) {
            STATES.remove(subLevel);
            return;
        }
        if (state.retracting) {
            return;
        }
        state.retracting = true;
        state.retractElapsedTicks = 0;
    }

    public static void clear(final ClientSubLevel subLevel) {
        STATES.remove(subLevel);
    }

    public static void clearAll() {
        STATES.clear();
    }

    public static void tick() {
        synchronized (STATES) {
            final Iterator<Map.Entry<ClientSubLevel, State>> iterator = STATES.entrySet().iterator();
            while (iterator.hasNext()) {
                final State state = iterator.next().getValue();
                if (state.retracting && ++state.retractElapsedTicks > RETRACT_TICKS) {
                    iterator.remove();
                }
            }
        }
    }

    public static List<Entry> snapshot() {
        synchronized (STATES) {
            final List<Entry> entries = new ArrayList<>(STATES.size());
            for (final Map.Entry<ClientSubLevel, State> entry : STATES.entrySet()) {
                final State state = entry.getValue();
                entries.add(new Entry(
                        entry.getKey(),
                        new Vector3d(state.apexWorld),
                        copy(state.lastBase),
                        state.retracting,
                        Math.min(1.0f, state.retractElapsedTicks / (float) RETRACT_TICKS)
                ));
            }
            return entries;
        }
    }

    private static @Nullable SableFrontierLaserGeometry.BasePolygon copy(
            final @Nullable SableFrontierLaserGeometry.BasePolygon base) {
        if (base == null) {
            return null;
        }
        final List<Vector3d> points = new ArrayList<>(base.points().size());
        for (final Vector3d point : base.points()) {
            points.add(new Vector3d(point));
        }
        return new SableFrontierLaserGeometry.BasePolygon(List.copyOf(points), new Vector3d(base.center()));
    }

    private static final class State {
        private final Vector3d apexWorld;
        private @Nullable SableFrontierLaserGeometry.BasePolygon lastBase;
        private boolean retracting;
        private int retractElapsedTicks;

        private State(final Vector3dc apexWorld) {
            this.apexWorld = new Vector3d(apexWorld);
        }
    }

    public record Entry(ClientSubLevel subLevel,
                        Vector3dc apexWorld,
                        @Nullable SableFrontierLaserGeometry.BasePolygon lastBase,
                        boolean retracting,
                        float retractProgress) {
    }
}

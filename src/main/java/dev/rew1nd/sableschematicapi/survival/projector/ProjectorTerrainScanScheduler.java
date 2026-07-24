package dev.rew1nd.sableschematicapi.survival.projector;

import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Fair, whole-server terrain sampling budget. */
public final class ProjectorTerrainScanScheduler {
    private static final Set<ProjectorBlockEntity> ACTIVE = new LinkedHashSet<>();
    private static int cursor;

    private ProjectorTerrainScanScheduler() {
    }

    public static void add(final ProjectorBlockEntity projector) {
        ACTIVE.add(projector);
    }

    public static void remove(final ProjectorBlockEntity projector) {
        ACTIVE.remove(projector);
    }

    public static void tick(final ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        final List<ProjectorBlockEntity> active = new ArrayList<>(ACTIVE);
        int budget = ProjectorConfig.TERRAIN_CHECKS_PER_TICK.get();
        int idlePasses = 0;
        while (budget > 0 && !active.isEmpty() && idlePasses < active.size()) {
            if (cursor >= active.size()) {
                cursor = 0;
            }
            final ProjectorBlockEntity projector = active.get(cursor++);
            final int slice = Math.max(1, budget / active.size());
            final int used = projector.scan(slice);
            budget -= used;
            if (used == 0) {
                idlePasses++;
                ACTIVE.remove(projector);
                active.remove(projector);
                if (cursor > 0) {
                    cursor--;
                }
            } else {
                idlePasses = 0;
            }
        }
    }

    public static void clear() {
        ACTIVE.clear();
        cursor = 0;
    }

    public static void onServerStopped(final ServerStoppedEvent event) {
        clear();
    }
}

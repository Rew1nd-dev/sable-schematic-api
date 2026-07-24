package dev.rew1nd.sableschematicapi.survival.projector;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Server lifecycle owner for a projector reveal. Network/client group rendering
 * is intentionally kept behind this boundary.
 */
public final class ProjectorRevealService {
    private ProjectorRevealService() {
    }

    public static void start(final ServerLevel level,
                             final BlockPos projectorPos,
                             final ProjectorPlacementHandle handle,
                             final long endGameTime) {
        ProjectorRevealGroups.start(level, projectorPos, handle, endGameTime);
    }

    public static void finish(final ServerLevel level,
                              final ProjectorPlacementHandle handle,
                              final boolean immediate) {
        ProjectorRevealGroups.stop(level, handle, immediate);
        handle.release(level);
    }
}

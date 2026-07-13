package dev.rew1nd.sableschematicapi.sublevel;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/** Flushes Sable's holding storage without saving the entire Minecraft level. */
public final class SubLevelStorageFlushService {
    private SubLevelStorageFlushService() {
    }

    public static SubLevelOperationResult flush(final MinecraftServer server) {
        int flushed = 0;
        final List<String> failures = new ArrayList<>();

        for (final ServerLevel level : server.getAllLevels()) {
            final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) {
                continue;
            }

            try {
                container.getHoldingChunkMap().saveAll();
                flushed++;
            } catch (final RuntimeException e) {
                final String dimension = level.dimension().location().toString();
                failures.add(dimension);
                SableSchematicApi.LOGGER.warn("Failed flushing Sable sub-level storage for {}", dimension, e);
            }
        }

        if (!failures.isEmpty()) {
            return SubLevelOperationResult.failure(
                    "Flushed Sable sub-level storage for " + flushed + " dimension(s), but failed for " + String.join(", ", failures) + "."
            );
        }

        return SubLevelOperationResult.success("Flushed Sable sub-level storage for " + flushed + " dimension(s).", flushed);
    }
}

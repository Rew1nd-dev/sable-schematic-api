package dev.rew1nd.sableschematicapi.sublevel;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public final class SubLevelManagementService {
    private SubLevelManagementService() {
    }

    public static SubLevelOperationResult rename(final MinecraftServer server, final UUID uuid, final String name) {
        if (name == null || name.isBlank()) {
            return SubLevelOperationResult.failure("Sub-level name is required.");
        }

        final Optional<SubLevelRecord> record = SubLevelDirectoryService.find(server, uuid);
        if (record.isEmpty()) {
            return SubLevelOperationResult.failure("No Sable sub-level found with UUID " + uuid + ".");
        }

        final ServerLevel level = server.getLevel(record.get().dimension());
        if (level == null) {
            return SubLevelOperationResult.failure("Target dimension is not loaded: " + record.get().dimension().location());
        }

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return SubLevelOperationResult.failure("No Sable sub-level container is available for this level.");
        }

        final SubLevel subLevel = container.getSubLevel(uuid);
        if (subLevel instanceof final ServerSubLevel serverSubLevel && !serverSubLevel.isRemoved()) {
            serverSubLevel.setName(name);
            return SubLevelOperationResult.success("Renamed loaded sub-level " + uuid + " to " + name + ".", 1);
        }

        if (record.get().pointer() == null) {
            return SubLevelOperationResult.failure("Stored sub-level " + record.get().displayName() + " has no storage pointer.");
        }

        final SubLevelStorage storage = container.getHoldingChunkMap().getStorage();
        final SubLevelData data = storage.attemptLoadSubLevel(record.get().pointer().chunkPos(), record.get().pointer().local());
        if (data == null) {
            return SubLevelOperationResult.failure("Failed to load stored sub-level data for " + record.get().displayName() + ".");
        }

        data.fullTag().putString("display_name", name);
        storage.attemptSaveSubLevel(record.get().pointer(), data);
        try {
            storage.flush();
        } catch (final IOException e) {
            return SubLevelOperationResult.failure("Renamed sub-level, but failed to flush storage: " + e.getMessage());
        }

        return SubLevelOperationResult.success("Renamed stored sub-level " + uuid + " to " + name + ".", 1);
    }
}

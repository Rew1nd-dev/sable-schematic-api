package dev.rew1nd.sableschematicapi.compat.simulated;

import dev.rew1nd.sableschematicapi.sublevel.SubLevelOperationResult;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class SimulatedPhysicsStaffLockCompat {
    private SimulatedPhysicsStaffLockCompat() {
    }

    public static boolean isStatic(final MinecraftServer server, final ResourceKey<Level> dimension, final UUID uuid) {
        final ServerLevel level = server.getLevel(dimension);
        return level != null && isStatic(level, uuid);
    }

    public static boolean isStatic(final ServerLevel level, final UUID uuid) {
        return savedLocks(level).contains(uuid);
    }

    public static SubLevelOperationResult toggleStatic(final ServerLevel level, final UUID uuid) {
        final ServerSubLevel subLevel = loadedSubLevel(level, uuid);
        if (subLevel == null) {
            return SubLevelOperationResult.failure("Simulated Project persistent locks can only be changed while the sub-level is loaded.");
        }

        final boolean wasStatic = isStatic(level, uuid);
        PhysicsStaffServerHandler.get(level).toggleLock(uuid);
        final boolean isStatic = isStatic(level, uuid);

        if (wasStatic == isStatic) {
            return SubLevelOperationResult.failure("Simulated Project did not change the lock state for sub-level " + uuid + ".");
        }

        return SubLevelOperationResult.success(
                "Switched sub-level " + uuid + (isStatic ? " to static." : " to non-static."),
                1
        );
    }

    public static SubLevelOperationResult ensureStatic(final ServerSubLevel subLevel) {
        final ServerLevel level = subLevel.getLevel();
        final UUID uuid = subLevel.getUniqueId();
        if (isStatic(level, uuid)) {
            return SubLevelOperationResult.success("Sub-level " + uuid + " is already static.", 0);
        }

        PhysicsStaffServerHandler.get(level).toggleLock(uuid);
        if (!isStatic(level, uuid)) {
            return SubLevelOperationResult.failure("Simulated Project did not lock sub-level " + uuid + ".");
        }

        return SubLevelOperationResult.success("Locked sub-level " + uuid + ".", 1);
    }

    public static SubLevelOperationResult ensureNonStatic(final ServerSubLevel subLevel) {
        final ServerLevel level = subLevel.getLevel();
        final UUID uuid = subLevel.getUniqueId();
        if (!isStatic(level, uuid)) {
            return SubLevelOperationResult.success("Sub-level " + uuid + " is already non-static.", 0);
        }

        PhysicsStaffServerHandler.get(level).toggleLock(uuid);
        if (isStatic(level, uuid)) {
            return SubLevelOperationResult.failure("Simulated Project did not unlock sub-level " + uuid + ".");
        }

        return SubLevelOperationResult.success("Unlocked sub-level " + uuid + ".", 1);
    }

    public static void relockIfStatic(final ServerSubLevel subLevel) {
        final ServerLevel level = subLevel.getLevel();
        final UUID uuid = subLevel.getUniqueId();
        if (!isStatic(level, uuid)) {
            return;
        }

        final PhysicsStaffServerHandler handler = PhysicsStaffServerHandler.get(level);
        handler.toggleLock(uuid);
        handler.toggleLock(uuid);
    }

    private static ServerSubLevel loadedSubLevel(final ServerLevel level, final UUID uuid) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }

        final SubLevel subLevel = container.getSubLevel(uuid);
        return subLevel instanceof final ServerSubLevel serverSubLevel && !serverSubLevel.isRemoved() ? serverSubLevel : null;
    }

    private static Set<UUID> savedLocks(final ServerLevel level) {
        final PhysicsStaffServerHandler handler = PhysicsStaffServerHandler.get(level);
        final CompoundTag tag = handler.save(new CompoundTag(), level.registryAccess());
        final ListTag list = tag.getList(PhysicsStaffServerHandler.ID, Tag.TAG_INT_ARRAY);
        final Set<UUID> locks = new HashSet<>();
        for (final Tag lockTag : list) {
            locks.add(NbtUtils.loadUUID(lockTag));
        }
        return locks;
    }
}

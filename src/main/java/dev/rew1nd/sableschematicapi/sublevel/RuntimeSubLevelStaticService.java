package dev.rew1nd.sableschematicapi.sublevel;

import dev.rew1nd.sableschematicapi.compat.simulated.SimulatedPhysicsStaffLockCompat;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.fixed.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class RuntimeSubLevelStaticService {
    private static final String SIMULATED_PHYSICS_STAFF_HANDLER = "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler";
    private static final Map<ResourceKey<Level>, Map<UUID, Lock>> LOCKS = new LinkedHashMap<>();
    private static Boolean simulatedPhysicsStaffAvailable;

    private RuntimeSubLevelStaticService() {
    }

    public static void onSubLevelContainerReady(final Level level, final SubLevelContainer container) {
        if (usesSimulatedPhysicsStaff()) {
            return;
        }

        if (!(level instanceof final ServerLevel serverLevel) || !(container instanceof final ServerSubLevelContainer serverContainer)) {
            return;
        }

        serverContainer.addObserver(new Observer(serverLevel));
        for (final ServerSubLevel subLevel : serverContainer.getAllSubLevels()) {
            applyLockIfNeeded(subLevel);
        }
    }

    public static void onServerStopped(final ServerStoppedEvent event) {
        for (final Map<UUID, Lock> locks : LOCKS.values()) {
            for (final Lock lock : locks.values()) {
                lock.remove();
            }
        }
        LOCKS.clear();
    }

    public static boolean isStatic(final ResourceKey<Level> dimension, final UUID uuid) {
        return locks(dimension).containsKey(uuid);
    }

    public static boolean isStatic(final MinecraftServer server, final ResourceKey<Level> dimension, final UUID uuid) {
        if (usesSimulatedPhysicsStaff()) {
            return SimulatedPhysicsStaffLockCompat.isStatic(server, dimension, uuid);
        }

        return isStatic(dimension, uuid);
    }

    public static SubLevelOperationResult toggleStatic(final ServerLevel level, final UUID uuid) {
        if (usesSimulatedPhysicsStaff()) {
            return SimulatedPhysicsStaffLockCompat.toggleStatic(level, uuid);
        }

        final Map<UUID, Lock> locks = locks(level.dimension());
        final Lock existing = locks.remove(uuid);
        if (existing != null) {
            existing.remove();
            return SubLevelOperationResult.success("Switched sub-level " + uuid + " to non-static.", 1);
        }

        final ServerSubLevel subLevel = loadedSubLevel(level, uuid);
        final PhysicsConstraintHandle handle = subLevel == null ? null : addConstraint(level, subLevel);
        locks.put(uuid, new Lock(uuid, handle));
        return SubLevelOperationResult.success("Switched sub-level " + uuid + " to static.", 1);
    }

    public static SubLevelOperationResult ensureStatic(final ServerSubLevel subLevel) {
        if (usesSimulatedPhysicsStaff()) {
            return SimulatedPhysicsStaffLockCompat.ensureStatic(subLevel);
        }

        final Map<UUID, Lock> locks = locks(subLevel.getLevel().dimension());
        final Lock existing = locks.get(subLevel.getUniqueId());
        if (existing != null && existing.valid()) {
            return SubLevelOperationResult.success("Sub-level " + subLevel.getUniqueId() + " is already static.", 0);
        }
        if (existing != null) {
            existing.remove();
        }

        final PhysicsConstraintHandle handle = addConstraint(subLevel.getLevel(), subLevel);
        if (handle == null) {
            locks.remove(subLevel.getUniqueId());
            return SubLevelOperationResult.failure("Failed to create a fixed constraint for sub-level " + subLevel.getUniqueId() + ".");
        }

        locks.put(subLevel.getUniqueId(), new Lock(subLevel.getUniqueId(), handle));
        return SubLevelOperationResult.success("Locked sub-level " + subLevel.getUniqueId() + ".", 1);
    }

    public static SubLevelOperationResult ensureNonStatic(final ServerSubLevel subLevel) {
        if (usesSimulatedPhysicsStaff()) {
            return SimulatedPhysicsStaffLockCompat.ensureNonStatic(subLevel);
        }

        final Lock removed = locks(subLevel.getLevel().dimension()).remove(subLevel.getUniqueId());
        if (removed == null) {
            return SubLevelOperationResult.success("Sub-level " + subLevel.getUniqueId() + " is already non-static.", 0);
        }

        removed.remove();
        return SubLevelOperationResult.success("Unlocked sub-level " + subLevel.getUniqueId() + ".", 1);
    }

    public static void applyLockIfNeeded(final SubLevel subLevel) {
        if (usesSimulatedPhysicsStaff()) {
            return;
        }

        if (!(subLevel instanceof final ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved()) {
            return;
        }

        final Map<UUID, Lock> locks = locks(serverSubLevel.getLevel().dimension());
        final Lock lock = locks.get(serverSubLevel.getUniqueId());
        if (lock == null || lock.valid()) {
            return;
        }

        locks.put(serverSubLevel.getUniqueId(), new Lock(serverSubLevel.getUniqueId(), addConstraint(serverSubLevel.getLevel(), serverSubLevel)));
    }

    public static void relockIfStatic(final ServerSubLevel subLevel) {
        if (usesSimulatedPhysicsStaff()) {
            SimulatedPhysicsStaffLockCompat.relockIfStatic(subLevel);
            return;
        }

        final Map<UUID, Lock> locks = locks(subLevel.getLevel().dimension());
        final Lock lock = locks.get(subLevel.getUniqueId());
        if (lock == null) {
            return;
        }

        lock.remove();
        locks.put(subLevel.getUniqueId(), new Lock(subLevel.getUniqueId(), addConstraint(subLevel.getLevel(), subLevel)));
    }

    public static void removeLock(final SubLevel subLevel) {
        if (usesSimulatedPhysicsStaff()) {
            return;
        }

        final Lock removed = locks(subLevel.getLevel().dimension()).remove(subLevel.getUniqueId());
        if (removed != null) {
            removed.remove();
        }
    }

    private static ServerSubLevel loadedSubLevel(final ServerLevel level, final UUID uuid) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }

        final SubLevel subLevel = container.getSubLevel(uuid);
        return subLevel instanceof final ServerSubLevel serverSubLevel && !serverSubLevel.isRemoved() ? serverSubLevel : null;
    }

    private static PhysicsConstraintHandle addConstraint(final ServerLevel level, final ServerSubLevel subLevel) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }

        container.physicsSystem().getPipeline().resetVelocity(subLevel);
        return container.physicsSystem().getPipeline().addConstraint(null, subLevel, new FixedConstraintConfiguration(
                subLevel.logicalPose().position(),
                subLevel.logicalPose().rotationPoint(),
                subLevel.logicalPose().orientation()
        ));
    }

    private static Map<UUID, Lock> locks(final ResourceKey<Level> dimension) {
        return LOCKS.computeIfAbsent(dimension, ignored -> new LinkedHashMap<>());
    }

    private static boolean usesSimulatedPhysicsStaff() {
        if (!ModList.get().isLoaded("simulated")) {
            return false;
        }

        if (simulatedPhysicsStaffAvailable == null) {
            simulatedPhysicsStaffAvailable = hasSimulatedPhysicsStaffHandler();
        }
        return simulatedPhysicsStaffAvailable;
    }

    private static boolean hasSimulatedPhysicsStaffHandler() {
        try {
            Class.forName(SIMULATED_PHYSICS_STAFF_HANDLER, false, RuntimeSubLevelStaticService.class.getClassLoader());
            return true;
        } catch (final ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    private record Lock(UUID subLevel, PhysicsConstraintHandle handle) {
        private boolean valid() {
            return this.handle != null && this.handle.isValid();
        }

        private void remove() {
            if (this.handle != null) {
                this.handle.remove();
            }
        }
    }

    private record Observer(ServerLevel level) implements SubLevelObserver {
        @Override
        public void onSubLevelAdded(final SubLevel subLevel) {
            RuntimeSubLevelStaticService.applyLockIfNeeded(subLevel);
        }

        @Override
        public void onSubLevelRemoved(final SubLevel subLevel, final SubLevelRemovalReason reason) {
            if (reason == SubLevelRemovalReason.REMOVED) {
                RuntimeSubLevelStaticService.removeLock(subLevel);
            }
        }
    }
}

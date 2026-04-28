package dev.rew1nd.sableschematicapi.sublevel;

import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.fixed.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class RuntimeSubLevelStaticService {
    private static final Map<ResourceKey<Level>, Map<UUID, Lock>> LOCKS = new LinkedHashMap<>();

    private RuntimeSubLevelStaticService() {
    }

    public static void onSubLevelContainerReady(final Level level, final SubLevelContainer container) {
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

    public static SubLevelOperationResult toggleStatic(final ServerLevel level, final UUID uuid) {
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

    public static void applyLockIfNeeded(final SubLevel subLevel) {
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
        final Map<UUID, Lock> locks = locks(subLevel.getLevel().dimension());
        final Lock lock = locks.get(subLevel.getUniqueId());
        if (lock == null) {
            return;
        }

        lock.remove();
        locks.put(subLevel.getUniqueId(), new Lock(subLevel.getUniqueId(), addConstraint(subLevel.getLevel(), subLevel)));
    }

    public static void removeLock(final SubLevel subLevel) {
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

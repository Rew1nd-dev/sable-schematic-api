package dev.rew1nd.sableschematicapi.sublevel;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class LoadedSubLevelTeleportService {
    private static final double PLAYER_SIDE_DISTANCE = 4.0;

    private LoadedSubLevelTeleportService() {
    }

    public static SubLevelOperationResult teleportPlayerTo(final ServerPlayer player, final SubLevelRecord target) {
        final ServerLevel level = player.getServer().getLevel(target.dimension());
        if (level == null) {
            return SubLevelOperationResult.failure("Target dimension is not loaded: " + target.dimension().location());
        }

        final Vector3dc position = target.pose().position();
        final boolean teleported = player.teleportTo(
                level,
                position.x(),
                position.y(),
                position.z(),
                Set.of(),
                player.getYRot(),
                player.getXRot()
        );

        if (!teleported) {
            return SubLevelOperationResult.failure("Failed to teleport player to sub-level " + target.displayName() + ".");
        }

        return SubLevelOperationResult.success("Teleported player to sub-level " + target.displayName() + ".", 1);
    }

    public static SubLevelOperationResult teleportSubLevelTo(final ServerPlayer player, final ServerSubLevel target) {
        return teleportSubLevelTo(player, target, destinationNear(player));
    }

    public static SubLevelOperationResult teleportSubLevelTo(final ServerPlayer player,
                                                             final ServerSubLevel target,
                                                             final Vec3 destination) {
        return teleportSubLevelGroupTo(player, Set.of(target.getUniqueId()), target.getUniqueId(), destination);
    }

    public static SubLevelOperationResult teleportSubLevelGroupTo(final ServerPlayer player,
                                                                  final Collection<UUID> targetIds,
                                                                  final UUID anchorId,
                                                                  final Vec3 destination) {
        if (targetIds == null || targetIds.isEmpty()) {
            return SubLevelOperationResult.failure("No sub-level group members were selected.");
        }

        if (anchorId == null) {
            return SubLevelOperationResult.failure("No sub-level group anchor was selected.");
        }

        final ServerLevel level = player.serverLevel();
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return SubLevelOperationResult.failure("No Sable sub-level container is available for this level.");
        }

        final SubLevel anchor = container.getSubLevel(anchorId);
        if (!(anchor instanceof final ServerSubLevel anchorSubLevel) || anchorSubLevel.isRemoved()) {
            return SubLevelOperationResult.failure("Target sub-level group anchor is not loaded.");
        }

        final Map<UUID, ServerSubLevel> members = new LinkedHashMap<>();
        for (final UUID targetId : targetIds) {
            final SubLevel subLevel = container.getSubLevel(targetId);
            if (!(subLevel instanceof final ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved()) {
                return SubLevelOperationResult.failure("Sub-level group member is not loaded: " + targetId + ".");
            }
            members.put(targetId, serverSubLevel);
        }

        for (final SubLevel connected : SubLevelHelper.getConnectedChain(anchorSubLevel)) {
            if (connected instanceof final ServerSubLevel serverSubLevel && !serverSubLevel.isRemoved()) {
                members.putIfAbsent(serverSubLevel.getUniqueId(), serverSubLevel);
            }
        }

        return teleportLoadedGroup(player, members.values(), anchorSubLevel, destination);
    }

    private static SubLevelOperationResult teleportLoadedGroup(final ServerPlayer player,
                                                               final Collection<ServerSubLevel> targets,
                                                               final ServerSubLevel anchor,
                                                               final Vec3 destination) {
        if (targets.isEmpty()) {
            return SubLevelOperationResult.failure("No loaded sub-level group members were found.");
        }

        if (!anchor.getLevel().dimension().equals(player.serverLevel().dimension())) {
            return SubLevelOperationResult.failure("Bringing sub-levels across dimensions is not supported yet.");
        }

        for (final ServerSubLevel target : targets) {
            if (target.isRemoved()) {
                return SubLevelOperationResult.failure("A target sub-level has already been removed.");
            }
            if (!target.getLevel().dimension().equals(player.serverLevel().dimension())) {
                return SubLevelOperationResult.failure("Bringing sub-levels across dimensions is not supported yet.");
            }
        }

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(anchor.getLevel());
        if (container == null) {
            return SubLevelOperationResult.failure("No Sable sub-level container is available for this level.");
        }

        final PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        final Vector3dc anchorPosition = anchor.logicalPose().position();
        final Vector3d delta = new Vector3d(
                destination.x - anchorPosition.x(),
                destination.y - anchorPosition.y(),
                destination.z - anchorPosition.z()
        );

        for (final ServerSubLevel target : targets) {
            pipeline.resetVelocity(target);
        }

        for (final ServerSubLevel target : targets) {
            final Vector3dc currentPosition = target.logicalPose().position();
            pipeline.teleport(
                    target,
                    new Vector3d(currentPosition).add(delta),
                    target.logicalPose().orientation()
            );
            RuntimeSubLevelStaticService.relockIfStatic(target);
        }

        final String description = anchor.getName() == null ? anchor.getUniqueId().toString() : anchor.getName();
        return SubLevelOperationResult.success("Teleported sub-level group anchored at " + description + " to the player.", targets.size());
    }

    public static Optional<ServerSubLevel> findLoaded(final MinecraftServer server, final SubLevelRecord record) {
        final ServerLevel level = server.getLevel(record.dimension());
        if (level == null) {
            return Optional.empty();
        }

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return Optional.empty();
        }

        final SubLevel subLevel = container.getSubLevel(record.uuid());
        if (subLevel instanceof final ServerSubLevel serverSubLevel && !serverSubLevel.isRemoved()) {
            return Optional.of(serverSubLevel);
        }
        return Optional.empty();
    }

    public static Vec3 destinationNear(final ServerPlayer player) {
        final Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        if (horizontal.lengthSqr() < 1.0E-6) {
            horizontal = Vec3.directionFromRotation(0.0F, player.getYRot());
        } else {
            horizontal = horizontal.normalize();
        }

        final Vec3 destination = player.position().add(horizontal.scale(PLAYER_SIDE_DISTANCE));
        return Sable.HELPER.projectOutOfSubLevel(player.serverLevel(), destination);
    }
}

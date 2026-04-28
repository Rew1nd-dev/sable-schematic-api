package dev.rew1nd.sableschematicapi.sublevel;

import dev.ryanhcode.sable.Sable;
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

import java.util.Optional;
import java.util.Set;

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
        if (target.isRemoved()) {
            return SubLevelOperationResult.failure("Target sub-level has already been removed.");
        }

        if (!target.getLevel().dimension().equals(player.serverLevel().dimension())) {
            return SubLevelOperationResult.failure("Bringing sub-levels across dimensions is not supported yet.");
        }

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(target.getLevel());
        if (container == null) {
            return SubLevelOperationResult.failure("No Sable sub-level container is available for this level.");
        }

        final PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        pipeline.resetVelocity(target);
        pipeline.teleport(
                target,
                new Vector3d(destination.x, destination.y, destination.z),
                target.logicalPose().orientation()
        );
        RuntimeSubLevelStaticService.relockIfStatic(target);

        final String description = target.getName() == null ? target.getUniqueId().toString() : target.getName();
        return SubLevelOperationResult.success("Teleported sub-level " + description + " to the player.", 1);
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

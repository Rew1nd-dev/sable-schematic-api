package dev.rew1nd.sableschematicapi.survival.projector;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Optional;
import java.util.UUID;

/** A block-local frame that can live in the parent world or a loaded Sable body. */
public final class ProjectorAttachment {
    private final BlockPos localPos;
    private final Block expectedBlock;
    private final UUID bodyId;
    private final Vector3d initialPosition;
    private final Quaterniond initialOrientation;

    private ProjectorAttachment(final BlockPos localPos,
                                final Block expectedBlock,
                                final UUID bodyId,
                                final Vector3dc initialPosition,
                                final Quaterniond initialOrientation) {
        this.localPos = localPos.immutable();
        this.expectedBlock = expectedBlock;
        this.bodyId = bodyId;
        this.initialPosition = new Vector3d(initialPosition);
        this.initialOrientation = new Quaterniond(initialOrientation);
    }

    public static ProjectorAttachment capture(final ServerLevel level, final BlockPos storagePos) {
        final SubLevel containing = Sable.HELPER.getContaining(level, storagePos);
        final UUID bodyId = containing == null ? null : containing.getUniqueId();
        final Pose3dc pose = containing == null ? null : containing.logicalPose();
        final Vector3d position = new Vector3d(storagePos.getX() + 0.5D,
                storagePos.getY() + 0.5D, storagePos.getZ() + 0.5D);
        final Quaterniond orientation = new Quaterniond();
        if (pose != null) {
            pose.transformPosition(position);
            orientation.set(pose.orientation());
        }
        return new ProjectorAttachment(storagePos, level.getBlockState(storagePos).getBlock(),
                bodyId, position, orientation);
    }

    public Optional<UUID> bodyId() {
        return Optional.ofNullable(this.bodyId);
    }

    public BlockPos localPos() {
        return this.localPos;
    }

    public Vector3d worldPosition(final ServerLevel level) {
        final Pose3dc pose = this.currentPose(level);
        final Vector3d result = new Vector3d(this.localPos.getX() + 0.5D,
                this.localPos.getY() + 0.5D, this.localPos.getZ() + 0.5D);
        return pose == null ? result : pose.transformPosition(result);
    }

    public Vector3d worldPoint(final ServerLevel level, final Vector3dc storagePoint) {
        final Pose3dc pose = this.currentPose(level);
        final Vector3d result = new Vector3d(storagePoint);
        return pose == null ? result : pose.transformPosition(result);
    }

    public Vector3d worldDirection(final ServerLevel level, final Vector3dc localDirection) {
        final Pose3dc pose = this.currentPose(level);
        final Vector3d result = new Vector3d(localDirection);
        if (pose != null) {
            pose.orientation().transform(result);
        }
        return result.normalize();
    }

    public boolean valid(final ServerLevel level) {
        final Pose3dc pose = this.currentPose(level);
        if (this.bodyId != null && pose == null) {
            return false;
        }
        return level.getBlockState(this.localPos).is(this.expectedBlock);
    }

    public boolean withinInitialThresholds(final ServerLevel level) {
        final Pose3dc pose = this.currentPose(level);
        final Vector3d currentPosition = this.worldPosition(level);
        final Quaterniond currentOrientation = pose == null
                ? new Quaterniond()
                : new Quaterniond(pose.orientation());
        final double translation = currentPosition.distance(this.initialPosition);
        final double rotation = Math.toDegrees(
                2.0D * Math.acos(Math.min(1.0D, Math.abs(currentOrientation.dot(this.initialOrientation))))
        );
        return translation <= ProjectorConfig.ANCHOR_TRANSLATION_THRESHOLD.get()
                && rotation <= ProjectorConfig.ANCHOR_ROTATION_THRESHOLD_DEGREES.get();
    }

    private Pose3dc currentPose(final ServerLevel level) {
        if (this.bodyId == null) {
            return null;
        }
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        final SubLevel subLevel = container.getSubLevel(this.bodyId);
        return subLevel instanceof ServerSubLevel ? subLevel.logicalPose() : null;
    }
}

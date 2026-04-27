package dev.rew1nd.sableschematicapi.api.blueprint;

import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;

import java.util.UUID;

/**
 * Save-time description of one source Sable sub-level selected for export.
 *
 * @param blueprintId   blueprint-local sub-level id
 * @param sourceUuid    source sub-level UUID at save time
 * @param subLevel      source server sub-level
 * @param storageBounds source storage bounds copied into the blueprint
 * @param blocksOrigin  storage origin used to convert positions into blueprint-local coordinates
 * @param sourcePose    source logical pose at save time
 */
public record SubLevelSaveFrame(int blueprintId,
                                UUID sourceUuid,
                                ServerSubLevel subLevel,
                                BoundingBox3i storageBounds,
                                BlockPos blocksOrigin,
                                Pose3d sourcePose) {
    /**
     * Copies mutable frame inputs defensively.
     */
    public SubLevelSaveFrame {
        storageBounds = new BoundingBox3i(storageBounds);
        blocksOrigin = blocksOrigin.immutable();
        sourcePose = new Pose3d(sourcePose);
    }

    /**
     * Tests whether a source storage position falls inside this frame.
     *
     * @param storagePos source storage position
     * @return whether a source storage position falls inside this frame
     */
    public boolean contains(final BlockPos storagePos) {
        return storagePos.getX() >= this.storageBounds.minX()
                && storagePos.getX() <= this.storageBounds.maxX()
                && storagePos.getY() >= this.storageBounds.minY()
                && storagePos.getY() <= this.storageBounds.maxY()
                && storagePos.getZ() >= this.storageBounds.minZ()
                && storagePos.getZ() <= this.storageBounds.maxZ();
    }

    /**
     * Converts a source storage position to this frame's blueprint-local position.
     *
     * @param storagePos source storage position
     * @return blueprint-local position
     */
    public BlockPos toLocal(final BlockPos storagePos) {
        return new BlockPos(
                storagePos.getX() - this.blocksOrigin.getX(),
                storagePos.getY() - this.blocksOrigin.getY(),
                storagePos.getZ() - this.blocksOrigin.getZ()
        );
    }
}

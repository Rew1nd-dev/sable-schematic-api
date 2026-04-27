package dev.rew1nd.sableschematicapi.api.blueprint;

import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;

import java.util.UUID;

public record SubLevelSaveFrame(int blueprintId,
                                UUID sourceUuid,
                                ServerSubLevel subLevel,
                                BoundingBox3i storageBounds,
                                BlockPos blocksOrigin,
                                Pose3d sourcePose) {
    public SubLevelSaveFrame {
        storageBounds = new BoundingBox3i(storageBounds);
        blocksOrigin = blocksOrigin.immutable();
        sourcePose = new Pose3d(sourcePose);
    }

    public boolean contains(final BlockPos storagePos) {
        return storagePos.getX() >= this.storageBounds.minX()
                && storagePos.getX() <= this.storageBounds.maxX()
                && storagePos.getY() >= this.storageBounds.minY()
                && storagePos.getY() <= this.storageBounds.maxY()
                && storagePos.getZ() >= this.storageBounds.minZ()
                && storagePos.getZ() <= this.storageBounds.maxZ();
    }

    public BlockPos toLocal(final BlockPos storagePos) {
        return new BlockPos(
                storagePos.getX() - this.blocksOrigin.getX(),
                storagePos.getY() - this.blocksOrigin.getY(),
                storagePos.getZ() - this.blocksOrigin.getZ()
        );
    }
}

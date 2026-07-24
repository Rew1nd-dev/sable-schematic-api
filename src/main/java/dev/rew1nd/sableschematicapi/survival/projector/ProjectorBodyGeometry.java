package dev.rew1nd.sableschematicapi.survival.projector;

import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import dev.ryanhcode.sable.api.physics.mass.MassTracker;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Geometry in the same center-of-mass-local frame used by Sable's logical pose. */
final class ProjectorBodyGeometry {
    private static final double MIN_MASS = 1.0e-9D;

    private ProjectorBodyGeometry() {
    }

    static BoundingBox3d anchorBounds(final ServerLevel level,
                                      final SableBlueprint.SubLevelData entry) {
        final BoundingBox3i localBounds = entry.localBounds();
        if (localBounds == BoundingBox3i.EMPTY || localBounds.volume() <= 0) {
            return BoundingBox3d.EMPTY;
        }

        double totalMass = 0.0D;
        final Vector3d weightedCenter = new Vector3d();
        for (final SableBlueprint.BlockData block : entry.blocks()) {
            if (!entry.isPaletteAvailable(block.paletteId())) {
                continue;
            }
            final BlockState state = entry.blockPalette().get(block.paletteId());
            final double mass = PhysicsBlockPropertyHelper.getMass(
                    level, block.localPos(), state);
            if (!(mass > 0.0D) || !Double.isFinite(mass)) {
                continue;
            }
            final Vector3dc blockCenter = MassTracker.BLOCK_CENTER_OF_MASS.apply(level, state);
            weightedCenter.fma(mass, new Vector3d(
                    block.localPos().getX() + blockCenter.x(),
                    block.localPos().getY() + blockCenter.y(),
                    block.localPos().getZ() + blockCenter.z()
            ));
            totalMass += mass;
        }

        if (!(totalMass > MIN_MASS) || !Double.isFinite(totalMass)) {
            return entry.anchorBounds();
        }
        final Vector3d centerOfMass = weightedCenter.div(totalMass);
        return new BoundingBox3d(
                localBounds.minX() - centerOfMass.x,
                localBounds.minY() - centerOfMass.y,
                localBounds.minZ() - centerOfMass.z,
                localBounds.maxX() + 1.0D - centerOfMass.x,
                localBounds.maxY() + 1.0D - centerOfMass.y,
                localBounds.maxZ() + 1.0D - centerOfMass.z
        );
    }
}

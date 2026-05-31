package dev.rew1nd.sableschematicapi.client.frontier;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3d;
import org.joml.Vector3fc;

public final class SableFrontierCoordinates {
    private SableFrontierCoordinates() {
    }

    public static Vector3d renderOrigin(final ClientSubLevel subLevel, final Vector3d dest) {
        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        return dest.set(sectionOrigin(bounds.minX()), sectionOrigin(bounds.minY()), sectionOrigin(bounds.minZ()));
    }

    public static ProjectionRange projectedRange(final ClientSubLevel subLevel,
                                                 final Vector3fc normal,
                                                 final float gradientWidth) {
        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        final Vector3d origin = renderOrigin(subLevel, new Vector3d());

        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (final float x : new float[]{bounds.minX(), bounds.maxX() + 1.0f}) {
            for (final float y : new float[]{bounds.minY(), bounds.maxY() + 1.0f}) {
                for (final float z : new float[]{bounds.minZ(), bounds.maxZ() + 1.0f}) {
                    final float projection = normal.x() * (float) (x - origin.x())
                            + normal.y() * (float) (y - origin.y())
                            + normal.z() * (float) (z - origin.z());
                    min = Math.min(min, projection);
                    max = Math.max(max, projection);
                }
            }
        }

        return new ProjectionRange(min - gradientWidth, max + gradientWidth);
    }

    public static boolean isBlockEntityVisible(final BlockEntity blockEntity) {
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(blockEntity);
        if (subLevel == null) {
            return true;
        }

        final SableFrontierData data = FrontierDataStore.get(subLevel);
        if (data == null || !data.enabled()) {
            return true;
        }

        final BlockPos pos = blockEntity.getBlockPos();
        final Vector3d origin = renderOrigin(subLevel, new Vector3d());
        final Vector3fc normal = data.normal();
        final float projection = normal.x() * (float) (pos.getX() + 0.5 - origin.x())
                + normal.y() * (float) (pos.getY() + 0.5 - origin.y())
                + normal.z() * (float) (pos.getZ() + 0.5 - origin.z());
        return data.distance() - projection >= 0.0f;
    }

    private static int sectionOrigin(final int blockCoordinate) {
        return (blockCoordinate >> 4) << 4;
    }

    public record ProjectionRange(float start, float end) {
    }
}

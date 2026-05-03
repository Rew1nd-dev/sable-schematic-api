package dev.rew1nd.sableschematicapi.tool.client.preview.util;

import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import org.joml.Vector3d;

public final class BlueprintToolPreviewBounds {
    private BlueprintToolPreviewBounds() {
    }

    public static long localVolume(final SableBlueprint.SubLevelData entry) {
        final int width = entry.localBounds().maxX() - entry.localBounds().minX() + 1;
        final int height = entry.localBounds().maxY() - entry.localBounds().minY() + 1;
        final int depth = entry.localBounds().maxZ() - entry.localBounds().minZ() + 1;
        return (long) Math.max(width, 0) * Math.max(height, 0) * Math.max(depth, 0);
    }

    public static AxisRange boundsRange(final BoundingBox3d bounds, final Vector3d axis) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (final double x : new double[]{bounds.minX(), bounds.maxX()}) {
            for (final double y : new double[]{bounds.minY(), bounds.maxY()}) {
                for (final double z : new double[]{bounds.minZ(), bounds.maxZ()}) {
                    final double value = x * axis.x() + y * axis.y() + z * axis.z();
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                }
            }
        }

        return new AxisRange(min, max);
    }

    public record AxisRange(double min, double max) {
        public double size() {
            return this.max - this.min;
        }
    }
}

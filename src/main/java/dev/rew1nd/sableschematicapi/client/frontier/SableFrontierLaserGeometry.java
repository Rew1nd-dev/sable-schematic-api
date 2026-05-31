package dev.rew1nd.sableschematicapi.client.frontier;

import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

public final class SableFrontierLaserGeometry {
    private static final double EPSILON = 1.0E-5;
    private static final float INVISIBLE_EDGE_DISTANCE_FACTOR = 0.65f;
    private static final Vector3d[] UNIT_CORNERS = {
            new Vector3d(0.0, 0.0, 0.0),
            new Vector3d(1.0, 0.0, 0.0),
            new Vector3d(0.0, 1.0, 0.0),
            new Vector3d(1.0, 1.0, 0.0),
            new Vector3d(0.0, 0.0, 1.0),
            new Vector3d(1.0, 0.0, 1.0),
            new Vector3d(0.0, 1.0, 1.0),
            new Vector3d(1.0, 1.0, 1.0)
    };

    private SableFrontierLaserGeometry() {
    }

    public static @Nullable BasePolygon currentBase(final ClientSubLevel subLevel,
                                                    final SableFrontierData data) {
        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        if (bounds == null) {
            return null;
        }

        final Vector3d origin = SableFrontierCoordinates.renderOrigin(subLevel, new Vector3d());
        final Vector3fc normal = data.normal();
        final float laserDistance = data.distance() + data.gradientWidth() * INVISIBLE_EDGE_DISTANCE_FACTOR;
        final PlaneBasis basis = createBasis(origin, normal, laserDistance);
        final Footprint footprint = actualBlockFootprint(subLevel, bounds, origin, normal, data.distance(), basis);
        if (footprint == null) {
            return null;
        }

        final List<Vector3d> localPoints = List.of(
                basis.point(footprint.minU, footprint.minV),
                basis.point(footprint.maxU, footprint.minV),
                basis.point(footprint.maxU, footprint.maxV),
                basis.point(footprint.minU, footprint.maxV)
        );

        final List<Vector3d> worldPoints = new ArrayList<>(localPoints.size());
        final Vector3d center = new Vector3d();
        for (final Vector3d localPoint : localPoints) {
            final Vector3d worldPoint = subLevel.renderPose().transformPosition(new Vector3d(localPoint));
            worldPoints.add(worldPoint);
            center.add(worldPoint);
        }
        center.div(worldPoints.size());
        return new BasePolygon(List.copyOf(worldPoints), center);
    }

    private static @Nullable Footprint actualBlockFootprint(final ClientSubLevel subLevel,
                                                            final BoundingBox3ic bounds,
                                                            final Vector3dc origin,
                                                            final Vector3fc normal,
                                                            final float distance,
                                                            final PlaneBasis basis) {
        Footprint footprint = null;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    pos.set(x, y, z);
                    final BlockState state = subLevel.getLevel().getBlockState(pos);
                    if (state.isAir() || !blockIntersectsPlane(x, y, z, origin, normal, distance)) {
                        continue;
                    }

                    if (footprint == null) {
                        footprint = new Footprint();
                    }
                    footprint.includeBlock(x, y, z, basis);
                }
            }
        }
        return footprint != null && footprint.valid() ? footprint : null;
    }

    private static boolean blockIntersectsPlane(final int x,
                                                final int y,
                                                final int z,
                                                final Vector3dc origin,
                                                final Vector3fc normal,
                                                final float distance) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (final Vector3d corner : UNIT_CORNERS) {
            final double projection = normal.x() * (x + corner.x() - origin.x())
                    + normal.y() * (y + corner.y() - origin.y())
                    + normal.z() * (z + corner.z() - origin.z());
            min = Math.min(min, projection);
            max = Math.max(max, projection);
        }
        return distance >= min - EPSILON && distance <= max + EPSILON;
    }

    private static PlaneBasis createBasis(final Vector3dc origin,
                                          final Vector3fc normal,
                                          final float distance) {
        final Vector3d n = new Vector3d(normal.x(), normal.y(), normal.z()).normalize();
        final Vector3d reference = Math.abs(n.y()) < 0.9 ? new Vector3d(0.0, 1.0, 0.0) : new Vector3d(1.0, 0.0, 0.0);
        final Vector3d u = n.cross(reference, new Vector3d()).normalize();
        final Vector3d v = n.cross(u, new Vector3d()).normalize();
        final Vector3d planeOrigin = new Vector3d(origin).fma(distance, n);
        return new PlaneBasis(planeOrigin, u, v);
    }

    private record PlaneBasis(Vector3dc planeOrigin, Vector3dc u, Vector3dc v) {
        private Vector3d point(final double uValue, final double vValue) {
            return new Vector3d(this.planeOrigin)
                    .fma(uValue, this.u)
                    .fma(vValue, this.v);
        }
    }

    private static final class Footprint {
        private double minU = Double.POSITIVE_INFINITY;
        private double maxU = Double.NEGATIVE_INFINITY;
        private double minV = Double.POSITIVE_INFINITY;
        private double maxV = Double.NEGATIVE_INFINITY;

        private void includeBlock(final int x, final int y, final int z, final PlaneBasis basis) {
            for (final Vector3d corner : UNIT_CORNERS) {
                final Vector3d delta = new Vector3d(x + corner.x(), y + corner.y(), z + corner.z())
                        .sub(basis.planeOrigin);
                final double u = delta.dot(basis.u);
                final double v = delta.dot(basis.v);
                this.minU = Math.min(this.minU, u);
                this.maxU = Math.max(this.maxU, u);
                this.minV = Math.min(this.minV, v);
                this.maxV = Math.max(this.maxV, v);
            }
        }

        private boolean valid() {
            return Double.isFinite(this.minU)
                    && Double.isFinite(this.maxU)
                    && Double.isFinite(this.minV)
                    && Double.isFinite(this.maxV)
                    && this.maxU - this.minU > EPSILON
                    && this.maxV - this.minV > EPSILON;
        }
    }

    public record BasePolygon(List<Vector3d> points, Vector3dc center) {
    }
}

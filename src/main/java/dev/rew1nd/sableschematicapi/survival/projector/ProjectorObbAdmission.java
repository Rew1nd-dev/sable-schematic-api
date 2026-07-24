package dev.rew1nd.sableschematicapi.survival.projector;

import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import dev.rew1nd.sableschematicapi.survival.BlueprintPlacementPlan;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Loaded-body broad phase plus 15-axis OBB SAT. */
public final class ProjectorObbAdmission {
    private static final double PENETRATION_EPSILON = 1.0e-5D;

    private ProjectorObbAdmission() {
    }

    public static void check(final ServerLevel level,
                             final SableBlueprint blueprint,
                             final BlueprintPlacementPlan plan,
                             final UUID ignoredBodyId) throws ProjectorAdmissionException {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            throw new ProjectorAdmissionException("sable_schematic_api.projector.status.no_container");
        }
        final List<Obb> existing = new ArrayList<>();
        for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.getUniqueId().equals(ignoredBodyId)) {
                continue;
            }
            final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
            if (bounds == BoundingBox3i.EMPTY || bounds.volume() <= 0) {
                continue;
            }
            existing.add(Obb.of(bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX() + 1.0D, bounds.maxY() + 1.0D, bounds.maxZ() + 1.0D,
                    subLevel.logicalPose()));
        }
        for (final SableBlueprint.SubLevelData entry : blueprint.subLevels()) {
            final BoundingBox3i bounds = entry.localBounds();
            if (bounds == BoundingBox3i.EMPTY || bounds.volume() <= 0) {
                continue;
            }
            final BoundingBox3d anchorBounds = ProjectorBodyGeometry.anchorBounds(level, entry);
            final Obb candidate = Obb.of(
                    anchorBounds.minX(), anchorBounds.minY(), anchorBounds.minZ(),
                    anchorBounds.maxX(), anchorBounds.maxY(), anchorBounds.maxZ(),
                    plan.pose(entry));
            for (final Obb body : existing) {
                if (candidate.aabbIntersects(body) && candidate.penetrates(body)) {
                    throw new ProjectorAdmissionException(
                            "sable_schematic_api.projector.status.body_collision");
                }
            }
        }
    }

    public record Obb(Vector3d center, Vector3d[] axes, double[] half, double[] aabb) {
        public static Obb of(final double minX, final double minY, final double minZ,
                             final double maxX, final double maxY, final double maxZ,
                             final Pose3dc pose) {
            final Vector3d localCenter = new Vector3d(
                    (minX + maxX) * 0.5D,
                    (minY + maxY) * 0.5D,
                    (minZ + maxZ) * 0.5D
            );
            final Vector3d center = pose.transformPosition(localCenter);
            final Vector3d[] axes = {
                    new Vector3d(1, 0, 0),
                    new Vector3d(0, 1, 0),
                    new Vector3d(0, 0, 1)
            };
            for (final Vector3d axis : axes) {
                pose.orientation().transform(axis);
                axis.normalize();
            }
            final double[] half = {
                    (maxX - minX) * 0.5D * Math.abs(pose.scale().x()),
                    (maxY - minY) * 0.5D * Math.abs(pose.scale().y()),
                    (maxZ - minZ) * 0.5D * Math.abs(pose.scale().z())
            };
            final double ex = Math.abs(axes[0].x) * half[0]
                    + Math.abs(axes[1].x) * half[1] + Math.abs(axes[2].x) * half[2];
            final double ey = Math.abs(axes[0].y) * half[0]
                    + Math.abs(axes[1].y) * half[1] + Math.abs(axes[2].y) * half[2];
            final double ez = Math.abs(axes[0].z) * half[0]
                    + Math.abs(axes[1].z) * half[1] + Math.abs(axes[2].z) * half[2];
            return new Obb(center, axes, half, new double[] {
                    center.x - ex, center.y - ey, center.z - ez,
                    center.x + ex, center.y + ey, center.z + ez
            });
        }

        private boolean aabbIntersects(final Obb other) {
            return this.aabb[3] > other.aabb[0] + PENETRATION_EPSILON
                    && other.aabb[3] > this.aabb[0] + PENETRATION_EPSILON
                    && this.aabb[4] > other.aabb[1] + PENETRATION_EPSILON
                    && other.aabb[4] > this.aabb[1] + PENETRATION_EPSILON
                    && this.aabb[5] > other.aabb[2] + PENETRATION_EPSILON
                    && other.aabb[5] > this.aabb[2] + PENETRATION_EPSILON;
        }

        private boolean penetrates(final Obb other) {
            final Vector3d delta = new Vector3d(other.center).sub(this.center);
            for (int i = 0; i < 3; i++) {
                if (separates(this.axes[i], delta, this, other)) {
                    return false;
                }
                if (separates(other.axes[i], delta, this, other)) {
                    return false;
                }
            }
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    final Vector3d axis = new Vector3d(this.axes[i]).cross(other.axes[j]);
                    if (axis.lengthSquared() > 1.0e-12D) {
                        axis.normalize();
                        if (separates(axis, delta, this, other)) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        private static boolean separates(final Vector3d axis,
                                         final Vector3d delta,
                                         final Obb left,
                                         final Obb right) {
            final double distance = Math.abs(delta.dot(axis));
            double radius = 0.0D;
            for (int i = 0; i < 3; i++) {
                radius += left.half[i] * Math.abs(left.axes[i].dot(axis));
                radius += right.half[i] * Math.abs(right.axes[i].dot(axis));
            }
            return distance >= radius - PENETRATION_EPSILON;
        }
    }
}

package dev.rew1nd.sableschematicapi.survival;

import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable world-space pose plan for a survival blueprint placement.
 */
public final class BlueprintPlacementPlan {
    private static final int NO_BASIS = -1;

    private final int basisSubLevelId;
    private final Vector3d origin;
    private final BoundingBox3d bounds;
    private final Map<Integer, Pose3d> poses;

    private BlueprintPlacementPlan(final int basisSubLevelId,
                                   final Vector3dc origin,
                                   final BoundingBox3d bounds,
                                   final Map<Integer, Pose3d> poses) {
        this.basisSubLevelId = basisSubLevelId;
        this.origin = new Vector3d(origin);
        this.bounds = new BoundingBox3d(bounds);
        final Map<Integer, Pose3d> copied = new LinkedHashMap<>();
        for (final Map.Entry<Integer, Pose3d> entry : poses.entrySet()) {
            copied.put(entry.getKey(), new Pose3d(entry.getValue()));
        }
        this.poses = Map.copyOf(copied);
    }

    public static BlueprintPlacementPlan legacy(final SableBlueprint blueprint, final Vec3 origin) {
        final Map<Integer, Pose3d> poses = new LinkedHashMap<>();
        for (final SableBlueprint.SubLevelData entry : blueprint.subLevels()) {
            final Pose3d pose = new Pose3d(entry.relativePose());
            pose.position().add(origin.x, origin.y, origin.z);
            pose.rotationPoint().set(0.0, 0.0, 0.0);
            poses.put(entry.id(), pose);
        }

        return new BlueprintPlacementPlan(NO_BASIS, new Vector3d(origin.x, origin.y, origin.z), BoundingBox3d.EMPTY, poses);
    }

    public static BlueprintPlacementPlan forLookTarget(final SableBlueprint blueprint,
                                                       final Vec3 hitPoint,
                                                       final double spacing) {
        final List<SableBlueprint.SubLevelData> subLevels = blueprint.subLevels();
        final Vector3d target = new Vector3d(hitPoint.x, hitPoint.y + spacing, hitPoint.z);
        if (subLevels.isEmpty()) {
            return empty(target);
        }

        final CanonicalLayout layout = canonicalLayout(subLevels);
        final double y0 = Math.max(1.0, layout.height()) * 0.5;
        target.y = hitPoint.y + y0 + spacing;
        return anchoredToBasisAabbCenter(subLevels, layout, target);
    }

    public static BlueprintPlacementPlan forCannon(final SableBlueprint blueprint,
                                                   final BlockPos cannonPos,
                                                   final BlueprintBuildProgress progress,
                                                   final double padding) {
        final List<SableBlueprint.SubLevelData> subLevels = blueprint.subLevels();
        if (subLevels.isEmpty()) {
            final Vector3d origin = new Vector3d(cannonPos.getX() + 0.5, cannonPos.getY() + 1.0 + padding, cannonPos.getZ() + 0.5);
            return empty(origin);
        }

        final CanonicalLayout layout = canonicalLayout(subLevels);
        final BlueprintBuildProgress.PlacementAnchor anchor = resolveAnchor(progress, layout.basis(), cannonPos, layout.bounds(), padding);
        return anchoredToCanonicalBoundsCenter(subLevels, layout, anchor.targetCenter());
    }

    private static BlueprintPlacementPlan empty(final Vector3dc origin) {
        return new BlueprintPlacementPlan(
                NO_BASIS,
                origin,
                new BoundingBox3d(origin.x(), origin.y(), origin.z(), origin.x(), origin.y(), origin.z()),
                Map.of()
        );
    }

    private static BlueprintPlacementPlan anchoredToBasisAabbCenter(final List<SableBlueprint.SubLevelData> subLevels,
                                                                    final CanonicalLayout layout,
                                                                    final Vector3dc targetAabbCenter) {
        final Vector3d offset = new Vector3d(targetAabbCenter).sub(layout.basisAabbCenter());
        return withOffset(subLevels, layout, offset);
    }

    private static BlueprintPlacementPlan anchoredToCanonicalBoundsCenter(final List<SableBlueprint.SubLevelData> subLevels,
                                                                          final CanonicalLayout layout,
                                                                          final Vector3dc targetCenter) {
        final Vector3d offset = new Vector3d(targetCenter).sub(center(layout.bounds()));
        return withOffset(subLevels, layout, offset);
    }

    private static BlueprintPlacementPlan withOffset(final List<SableBlueprint.SubLevelData> subLevels,
                                                     final CanonicalLayout layout,
                                                     final Vector3dc offset) {
        final Map<Integer, Pose3d> poses = new LinkedHashMap<>();
        for (final SableBlueprint.SubLevelData entry : subLevels) {
            poses.put(entry.id(), cannonPose(entry, layout.basis(), offset));
        }

        final BoundingBox3d worldBounds = new BoundingBox3d(layout.bounds()).move(offset.x(), offset.y(), offset.z());
        return new BlueprintPlacementPlan(layout.basis().id(), offset, worldBounds, poses);
    }

    public int basisSubLevelId() {
        return this.basisSubLevelId;
    }

    public Vector3d origin() {
        return new Vector3d(this.origin);
    }

    public Vec3 originVec3() {
        return new Vec3(this.origin.x, this.origin.y, this.origin.z);
    }

    public BoundingBox3d bounds() {
        return new BoundingBox3d(this.bounds);
    }

    public Pose3d pose(final SableBlueprint.SubLevelData entry) {
        final Pose3d pose = this.poses.get(entry.id());
        if (pose == null) {
            throw new IllegalStateException("No placement pose for blueprint sub-level " + entry.id());
        }
        return new Pose3d(pose);
    }

    private static BlueprintBuildProgress.PlacementAnchor resolveAnchor(final BlueprintBuildProgress progress,
                                                                        final SableBlueprint.SubLevelData basis,
                                                                        final BlockPos cannonPos,
                                                                        final BoundingBox3d canonicalBounds,
                                                                        final double padding) {
        final BlueprintBuildProgress.PlacementAnchor existing = progress.placementAnchor();
        if (existing != null && existing.basisSubLevelId() == basis.id()) {
            return existing;
        }

        final double height = Math.max(1.0, canonicalBounds.maxY() - canonicalBounds.minY());
        final Vector3d targetCenter = new Vector3d(
                cannonPos.getX() + 0.5,
                cannonPos.getY() + 1.0 + padding + height * 0.5,
                cannonPos.getZ() + 0.5
        );
        final BlueprintBuildProgress.PlacementAnchor anchor =
                new BlueprintBuildProgress.PlacementAnchor(basis.id(), targetCenter, padding);
        progress.setPlacementAnchor(anchor);
        return anchor;
    }

    private static Pose3d cannonPose(final SableBlueprint.SubLevelData entry,
                                     final SableBlueprint.SubLevelData basis,
                                     final Vector3dc offset) {
        final Vector3d position = new Vector3d();
        entry.relativePose().transformPosition(position);
        basis.relativePose().transformPositionInverse(position);
        position.add(offset);

        final Quaterniond orientation = new Quaterniond(basis.relativePose().orientation())
                .invert()
                .mul(entry.relativePose().orientation());

        return new Pose3d(position, orientation, new Vector3d(), relativeScale(entry.relativePose().scale(), basis.relativePose().scale()));
    }

    private static Vector3d relativeScale(final Vector3dc entryScale, final Vector3dc basisScale) {
        return new Vector3d(
                safeDivide(entryScale.x(), basisScale.x()),
                safeDivide(entryScale.y(), basisScale.y()),
                safeDivide(entryScale.z(), basisScale.z())
        );
    }

    private static double safeDivide(final double value, final double divisor) {
        return Math.abs(divisor) < 1.0e-8 ? value : value / divisor;
    }

    private static SableBlueprint.SubLevelData selectBasis(final List<SableBlueprint.SubLevelData> subLevels) {
        return subLevels.stream()
                .max(Comparator
                        .comparingLong(BlueprintPlacementPlan::localVolume)
                        .thenComparingInt(entry -> entry.blocks().size()))
                .orElseThrow();
    }

    private static long localVolume(final SableBlueprint.SubLevelData entry) {
        final BoundingBox3i bounds = entry.localBounds();
        final int width = bounds.maxX() - bounds.minX() + 1;
        final int height = bounds.maxY() - bounds.minY() + 1;
        final int depth = bounds.maxZ() - bounds.minZ() + 1;
        return (long) Math.max(width, 0) * Math.max(height, 0) * Math.max(depth, 0);
    }

    private static CanonicalLayout canonicalLayout(final List<SableBlueprint.SubLevelData> subLevels) {
        final SableBlueprint.SubLevelData basis = selectBasis(subLevels);
        return new CanonicalLayout(basis, canonicalBounds(subLevels, basis), localAabbCenter(basis));
    }

    private static BoundingBox3d canonicalBounds(final List<SableBlueprint.SubLevelData> subLevels,
                                                 final SableBlueprint.SubLevelData basis) {
        final BoundsBuilder builder = new BoundsBuilder();
        for (final SableBlueprint.SubLevelData entry : subLevels) {
            includeLocalBounds(builder, entry, basis);
        }

        if (!builder.hasBounds()) {
            builder.include(new Vector3d());
            builder.include(new Vector3d(1.0, 1.0, 1.0));
        }
        return builder.build();
    }

    private static void includeLocalBounds(final BoundsBuilder builder,
                                           final SableBlueprint.SubLevelData entry,
                                           final SableBlueprint.SubLevelData basis) {
        final BoundingBox3i bounds = entry.localBounds();
        if (bounds == BoundingBox3i.EMPTY || bounds.volume() <= 0) {
            final Vector3d point = new Vector3d();
            entry.relativePose().transformPosition(point);
            basis.relativePose().transformPositionInverse(point);
            builder.include(point);
            return;
        }

        final double[] xs = new double[]{bounds.minX(), bounds.maxX() + 1.0};
        final double[] ys = new double[]{bounds.minY(), bounds.maxY() + 1.0};
        final double[] zs = new double[]{bounds.minZ(), bounds.maxZ() + 1.0};
        for (final double x : xs) {
            for (final double y : ys) {
                for (final double z : zs) {
                    final Vector3d point = new Vector3d(x, y, z);
                    entry.relativePose().transformPosition(point);
                    basis.relativePose().transformPositionInverse(point);
                    builder.include(point);
                }
            }
        }
    }

    private static Vector3d localAabbCenter(final SableBlueprint.SubLevelData entry) {
        final BoundingBox3i bounds = entry.localBounds();
        if (bounds == BoundingBox3i.EMPTY || bounds.volume() <= 0) {
            return new Vector3d();
        }

        return new Vector3d(
                (bounds.minX() + bounds.maxX() + 1.0) * 0.5,
                (bounds.minY() + bounds.maxY() + 1.0) * 0.5,
                (bounds.minZ() + bounds.maxZ() + 1.0) * 0.5
        );
    }

    private static Vector3d center(final BoundingBox3d bounds) {
        return new Vector3d(
                (bounds.minX() + bounds.maxX()) * 0.5,
                (bounds.minY() + bounds.maxY()) * 0.5,
                (bounds.minZ() + bounds.maxZ()) * 0.5
        );
    }

    private record CanonicalLayout(SableBlueprint.SubLevelData basis,
                                   BoundingBox3d bounds,
                                   Vector3d basisAabbCenter) {
        private CanonicalLayout {
            bounds = new BoundingBox3d(bounds);
            basisAabbCenter = new Vector3d(basisAabbCenter);
        }

        private double height() {
            return this.bounds.maxY() - this.bounds.minY();
        }

        @Override
        public BoundingBox3d bounds() {
            return new BoundingBox3d(this.bounds);
        }

        @Override
        public Vector3d basisAabbCenter() {
            return new Vector3d(this.basisAabbCenter);
        }
    }

    private static final class BoundsBuilder {
        private boolean hasBounds;
        private double minX;
        private double minY;
        private double minZ;
        private double maxX;
        private double maxY;
        private double maxZ;

        private boolean hasBounds() {
            return this.hasBounds;
        }

        private void include(final Vector3dc point) {
            if (!this.hasBounds) {
                this.minX = point.x();
                this.minY = point.y();
                this.minZ = point.z();
                this.maxX = point.x();
                this.maxY = point.y();
                this.maxZ = point.z();
                this.hasBounds = true;
                return;
            }

            this.minX = Math.min(this.minX, point.x());
            this.minY = Math.min(this.minY, point.y());
            this.minZ = Math.min(this.minZ, point.z());
            this.maxX = Math.max(this.maxX, point.x());
            this.maxY = Math.max(this.maxY, point.y());
            this.maxZ = Math.max(this.maxZ, point.z());
        }

        private BoundingBox3d build() {
            return new BoundingBox3d(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
        }
    }
}

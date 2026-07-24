package dev.rew1nd.sableschematicapi.client.frontier;

import dev.rew1nd.sableschematicapi.network.ProjectorRevealPayload;
import dev.rew1nd.sableschematicapi.survival.projector.ProjectorBlock;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Client-side pending/resolved projector groups using a shared world plane. */
public final class ProjectorFrontierClientGroups {
    private static final int RETRACT_TICKS = 8;
    private static final Map<UUID, Group> GROUPS = new LinkedHashMap<>();

    private ProjectorFrontierClientGroups() {
    }

    public static void handle(final ProjectorRevealPayload payload) {
        if (payload.action() == ProjectorRevealPayload.START) {
            GROUPS.put(payload.groupId(), new Group(payload));
            return;
        }
        final Group group = GROUPS.get(payload.groupId());
        if (group == null) {
            return;
        }
        group.disableBodies();
        if (payload.immediate() || group.lastBase == null) {
            GROUPS.remove(payload.groupId());
        } else {
            group.retracting = true;
            group.retractTicks = 0;
        }
    }

    public static void tick() {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clear();
            return;
        }
        GROUPS.entrySet().removeIf(entry -> {
            final Group group = entry.getValue();
            if (group.retracting) {
                return ++group.retractTicks > RETRACT_TICKS;
            }
            if (minecraft.level.getGameTime() >= group.payload.endGameTime()) {
                group.disableBodies();
                if (group.lastBase == null) {
                    return true;
                }
                group.retracting = true;
                group.retractTicks = 0;
                return false;
            }
            group.applyPlane();
            return false;
        });
    }

    public static List<LaserEntry> laserEntries() {
        final List<LaserEntry> result = new ArrayList<>();
        for (final Group group : GROUPS.values()) {
            if (group.retracting) {
                if (group.lastBase != null && group.lastApex != null) {
                    result.add(new LaserEntry(
                            group.lastApex,
                            retract(group.lastApex, group.lastBase,
                                    Math.min(1.0F, group.retractTicks / (float) RETRACT_TICKS))
                    ));
                }
                continue;
            }
            final Vector3d apex = group.apex();
            final SableFrontierLaserGeometry.BasePolygon base = group.currentBase();
            if (apex != null && base != null) {
                group.lastApex = new Vector3d(apex);
                group.lastBase = base;
                result.add(new LaserEntry(apex, base));
            }
        }
        return result;
    }

    public static void clear() {
        for (final Group group : GROUPS.values()) {
            group.disableBodies();
        }
        GROUPS.clear();
    }

    private static SableFrontierLaserGeometry.BasePolygon retract(
            final Vector3dc apex,
            final SableFrontierLaserGeometry.BasePolygon base,
            final float progress) {
        final List<Vector3d> points = base.points().stream()
                .map(point -> new Vector3d(point).lerp(apex, progress))
                .toList();
        return new SableFrontierLaserGeometry.BasePolygon(
                points,
                new Vector3d(base.center()).lerp(apex, progress)
        );
    }

    public record LaserEntry(Vector3dc apex, SableFrontierLaserGeometry.BasePolygon base) {
    }

    private static final class Group {
        private final ProjectorRevealPayload payload;
        private final Set<ClientSubLevel> resolved = new LinkedHashSet<>();
        private boolean retracting;
        private int retractTicks;
        private SableFrontierLaserGeometry.BasePolygon lastBase;
        private Vector3d lastApex;

        private Group(final ProjectorRevealPayload payload) {
            this.payload = payload;
        }

        private double worldDistance() {
            final Minecraft minecraft = Minecraft.getInstance();
            final long time = minecraft.level == null ? this.payload.startGameTime() : minecraft.level.getGameTime();
            final double duration = Math.max(1.0D, this.payload.endGameTime() - this.payload.startGameTime());
            final double progress = Math.max(0.0D, Math.min(1.0D,
                    (time - this.payload.startGameTime()) / duration));
            return this.payload.startDistance()
                    + (this.payload.endDistance() - this.payload.startDistance()) * progress;
        }

        private void applyPlane() {
            final Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return;
            }
            final SubLevelContainer container = SubLevelContainer.getContainer(minecraft.level);
            if (container == null) {
                return;
            }
            final double distance = this.worldDistance();
            for (final UUID id : this.payload.bodyIds()) {
                final SubLevel candidate = container.getSubLevel(id);
                if (!(candidate instanceof final ClientSubLevel subLevel) || subLevel.isRemoved()) {
                    continue;
                }
                this.resolved.add(subLevel);
                applyWorldPlane(subLevel, this.payload.normal(), distance);
            }
        }

        private void disableBodies() {
            for (final ClientSubLevel subLevel : this.resolved) {
                final SableFrontierData data = FrontierDataStore.get(subLevel);
                if (data != null) {
                    data.setEnabled(false);
                }
            }
            this.resolved.clear();
        }

        private @Nullable Vector3d apex() {
            final Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return null;
            }
            final Direction facing;
            try {
                facing = minecraft.level.getBlockState(this.payload.projectorPos())
                        .getValue(ProjectorBlock.FACING);
            } catch (final IllegalArgumentException exception) {
                return null;
            }
            final Vector3d point = new Vector3d(
                    this.payload.projectorPos().getX() + 0.5D + facing.getStepX() * 0.9D,
                    this.payload.projectorPos().getY() + 0.8D,
                    this.payload.projectorPos().getZ() + 0.5D + facing.getStepZ() * 0.9D
            );
            if (this.payload.projectorBodyId() == null) {
                return point;
            }
            final SubLevelContainer container = SubLevelContainer.getContainer(minecraft.level);
            final SubLevel subLevel = container == null
                    ? null
                    : container.getSubLevel(this.payload.projectorBodyId());
            return subLevel instanceof final ClientSubLevel clientSubLevel
                    ? clientSubLevel.renderPose().transformPosition(point)
                    : null;
        }

        private @Nullable SableFrontierLaserGeometry.BasePolygon currentBase() {
            final Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return null;
            }
            final SubLevelContainer container = SubLevelContainer.getContainer(minecraft.level);
            if (container == null) {
                return null;
            }
            final Vector3d normal = new Vector3d(this.payload.normal()).normalize();
            final double distance = this.worldDistance();
            final List<Vector3d> intersections = new ArrayList<>();
            for (final UUID id : this.payload.bodyIds()) {
                final SubLevel candidate = container.getSubLevel(id);
                if (candidate instanceof final ClientSubLevel subLevel && !subLevel.isRemoved()) {
                    includeIntersections(subLevel, normal, distance, intersections);
                }
            }
            return convexHull(intersections, normal, distance);
        }
    }

    private static void applyWorldPlane(final ClientSubLevel subLevel,
                                        final Vector3dc worldNormal,
                                        final double worldDistance) {
        final Vector3d origin = SableFrontierCoordinates.renderOrigin(subLevel, new Vector3d());
        final Pose3dc pose = subLevel.renderPose();
        final Vector3d worldOrigin = pose.transformPosition(new Vector3d(origin));
        final Vector3d lx = pose.transformPosition(new Vector3d(origin).add(1, 0, 0)).sub(worldOrigin);
        final Vector3d ly = pose.transformPosition(new Vector3d(origin).add(0, 1, 0)).sub(worldOrigin);
        final Vector3d lz = pose.transformPosition(new Vector3d(origin).add(0, 0, 1)).sub(worldOrigin);
        final Vector3d localNormal = new Vector3d(
                worldNormal.dot(lx),
                worldNormal.dot(ly),
                worldNormal.dot(lz)
        );
        final double scale = localNormal.length();
        if (scale < 1.0e-9D) {
            return;
        }
        localNormal.div(scale);
        final SableFrontierData data = FrontierDataStore.getOrCreate(subLevel);
        data.setEnabled(true);
        data.setNormal(new Vector3f((float) localNormal.x, (float) localNormal.y, (float) localNormal.z));
        data.setDistance((float) ((worldDistance - worldNormal.dot(worldOrigin)) / scale));
        data.setGradientWidth((float) (3.0D / scale));
    }

    private static void includeIntersections(final ClientSubLevel subLevel,
                                             final Vector3dc normal,
                                             final double distance,
                                             final List<Vector3d> out) {
        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        final Vector3d[] corners = new Vector3d[8];
        int index = 0;
        for (final double x : new double[] {bounds.minX(), bounds.maxX() + 1.0D}) {
            for (final double y : new double[] {bounds.minY(), bounds.maxY() + 1.0D}) {
                for (final double z : new double[] {bounds.minZ(), bounds.maxZ() + 1.0D}) {
                    corners[index++] = subLevel.renderPose().transformPosition(new Vector3d(x, y, z));
                }
            }
        }
        final int[][] edges = {
                {0, 1}, {0, 2}, {0, 4}, {1, 3}, {1, 5}, {2, 3},
                {2, 6}, {3, 7}, {4, 5}, {4, 6}, {5, 7}, {6, 7}
        };
        for (final int[] edge : edges) {
            final Vector3d a = corners[edge[0]];
            final Vector3d b = corners[edge[1]];
            final double da = normal.dot(a) - distance;
            final double db = normal.dot(b) - distance;
            if (Math.abs(da) < 1.0e-7D) {
                out.add(new Vector3d(a));
            }
            if (Math.abs(db) < 1.0e-7D) {
                out.add(new Vector3d(b));
            }
            if (da * db < 0.0D) {
                out.add(new Vector3d(a).lerp(b, da / (da - db)));
            }
        }
    }

    private static @Nullable SableFrontierLaserGeometry.BasePolygon convexHull(
            final List<Vector3d> points,
            final Vector3dc normal,
            final double distance) {
        if (points.size() < 3) {
            return null;
        }
        final Vector3d reference = Math.abs(normal.y()) < 0.9D
                ? new Vector3d(0, 1, 0)
                : new Vector3d(1, 0, 0);
        final Vector3d u = new Vector3d(normal).cross(reference).normalize();
        final Vector3d v = new Vector3d(normal).cross(u).normalize();
        final Vector3d planeOrigin = new Vector3d(normal).mul(distance);
        final List<Point2> projected = points.stream()
                .map(point -> {
                    final Vector3d delta = new Vector3d(point).sub(planeOrigin);
                    return new Point2(delta.dot(u), delta.dot(v), point);
                })
                .distinct()
                .sorted(Comparator.comparingDouble(Point2::x).thenComparingDouble(Point2::y))
                .toList();
        if (projected.size() < 3) {
            return null;
        }
        final List<Point2> lower = new ArrayList<>();
        for (final Point2 point : projected) {
            while (lower.size() >= 2 && cross(lower.get(lower.size() - 2), lower.get(lower.size() - 1), point) <= 1.0e-8D) {
                lower.remove(lower.size() - 1);
            }
            lower.add(point);
        }
        final List<Point2> upper = new ArrayList<>();
        for (int i = projected.size() - 1; i >= 0; i--) {
            final Point2 point = projected.get(i);
            while (upper.size() >= 2 && cross(upper.get(upper.size() - 2), upper.get(upper.size() - 1), point) <= 1.0e-8D) {
                upper.remove(upper.size() - 1);
            }
            upper.add(point);
        }
        lower.remove(lower.size() - 1);
        upper.remove(upper.size() - 1);
        lower.addAll(upper);
        if (lower.size() < 3) {
            return null;
        }
        final List<Vector3d> hull = lower.stream().map(point -> new Vector3d(point.world())).toList();
        final Vector3d center = new Vector3d();
        hull.forEach(center::add);
        center.div(hull.size());
        return new SableFrontierLaserGeometry.BasePolygon(hull, center);
    }

    private static double cross(final Point2 a, final Point2 b, final Point2 c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }

    private record Point2(double x, double y, Vector3dc world) {
        private Point2 {
            world = new Vector3d(world);
        }

        @Override
        public boolean equals(final Object object) {
            return object instanceof Point2 other
                    && Math.abs(this.x - other.x) < 1.0e-7D
                    && Math.abs(this.y - other.y) < 1.0e-7D;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }
}

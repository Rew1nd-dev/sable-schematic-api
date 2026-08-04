package dev.rew1nd.sableschematicapi.survival.projector;

import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import dev.rew1nd.sableschematicapi.survival.BlueprintPayloads;
import dev.rew1nd.sableschematicapi.survival.BlueprintPlacementPlan;
import dev.rew1nd.sableschematicapi.survival.SableSchematicApiBlocks;
import dev.rew1nd.sableschematicapi.survival.film.FilmBlueprintFreezer;
import dev.rew1nd.sableschematicapi.survival.film.FilmState;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class ProjectorAdmission implements AutoCloseable {
    private static final double SCREEN_GAP = 1.0e-3D;

    private final FilmState film;
    private final SableBlueprint blueprint;
    private final BlueprintPlacementPlan plan;
    private final ProjectorAttachment projector;
    private final ProjectorScreen screen;
    private final ProjectorScreen.Frame screenFrame;
    private final ProjectorTicketLease ticketLease;
    private final Vector3d sweepStart;
    private final Vector3d sweepEnd;
    private final List<Cursor> cursors;
    private final long totalChecks;
    private long checked;
    private int cursorIndex;
    private boolean collided;
    private boolean closed;

    private ProjectorAdmission(final FilmState film,
                               final SableBlueprint blueprint,
                               final BlueprintPlacementPlan plan,
                               final ProjectorAttachment projector,
                               final ProjectorScreen screen,
                               final ProjectorScreen.Frame screenFrame,
                               final ProjectorTicketLease ticketLease,
                               final Vector3dc sweepStart,
                               final Vector3dc sweepEnd,
                               final List<Cursor> cursors,
                               final long totalChecks) {
        this.film = film;
        this.blueprint = blueprint;
        this.plan = plan;
        this.projector = projector;
        this.screen = screen;
        this.screenFrame = screenFrame;
        this.ticketLease = ticketLease;
        this.sweepStart = new Vector3d(sweepStart);
        this.sweepEnd = new Vector3d(sweepEnd);
        this.cursors = cursors;
        this.totalChecks = totalChecks;
    }

    public static ProjectorAdmission begin(final ServerLevel level,
                                           final BlockPos projectorPos,
                                           @Nullable final Entity rayEntity,
                                           final FilmState film) throws ProjectorAdmissionException {
        final ProjectorAttachment projector = ProjectorAttachment.capture(level, projectorPos);
        final Vector3d lens = lensPosition(level, projector, projectorPos);
        final Vector3d forward = lensDirection(level, projector, projectorPos);
        final Vec3 rayStart = new Vec3(lens.x, lens.y, lens.z);
        final Vec3 rayEnd = rayStart.add(
                forward.x * ProjectorConfig.MAX_RAY_DISTANCE.get(),
                forward.y * ProjectorConfig.MAX_RAY_DISTANCE.get(),
                forward.z * ProjectorConfig.MAX_RAY_DISTANCE.get()
        );
        final BlockHitResult hit = level.clip(new ClipContext(
                rayStart, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rayEntity
        ));
        if (hit.getType() != HitResult.Type.BLOCK) {
            throw new ProjectorAdmissionException("sable_schematic_api.projector.status.no_screen");
        }
        final BlockState hitState = level.getBlockState(hit.getBlockPos());
        if (!hitState.is(SableSchematicApiBlocks.PROJECTOR_SCREEN.get())
                && !hitState.is(SableSchematicApiBlocks.PROJECTOR_SCREEN_LOCATOR.get())) {
            throw new ProjectorAdmissionException("sable_schematic_api.projector.status.no_screen");
        }
        final ProjectorScreen screen = ProjectorScreen.discover(
                level, hit.getBlockPos(), hit.getDirection());
        final ProjectorScreen.Frame frame = screen.frame(level);

        final SableBlueprint blueprint;
        try {
            final FilmBlueprintFreezer.ResolvedFilmBlueprint resolved =
                    FilmBlueprintFreezer.resolve(level.getServer(), film);
            blueprint = BlueprintPayloads.readCompressed(resolved.compressedBlueprint());
        } catch (final IOException | RuntimeException exception) {
            throw new ProjectorAdmissionException(
                    "sable_schematic_api.projector.status.invalid_blueprint", exception);
        }
        if (blueprint.subLevels().isEmpty()) {
            throw new ProjectorAdmissionException("sable_schematic_api.projector.status.no_bodies");
        }

        final Quaterniond orientation = orientation(frame);
        orientation.mul(new Quaterniond().rotateY(Math.toRadians(film.rotation().degrees())));
        final Pose3d provisionalBasis = new Pose3d(
                new Vector3d(frame.center()), orientation, new Vector3d(), new Vector3d(1, 1, 1)
        );
        final BoundingBox3d basisBounds = BlueprintPlacementPlan.basisBounds(blueprint);
        final ProjectionFrame projection = ProjectionFrame.forNormal(frame.normal());
        final Extrema extrema = extrema(basisBounds, provisionalBasis, projection);
        final double projectedLength = extrema.maxZ() - extrema.minZ();
        final Vector3d targetCenter = new Vector3d(frame.surface())
                .fma(SCREEN_GAP + projectedLength * 0.5D, projection.normal());
        final Vector3d offset = new Vector3d(projection.x())
                .mul(targetCenter.dot(projection.x()) - extrema.centerX())
                .add(new Vector3d(projection.y())
                        .mul(targetCenter.dot(projection.y()) - extrema.centerY()))
                .add(new Vector3d(projection.normal())
                        .mul(targetCenter.dot(projection.normal()) - extrema.centerZ()));
        final Pose3d finalBasis = new Pose3d(
                new Vector3d(frame.center()).add(offset),
                orientation,
                new Vector3d(),
                new Vector3d(1, 1, 1)
        );
        final BlueprintPlacementPlan plan = BlueprintPlacementPlan.forPose(blueprint, finalBasis);
        final Extrema placedExtrema = extrema(basisBounds, finalBasis, projection);
        final Vector3d sweepStart = pointOnAxis(targetCenter, projection, placedExtrema.minZ());
        final Vector3d sweepEnd = pointOnAxis(targetCenter, projection, placedExtrema.maxZ());

        if (!insideCone(level, projector, projectorPos, sweepStart, sweepEnd)) {
            throw new ProjectorAdmissionException("sable_schematic_api.projector.status.outside_cone");
        }
        ProjectorObbAdmission.check(
                level, blueprint, plan, screen.locator().bodyId().orElse(null));

        final List<Cursor> cursors = new ArrayList<>();
        long total = 0;
        for (final SableBlueprint.SubLevelData entry : blueprint.subLevels()) {
            final BoundingBox3i bounds = entry.localBounds();
            if (bounds == BoundingBox3i.EMPTY || bounds.volume() <= 0) {
                continue;
            }
            final long volume = (long) (bounds.maxX() - bounds.minX() + 1)
                    * (bounds.maxY() - bounds.minY() + 1)
                    * (bounds.maxZ() - bounds.minZ() + 1);
            total = Math.addExact(total, volume);
            cursors.add(new Cursor(
                    entry, bounds, ProjectorBodyGeometry.anchorBounds(level, entry)));
        }
        final ProjectorTicketLease lease = new ProjectorTicketLease(level);
        try {
            if (projector.bodyId().isPresent()) {
                lease.retain(projector.bodyId().orElseThrow());
            }
            if (screen.locator().bodyId().isPresent()) {
                lease.retain(screen.locator().bodyId().orElseThrow());
            }
        } catch (final ProjectorAdmissionException exception) {
            lease.releaseAll();
            throw exception;
        }
        return new ProjectorAdmission(film, blueprint, plan, projector, screen, frame, lease,
                sweepStart, sweepEnd, cursors, total);
    }

    public int scanTerrain(final ServerLevel level, final int budget) {
        int used = 0;
        while (used < budget && this.cursorIndex < this.cursors.size() && !this.collided && !this.closed) {
            final Cursor cursor = this.cursors.get(this.cursorIndex);
            final Vector3d worldPoint = cursor.nextWorldPoint(this.plan);
            used++;
            this.checked++;
            if (collidesTerrain(level, worldPoint)) {
                this.collided = true;
                break;
            }
            if (cursor.done()) {
                this.cursorIndex++;
            }
        }
        return used;
    }

    public boolean validForPlacement(final ServerLevel level,
                                     final BlockPos projectorPos) {
        return !this.closed
                && this.projector.valid(level)
                && this.screen.locator().valid(level)
                && this.projector.withinInitialThresholds(level)
                && this.screen.locator().withinInitialThresholds(level)
                && insideCone(level, this.projector, projectorPos, this.sweepStart, this.sweepEnd);
    }

    public FilmState film() {
        return this.film;
    }

    public SableBlueprint blueprint() {
        return this.blueprint;
    }

    public BlueprintPlacementPlan plan() {
        return this.plan;
    }

    public ProjectorAttachment projector() {
        return this.projector;
    }

    public ProjectorScreen screen() {
        return this.screen;
    }

    public ProjectorScreen.Frame screenFrame() {
        return this.screenFrame;
    }

    ProjectorTicketLease ticketLease() {
        return this.ticketLease;
    }

    public Vector3d sweepStart() {
        return new Vector3d(this.sweepStart);
    }

    public Vector3d sweepEnd() {
        return new Vector3d(this.sweepEnd);
    }

    public long totalChecks() {
        return this.totalChecks;
    }

    public long checked() {
        return this.checked;
    }

    public boolean collided() {
        return this.collided;
    }

    public boolean complete() {
        return !this.collided && this.checked >= this.totalChecks;
    }

    @Override
    public void close() {
        this.closed = true;
        this.ticketLease.releaseAll();
    }

    private static boolean collidesTerrain(final ServerLevel level, final Vector3dc point) {
        final BlockPos pos = BlockPos.containing(point.x(), point.y(), point.z());
        final BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        final Vec3 local = new Vec3(point.x() - pos.getX(), point.y() - pos.getY(), point.z() - pos.getZ());
        for (final AABB box : state.getCollisionShape(level, pos).toAabbs()) {
            if (box.contains(local)) {
                return true;
            }
        }
        return false;
    }

    static Vector3d lensPosition(final ServerLevel level,
                                 final ProjectorAttachment attachment,
                                 final BlockPos projectorPos) {
        final Direction facing = level.getBlockState(projectorPos).getValue(ProjectorBlock.FACING);
        return attachment.worldPoint(level, new Vector3d(
                projectorPos.getX() + 0.5D + facing.getStepX() * 0.9D,
                projectorPos.getY() + 0.8D,
                projectorPos.getZ() + 0.5D + facing.getStepZ() * 0.9D
        ));
    }

    static Vector3d lensDirection(final ServerLevel level,
                                  final ProjectorAttachment attachment,
                                  final BlockPos projectorPos) {
        final Direction facing = level.getBlockState(projectorPos).getValue(ProjectorBlock.FACING);
        return attachment.worldDirection(level,
                new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ()));
    }

    private static boolean insideCone(final ServerLevel level,
                                      final ProjectorAttachment attachment,
                                      final BlockPos projectorPos,
                                      final Vector3dc start,
                                      final Vector3dc end) {
        final Vector3d lens = lensPosition(level, attachment, projectorPos);
        final Vector3d forward = lensDirection(level, attachment, projectorPos);
        final double cosine = Math.cos(Math.toRadians(ProjectorConfig.HALF_CONE_ANGLE_DEGREES.get()));
        return insideCone(lens, forward, start, cosine) && insideCone(lens, forward, end, cosine);
    }

    private static boolean insideCone(final Vector3dc lens,
                                      final Vector3dc forward,
                                      final Vector3dc point,
                                      final double cosine) {
        final Vector3d delta = new Vector3d(point).sub(lens);
        final double length = delta.length();
        return length > 1.0e-6D && delta.dot(forward) > 0.0D
                && delta.dot(forward) / length >= cosine;
    }

    private static Quaterniond orientation(final ProjectorScreen.Frame frame) {
        final Matrix3d matrix = new Matrix3d();
        matrix.setColumn(0, frame.x());
        matrix.setColumn(1, frame.y());
        matrix.setColumn(2, frame.z());
        return new Quaterniond().setFromNormalized(matrix);
    }

    private static Extrema extrema(final BoundingBox3d bounds,
                                   final Pose3d basisPose,
                                   final ProjectionFrame frame) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        final double[] xs = {bounds.minX(), bounds.maxX()};
        final double[] ys = {bounds.minY(), bounds.maxY()};
        final double[] zs = {bounds.minZ(), bounds.maxZ()};
        for (final double x : xs) for (final double y : ys) for (final double z : zs) {
            final Vector3d point = basisPose.transformPosition(new Vector3d(x, y, z));
            final double px = point.dot(frame.x());
            final double py = point.dot(frame.y());
            final double pz = point.dot(frame.normal());
            minX = Math.min(minX, px);
            minY = Math.min(minY, py);
            minZ = Math.min(minZ, pz);
            maxX = Math.max(maxX, px);
            maxY = Math.max(maxY, py);
            maxZ = Math.max(maxZ, pz);
        }
        return new Extrema(minX, maxX, minY, maxY, minZ, maxZ);
    }

    private static Vector3d pointOnAxis(final Vector3dc center,
                                        final ProjectionFrame frame,
                                        final double z) {
        return new Vector3d(frame.x()).mul(center.dot(frame.x()))
                .add(new Vector3d(frame.y()).mul(center.dot(frame.y())))
                .add(new Vector3d(frame.normal()).mul(z));
    }

    private record Extrema(double minX, double maxX,
                           double minY, double maxY,
                           double minZ, double maxZ) {
        private double centerX() {
            return (this.minX + this.maxX) * 0.5D;
        }

        private double centerY() {
            return (this.minY + this.maxY) * 0.5D;
        }

        private double centerZ() {
            return (this.minZ + this.maxZ) * 0.5D;
        }
    }

    private record ProjectionFrame(Vector3d x, Vector3d y, Vector3d normal) {
        private ProjectionFrame {
            x = new Vector3d(x);
            y = new Vector3d(y);
            normal = new Vector3d(normal);
        }

        private static ProjectionFrame forNormal(final Vector3dc normal) {
            final Vector3d z = new Vector3d(normal).normalize();
            final Vector3d seed = Math.abs(z.y) < 0.999D
                    ? new Vector3d(0, 1, 0)
                    : new Vector3d(0, 0, -1);
            final Vector3d x = seed.cross(z, new Vector3d()).normalize();
            final Vector3d y = z.cross(x, new Vector3d()).normalize();
            return new ProjectionFrame(x, y, z);
        }
    }

    private static final class Cursor {
        private final SableBlueprint.SubLevelData entry;
        private final BoundingBox3i bounds;
        private final BoundingBox3d anchorBounds;
        private int x;
        private int y;
        private int z;
        private boolean done;

        private Cursor(final SableBlueprint.SubLevelData entry,
                       final BoundingBox3i bounds,
                       final BoundingBox3d anchorBounds) {
            this.entry = entry;
            this.bounds = bounds;
            this.anchorBounds = new BoundingBox3d(anchorBounds);
            this.x = bounds.minX();
            this.y = bounds.minY();
            this.z = bounds.minZ();
        }

        private Vector3d nextWorldPoint(final BlueprintPlacementPlan plan) {
            final Vector3d point = new Vector3d(
                    this.anchorBounds.minX() + (this.x - this.bounds.minX()) + 0.5D,
                    this.anchorBounds.minY() + (this.y - this.bounds.minY()) + 0.5D,
                    this.anchorBounds.minZ() + (this.z - this.bounds.minZ()) + 0.5D
            );
            plan.pose(this.entry).transformPosition(point);
            this.advance();
            return point;
        }

        private void advance() {
            if (++this.x <= this.bounds.maxX()) {
                return;
            }
            this.x = this.bounds.minX();
            if (++this.y <= this.bounds.maxY()) {
                return;
            }
            this.y = this.bounds.minY();
            if (++this.z <= this.bounds.maxZ()) {
                return;
            }
            this.done = true;
        }

        private boolean done() {
            return this.done;
        }
    }
}

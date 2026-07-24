package dev.rew1nd.sableschematicapi.survival.projector;

import dev.rew1nd.sableschematicapi.blueprint.SableBlueprintPlacer;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ProjectorPlacementHandle {
    private final ProjectorAdmission admission;
    private final BlockPos projectorPos;
    private final Set<UUID> placedBodyIds;
    private final List<PhysicsConstraintHandle> constraints;
    private final int durationTicks;
    private boolean released;

    private ProjectorPlacementHandle(final ProjectorAdmission admission,
                                     final BlockPos projectorPos,
                                     final Set<UUID> placedBodyIds,
                                     final List<PhysicsConstraintHandle> constraints,
                                     final int durationTicks) {
        this.admission = admission;
        this.projectorPos = projectorPos.immutable();
        this.placedBodyIds = Set.copyOf(placedBodyIds);
        this.constraints = List.copyOf(constraints);
        this.durationTicks = durationTicks;
    }

    public static ProjectorPlacementHandle place(final ServerLevel level,
                                                 final BlockPos projectorPos,
                                                 final ProjectorAdmission admission)
            throws ProjectorAdmissionException {
        if (!admission.validForPlacement(level, projectorPos)) {
            throw new ProjectorAdmissionException("sable_schematic_api.projector.status.anchor_moved");
        }
        ProjectorObbAdmission.check(level, admission.blueprint(), admission.plan());
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            throw new ProjectorAdmissionException("sable_schematic_api.projector.status.no_container");
        }

        final SableBlueprintPlacer.Result result =
                SableBlueprintPlacer.place(level, admission.blueprint(), admission.plan());
        if (result.placedSubLevels() < 1) {
            throw new ProjectorAdmissionException("sable_schematic_api.projector.status.no_bodies");
        }
        final Set<UUID> placed = new LinkedHashSet<>(result.subLevelUuidMap().values());
        if (placed.isEmpty()) {
            throw new ProjectorAdmissionException("sable_schematic_api.projector.status.no_bodies");
        }
        final List<PhysicsConstraintHandle> constraints = new ArrayList<>();
        try {
            for (final UUID id : placed) {
                final SubLevel candidate = container.getSubLevel(id);
                if (!(candidate instanceof final ServerSubLevel subLevel)) {
                    throw new IllegalStateException("Placed body is not loaded: " + id);
                }
                if (!subLevel.getMassTracker().isInvalid()) {
                    container.physicsSystem().getPipeline().resetVelocity(subLevel);
                    final PhysicsConstraintHandle constraint =
                            container.physicsSystem().getPipeline().addConstraint(
                                    null,
                                    subLevel,
                                    new FixedConstraintConfiguration(
                                            subLevel.logicalPose().position(),
                                            subLevel.logicalPose().rotationPoint(),
                                            subLevel.logicalPose().orientation()
                                    )
                            );
                    if (constraint == null) {
                        throw new IllegalStateException("Could not fix placed body " + id);
                    }
                    constraints.add(constraint);
                }
                admission.ticketLease().retain(id);
            }
        } catch (final RuntimeException | ProjectorAdmissionException failure) {
            for (final PhysicsConstraintHandle constraint : constraints) {
                constraint.remove();
            }
            admission.ticketLease().releaseAll();
            rollback(container, placed);
            throw new ProjectorAdmissionException(
                    "sable_schematic_api.projector.status.protection_failed", failure);
        }

        final double distance = admission.sweepStart().distance(admission.sweepEnd()) + 6.0D;
        final int duration = Math.max(1, (int) Math.ceil(
                distance / ProjectorConfig.FRONTIER_SPEED.get() * 20.0D
        ));
        return new ProjectorPlacementHandle(admission, projectorPos, placed, constraints, duration);
    }

    public Set<UUID> placedBodyIds() {
        return this.placedBodyIds;
    }

    public int durationTicks() {
        return this.durationTicks;
    }

    public ProjectorAdmission admission() {
        return this.admission;
    }

    public boolean valid(final ServerLevel level) {
        return !this.released
                && this.admission.validForPlacement(level, this.projectorPos);
    }

    public void release(final ServerLevel level) {
        if (this.released) {
            return;
        }
        this.released = true;
        for (final PhysicsConstraintHandle constraint : this.constraints) {
            constraint.remove();
        }
        this.admission.ticketLease().releaseAll();
    }

    public void rollback(final ServerLevel level) {
        this.release(level);
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null) {
            rollback(container, this.placedBodyIds);
        }
    }

    private static void rollback(final ServerSubLevelContainer container, final Set<UUID> placed) {
        for (final UUID id : placed) {
            final SubLevel subLevel = container.getSubLevel(id);
            if (subLevel instanceof ServerSubLevel) {
                container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
            }
        }
    }
}

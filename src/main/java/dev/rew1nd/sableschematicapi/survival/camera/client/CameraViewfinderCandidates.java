package dev.rew1nd.sableschematicapi.survival.camera.client;

import dev.rew1nd.sableschematicapi.survival.camera.CameraState;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Client-side prediction for the camera's fully-contained viewfinder selection. */
final class CameraViewfinderCandidates {
    private static final double ASPECT_RATIO = 16.0D / 9.0D;
    private static final double PREVIEW_RANGE = 128.0D;

    private CameraViewfinderCandidates() {
    }

    static List<Candidate> find(final Player player, final int requestedFov) {
        final SubLevelContainer container = SubLevelContainer.getContainer(player.level());
        if (container == null) {
            return List.of();
        }

        final Vec3 eye = player.getEyePosition();
        final Vec3 forward = player.getLookAngle().normalize();
        final List<Candidate> candidates = new ArrayList<>();
        for (final SubLevel body : container.getAllSubLevels()) {
            if (body.isRemoved()) {
                continue;
            }
            final AABB bounds = toAabb(body.boundingBox());
            if (isWithinRange(eye, bounds) && isFullyInsideView(eye, forward, requestedFov, bounds)) {
                candidates.add(new Candidate(body.getUniqueId(), bounds));
            }
        }
        return List.copyOf(candidates);
    }

    private static boolean isWithinRange(final Vec3 camera, final AABB box) {
        return camera.distanceToSqr(box.getCenter()) <= PREVIEW_RANGE * PREVIEW_RANGE;
    }

    private static boolean isFullyInsideView(final Vec3 camera,
                                             final Vec3 forward,
                                             final int requestedFov,
                                             final AABB box) {
        final Vec3 worldUp = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = forward.cross(worldUp);
        right = right.lengthSqr() < 1.0E-8D ? new Vec3(1.0D, 0.0D, 0.0D) : right.normalize();
        final Vec3 up = right.cross(forward).normalize();
        final double verticalTan = Math.tan(Math.toRadians(CameraState.clampFov(requestedFov)) * 0.5D);
        final double horizontalTan = verticalTan * ASPECT_RATIO;
        for (final double x : new double[]{box.minX, box.maxX}) {
            for (final double y : new double[]{box.minY, box.maxY}) {
                for (final double z : new double[]{box.minZ, box.maxZ}) {
                    final Vec3 local = new Vec3(x, y, z).subtract(camera);
                    final double depth = local.dot(forward);
                    if (depth <= 0.0D
                            || Math.abs(local.dot(right)) > depth * horizontalTan
                            || Math.abs(local.dot(up)) > depth * verticalTan) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static AABB toAabb(final BoundingBox3dc box) {
        return new AABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    record Candidate(UUID id, AABB bounds) {
    }
}

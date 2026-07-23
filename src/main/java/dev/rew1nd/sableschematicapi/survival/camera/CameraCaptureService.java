package dev.rew1nd.sableschematicapi.survival.camera;

import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprintExporter;
import dev.rew1nd.sableschematicapi.blueprint.tool.BlueprintToolService;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Non-persistent server cache for camera captures. There is at most one
 * capture per player, so a server restart or a new capture naturally frees
 * all retained blueprint bytes.
 */
public final class CameraCaptureService {
    private static final double ASPECT_RATIO = 16.0D / 9.0D;
    private static final Map<UUID, Capture> CAPTURES = new ConcurrentHashMap<>();

    private CameraCaptureService() {
    }

    public static Result capture(final ServerPlayer player, final int requestedFov) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(player.serverLevel());
        if (container == null) {
            return Result.failure("no_sublevel_container");
        }

        final int fov = CameraState.clampFov(requestedFov);
        final double range = CameraConfig.MAX_CAPTURE_RANGE.get();
        final LinkedHashSet<UUID> selected = new LinkedHashSet<>();
        for (final ServerSubLevel body : container.getAllSubLevels()) {
            if (body.isRemoved() || !isWithinRange(player.getEyePosition(), body.boundingBox(), range)) {
                continue;
            }
            if (isFullyInsideView(player.getEyePosition(), player.getLookAngle(), fov, body.boundingBox())) {
                selected.add(body.getUniqueId());
            }
        }

        if (selected.isEmpty()) {
            return Result.failure("no_candidates");
        }
        if (selected.size() > CameraConfig.MAX_CAPTURED_BODIES.get()) {
            return Result.failure("too_many_bodies");
        }

        final SableBlueprint blueprint = SableBlueprintExporter.exportSelected(player.serverLevel(), player.position(), selected);
        if (blueprint.isEmpty()) {
            return Result.failure("empty_capture");
        }

        final byte[] data;
        try {
            data = BlueprintToolService.writeToBytes(blueprint);
        } catch (final IOException e) {
            return Result.failure("encode_failed");
        }
        if (data.length > CameraConfig.MAX_BLUEPRINT_BYTES.get()) {
            return Result.failure("too_large");
        }

        final UUID id = UUID.randomUUID();
        final UUID owner = player.getUUID();
        CAPTURES.put(owner, new Capture(id, owner, data, Set.copyOf(selected)));
        return Result.success(id, selected, data.length);
    }

    public static Optional<Capture> get(final UUID owner, final UUID captureId) {
        if (owner == null || captureId == null) {
            return Optional.empty();
        }
        final Capture capture = CAPTURES.get(owner);
        return capture != null && capture.id().equals(captureId) ? Optional.of(capture) : Optional.empty();
    }

    public static void discard(final UUID owner, final UUID captureId) {
        if (owner == null || captureId == null) {
            return;
        }
        CAPTURES.computeIfPresent(owner, (ignored, capture) -> capture.id().equals(captureId) ? null : capture);
    }

    public static void onServerStopped(final ServerStoppedEvent event) {
        CAPTURES.clear();
    }

    private static boolean isWithinRange(final Vec3 camera, final BoundingBox3dc box, final double range) {
        final double x = (box.minX() + box.maxX()) * 0.5D;
        final double y = (box.minY() + box.maxY()) * 0.5D;
        final double z = (box.minZ() + box.maxZ()) * 0.5D;
        return camera.distanceToSqr(x, y, z) <= range * range;
    }

    private static boolean isFullyInsideView(final Vec3 camera,
                                             final Vec3 rawForward,
                                             final int fov,
                                             final BoundingBox3dc box) {
        final Vec3 forward = rawForward.normalize();
        final Vec3 worldUp = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = forward.cross(worldUp);
        if (right.lengthSqr() < 1.0E-8D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        final Vec3 up = right.cross(forward).normalize();
        final double verticalTan = Math.tan(Math.toRadians(fov) * 0.5D);
        final double horizontalTan = verticalTan * ASPECT_RATIO;
        final double[] xs = {box.minX(), box.maxX()};
        final double[] ys = {box.minY(), box.maxY()};
        final double[] zs = {box.minZ(), box.maxZ()};
        for (final double x : xs) {
            for (final double y : ys) {
                for (final double z : zs) {
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

    public record Capture(UUID id, UUID owner, byte[] data, java.util.Set<UUID> bodyIds) {
        public Capture {
            data = data.clone();
            bodyIds = Set.copyOf(bodyIds);
        }

        @Override
        public byte[] data() {
            return this.data.clone();
        }
    }

    public record Result(boolean success, String reason, UUID captureId, java.util.Set<UUID> bodyIds, int byteSize) {
        static Result success(final UUID id, final java.util.Set<UUID> bodies, final int byteSize) {
            return new Result(true, "ok", id, Set.copyOf(bodies), byteSize);
        }

        static Result failure(final String reason) {
            return new Result(false, reason, null, Set.of(), 0);
        }
    }
}

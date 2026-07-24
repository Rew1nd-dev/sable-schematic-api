package dev.rew1nd.sableschematicapi.survival.projector;

import dev.rew1nd.sableschematicapi.network.ProjectorRevealPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3d;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Shared-world-plane reveal registry. The packet-backed client projection is
 * added independently from the server placement transaction.
 */
public final class ProjectorRevealGroups {
    private static final Map<ProjectorPlacementHandle, Active> BY_HANDLE = new IdentityHashMap<>();
    private static final Map<UUID, Active> ACTIVE = new LinkedHashMap<>();

    private ProjectorRevealGroups() {
    }

    public static void start(final ServerLevel level,
                             final BlockPos projectorPos,
                             final ProjectorPlacementHandle handle,
                             final long endGameTime) {
        final ProjectorAdmission admission = handle.admission();
        final Vector3d normal = admission.screenFrame().normal().normalize();
        final Active active = new Active(
                UUID.randomUUID(),
                level,
                handle,
                projectorPos.immutable(),
                normal,
                admission.sweepStart().dot(normal) - 3.0D,
                admission.sweepEnd().dot(normal) + 3.0D,
                level.getGameTime(),
                endGameTime
        );
        BY_HANDLE.put(handle, active);
        ACTIVE.put(active.id, active);
        PacketDistributor.sendToPlayersInDimension(level, active.payload(ProjectorRevealPayload.START, false));
    }

    public static void stop(final ServerLevel level,
                            final ProjectorPlacementHandle handle,
                            final boolean immediate) {
        final Active active = BY_HANDLE.remove(handle);
        if (active == null) {
            return;
        }
        ACTIVE.remove(active.id);
        PacketDistributor.sendToPlayersInDimension(level, active.payload(ProjectorRevealPayload.STOP, immediate));
    }

    public static void onPlayerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof final ServerPlayer player) {
            sync(player);
        }
    }

    public static void onPlayerChangedDimension(final PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof final ServerPlayer player) {
            sync(player);
        }
    }

    public static void onServerStopped(final ServerStoppedEvent event) {
        BY_HANDLE.clear();
        ACTIVE.clear();
    }

    private static void sync(final ServerPlayer player) {
        for (final Active active : ACTIVE.values()) {
            if (active.level.dimension() == player.level().dimension()
                    && active.endGameTime > active.level.getGameTime()) {
                PacketDistributor.sendToPlayer(player, active.payload(ProjectorRevealPayload.START, false));
            }
        }
    }

    private record Active(UUID id,
                          ServerLevel level,
                          ProjectorPlacementHandle handle,
                          BlockPos projectorPos,
                          Vector3d normal,
                          double startDistance,
                          double endDistance,
                          long startGameTime,
                          long endGameTime) {
        private ProjectorRevealPayload payload(final byte action, final boolean immediate) {
            return new ProjectorRevealPayload(
                    action,
                    this.id,
                    this.handle.placedBodyIds().stream().toList(),
                    this.normal,
                    this.startDistance,
                    this.endDistance,
                    this.startGameTime,
                    this.endGameTime,
                    this.handle.admission().projector().bodyId().orElse(null),
                    this.projectorPos,
                    immediate
            );
        }
    }
}

package dev.rew1nd.sableschematicapi.sublevel;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Comparator;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class PendingSubLevelLoadTeleportService {
    private static final TicketType<ChunkPos> TICKET_TYPE = TicketType.create(
            "sable_schematic_api_pending_sublevel",
            Comparator.comparingLong(ChunkPos::toLong)
    );
    private static final int TICKET_DISTANCE = 1;
    private static final int MAX_TICKETED_CHUNKS = 1024;
    private static final long TIMEOUT_TICKS = 400L;
    private static final Map<UUID, PendingRequest> REQUESTS = new LinkedHashMap<>();

    private PendingSubLevelLoadTeleportService() {
    }

    public static SubLevelOperationResult requestTeleportSubLevelToPlayer(final ServerPlayer player,
                                                                          final SubLevelRecord target) {
        final MinecraftServer server = player.getServer();
        if (!target.dimension().equals(player.serverLevel().dimension())) {
            return SubLevelOperationResult.failure("Bringing sub-levels across dimensions is not supported yet.");
        }

        final ServerLevel level = player.serverLevel();
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return SubLevelOperationResult.failure("No Sable sub-level container is available for this level.");
        }

        final SubLevelGroupRecord group = SubLevelGroupService.findGroup(server, target.uuid())
                .orElse(new SubLevelGroupRecord(target.uuid(), List.of(target)));
        for (final SubLevelRecord member : group.members()) {
            if (!member.dimension().equals(player.serverLevel().dimension())) {
                return SubLevelOperationResult.failure("Bringing sub-level groups across dimensions is not supported yet.");
            }
        }

        final Set<UUID> targetIds = new LinkedHashSet<>(group.memberIds());
        final Vec3 destination = LoadedSubLevelTeleportService.destinationNear(player);
        if (allLoaded(container, targetIds)) {
            return LoadedSubLevelTeleportService.teleportSubLevelGroupTo(player, targetIds, target.uuid(), destination);
        }

        for (final SubLevelRecord member : group.members()) {
            if (!member.loaded() && member.pointer() == null) {
                return SubLevelOperationResult.failure("Stored sub-level " + member.displayName() + " has no storage pointer.");
            }
        }

        final RequiredChunks requiredChunks = collectRequiredChunks(level, group.members());
        if (requiredChunks.ticketedChunks().isEmpty()) {
            return SubLevelOperationResult.failure("No source chunks were found for sub-level group anchored at " + target.displayName() + ".");
        }

        if (requiredChunks.ticketedChunks().size() > MAX_TICKETED_CHUNKS) {
            return SubLevelOperationResult.failure("Sub-level group anchored at " + target.displayName() + " requires too many source chunks to load safely.");
        }

        cancelForPlayer(server, player.getUUID());
        addTickets(level, requiredChunks.ticketedChunks());
        primeVisibleHoldingChunks(level, container, requiredChunks.holdingChunks(), new LinkedHashSet<>());

        final UUID requestId = UUID.randomUUID();
        REQUESTS.put(requestId, new PendingRequest(
                requestId,
                player.getUUID(),
                target.dimension(),
                target.uuid(),
                targetIds,
                destination,
                level.getGameTime(),
                requiredChunks.ticketedChunks(),
                requiredChunks.holdingChunks(),
                new LinkedHashSet<>()
        ));

        return SubLevelOperationResult.success("Queued sub-level group anchored at " + target.displayName() + " for loading.", group.members().size());
    }

    public static void tick(final ServerTickEvent.Post event) {
        if (REQUESTS.isEmpty()) {
            return;
        }

        final MinecraftServer server = event.getServer();
        final Iterator<PendingRequest> iterator = REQUESTS.values().iterator();
        while (iterator.hasNext()) {
            final PendingRequest request = iterator.next();
            final TickResult result = tickRequest(server, request);
            if (!result.done()) {
                continue;
            }

            iterator.remove();
            if (result.level() != null) {
                releaseTickets(result.level(), request.ticketedChunks());
            }
        }
    }

    public static void cancelForPlayer(final MinecraftServer server, final UUID playerId) {
        final Iterator<PendingRequest> iterator = REQUESTS.values().iterator();
        while (iterator.hasNext()) {
            final PendingRequest request = iterator.next();
            if (!request.playerId().equals(playerId)) {
                continue;
            }

            iterator.remove();
            final ServerLevel level = server.getLevel(request.dimension());
            if (level != null) {
                releaseTickets(level, request.ticketedChunks());
            }
        }
    }

    private static TickResult tickRequest(final MinecraftServer server, final PendingRequest request) {
        final ServerLevel level = server.getLevel(request.dimension());
        if (level == null) {
            return new TickResult(true, null);
        }

        final ServerPlayer player = server.getPlayerList().getPlayer(request.playerId());
        if (player == null || player.isRemoved()) {
            return new TickResult(true, level);
        }

        if (!player.serverLevel().dimension().equals(request.dimension())) {
            notify(player, SubLevelOperationResult.failure("Pending sub-level teleport was cancelled because you changed dimensions."));
            return new TickResult(true, level);
        }

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            notify(player, SubLevelOperationResult.failure("Pending sub-level teleport failed because the Sable container is unavailable."));
            return new TickResult(true, level);
        }

        primeVisibleHoldingChunks(level, container, request.holdingChunks(), request.primedHoldingChunks());

        if (allLoaded(container, request.subLevelIds())) {
            final SubLevelOperationResult result = LoadedSubLevelTeleportService.teleportSubLevelGroupTo(
                    player,
                    request.subLevelIds(),
                    request.anchorSubLevelId(),
                    request.destination()
            );
            notify(player, result);
            return new TickResult(true, level);
        }

        if (level.getGameTime() - request.createdGameTime() > TIMEOUT_TICKS) {
            notify(player, SubLevelOperationResult.failure("Timed out while waiting for the stored sub-level group to load."));
            SableSchematicApi.LOGGER.warn("Timed out waiting for stored Sable sub-level group {} to load for player {}", request.subLevelIds(), request.playerId());
            return new TickResult(true, level);
        }

        return new TickResult(false, level);
    }

    private static RequiredChunks collectRequiredChunks(final ServerLevel level,
                                                        final Collection<SubLevelRecord> targets) {
        final Map<UUID, SubLevelRecord> recordsById = SubLevelDirectoryService.list(level).stream()
                .collect(Collectors.toMap(SubLevelRecord::uuid, Function.identity(), (first, second) -> first));
        final Set<ChunkPos> ticketedChunks = new LinkedHashSet<>();
        final Set<ChunkPos> holdingChunks = new LinkedHashSet<>();

        for (final SubLevelRecord target : targets) {
            addRecordChunks(ticketedChunks, holdingChunks, target);
            for (final UUID dependency : target.dependencies()) {
                final SubLevelRecord dependencyRecord = recordsById.get(dependency);
                if (dependencyRecord != null) {
                    addRecordChunks(ticketedChunks, holdingChunks, dependencyRecord);
                }
            }
        }

        return new RequiredChunks(ticketedChunks, holdingChunks);
    }

    private static boolean allLoaded(final ServerSubLevelContainer container, final Set<UUID> targetIds) {
        for (final UUID targetId : targetIds) {
            final SubLevel subLevel = container.getSubLevel(targetId);
            if (!(subLevel instanceof final ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved()) {
                return false;
            }
        }
        return true;
    }

    private static void addRecordChunks(final Set<ChunkPos> ticketedChunks,
                                        final Set<ChunkPos> holdingChunks,
                                        final SubLevelRecord record) {
        if (record.pointer() != null) {
            ticketedChunks.add(record.pointer().chunkPos());
            holdingChunks.add(record.pointer().chunkPos());
        }

        addBoundsChunks(ticketedChunks, record.bounds());
        addBoundsChunks(holdingChunks, record.bounds());
    }

    private static void addBoundsChunks(final Set<ChunkPos> chunks, final BoundingBox3dc bounds) {
        final int minX = Mth.floor(bounds.minX() - 1.0) >> 4;
        final int minZ = Mth.floor(bounds.minZ() - 1.0) >> 4;
        final int maxX = Mth.floor(bounds.maxX() + 1.0) >> 4;
        final int maxZ = Mth.floor(bounds.maxZ() + 1.0) >> 4;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
    }

    private static void addTickets(final ServerLevel level, final Set<ChunkPos> chunks) {
        for (final ChunkPos chunk : chunks) {
            level.getChunkSource().addRegionTicket(TICKET_TYPE, chunk, TICKET_DISTANCE, chunk, true);
        }
    }

    private static void releaseTickets(final ServerLevel level, final Set<ChunkPos> chunks) {
        for (final ChunkPos chunk : chunks) {
            level.getChunkSource().removeRegionTicket(TICKET_TYPE, chunk, TICKET_DISTANCE, chunk, true);
        }
    }

    private static void primeVisibleHoldingChunks(final ServerLevel level,
                                                  final ServerSubLevelContainer container,
                                                  final Set<ChunkPos> holdingChunks,
                                                  final Set<ChunkPos> primedHoldingChunks) {
        for (final ChunkPos chunk : holdingChunks) {
            if (primedHoldingChunks.contains(chunk)) {
                continue;
            }
            if (!isVisibleAndBlockTicking(level, chunk)) {
                continue;
            }

            container.getHoldingChunkMap().updateChunkStatus(chunk, true);
            primedHoldingChunks.add(chunk);
        }
    }

    private static boolean isVisibleAndBlockTicking(final ServerLevel level, final ChunkPos chunk) {
        return level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null
                && PhysicsChunkTicketManager.isChunkLoadedEnough(level, chunk.x, chunk.z);
    }

    private static void notify(final ServerPlayer player, final SubLevelOperationResult result) {
        player.displayClientMessage(
                result.asComponent().copy().withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED),
                true
        );
    }

    private record PendingRequest(UUID requestId,
                                  UUID playerId,
                                  ResourceKey<Level> dimension,
                                  UUID anchorSubLevelId,
                                  Set<UUID> subLevelIds,
                                  Vec3 destination,
                                  long createdGameTime,
                                  Set<ChunkPos> ticketedChunks,
                                  Set<ChunkPos> holdingChunks,
                                  Set<ChunkPos> primedHoldingChunks) {
    }

    private record RequiredChunks(Set<ChunkPos> ticketedChunks, Set<ChunkPos> holdingChunks) {
    }

    private record TickResult(boolean done, ServerLevel level) {
    }
}

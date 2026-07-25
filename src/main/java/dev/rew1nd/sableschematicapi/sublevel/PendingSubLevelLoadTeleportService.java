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
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

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
import java.util.function.BiConsumer;
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
    private static BiConsumer<MinecraftServer, UUID> detachedCompletionListener = (server, playerId) -> {
    };

    private PendingSubLevelLoadTeleportService() {
    }

    public static void setDetachedCompletionListener(final BiConsumer<MinecraftServer, UUID> listener) {
        detachedCompletionListener = listener == null ? (server, playerId) -> {
        } : listener;
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
                true,
                false,
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

    /**
     * Loads a stored target (including its holding dependencies) before teleporting the
     * requesting player to the exact world-AABB max corner. Cross-dimension targets are valid.
     */
    public static SubLevelOperationResult requestTeleportPlayerToBody(final ServerPlayer player,
                                                                       final SubLevelRecord target) {
        if (target == null) {
            return SubLevelOperationResult.failure("No sub-level was selected.");
        }
        final MinecraftServer server = player.getServer();
        final ServerLevel level = server.getLevel(target.dimension());
        if (level == null) {
            return SubLevelOperationResult.failure(
                    "Target dimension is not loaded: " + target.dimension().location()
            );
        }
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return SubLevelOperationResult.failure(
                    "No Sable sub-level container is available for the target level."
            );
        }

        final BoundingBox3dc targetBounds = target.bounds();
        final Vec3 destination = new Vec3(
                targetBounds.maxX(),
                targetBounds.maxY(),
                targetBounds.maxZ()
        );
        final Set<UUID> targetIds = Set.of(target.uuid());
        if (allLoaded(container, targetIds)) {
            final boolean teleported = player.teleportTo(
                    level,
                    destination.x,
                    destination.y,
                    destination.z,
                    Set.of(),
                    player.getYRot(),
                    player.getXRot()
            );
            return teleported
                    ? SubLevelOperationResult.success(
                            "Teleported player to sub-level " + target.displayName() + ".",
                            1
                    )
                    : SubLevelOperationResult.failure(
                            "Failed to teleport player to sub-level " + target.displayName() + "."
                    );
        }
        if (target.pointer() == null) {
            return SubLevelOperationResult.failure(
                    "Stored sub-level " + target.displayName() + " has no storage pointer."
            );
        }

        final RequiredChunks requiredChunks = collectRequiredChunks(level, List.of(target));
        if (requiredChunks.ticketedChunks().isEmpty()) {
            return SubLevelOperationResult.failure(
                    "No source chunks were found for sub-level " + target.displayName() + "."
            );
        }
        if (requiredChunks.ticketedChunks().size() > MAX_TICKETED_CHUNKS) {
            return SubLevelOperationResult.failure(
                    "Sub-level " + target.displayName() + " requires too many source chunks to load safely."
            );
        }

        cancelForPlayer(server, player.getUUID());
        addTickets(level, requiredChunks.ticketedChunks());
        primeVisibleHoldingChunks(level, container, requiredChunks.holdingChunks(), new LinkedHashSet<>());
        final UUID requestId = UUID.randomUUID();
        REQUESTS.put(requestId, new PendingRequest(
                requestId,
                player.getUUID(),
                true,
                true,
                target.dimension(),
                target.uuid(),
                targetIds,
                destination,
                level.getGameTime(),
                requiredChunks.ticketedChunks(),
                requiredChunks.holdingChunks(),
                new LinkedHashSet<>()
        ));
        return SubLevelOperationResult.success(
                "Queued sub-level " + target.displayName() + " for player teleport.",
                1
        );
    }

    /**
     * Accepts an already signature-checked group snapshot for the physics manager.
     * The destination and membership are frozen now; the task no longer follows the player.
     */
    public static SubLevelOperationResult requestTeleportGroupToPlayerSnapshot(final ServerPlayer player,
                                                                                final SubLevelGroupRecord group,
                                                                                final UUID anchorId) {
        if (group == null || anchorId == null || !group.contains(anchorId)) {
            return SubLevelOperationResult.failure("The selected sub-level group anchor is invalid.");
        }

        final MinecraftServer server = player.getServer();
        final SubLevelRecord anchor = group.find(anchorId).orElseThrow();
        if (!anchor.dimension().equals(player.serverLevel().dimension())) {
            return SubLevelOperationResult.failure("Bringing sub-levels across dimensions is not supported yet.");
        }
        for (final SubLevelRecord member : group.members()) {
            if (!member.dimension().equals(anchor.dimension())) {
                return SubLevelOperationResult.failure("Bringing sub-level groups across dimensions is not supported yet.");
            }
        }

        final ServerLevel level = player.serverLevel();
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return SubLevelOperationResult.failure("No Sable sub-level container is available for this level.");
        }

        final Set<UUID> targetIds = new LinkedHashSet<>(group.memberIds());
        final Vec3 destination = LoadedSubLevelTeleportService.destinationNear(player);
        if (allLoaded(container, targetIds)) {
            return LoadedSubLevelTeleportService.teleportSubLevelGroupTo(
                    level,
                    targetIds,
                    anchorId,
                    destination
            );
        }

        for (final SubLevelRecord member : group.members()) {
            if (!member.loaded() && member.pointer() == null) {
                return SubLevelOperationResult.failure(
                        "Stored sub-level " + member.displayName() + " has no storage pointer."
                );
            }
        }

        final RequiredChunks requiredChunks = collectRequiredChunks(level, group.members());
        if (requiredChunks.ticketedChunks().isEmpty()) {
            return SubLevelOperationResult.failure(
                    "No source chunks were found for sub-level group anchored at " + anchor.displayName() + "."
            );
        }
        if (requiredChunks.ticketedChunks().size() > MAX_TICKETED_CHUNKS) {
            return SubLevelOperationResult.failure(
                    "Sub-level group anchored at " + anchor.displayName()
                            + " requires too many source chunks to load safely."
            );
        }

        addTickets(level, requiredChunks.ticketedChunks());
        primeVisibleHoldingChunks(level, container, requiredChunks.holdingChunks(), new LinkedHashSet<>());
        final UUID requestId = UUID.randomUUID();
        REQUESTS.put(requestId, new PendingRequest(
                requestId,
                player.getUUID(),
                false,
                false,
                anchor.dimension(),
                anchorId,
                targetIds,
                destination,
                level.getGameTime(),
                requiredChunks.ticketedChunks(),
                requiredChunks.holdingChunks(),
                new LinkedHashSet<>()
        ));
        return SubLevelOperationResult.success(
                "Queued sub-level group anchored at " + anchor.displayName() + " for loading.",
                group.members().size()
        );
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
            if (!request.followPlayer()) {
                detachedCompletionListener.accept(server, request.playerId());
            }
        }
    }

    public static void cancelForPlayer(final MinecraftServer server, final UUID playerId) {
        final Iterator<PendingRequest> iterator = REQUESTS.values().iterator();
        while (iterator.hasNext()) {
            final PendingRequest request = iterator.next();
            if (!request.followPlayer() || !request.playerId().equals(playerId)) {
                continue;
            }

            iterator.remove();
            final ServerLevel level = server.getLevel(request.dimension());
            if (level != null) {
                releaseTickets(level, request.ticketedChunks());
            }
        }
    }

    public static void onServerStopped(final ServerStoppedEvent event) {
        for (final PendingRequest request : REQUESTS.values()) {
            final ServerLevel level = event.getServer().getLevel(request.dimension());
            if (level != null) {
                releaseTickets(level, request.ticketedChunks());
            }
        }
        REQUESTS.clear();
    }

    private static TickResult tickRequest(final MinecraftServer server, final PendingRequest request) {
        final ServerLevel level = server.getLevel(request.dimension());
        if (level == null) {
            return new TickResult(true, null);
        }

        final ServerPlayer player = server.getPlayerList().getPlayer(request.playerId());
        if (request.followPlayer() && (player == null || player.isRemoved())) {
            return new TickResult(true, level);
        }

        if (request.followPlayer()
                && !request.teleportPlayer()
                && !player.serverLevel().dimension().equals(request.dimension())) {
            notify(player, SubLevelOperationResult.failure("Pending sub-level teleport was cancelled because you changed dimensions."));
            return new TickResult(true, level);
        }

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            notifyIfOnline(player, SubLevelOperationResult.failure("Pending sub-level teleport failed because the Sable container is unavailable."));
            return new TickResult(true, level);
        }

        primeVisibleHoldingChunks(level, container, request.holdingChunks(), request.primedHoldingChunks());

        if (allLoaded(container, request.subLevelIds())) {
            final SubLevelOperationResult result;
            if (request.teleportPlayer()) {
                final boolean teleported = player.teleportTo(
                        level,
                        request.destination().x,
                        request.destination().y,
                        request.destination().z,
                        Set.of(),
                        player.getYRot(),
                        player.getXRot()
                );
                result = teleported
                        ? SubLevelOperationResult.success(
                                "Teleported player to loaded sub-level " + request.anchorSubLevelId() + ".",
                                1
                        )
                        : SubLevelOperationResult.failure(
                                "Failed to teleport player to loaded sub-level "
                                        + request.anchorSubLevelId() + "."
                        );
            } else {
                result = request.followPlayer()
                    ? LoadedSubLevelTeleportService.teleportSubLevelGroupTo(
                            player,
                            request.subLevelIds(),
                            request.anchorSubLevelId(),
                            request.destination()
                    )
                    : LoadedSubLevelTeleportService.teleportSubLevelGroupTo(
                            level,
                            request.subLevelIds(),
                            request.anchorSubLevelId(),
                            request.destination()
                    );
            }
            notifyIfOnline(player, result);
            return new TickResult(true, level);
        }

        if (level.getGameTime() - request.createdGameTime() > TIMEOUT_TICKS) {
            notifyIfOnline(player, SubLevelOperationResult.failure("Timed out while waiting for the stored sub-level group to load."));
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

    private static void notifyIfOnline(final ServerPlayer player, final SubLevelOperationResult result) {
        if (player != null && !player.isRemoved()) {
            notify(player, result);
        } else {
            SableSchematicApi.LOGGER.info(result.message());
        }
    }

    private record PendingRequest(UUID requestId,
                                  UUID playerId,
                                  boolean followPlayer,
                                  boolean teleportPlayer,
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

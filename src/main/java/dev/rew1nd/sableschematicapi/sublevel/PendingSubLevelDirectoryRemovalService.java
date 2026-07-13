package dev.rew1nd.sableschematicapi.sublevel;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Removes a snapshot of directory records without blocking the server while stored entries load. */
public final class PendingSubLevelDirectoryRemovalService {
    private static final TicketType<ChunkPos> TICKET_TYPE = TicketType.create(
            "sable_schematic_api_directory_removal",
            Comparator.comparingLong(ChunkPos::toLong)
    );
    private static final int TICKET_DISTANCE = 1;
    private static final int MAX_TICKETED_CHUNKS = 1024;
    private static final long LOAD_TIMEOUT_TICKS = 400L;

    private static RemovalJob activeJob;

    private PendingSubLevelDirectoryRemovalService() {
    }

    public static SubLevelOperationResult requestRemoval(final MinecraftServer server, final CommandSourceStack source) {
        if (activeJob != null) {
            return SubLevelOperationResult.failure("A Sable sub-level directory removal job is already running.");
        }

        final Map<RecordKey, SubLevelRecord> records = new LinkedHashMap<>();
        for (final SubLevelRecord record : SubLevelDirectoryService.listAll(server)) {
            records.putIfAbsent(new RecordKey(record.dimension(), record.uuid()), record);
        }

        if (records.isEmpty()) {
            return SubLevelOperationResult.success("No Sable sub-level directory entries were found.", 0);
        }

        activeJob = new RemovalJob(source, List.copyOf(records.values()), records);
        return SubLevelOperationResult.success("Queued removal of " + records.size() + " Sable sub-level directory entr" + (records.size() == 1 ? "y." : "ies."), 0);
    }

    public static void tick(final ServerTickEvent.Post event) {
        if (activeJob == null) {
            return;
        }

        final RemovalJob job = activeJob;
        try {
            if (tickJob(event.getServer(), job)) {
                activeJob = null;
                finish(event.getServer(), job);
            }
        } catch (final RuntimeException e) {
            activeJob = null;
            releaseCurrentTickets(event.getServer(), job);
            SableSchematicApi.LOGGER.error("Sable sub-level directory removal job crashed", e);
            job.source().sendFailure(Component.literal("Sable sub-level directory removal stopped: " + e.getMessage()));
        }
    }

    public static void onServerStopped(final ServerStoppedEvent event) {
        if (activeJob != null) {
            releaseCurrentTickets(event.getServer(), activeJob);
            activeJob = null;
        }
    }

    private static boolean tickJob(final MinecraftServer server, final RemovalJob job) {
        if (job.current() == null) {
            if (job.nextIndex() >= job.records().size()) {
                return true;
            }

            final SubLevelRecord record = job.records().get(job.nextIndex());
            job.setNextIndex(job.nextIndex() + 1);
            startRecord(server, job, record);
            return false;
        }

        final PendingRecord current = job.current();
        final ServerLevel level = server.getLevel(current.record().dimension());
        if (level == null) {
            job.unavailableDimensions++;
            job.setCurrent(null);
            return false;
        }

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            job.failures++;
            SableSchematicApi.LOGGER.warn("No Sable sub-level container while removing directory entry {}", current.record().uuid());
            releaseCurrentTickets(server, job);
            job.setCurrent(null);
            return false;
        }

        primeHoldingChunks(level, container, current.holdingChunks(), current.primedHoldingChunks());
        if (removeIfLoaded(level, container, current.record(), job)) {
            releaseCurrentTickets(server, job);
            job.setCurrent(null);
            return false;
        }

        if (level.getGameTime() - current.createdGameTime() > LOAD_TIMEOUT_TICKS) {
            job.loadFailures++;
            SableSchematicApi.LOGGER.warn("Timed out loading stored Sable sub-level {} for directory removal", current.record().uuid());
            releaseCurrentTickets(server, job);
            job.setCurrent(null);
        }

        return false;
    }

    private static void startRecord(final MinecraftServer server, final RemovalJob job, final SubLevelRecord record) {
        final ServerLevel level = server.getLevel(record.dimension());
        if (level == null) {
            job.unavailableDimensions++;
            return;
        }

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            job.failures++;
            return;
        }

        if (removeIfLoaded(level, container, record, job)) {
            return;
        }

        if (record.pointer() == null) {
            job.missingEntries++;
            return;
        }

        final RequiredChunks chunks = collectRequiredChunks(record, job.recordsByKey());
        if (chunks.ticketedChunks().isEmpty() || chunks.ticketedChunks().size() > MAX_TICKETED_CHUNKS) {
            job.loadFailures++;
            SableSchematicApi.LOGGER.warn("Refusing to load directory entry {} for removal because it requires {} chunks", record.uuid(), chunks.ticketedChunks().size());
            return;
        }

        addTickets(level, chunks.ticketedChunks());
        final PendingRecord pending = new PendingRecord(record, level.getGameTime(), chunks.ticketedChunks(), chunks.holdingChunks());
        job.setCurrent(pending);
        primeHoldingChunks(level, container, pending.holdingChunks(), pending.primedHoldingChunks());
    }

    private static boolean removeIfLoaded(final ServerLevel level,
                                          final ServerSubLevelContainer container,
                                          final SubLevelRecord record,
                                          final RemovalJob job) {
        final SubLevel subLevel = container.getSubLevel(record.uuid());
        if (!(subLevel instanceof final ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved()) {
            return false;
        }

        try {
            container.removeSubLevel(serverSubLevel, SubLevelRemovalReason.REMOVED);
            job.removed++;
            job.affectedDimensions.add(level.dimension());
            return true;
        } catch (final RuntimeException e) {
            job.failures++;
            SableSchematicApi.LOGGER.warn("Failed removing loaded Sable sub-level {}", record.uuid(), e);
            return true;
        }
    }

    private static RequiredChunks collectRequiredChunks(final SubLevelRecord record,
                                                        final Map<RecordKey, SubLevelRecord> recordsByKey) {
        final Set<ChunkPos> ticketedChunks = new LinkedHashSet<>();
        final Set<ChunkPos> holdingChunks = new LinkedHashSet<>();
        addRecordChunks(record, ticketedChunks, holdingChunks);

        for (final UUID dependencyId : record.dependencies()) {
            final SubLevelRecord dependency = recordsByKey.get(new RecordKey(record.dimension(), dependencyId));
            if (dependency != null) {
                addRecordChunks(dependency, ticketedChunks, holdingChunks);
            }
        }

        return new RequiredChunks(ticketedChunks, holdingChunks);
    }

    private static void addRecordChunks(final SubLevelRecord record,
                                        final Set<ChunkPos> ticketedChunks,
                                        final Set<ChunkPos> holdingChunks) {
        if (record.pointer() != null) {
            ticketedChunks.add(record.pointer().chunkPos());
            holdingChunks.add(record.pointer().chunkPos());
        }
        addBoundsChunks(record.bounds(), ticketedChunks);
        addBoundsChunks(record.bounds(), holdingChunks);
    }

    private static void addBoundsChunks(final BoundingBox3dc bounds, final Set<ChunkPos> chunks) {
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

    private static void addTickets(final ServerLevel level, final Collection<ChunkPos> chunks) {
        for (final ChunkPos chunk : chunks) {
            level.getChunkSource().addRegionTicket(TICKET_TYPE, chunk, TICKET_DISTANCE, chunk, true);
        }
    }

    private static void releaseTickets(final ServerLevel level, final Collection<ChunkPos> chunks) {
        for (final ChunkPos chunk : chunks) {
            level.getChunkSource().removeRegionTicket(TICKET_TYPE, chunk, TICKET_DISTANCE, chunk, true);
        }
    }

    private static void primeHoldingChunks(final ServerLevel level,
                                           final ServerSubLevelContainer container,
                                           final Collection<ChunkPos> holdingChunks,
                                           final Set<ChunkPos> primedHoldingChunks) {
        for (final ChunkPos chunk : holdingChunks) {
            if (primedHoldingChunks.contains(chunk)
                    || level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null
                    || !PhysicsChunkTicketManager.isChunkLoadedEnough(level, chunk.x, chunk.z)) {
                continue;
            }

            container.getHoldingChunkMap().updateChunkStatus(chunk, true);
            primedHoldingChunks.add(chunk);
        }
    }

    private static void releaseCurrentTickets(final MinecraftServer server, final RemovalJob job) {
        final PendingRecord current = job.current();
        if (current == null) {
            return;
        }

        final ServerLevel level = server.getLevel(current.record().dimension());
        if (level != null) {
            releaseTickets(level, current.ticketedChunks());
        }
    }

    private static void finish(final MinecraftServer server, final RemovalJob job) {
        final SubLevelOperationResult flush = SubLevelStorageFlushService.flush(server);
        final String message = "Sable sub-level directory removal finished: removed " + job.removed
                + ", missing " + job.missingEntries
                + ", load failed " + job.loadFailures
                + ", other failures " + job.failures
                + ", unavailable dimensions " + job.unavailableDimensions
                + ". " + flush.message();

        if (flush.success() && job.loadFailures == 0 && job.failures == 0 && job.unavailableDimensions == 0) {
            job.source().sendSuccess(() -> Component.literal(message), true);
        } else {
            job.source().sendFailure(Component.literal(message));
        }
    }

    private record RecordKey(ResourceKey<Level> dimension, UUID uuid) {
    }

    private record RequiredChunks(Set<ChunkPos> ticketedChunks, Set<ChunkPos> holdingChunks) {
    }

    private static final class PendingRecord {
        private final SubLevelRecord record;
        private final long createdGameTime;
        private final Set<ChunkPos> ticketedChunks;
        private final Set<ChunkPos> holdingChunks;
        private final Set<ChunkPos> primedHoldingChunks = new LinkedHashSet<>();

        private PendingRecord(final SubLevelRecord record,
                              final long createdGameTime,
                              final Set<ChunkPos> ticketedChunks,
                              final Set<ChunkPos> holdingChunks) {
            this.record = record;
            this.createdGameTime = createdGameTime;
            this.ticketedChunks = ticketedChunks;
            this.holdingChunks = holdingChunks;
        }

        private SubLevelRecord record() {
            return record;
        }

        private long createdGameTime() {
            return createdGameTime;
        }

        private Set<ChunkPos> ticketedChunks() {
            return ticketedChunks;
        }

        private Set<ChunkPos> holdingChunks() {
            return holdingChunks;
        }

        private Set<ChunkPos> primedHoldingChunks() {
            return primedHoldingChunks;
        }
    }

    private static final class RemovalJob {
        private final CommandSourceStack source;
        private final List<SubLevelRecord> records;
        private final Map<RecordKey, SubLevelRecord> recordsByKey;
        private final Set<ResourceKey<Level>> affectedDimensions = new LinkedHashSet<>();
        private int nextIndex;
        private int removed;
        private int missingEntries;
        private int loadFailures;
        private int failures;
        private int unavailableDimensions;
        private PendingRecord current;

        private RemovalJob(final CommandSourceStack source,
                           final List<SubLevelRecord> records,
                           final Map<RecordKey, SubLevelRecord> recordsByKey) {
            this.source = source;
            this.records = records;
            this.recordsByKey = recordsByKey;
        }

        private CommandSourceStack source() {
            return source;
        }

        private List<SubLevelRecord> records() {
            return records;
        }

        private Map<RecordKey, SubLevelRecord> recordsByKey() {
            return recordsByKey;
        }

        private int nextIndex() {
            return nextIndex;
        }

        private void setNextIndex(final int nextIndex) {
            this.nextIndex = nextIndex;
        }

        private PendingRecord current() {
            return current;
        }

        private void setCurrent(final PendingRecord current) {
            this.current = current;
        }
    }
}

package dev.rew1nd.sableschematicapi.api.blueprint;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mutable state for one blueprint placement operation.
 *
 * <p>The session records how source sub-level UUIDs and blueprint-local block
 * references map to newly allocated placed sub-levels. Mappers and events use
 * this object to rebuild references without pointing back at the source world.</p>
 */
public class BlueprintPlaceSession {
    private final ServerLevel level;
    private final Vector3d origin;
    private final Int2ObjectOpenHashMap<ServerSubLevel> placedSubLevels = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<BlockPos> placedBlockOrigins = new Int2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<UUID, UUID> subLevelUuidMap = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<UUID, UUID> allocatedUuidMap = new Object2ObjectOpenHashMap<>();
    private final List<BlueprintPlacedBlock> placedBlocks = new ObjectArrayList<>();
    private final List<Runnable> afterBlockEntityTasks = new ObjectArrayList<>();
    private final CompoundTag globalExtraData;
    private BlueprintPlacePhase phase = BlueprintPlacePhase.DECODE;

    /**
     * Creates a placement session.
     *
     * @param level           target server level
     * @param origin          placement origin
     * @param globalExtraData blueprint sidecar data copied from the saved blueprint
     */
    public BlueprintPlaceSession(final ServerLevel level, final Vector3dc origin, final CompoundTag globalExtraData) {
        this.level = level;
        this.origin = new Vector3d(origin);
        this.globalExtraData = globalExtraData.copy();
    }

    /**
     * Returns the server level receiving the blueprint.
     *
     * @return the server level receiving the blueprint
     */
    public ServerLevel level() {
        return this.level;
    }

    /**
     * Returns the placement origin supplied by the command, tool, or caller.
     *
     * @return placement origin supplied by the command, tool, or caller
     */
    public Vector3dc origin() {
        return this.origin;
    }

    /**
     * Returns the current placement phase.
     *
     * @return the current placement phase
     */
    public BlueprintPlacePhase phase() {
        return this.phase;
    }

    /**
     * Updates the lifecycle phase. This is controlled by the placer.
     *
     * @param phase new placement phase
     */
    public void setPhase(final BlueprintPlacePhase phase) {
        this.phase = phase;
    }

    /**
     * Returns the mutable copy of blueprint {@code global_extra_data} for this placement.
     *
     * <p>Event implementations should read their own namespaced sidecar data from here.</p>
     *
     * @return mutable placement-local sidecar tag
     */
    public CompoundTag globalExtraData() {
        return this.globalExtraData;
    }

    /**
     * Records the placed sub-level and storage origin allocated for a blueprint sub-level.
     *
     * <p>This is called before block states are placed, so mappers can use
     * {@link #mapSubLevel(UUID)} and {@link #mapBlock(BlueprintBlockRef)} during
     * block entity and entity remapping.</p>
     *
     * @param blueprintId       blueprint-local sub-level id
     * @param sourceUuid        source sub-level UUID at save time
     * @param placedSubLevel    newly allocated placed sub-level
     * @param placedBlockOrigin storage origin for the placed sub-level payload
     */
    public void mapSubLevel(final int blueprintId, final UUID sourceUuid, final ServerSubLevel placedSubLevel, final BlockPos placedBlockOrigin) {
        this.placedSubLevels.put(blueprintId, placedSubLevel);
        this.placedBlockOrigins.put(blueprintId, placedBlockOrigin.immutable());
        this.subLevelUuidMap.put(sourceUuid, placedSubLevel.getUniqueId());
    }

    /**
     * Records a block state that was placed from the blueprint.
     *
     * <p>The returned view later receives its loaded block entity, if any.</p>
     *
     * @param blueprintId       blueprint-local sub-level id
     * @param sourceUuid        source sub-level UUID at save time
     * @param placedSubLevel    placed sub-level containing the block
     * @param localPos          blueprint-local block position
     * @param storagePos        placed storage position
     * @param state             placed block state
     * @param blockEntityDataId index into saved block entity payloads, or a negative value
     * @return placed block view
     */
    public BlueprintPlacedBlock recordPlacedBlock(final int blueprintId,
                                                  final UUID sourceUuid,
                                                  final ServerSubLevel placedSubLevel,
                                                  final BlockPos localPos,
                                                  final BlockPos storagePos,
                                                  final BlockState state,
                                                  final int blockEntityDataId) {
        final BlueprintPlacedBlock block = new BlueprintPlacedBlock(
                this,
                blueprintId,
                sourceUuid,
                placedSubLevel,
                localPos,
                storagePos,
                state,
                blockEntityDataId
        );
        this.placedBlocks.add(block);
        return block;
    }

    /**
     * Attaches the loaded block entity to a placed block view.
     *
     * @param block       placed block view
     * @param blockEntity loaded block entity, or {@code null}
     */
    public void attachBlockEntity(final BlueprintPlacedBlock block, @Nullable final BlockEntity blockEntity) {
        block.attachBlockEntity(blockEntity);
    }

    /**
     * Returns the placed sub-level for a blueprint-local id.
     *
     * @return placed sub-level for the blueprint-local id, or {@code null} when not allocated
     * @param blueprintId blueprint-local sub-level id
     */
    public @Nullable ServerSubLevel placedSubLevel(final int blueprintId) {
        return this.placedSubLevels.get(blueprintId);
    }

    /**
     * Returns the placed storage origin for a blueprint-local id.
     *
     * @return placed storage origin for the blueprint-local sub-level id, or {@code null}
     * @param blueprintId blueprint-local sub-level id
     */
    public @Nullable BlockPos placedBlockOrigin(final int blueprintId) {
        return this.placedBlockOrigins.get(blueprintId);
    }

    /**
     * Maps a source sub-level UUID to the newly placed sub-level UUID.
     *
     * @param sourceUuid source sub-level UUID at save time
     * @return placed UUID, or {@code null} when the source sub-level was not copied
     */
    public @Nullable UUID mapSubLevel(final UUID sourceUuid) {
        return this.subLevelUuidMap.get(sourceUuid);
    }

    /**
     * Maps a saved sub-level reference to the newly placed sub-level UUID.
     *
     * @param ref blueprint-local sub-level reference
     * @return placed UUID, or {@code null} when the source sub-level was not copied
     */
    public @Nullable UUID mapSubLevel(final BlueprintSubLevelRef ref) {
        return this.subLevelUuidMap.get(ref.sourceUuid());
    }

    /**
     * Maps a blueprint-local block reference to a placed storage position.
     *
     * @param ref blueprint-local block reference
     * @return placed storage position, or {@code null} when the referenced sub-level was not allocated
     */
    public @Nullable BlockPos mapBlock(final BlueprintBlockRef ref) {
        final BlockPos origin = this.placedBlockOrigins.get(ref.subLevelId());
        if (origin == null) {
            return null;
        }

        final BlockPos local = ref.localPos();
        return new BlockPos(
                origin.getX() + local.getX(),
                origin.getY() + local.getY(),
                origin.getZ() + local.getZ()
        );
    }

    /**
     * Allocates a stable new UUID for one source UUID within this placement.
     *
     * <p>Repeated calls with the same source UUID during one placement return the same
     * generated UUID. Use this for manager-owned data such as ropes or constraints that
     * must avoid collisions when the same blueprint is loaded more than once.</p>
     *
     * @param sourceUuid source UUID stored in the blueprint
     * @return stable replacement UUID for this placement
     */
    public UUID allocateMappedUuid(final UUID sourceUuid) {
        return this.allocatedUuidMap.computeIfAbsent(sourceUuid, ignored -> UUID.randomUUID());
    }

    /**
     * Preloads a stable placement-local replacement UUID.
     *
     * <p>Incremental placers can use this when resuming a build whose manager-owned
     * UUID mappings were already persisted.</p>
     *
     * @param sourceUuid source UUID stored in the blueprint
     * @param placedUuid replacement UUID to use for this placement
     */
    public void mapAllocatedUuid(final UUID sourceUuid, final UUID placedUuid) {
        this.allocatedUuidMap.put(sourceUuid, placedUuid);
    }

    /**
     * Returns allocated source-to-placement UUID mappings.
     *
     * @return immutable copy of allocated source UUID to placement UUID mappings
     */
    public Map<UUID, UUID> allocatedUuidMap() {
        return Map.copyOf(this.allocatedUuidMap);
    }

    /**
     * Schedules a task to run after all block entities have loaded.
     *
     * @param task task to run after block entity loading
     */
    public void deferAfterBlockEntities(final Runnable task) {
        this.afterBlockEntityTasks.add(task);
    }

    /**
     * Runs and clears tasks registered with {@link #deferAfterBlockEntities(Runnable)}.
     */
    public void runAfterBlockEntityTasks() {
        for (final Runnable task : this.afterBlockEntityTasks) {
            task.run();
        }
        this.afterBlockEntityTasks.clear();
    }

    /**
     * Returns source-to-placed sub-level UUID mappings.
     *
     * @return immutable copy of source sub-level UUID to placed sub-level UUID mappings
     */
    public Map<UUID, UUID> subLevelUuidMap() {
        return Map.copyOf(this.subLevelUuidMap);
    }

    /**
     * Returns a query view over placed block states.
     *
     * @return query view over block states already placed from the blueprint
     */
    public BlueprintPlacedBlocksView placedBlocks() {
        return new BlueprintPlacedBlocksView(this.placedBlocks);
    }
}

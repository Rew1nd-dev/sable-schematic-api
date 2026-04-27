package dev.rew1nd.sableschematicapi.api.blueprint;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Mutable state for one blueprint export operation.
 *
 * <p>The session owns the save lifecycle phase, selected sub-level frames,
 * saved block views, and the global sidecar tag shared by
 * {@link SableBlueprintEvent} implementations.</p>
 */
public class BlueprintSaveSession {
    private final ServerLevel level;
    private final Vector3d rootOrigin;
    private final BoundingBox3d rootBounds;
    private final List<SubLevelSaveFrame> frames = new ObjectArrayList<>();
    private final List<BlueprintSavedBlock> savedBlocks = new ObjectArrayList<>();
    private final Object2IntOpenHashMap<UUID> sourceSubLevelIds = new Object2IntOpenHashMap<>();
    private final CompoundTag globalExtraData = new CompoundTag();
    private BlueprintSavePhase phase = BlueprintSavePhase.SELECT_SUBLEVELS;

    /**
     * Creates a save session.
     *
     * @param level      source server level
     * @param rootOrigin origin supplied by the command, tool, or caller
     * @param rootBounds world-space bounds used to select intersecting sub-levels
     */
    public BlueprintSaveSession(final ServerLevel level, final Vector3dc rootOrigin, final BoundingBox3d rootBounds) {
        this.level = level;
        this.rootOrigin = new Vector3d(rootOrigin);
        this.rootBounds = new BoundingBox3d(rootBounds);
        this.sourceSubLevelIds.defaultReturnValue(-1);
    }

    /**
     * Adds a source sub-level selected for this blueprint.
     *
     * <p>This is called by the exporter before block data is saved. Compatibility
     * code normally reads frames through {@link #frames()} instead of adding them.</p>
     *
     * @param frame source sub-level frame
     */
    public void addFrame(final SubLevelSaveFrame frame) {
        this.frames.add(frame);
        this.sourceSubLevelIds.put(frame.sourceUuid(), frame.blueprintId());
    }

    /**
     * Records a block that was written into the blueprint.
     *
     * <p>The returned view is also exposed through {@link #savedBlocks()} so global
     * events can discover blueprint-local references after block saving.</p>
     *
     * @param frame             source sub-level frame
     * @param storagePos        source storage position
     * @param localPos          blueprint-local block position
     * @param state             saved block state
     * @param blockEntity       source block entity, or {@code null}
     * @param blockEntityDataId index into saved block entity payloads, or a negative value
     * @param blockEntityTag    saved block entity tag, or {@code null}
     * @return saved block view
     */
    public BlueprintSavedBlock recordSavedBlock(final SubLevelSaveFrame frame,
                                                final BlockPos storagePos,
                                                final BlockPos localPos,
                                                final BlockState state,
                                                @Nullable final BlockEntity blockEntity,
                                                final int blockEntityDataId,
                                                @Nullable final CompoundTag blockEntityTag) {
        final BlueprintSavedBlock block = new BlueprintSavedBlock(
                this,
                frame,
                storagePos,
                localPos,
                state,
                blockEntity,
                blockEntityDataId,
                blockEntityTag
        );
        this.savedBlocks.add(block);
        return block;
    }

    /**
     * Returns the server level being exported.
     *
     * @return the server level being exported
     */
    public ServerLevel level() {
        return this.level;
    }

    /**
     * Returns the current export phase.
     *
     * @return the current export phase
     */
    public BlueprintSavePhase phase() {
        return this.phase;
    }

    /**
     * Updates the lifecycle phase. This is controlled by the exporter.
     *
     * @param phase new export phase
     */
    public void setPhase(final BlueprintSavePhase phase) {
        this.phase = phase;
    }

    /**
     * Returns the origin supplied by the command, tool, or caller.
     *
     * @return the origin supplied by the command, tool, or caller
     */
    public Vector3dc rootOrigin() {
        return this.rootOrigin;
    }

    /**
     * Returns the root world-space bounds used to select intersecting sub-levels.
     *
     * @return copy of the root world-space bounds used to select intersecting sub-levels
     */
    public BoundingBox3d rootBounds() {
        return new BoundingBox3d(this.rootBounds);
    }

    /**
     * Returns all source sub-level frames selected for this blueprint.
     *
     * @return immutable snapshot of all source sub-level frames selected for this blueprint
     */
    public List<SubLevelSaveFrame> frames() {
        return List.copyOf(this.frames);
    }

    /**
     * Returns a query view over blocks already written into the blueprint.
     *
     * @return query view over blocks that have already been written into the blueprint
     */
    public BlueprintSavedBlocksView savedBlocks() {
        return new BlueprintSavedBlocksView(this.savedBlocks);
    }

    /**
     * Returns the mutable global sidecar tag stored under blueprint {@code global_extra_data}.
     *
     * <p>Event implementations should keep their data under their own namespaced event id.</p>
     *
     * @return mutable save-side sidecar tag
     */
    public CompoundTag globalExtraData() {
        return this.globalExtraData;
    }

    /**
     * Converts a source storage position into a blueprint-local block reference.
     *
     * @param sourceStoragePos position in a source sub-level storage
     * @return a reference when the position is inside a saved frame
     */
    public Optional<BlueprintBlockRef> blockRef(final BlockPos sourceStoragePos) {
        for (final SubLevelSaveFrame frame : this.frames) {
            if (frame.contains(sourceStoragePos)) {
                return Optional.of(new BlueprintBlockRef(frame.blueprintId(), frame.toLocal(sourceStoragePos)));
            }
        }

        return Optional.empty();
    }

    /**
     * Converts a source sub-level UUID into a blueprint-local sub-level reference.
     *
     * @param sourceUuid source sub-level UUID at save time
     * @return a reference when the sub-level is part of this blueprint
     */
    public Optional<BlueprintSubLevelRef> subLevelRef(final UUID sourceUuid) {
        final int id = this.sourceSubLevelIds.getInt(sourceUuid);
        if (id < 0) {
            return Optional.empty();
        }

        return Optional.of(new BlueprintSubLevelRef(id, sourceUuid));
    }

    /**
     * Finds the blueprint-local id for a source sub-level UUID.
     *
     * @param sourceUuid source sub-level UUID at save time
     * @return blueprint-local sub-level id, or {@code null} when the sub-level was not saved
     */
    public @Nullable Integer blueprintId(final UUID sourceUuid) {
        final int id = this.sourceSubLevelIds.getInt(sourceUuid);
        return id >= 0 ? id : null;
    }
}

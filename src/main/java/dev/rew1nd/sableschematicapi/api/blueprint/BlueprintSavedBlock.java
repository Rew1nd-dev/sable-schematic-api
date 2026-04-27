package dev.rew1nd.sableschematicapi.api.blueprint;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Read-only description of one block written into a blueprint during save.
 *
 * <p>Instances are exposed through {@link BlueprintSaveSession#savedBlocks()} for
 * global save events. The object keeps both the source storage position and the
 * blueprint-local reference used for sidecar data.</p>
 */
public final class BlueprintSavedBlock {
    private final BlueprintSaveSession session;
    private final SubLevelSaveFrame frame;
    private final ServerSubLevel subLevel;
    private final int blueprintSubLevelId;
    private final UUID sourceSubLevelUuid;
    private final BlockPos storagePos;
    private final BlockPos localPos;
    private final BlockState state;
    private final @Nullable BlockEntity blockEntity;
    private final int blockEntityDataId;
    private final @Nullable CompoundTag blockEntityTag;

    BlueprintSavedBlock(final BlueprintSaveSession session,
                        final SubLevelSaveFrame frame,
                        final BlockPos storagePos,
                        final BlockPos localPos,
                        final BlockState state,
                        @Nullable final BlockEntity blockEntity,
                        final int blockEntityDataId,
                        @Nullable final CompoundTag blockEntityTag) {
        this.session = session;
        this.frame = frame;
        this.subLevel = frame.subLevel();
        this.blueprintSubLevelId = frame.blueprintId();
        this.sourceSubLevelUuid = frame.sourceUuid();
        this.storagePos = storagePos.immutable();
        this.localPos = localPos.immutable();
        this.state = state;
        this.blockEntity = blockEntity;
        this.blockEntityDataId = blockEntityDataId;
        this.blockEntityTag = blockEntityTag != null ? blockEntityTag.copy() : null;
    }

    /**
     * Returns the save session that recorded this block.
     *
     * @return save session that recorded this block
     */
    public BlueprintSaveSession session() {
        return this.session;
    }

    /**
     * Returns the source sub-level frame that contains this block.
     *
     * @return source sub-level frame that contains this block
     */
    public SubLevelSaveFrame frame() {
        return this.frame;
    }

    /**
     * Returns the source sub-level that contains this block.
     *
     * @return source sub-level that contains this block
     */
    public ServerSubLevel subLevel() {
        return this.subLevel;
    }

    /**
     * Returns the blueprint-local sub-level id.
     *
     * @return blueprint-local sub-level id
     */
    public int blueprintSubLevelId() {
        return this.blueprintSubLevelId;
    }

    /**
     * Returns the source sub-level UUID at save time.
     *
     * @return source sub-level UUID at save time
     */
    public UUID sourceSubLevelUuid() {
        return this.sourceSubLevelUuid;
    }

    /**
     * Returns the source storage position.
     *
     * @return source storage position
     */
    public BlockPos storagePos() {
        return this.storagePos;
    }

    /**
     * Returns the blueprint-local block position.
     *
     * @return blueprint-local block position
     */
    public BlockPos localPos() {
        return this.localPos;
    }

    /**
     * Returns the saved block state.
     *
     * @return saved block state
     */
    public BlockState state() {
        return this.state;
    }

    /**
     * Returns the source block entity.
     *
     * @return source block entity, or {@code null}
     */
    public @Nullable BlockEntity blockEntity() {
        return this.blockEntity;
    }

    /**
     * Returns the index into the saved block entity payload list.
     *
     * @return index into the saved block entity payload list, or a negative value when none exists
     */
    public int blockEntityDataId() {
        return this.blockEntityDataId;
    }

    /**
     * Returns whether this block has a stored block entity payload.
     *
     * @return whether this block has a stored block entity payload
     */
    public boolean hasBlockEntityData() {
        return this.blockEntityDataId >= 0;
    }

    /**
     * Returns a defensive copy of the stored block entity tag.
     *
     * @return defensive copy of the stored block entity tag, or {@code null}
     */
    public @Nullable CompoundTag blockEntityTag() {
        return this.blockEntityTag != null ? this.blockEntityTag.copy() : null;
    }

    /**
     * Returns a blueprint-local reference to this block.
     *
     * @return blueprint-local reference to this block
     */
    public BlueprintBlockRef ref() {
        return new BlueprintBlockRef(this.blueprintSubLevelId, this.localPos);
    }
}

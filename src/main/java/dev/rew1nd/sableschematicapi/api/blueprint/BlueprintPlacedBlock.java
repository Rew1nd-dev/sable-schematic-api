package dev.rew1nd.sableschematicapi.api.blueprint;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Read-only description of one block placed from a blueprint.
 *
 * <p>Instances are exposed through {@link BlueprintPlaceSession#placedBlocks()}
 * so global placement events can find placed storage positions and loaded block
 * entities for blueprint-local references.</p>
 */
public final class BlueprintPlacedBlock {
    private final BlueprintPlaceSession session;
    private final int blueprintSubLevelId;
    private final UUID sourceSubLevelUuid;
    private final ServerSubLevel placedSubLevel;
    private final BlockPos localPos;
    private final BlockPos storagePos;
    private final BlockState state;
    private final int blockEntityDataId;
    private @Nullable BlockEntity blockEntity;

    BlueprintPlacedBlock(final BlueprintPlaceSession session,
                         final int blueprintSubLevelId,
                         final UUID sourceSubLevelUuid,
                         final ServerSubLevel placedSubLevel,
                         final BlockPos localPos,
                         final BlockPos storagePos,
                         final BlockState state,
                         final int blockEntityDataId) {
        this.session = session;
        this.blueprintSubLevelId = blueprintSubLevelId;
        this.sourceSubLevelUuid = sourceSubLevelUuid;
        this.placedSubLevel = placedSubLevel;
        this.localPos = localPos.immutable();
        this.storagePos = storagePos.immutable();
        this.state = state;
        this.blockEntityDataId = blockEntityDataId;
    }

    /**
     * Returns the placement session that recorded this block.
     *
     * @return placement session that recorded this block
     */
    public BlueprintPlaceSession session() {
        return this.session;
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
     * Returns the newly allocated placed sub-level UUID.
     *
     * @return newly allocated placed sub-level UUID
     */
    public UUID placedSubLevelUuid() {
        return this.placedSubLevel.getUniqueId();
    }

    /**
     * Returns the placed sub-level that contains this block.
     *
     * @return placed sub-level that contains this block
     */
    public ServerSubLevel placedSubLevel() {
        return this.placedSubLevel;
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
     * Returns the placed storage position.
     *
     * @return placed storage position
     */
    public BlockPos storagePos() {
        return this.storagePos;
    }

    /**
     * Returns the placed block state.
     *
     * @return placed block state
     */
    public BlockState state() {
        return this.state;
    }

    /**
     * Returns the index into the blueprint block entity payload list.
     *
     * @return index into the blueprint block entity payload list, or a negative value when none exists
     */
    public int blockEntityDataId() {
        return this.blockEntityDataId;
    }

    /**
     * Returns whether this block had a stored block entity payload.
     *
     * @return whether this block had a stored block entity payload
     */
    public boolean hasBlockEntityData() {
        return this.blockEntityDataId >= 0;
    }

    /**
     * Returns the loaded placed block entity.
     *
     * @return loaded placed block entity, or {@code null} before loading or when absent
     */
    public @Nullable BlockEntity blockEntity() {
        return this.blockEntity;
    }

    /**
     * Returns a blueprint-local reference to this block.
     *
     * @return blueprint-local reference to this block
     */
    public BlueprintBlockRef ref() {
        return new BlueprintBlockRef(this.blueprintSubLevelId, this.localPos);
    }

    void attachBlockEntity(@Nullable final BlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }
}

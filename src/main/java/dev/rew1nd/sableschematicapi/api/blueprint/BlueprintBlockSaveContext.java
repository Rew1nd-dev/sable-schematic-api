package dev.rew1nd.sableschematicapi.api.blueprint;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Per-block context exposed to blueprint save mappers.
 *
 * <p>Positions in this context refer to the source sub-level storage and the
 * blueprint-local payload being written. Use {@link #blockRef(BlockPos)} and
 * {@link #subLevelRef(UUID)} when a native NBT field points at another copied
 * block or sub-level.</p>
 */
public class BlueprintBlockSaveContext {
    private final BlueprintSaveSession session;
    private final SubLevelSaveFrame frame;
    private final ServerLevel level;
    private final ServerSubLevel subLevel;
    private final int blueprintSubLevelId;
    private final BlockPos storagePos;
    private final BlockPos localPos;
    private final BlockState state;
    private final @Nullable BlockEntity blockEntity;
    private @Nullable CompoundTag defaultBlockEntityTag;

    /**
     * Creates a save-time block mapper context.
     *
     * @param session     save session
     * @param frame       source sub-level frame
     * @param storagePos  source storage position
     * @param localPos    blueprint-local block position
     * @param state       source block state
     * @param blockEntity source block entity, or {@code null}
     */
    public BlueprintBlockSaveContext(final BlueprintSaveSession session,
                                     final SubLevelSaveFrame frame,
                                     final BlockPos storagePos,
                                     final BlockPos localPos,
                                     final BlockState state,
                                     @Nullable final BlockEntity blockEntity) {
        this.session = session;
        this.frame = frame;
        this.level = session.level();
        this.subLevel = frame.subLevel();
        this.blueprintSubLevelId = frame.blueprintId();
        this.storagePos = storagePos.immutable();
        this.localPos = localPos.immutable();
        this.state = state;
        this.blockEntity = blockEntity;
    }

    /**
     * Returns the save session that owns this mapper call.
     *
     * @return save session that owns this mapper call
     */
    public BlueprintSaveSession session() {
        return this.session;
    }

    /**
     * Returns the source sub-level frame currently being saved.
     *
     * @return source sub-level frame currently being saved
     */
    public SubLevelSaveFrame frame() {
        return this.frame;
    }

    /**
     * Returns the source server level.
     *
     * @return source server level
     */
    public ServerLevel level() {
        return this.level;
    }

    /**
     * Returns the source Sable sub-level containing the block.
     *
     * @return source Sable sub-level containing the block
     */
    public ServerSubLevel subLevel() {
        return this.subLevel;
    }

    /**
     * Returns the UUID of the source sub-level at save time.
     *
     * @return UUID of the source sub-level at save time
     */
    public UUID sourceSubLevelUuid() {
        return this.subLevel.getUniqueId();
    }

    /**
     * Returns the blueprint-local id assigned to the source sub-level.
     *
     * @return blueprint-local id assigned to the source sub-level
     */
    public int blueprintSubLevelId() {
        return this.blueprintSubLevelId;
    }

    /**
     * Returns the source storage position of the block.
     *
     * @return source storage position of the block
     */
    public BlockPos storagePos() {
        return this.storagePos;
    }

    /**
     * Returns the blueprint-local block position.
     *
     * @return blueprint-local block position relative to the saved sub-level payload
     */
    public BlockPos localPos() {
        return this.localPos;
    }

    /**
     * Returns the source block state being saved.
     *
     * @return source block state being saved
     */
    public BlockState state() {
        return this.state;
    }

    /**
     * Returns the source block entity.
     *
     * @return source block entity, or {@code null} when the block has none
     */
    public @Nullable BlockEntity blockEntity() {
        return this.blockEntity;
    }

    /**
     * Returns registry access from the source level.
     *
     * @return registry access from the source level
     */
    public RegistryAccess registryAccess() {
        return this.level.registryAccess();
    }

    /**
     * Returns a blueprint-local reference to this block.
     *
     * @return blueprint-local reference to this block
     */
    public BlueprintBlockRef selfRef() {
        return new BlueprintBlockRef(this.blueprintSubLevelId, this.localPos);
    }

    /**
     * Converts a source storage position to a blueprint-local block reference.
     *
     * @param sourceStoragePos source storage position
     * @return blueprint-local block reference when the position is inside a saved frame
     */
    public Optional<BlueprintBlockRef> blockRef(final BlockPos sourceStoragePos) {
        return this.session.blockRef(sourceStoragePos);
    }

    /**
     * Converts a source sub-level UUID to a blueprint-local sub-level reference.
     *
     * @param sourceUuid source sub-level UUID at save time
     * @return blueprint-local sub-level reference when the sub-level is part of the blueprint
     */
    public Optional<BlueprintSubLevelRef> subLevelRef(final UUID sourceUuid) {
        return this.session.subLevelRef(sourceUuid);
    }

    /**
     * Saves the source block entity using vanilla full metadata.
     *
     * <p>The value is cached for this context and returned as a defensive copy.
     * Mappers may call this if they need the default tag after inspecting runtime state.</p>
     *
     * @return default block entity tag, or {@code null} when the block has no block entity
     */
    public @Nullable CompoundTag saveDefaultBlockEntityTag() {
        if (this.blockEntity == null) {
            return null;
        }

        if (this.defaultBlockEntityTag == null) {
            this.defaultBlockEntityTag = this.blockEntity.saveWithFullMetadata(this.registryAccess());
        }

        return this.defaultBlockEntityTag.copy();
    }
}

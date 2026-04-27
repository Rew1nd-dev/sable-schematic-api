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

    public BlueprintSaveSession session() {
        return this.session;
    }

    public SubLevelSaveFrame frame() {
        return this.frame;
    }

    public ServerLevel level() {
        return this.level;
    }

    public ServerSubLevel subLevel() {
        return this.subLevel;
    }

    public UUID sourceSubLevelUuid() {
        return this.subLevel.getUniqueId();
    }

    public int blueprintSubLevelId() {
        return this.blueprintSubLevelId;
    }

    public BlockPos storagePos() {
        return this.storagePos;
    }

    public BlockPos localPos() {
        return this.localPos;
    }

    public BlockState state() {
        return this.state;
    }

    public @Nullable BlockEntity blockEntity() {
        return this.blockEntity;
    }

    public RegistryAccess registryAccess() {
        return this.level.registryAccess();
    }

    public BlueprintBlockRef selfRef() {
        return new BlueprintBlockRef(this.blueprintSubLevelId, this.localPos);
    }

    public Optional<BlueprintBlockRef> blockRef(final BlockPos sourceStoragePos) {
        return this.session.blockRef(sourceStoragePos);
    }

    public Optional<BlueprintSubLevelRef> subLevelRef(final UUID sourceUuid) {
        return this.session.subLevelRef(sourceUuid);
    }

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

package dev.rew1nd.sableschematicapi.api.blueprint;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Per-block context exposed to blueprint place mappers.
 */
public class BlueprintBlockPlaceContext {
    private final BlueprintPlaceSession session;
    private final ServerLevel level;
    private final ServerSubLevel subLevel;
    private final int blueprintSubLevelId;
    private final UUID sourceSubLevelUuid;
    private final BlockPos localPos;
    private final BlockPos storagePos;
    private final BlockState state;

    public BlueprintBlockPlaceContext(final BlueprintPlaceSession session,
                                      final ServerSubLevel subLevel,
                                      final int blueprintSubLevelId,
                                      final UUID sourceSubLevelUuid,
                                      final BlockPos localPos,
                                      final BlockPos storagePos,
                                      final BlockState state) {
        this.session = session;
        this.level = session.level();
        this.subLevel = subLevel;
        this.blueprintSubLevelId = blueprintSubLevelId;
        this.sourceSubLevelUuid = sourceSubLevelUuid;
        this.localPos = localPos.immutable();
        this.storagePos = storagePos.immutable();
        this.state = state;
    }

    public BlueprintPlaceSession session() {
        return this.session;
    }

    public BlueprintPlacePhase phase() {
        return this.session.phase();
    }

    public ServerLevel level() {
        return this.level;
    }

    public ServerSubLevel subLevel() {
        return this.subLevel;
    }

    public int blueprintSubLevelId() {
        return this.blueprintSubLevelId;
    }

    public UUID sourceSubLevelUuid() {
        return this.sourceSubLevelUuid;
    }

    public UUID placedSubLevelUuid() {
        return this.subLevel.getUniqueId();
    }

    public BlockPos localPos() {
        return this.localPos;
    }

    public BlockPos sourceLocalPos() {
        return this.localPos;
    }

    public BlockPos storagePos() {
        return this.storagePos;
    }

    public BlockPos placedStoragePos() {
        return this.storagePos;
    }

    public BlockPos placedSubLevelLocalPos() {
        return this.localPos;
    }

    public BlockState state() {
        return this.state;
    }

    public BlockState placedState() {
        return this.state;
    }

    public @Nullable UUID mapSubLevel(final UUID sourceUuid) {
        return this.session.mapSubLevel(sourceUuid);
    }

    public @Nullable BlockPos mapBlock(final BlueprintBlockRef ref) {
        return this.session.mapBlock(ref);
    }

    public @Nullable BlockPos mapBlockPos(final BlueprintBlockRef ref) {
        return this.session.mapBlock(ref);
    }

    public UUID allocateMappedUuid(final UUID sourceUuid) {
        return this.session.allocateMappedUuid(sourceUuid);
    }

    public void deferAfterBlockEntities(final Runnable task) {
        this.session.deferAfterBlockEntities(task);
    }
}

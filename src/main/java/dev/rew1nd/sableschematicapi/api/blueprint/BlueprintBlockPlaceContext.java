package dev.rew1nd.sableschematicapi.api.blueprint;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Per-block context exposed to blueprint place mappers.
 *
 * <p>Positions in this context refer to the placed copy. The original source
 * sub-level UUID remains available so saved sidecar references can be mapped to
 * the new placed sub-level UUID.</p>
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

    /**
     * Creates a placed-block mapper context.
     *
     * @param session              placement session
     * @param subLevel             placed sub-level containing the block
     * @param blueprintSubLevelId  blueprint-local source sub-level id
     * @param sourceSubLevelUuid   source sub-level UUID at save time
     * @param localPos             blueprint-local block position
     * @param storagePos           placed storage position
     * @param state                placed block state
     */
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

    /**
     * Returns the placement session that owns this mapper call.
     *
     * @return placement session that owns this mapper call
     */
    public BlueprintPlaceSession session() {
        return this.session;
    }

    /**
     * Returns the current placement phase.
     *
     * @return current placement phase
     */
    public BlueprintPlacePhase phase() {
        return this.session.phase();
    }

    /**
     * Returns the target server level.
     *
     * @return target server level
     */
    public ServerLevel level() {
        return this.level;
    }

    /**
     * Returns the placed Sable sub-level containing the block.
     *
     * @return placed Sable sub-level containing the block
     */
    public ServerSubLevel subLevel() {
        return this.subLevel;
    }

    /**
     * Returns the blueprint-local id of the source sub-level.
     *
     * @return blueprint-local id of the source sub-level
     */
    public int blueprintSubLevelId() {
        return this.blueprintSubLevelId;
    }

    /**
     * Returns the UUID of the source sub-level at save time.
     *
     * @return UUID of the source sub-level at save time
     */
    public UUID sourceSubLevelUuid() {
        return this.sourceSubLevelUuid;
    }

    /**
     * Returns the UUID of the newly placed sub-level.
     *
     * @return UUID of the newly placed sub-level
     */
    public UUID placedSubLevelUuid() {
        return this.subLevel.getUniqueId();
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
     * Alias for {@link #localPos()} kept for mapper readability.
     *
     * @return blueprint-local block position
     */
    public BlockPos sourceLocalPos() {
        return this.localPos;
    }

    /**
     * Returns the placed storage position of the block.
     *
     * @return placed storage position of the block
     */
    public BlockPos storagePos() {
        return this.storagePos;
    }

    /**
     * Alias for {@link #storagePos()} kept for mapper readability.
     *
     * @return placed storage position of the block
     */
    public BlockPos placedStoragePos() {
        return this.storagePos;
    }

    /**
     * Alias for {@link #localPos()}.
     *
     * @return blueprint-local block position
     */
    public BlockPos placedSubLevelLocalPos() {
        return this.localPos;
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
     * Alias for {@link #state()}.
     *
     * @return placed block state
     */
    public BlockState placedState() {
        return this.state;
    }

    /**
     * Maps a source sub-level UUID to the corresponding placed sub-level UUID.
     *
     * @param sourceUuid source sub-level UUID at save time
     * @return placed sub-level UUID, or {@code null} when the source sub-level was not copied
     */
    public @Nullable UUID mapSubLevel(final UUID sourceUuid) {
        return this.session.mapSubLevel(sourceUuid);
    }

    /**
     * Maps a blueprint-local block reference to a placed storage position.
     *
     * @param ref blueprint-local block reference
     * @return placed storage position, or {@code null} when the referenced sub-level was not copied
     */
    public @Nullable BlockPos mapBlock(final BlueprintBlockRef ref) {
        return this.session.mapBlock(ref);
    }

    /**
     * Alias for {@link #mapBlock(BlueprintBlockRef)}.
     *
     * @param ref blueprint-local block reference
     * @return placed storage position, or {@code null}
     */
    public @Nullable BlockPos mapBlockPos(final BlueprintBlockRef ref) {
        return this.session.mapBlock(ref);
    }

    /**
     * Allocates or returns a placement-local replacement UUID for a source UUID.
     *
     * @param sourceUuid source UUID stored in the blueprint
     * @return stable replacement UUID for this placement
     */
    public UUID allocateMappedUuid(final UUID sourceUuid) {
        return this.session.allocateMappedUuid(sourceUuid);
    }

    /**
     * Schedules work after all copied block entities have loaded.
     *
     * @param task task to run after block entity loading
     */
    public void deferAfterBlockEntities(final Runnable task) {
        this.session.deferAfterBlockEntities(task);
    }
}

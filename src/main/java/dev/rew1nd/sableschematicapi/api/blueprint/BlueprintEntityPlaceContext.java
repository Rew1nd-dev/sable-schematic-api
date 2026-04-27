package dev.rew1nd.sableschematicapi.api.blueprint;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.UUID;

/**
 * Per-entity context exposed to blueprint place mappers.
 *
 * <p>The placer rewrites the entity's vanilla {@code Pos} before this context is
 * exposed. Use this context to map additional custom anchors, sub-level UUIDs,
 * or manager references stored inside entity NBT.</p>
 */
public class BlueprintEntityPlaceContext {
    private final BlueprintPlaceSession session;
    private final ServerLevel level;
    private final ServerSubLevel subLevel;
    private final int blueprintSubLevelId;
    private final UUID sourceSubLevelUuid;
    private final Vector3d localPos;
    private final Vector3d storagePos;

    /**
     * Creates a placed-entity mapper context.
     *
     * @param session              placement session
     * @param subLevel             placed sub-level that will contain the entity
     * @param blueprintSubLevelId  blueprint-local source sub-level id
     * @param sourceSubLevelUuid   source sub-level UUID at save time
     * @param localPos             saved blueprint-local entity position
     * @param storagePos           placed storage position
     */
    public BlueprintEntityPlaceContext(final BlueprintPlaceSession session,
                                       final ServerSubLevel subLevel,
                                       final int blueprintSubLevelId,
                                       final UUID sourceSubLevelUuid,
                                       final Vector3dc localPos,
                                       final Vector3dc storagePos) {
        this.session = session;
        this.level = session.level();
        this.subLevel = subLevel;
        this.blueprintSubLevelId = blueprintSubLevelId;
        this.sourceSubLevelUuid = sourceSubLevelUuid;
        this.localPos = new Vector3d(localPos);
        this.storagePos = new Vector3d(storagePos);
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
     * Returns the placed Sable sub-level that will contain the entity.
     *
     * @return placed Sable sub-level that will contain the entity
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
     * Returns the saved blueprint-local entity position.
     *
     * @return saved blueprint-local entity position
     */
    public Vector3dc localPos() {
        return this.localPos;
    }

    /**
     * Returns the placed storage position written to the entity's vanilla {@code Pos}.
     *
     * @return placed storage position written to the entity's vanilla {@code Pos}
     */
    public Vector3dc storagePos() {
        return this.storagePos;
    }

    /**
     * Returns the placed block origin for the entity's blueprint sub-level.
     *
     * @return placed block origin for the entity's blueprint sub-level, or {@code null}
     */
    public @Nullable BlockPos placedBlocksOrigin() {
        return this.session.placedBlockOrigin(this.blueprintSubLevelId);
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
     * Allocates or returns a placement-local replacement UUID for a source UUID.
     *
     * @param sourceUuid source UUID stored in the blueprint
     * @return stable replacement UUID for this placement
     */
    public UUID allocateMappedUuid(final UUID sourceUuid) {
        return this.session.allocateMappedUuid(sourceUuid);
    }
}

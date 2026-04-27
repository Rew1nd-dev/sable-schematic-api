package dev.rew1nd.sableschematicapi.api.blueprint;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Optional;
import java.util.UUID;

/**
 * Per-entity context exposed to blueprint save mappers.
 *
 * <p>The entity's default {@code Pos} is saved separately as blueprint-local
 * {@link #localPos()}. Entity mappers should use this context to rewrite any
 * additional source storage references stored inside custom entity NBT.</p>
 */
public class BlueprintEntitySaveContext {
    private final BlueprintSaveSession session;
    private final SubLevelSaveFrame frame;
    private final ServerLevel level;
    private final ServerSubLevel subLevel;
    private final int blueprintSubLevelId;
    private final Entity entity;
    private final Vector3d localPos;

    /**
     * Creates a save-time entity mapper context.
     *
     * @param session  save session
     * @param frame    source sub-level frame
     * @param entity   source entity being saved
     * @param localPos entity position relative to the saved sub-level block origin
     */
    public BlueprintEntitySaveContext(final BlueprintSaveSession session,
                                      final SubLevelSaveFrame frame,
                                      final Entity entity,
                                      final Vector3dc localPos) {
        this.session = session;
        this.frame = frame;
        this.level = session.level();
        this.subLevel = frame.subLevel();
        this.blueprintSubLevelId = frame.blueprintId();
        this.entity = entity;
        this.localPos = new Vector3d(localPos);
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
     * Returns the source Sable sub-level containing the entity.
     *
     * @return source Sable sub-level containing the entity
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
     * Returns the source entity being saved.
     *
     * @return source entity being saved
     */
    public Entity entity() {
        return this.entity;
    }

    /**
     * Returns the entity position relative to the saved sub-level block origin.
     *
     * @return entity position relative to the saved sub-level block origin
     */
    public Vector3dc localPos() {
        return this.localPos;
    }

    /**
     * Returns the source storage origin used for blueprint-local conversion.
     *
     * @return source storage origin used to convert entity and anchor positions to blueprint-local coordinates
     */
    public BlockPos blocksOrigin() {
        return this.frame.blocksOrigin();
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
}

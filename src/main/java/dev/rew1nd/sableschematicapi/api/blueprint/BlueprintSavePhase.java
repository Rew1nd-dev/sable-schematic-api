package dev.rew1nd.sableschematicapi.api.blueprint;

/**
 * Lifecycle phases for one blueprint export operation.
 */
public enum BlueprintSavePhase {
    /**
     * Intersecting Sable sub-levels are being selected from the root bounds.
     */
    SELECT_SUBLEVELS,
    /**
     * Save frames and blueprint-local sub-level ids are being created.
     */
    BUILD_FRAMES,
    /**
     * Global events may prepare sidecar data before block data is read.
     */
    BEFORE_BLOCKS,
    /**
     * Blocks and block entity payloads are being written.
     */
    SAVE_BLOCKS,
    /**
     * Global events may inspect saved blocks and write block-based sidecar data.
     */
    AFTER_BLOCKS,
    /**
     * Entities and entity mapper payloads are being written.
     */
    SAVE_ENTITIES,
    /**
     * Global events may inspect final entity data before blueprint finalization.
     */
    AFTER_SAVE,
    /**
     * Blueprint output is being finalized.
     */
    FINALIZE
}

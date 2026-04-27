package dev.rew1nd.sableschematicapi.api.blueprint;

/**
 * Lifecycle phases for one blueprint placement operation.
 */
public enum BlueprintPlacePhase {
    /**
     * Blueprint payload is being decoded.
     */
    DECODE,
    /**
     * Destination Sable sub-levels are being allocated.
     */
    ALLOCATE_SUBLEVELS,
    /**
     * Source references are being mapped to placed sub-levels and block origins.
     */
    BUILD_MAPPINGS,
    /**
     * Global events may prepare state before block states are written.
     */
    BEFORE_BLOCKS,
    /**
     * Block states are being written into placed sub-level storage.
     */
    PLACE_BLOCK_STATES,
    /**
     * Block entity payloads are being loaded and block mappers are running.
     */
    LOAD_BLOCK_ENTITIES,
    /**
     * Deferred block entity mapper tasks and global post-block-entity events are running.
     */
    AFTER_BLOCK_ENTITIES,
    /**
     * Saved entities are being created in placed sub-level storage.
     */
    PLACE_ENTITIES,
    /**
     * Block notifications, bounding boxes, and related world updates are running.
     */
    WORLD_UPDATES,
    /**
     * Final global placement events are running.
     */
    AFTER_PLACE,
    /**
     * Placement result is being finalized.
     */
    FINALIZE
}

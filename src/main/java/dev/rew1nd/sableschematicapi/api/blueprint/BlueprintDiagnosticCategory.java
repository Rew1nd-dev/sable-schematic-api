package dev.rew1nd.sableschematicapi.api.blueprint;

/**
 * Recoverable blueprint issue category.
 */
public enum BlueprintDiagnosticCategory {
    MISSING_BLOCK_STATE,
    INVALID_PALETTE_REFERENCE,
    INVALID_BLOCK_DATA,
    INVALID_BLOCK_ENTITY_REFERENCE,
    SKIPPED_BLOCK,
    BLOCK_PLACE_FAILED,
    BLOCK_ENTITY_LOAD_FAILED,
    ENTITY_SKIPPED,
    ENTITY_PLACE_FAILED,
    MAPPER_FAILED,
    EVENT_FAILED,
    TASK_FAILED,
    WORLD_UPDATE_FAILED
}

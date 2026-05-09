package dev.rew1nd.sableschematicapi.api.blueprint;

/**
 * Blueprint lifecycle stage that produced a diagnostic.
 */
public enum BlueprintDiagnosticStage {
    DECODE,
    BEFORE_BLOCKS,
    PLACE_BLOCK_STATES,
    LOAD_BLOCK_ENTITIES,
    AFTER_BLOCK_ENTITIES,
    PLACE_ENTITIES,
    WORLD_UPDATES,
    AFTER_PLACE,
    FINALIZE
}

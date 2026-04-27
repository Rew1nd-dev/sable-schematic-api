package dev.rew1nd.sableschematicapi.api.blueprint;

public enum BlueprintPlacePhase {
    DECODE,
    ALLOCATE_SUBLEVELS,
    BUILD_MAPPINGS,
    BEFORE_BLOCKS,
    PLACE_BLOCK_STATES,
    LOAD_BLOCK_ENTITIES,
    AFTER_BLOCK_ENTITIES,
    PLACE_ENTITIES,
    WORLD_UPDATES,
    AFTER_PLACE,
    FINALIZE
}

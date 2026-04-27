package dev.rew1nd.sableschematicapi.api.blueprint;

public enum BlueprintSavePhase {
    SELECT_SUBLEVELS,
    BUILD_FRAMES,
    BEFORE_BLOCKS,
    SAVE_BLOCKS,
    AFTER_BLOCKS,
    SAVE_ENTITIES,
    AFTER_SAVE,
    FINALIZE
}

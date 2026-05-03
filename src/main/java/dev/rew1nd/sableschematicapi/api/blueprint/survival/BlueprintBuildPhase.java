package dev.rew1nd.sableschematicapi.api.blueprint.survival;

/**
 * Persistent phase of an incremental survival blueprint build.
 */
public enum BlueprintBuildPhase {
    DECODE,
    PLAN,
    ALLOCATE_AND_PLACE_BLOCKS,
    PRE_COMMIT_VALIDATE,
    COMMIT_RUNTIME_STATE,
    WORLD_UPDATES,
    FINALIZE,
    DONE,
    FAILED
}

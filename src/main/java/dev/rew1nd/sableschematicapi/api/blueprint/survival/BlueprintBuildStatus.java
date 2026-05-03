package dev.rew1nd.sableschematicapi.api.blueprint.survival;

/**
 * Result status returned by one incremental survival placer step.
 */
public enum BlueprintBuildStatus {
    IDLE,
    CONTINUE,
    WAITING_FOR_MATERIALS,
    WAITING_FOR_CHUNK,
    COMMIT_READY,
    DONE,
    FAILED
}

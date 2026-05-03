package dev.rew1nd.sableschematicapi.api.blueprint.survival;

/**
 * Survival-build admission mode for a saved block entity payload.
 */
public enum BlueprintNbtLoadMode {
    /**
     * Do not load the saved block entity payload.
     */
    NONE,
    /**
     * Load a sanitized copy of the saved block entity payload.
     */
    SANITIZED,
    /**
     * Load the saved block entity payload as-is.
     */
    FULL,
    /**
     * Refuse to build this block in survival mode.
     */
    DENY
}

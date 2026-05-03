package dev.rew1nd.sableschematicapi.api.blueprint.survival;

/**
 * Per-tick work budget for the incremental survival placer.
 */
public record BlueprintBuildTickBudget(int maxBlocks, int maxBlockEntities, int maxPostProcessOperations, long maxNanos) {
    public static final BlueprintBuildTickBudget DEFAULT = new BlueprintBuildTickBudget(64, 4096, 4096, 0L);

    public int blockLimit() {
        return Math.max(1, this.maxBlocks);
    }
}

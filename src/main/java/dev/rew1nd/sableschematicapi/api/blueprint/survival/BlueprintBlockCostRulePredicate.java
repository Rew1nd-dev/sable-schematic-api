package dev.rew1nd.sableschematicapi.api.blueprint.survival;

/**
 * Predicate used to select the first matching block cost rule for a survival
 * blueprint block payload.
 */
@FunctionalInterface
public interface BlueprintBlockCostRulePredicate {
    boolean matches(BlueprintBuildBlockPayload payload);
}

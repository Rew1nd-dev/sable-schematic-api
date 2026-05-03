package dev.rew1nd.sableschematicapi.api.blueprint.survival;

import net.minecraft.network.chat.Component;

/**
 * Result returned by one incremental survival placer step.
 */
public record BlueprintBuildStepResult(BlueprintBuildStatus status,
                                       int affectedBlocks,
                                       CostQuote missingCost,
                                       Component message) {
    public static BlueprintBuildStepResult status(final BlueprintBuildStatus status, final Component message) {
        return new BlueprintBuildStepResult(status, 0, CostQuote.empty(CostTiming.INFORMATIONAL), message);
    }

    public static BlueprintBuildStepResult continueWith(final int affectedBlocks) {
        return new BlueprintBuildStepResult(
                BlueprintBuildStatus.CONTINUE,
                affectedBlocks,
                CostQuote.empty(CostTiming.INFORMATIONAL),
                Component.empty()
        );
    }

    public static BlueprintBuildStepResult waitingForMaterials(final CostQuote quote) {
        return new BlueprintBuildStepResult(
                BlueprintBuildStatus.WAITING_FOR_MATERIALS,
                0,
                quote,
                Component.literal("Waiting for blueprint build materials.")
        );
    }

    public static BlueprintBuildStepResult failed(final String message) {
        return status(BlueprintBuildStatus.FAILED, Component.literal(message));
    }
}

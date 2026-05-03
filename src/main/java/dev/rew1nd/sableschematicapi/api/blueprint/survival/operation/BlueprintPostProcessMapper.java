package dev.rew1nd.sableschematicapi.api.blueprint.survival.operation;

import net.minecraft.resources.ResourceLocation;

/**
 * Executes a typed post-process operation during the commit phase.
 *
 * @param <T> the operation type
 */
public interface BlueprintPostProcessMapper<T extends BlueprintPostProcessOperation> {
    /**
     * The operation type this mapper handles. Must match {@link BlueprintPostProcessOperation#type()}.
     */
    ResourceLocation type();

    /**
     * Validates whether this operation can be executed in the current placement context.
     * Operations that fail admission are skipped and recorded as warnings.
     */
    AdmissionResult admit(T operation, BlueprintPostProcessAdmissionContext context);

    /**
     * Executes the operation. Called after all admission checks and cost consumption
     * have succeeded.
     */
    void apply(T operation, BlueprintPostProcessContext context);
}

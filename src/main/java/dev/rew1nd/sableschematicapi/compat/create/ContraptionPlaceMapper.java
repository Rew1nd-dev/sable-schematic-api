package dev.rew1nd.sableschematicapi.compat.create;

import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.AdmissionResult;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintPostProcessAdmissionContext;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintPostProcessContext;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintPostProcessMapper;
import net.minecraft.resources.ResourceLocation;

/**
 * Admission and application for contraption place operations.
 *
 * <p>Admission is always granted. Application is a no-op because the actual
 * entity creation happens in the existing {@code placeEntities()} pipeline
 * via {@link CreateContraptionEntityMapper}.</p>
 */
public final class ContraptionPlaceMapper implements BlueprintPostProcessMapper<ContraptionPlaceOperation> {

    @Override
    public ResourceLocation type() {
        return ContraptionPlaceOperation.TYPE;
    }

    @Override
    public AdmissionResult admit(final ContraptionPlaceOperation operation,
                                  final BlueprintPostProcessAdmissionContext context) {
        // Contraption entities from blueprints are always admissible.
        // The existing entity pipeline handles anchor remapping and creation.
        return AdmissionResult.admit();
    }

    @Override
    public void apply(final ContraptionPlaceOperation operation,
                       final BlueprintPostProcessContext context) {
        // No-op: the entity is created by placeEntities() → CreateContraptionEntityMapper
    }
}

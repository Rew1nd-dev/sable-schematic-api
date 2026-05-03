package dev.rew1nd.sableschematicapi.compat.create;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintPostProcessOperation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Typed post-process operation for one contraption entity that will be placed
 * during the commit phase.
 *
 * <p>The entity itself is created by the existing {@code placeEntities()} pipeline.
 * This operation only carries cost and admission. The mapper's {@code apply()} is a no-op.</p>
 */
public record ContraptionPlaceOperation(
        int subLevelId,
        int entityIndex,
        String entityTypeKey,
        CompoundTag entityTag
) implements BlueprintPostProcessOperation {

    public static final ResourceLocation TYPE =
            SableSchematicApi.id("create/contraption/place");

    @Override
    public ResourceLocation type() {
        return TYPE;
    }

    @Override
    public String stableKey() {
        return TYPE + "#" + subLevelId + "/" + entityIndex;
    }
}

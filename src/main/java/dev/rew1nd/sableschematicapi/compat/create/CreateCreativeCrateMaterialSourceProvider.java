package dev.rew1nd.sableschematicapi.compat.create;

import com.simibubi.create.AllBlocks;
import dev.rew1nd.sableschematicapi.survival.BlueprintCannonMaterialSource;
import dev.rew1nd.sableschematicapi.survival.BlueprintCannonMaterialSourceContext;
import dev.rew1nd.sableschematicapi.survival.BlueprintCannonMaterialSourceProvider;
import dev.rew1nd.sableschematicapi.survival.BlueprintCannonMaterialSources;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

final class CreateCreativeCrateMaterialSourceProvider implements BlueprintCannonMaterialSourceProvider {
    @Override
    public @Nullable BlueprintCannonMaterialSource create(final BlueprintCannonMaterialSourceContext context) {
        if (context.sourceDirection() != Direction.DOWN) {
            return null;
        }
        if (!context.state().is(AllBlocks.CREATIVE_CRATE.get())) {
            return null;
        }

        return BlueprintCannonMaterialSources.unlimited();
    }
}

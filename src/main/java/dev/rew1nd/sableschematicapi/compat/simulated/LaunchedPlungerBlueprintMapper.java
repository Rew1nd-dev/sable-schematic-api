package dev.rew1nd.sableschematicapi.compat.simulated;

import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintEntitySaveContext;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintEntityMapper;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

final class LaunchedPlungerBlueprintMapper implements SableBlueprintEntityMapper {
    @Override
    public @Nullable CompoundTag save(final BlueprintEntitySaveContext context, @Nullable final CompoundTag defaultTag) {
        return null;
    }
}

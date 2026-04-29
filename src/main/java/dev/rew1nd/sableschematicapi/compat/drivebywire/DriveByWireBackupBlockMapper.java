package dev.rew1nd.sableschematicapi.compat.drivebywire;

import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockSaveContext;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintBlockMapper;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

final class DriveByWireBackupBlockMapper implements SableBlueprintBlockMapper {
    @Override
    public @Nullable CompoundTag save(final BlueprintBlockSaveContext context, @Nullable final CompoundTag defaultTag) {
        return null;
    }
}

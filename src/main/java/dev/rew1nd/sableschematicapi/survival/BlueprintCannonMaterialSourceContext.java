package dev.rew1nd.sableschematicapi.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Context used to create a material source for one candidate block around a cannon.
 */
public record BlueprintCannonMaterialSourceContext(ServerLevel level,
                                                   BlockPos cannonPos,
                                                   BlockPos sourcePos,
                                                   Direction sourceDirection,
                                                   Direction sourceSide,
                                                   BlockState state,
                                                   @Nullable BlockEntity blockEntity) {
    public BlueprintCannonMaterialSourceContext {
        cannonPos = cannonPos.immutable();
        sourcePos = sourcePos.immutable();
    }
}

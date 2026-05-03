package dev.rew1nd.sableschematicapi.api.blueprint.survival;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Context supplied to survival block material rules.
 */
public record BlueprintBlockCostContext(ServerLevel level, Vec3 origin) {
}

package dev.rew1nd.sableschematicapi.survival;

import net.minecraft.world.item.ItemStack;

/**
 * One material source visible to a blueprint cannon.
 */
public interface BlueprintCannonMaterialSource {
    boolean unlimited();

    int available(ItemStack required);

    int consume(ItemStack required, int count);
}

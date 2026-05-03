package dev.rew1nd.sableschematicapi.api.blueprint.survival;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * One material line in a survival blueprint quote.
 *
 * @param ruleId      rule that produced this line
 * @param stack       required item stack template and count
 * @param description user-facing description
 */
public record CostLine(ResourceLocation ruleId, ItemStack stack, Component description) {
    public CostLine {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(description, "description");
        stack = stack.copy();
    }

    @Override
    public ItemStack stack() {
        return this.stack.copy();
    }
}

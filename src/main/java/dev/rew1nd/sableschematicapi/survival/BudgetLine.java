package dev.rew1nd.sableschematicapi.survival;

import net.minecraft.world.item.ItemStack;

/**
 * One line in the estimated budget display for the blueprint cannon UI.
 *
 * @param item      the required item
 * @param required  how many are needed
 * @param available how many are currently in nearby inventories
 * @param unlimited whether an unlimited material source satisfies this line
 */
public record BudgetLine(ItemStack item, int required, int available, boolean unlimited) {
    public BudgetLine(final ItemStack item, final int required, final int available) {
        this(item, required, available, false);
    }

    public BudgetLine {
        item = item.copyWithCount(1);
    }

    public boolean satisfied() {
        return this.unlimited || this.available >= this.required;
    }

    public ItemStack displayStack() {
        return this.item.copyWithCount(this.required);
    }
}

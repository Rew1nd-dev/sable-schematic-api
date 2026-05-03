package dev.rew1nd.sableschematicapi.survival;

import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBuildMaterialBudget;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.ConsumeResult;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostLine;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostQuote;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Material budget backed by item-handler capabilities around and below the cannon.
 */
public final class BlueprintCannonMaterialBudget implements BlueprintBuildMaterialBudget {
    private final ServerLevel level;
    private final BlockPos cannonPos;

    public BlueprintCannonMaterialBudget(final ServerLevel level, final BlockPos cannonPos) {
        this.level = level;
        this.cannonPos = cannonPos.immutable();
    }

    public static int countMaterialSources(final ServerLevel level, final BlockPos cannonPos) {
        return BlueprintCannonMaterialSources.sources(level, cannonPos).size();
    }

    @Override
    public boolean canAfford(final CostQuote quote) {
        for (final ItemStack required : compact(quote.lines())) {
            if (!canAfford(required)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ConsumeResult consume(final CostQuote quote) {
        final List<ItemStack> requiredStacks = compact(quote.lines());
        for (final ItemStack required : requiredStacks) {
            if (!canAfford(required)) {
                return ConsumeResult.failure(Component.literal("Nearby inventories are missing blueprint materials."));
            }
        }

        for (final ItemStack required : requiredStacks) {
            consume(required);
        }
        return ConsumeResult.success();
    }

    private boolean canAfford(final ItemStack required) {
        if (required.isEmpty()) {
            return true;
        }

        int available = 0;
        for (final BlueprintCannonMaterialSource source : BlueprintCannonMaterialSources.sources(this.level, this.cannonPos)) {
            if (source.unlimited()) {
                return true;
            }
            available = saturatedAdd(available, source.available(required));
            if (available >= required.getCount()) {
                return true;
            }
        }
        return false;
    }

    private void consume(final ItemStack required) {
        final List<BlueprintCannonMaterialSource> sources = BlueprintCannonMaterialSources.sources(this.level, this.cannonPos);
        for (final BlueprintCannonMaterialSource source : sources) {
            if (source.unlimited()) {
                source.consume(required, required.getCount());
                return;
            }
        }

        int remaining = required.getCount();
        for (final BlueprintCannonMaterialSource source : sources) {
            remaining -= source.consume(required, remaining);
            if (remaining <= 0) {
                return;
            }
        }
    }

    private static List<ItemStack> compact(final List<CostLine> lines) {
        final List<ItemStack> stacks = new ArrayList<>();
        for (final CostLine line : lines) {
            final ItemStack stack = line.stack();
            if (stack.isEmpty()) {
                continue;
            }

            ItemStack existing = null;
            for (final ItemStack candidate : stacks) {
                if (ItemStack.isSameItemSameComponents(candidate, stack)) {
                    existing = candidate;
                    break;
                }
            }
            if (existing == null) {
                stacks.add(stack);
            } else {
                existing.setCount(existing.getCount() + stack.getCount());
            }
        }
        return stacks;
    }

    /**
     * Counts how many of the given item are available in inventories around the cannon.
     */
    public static int countAvailable(final ServerLevel level,
                                      final BlockPos cannonPos,
                                      final ItemStack required) {
        final Availability availability = availability(level, cannonPos, required);
        return availability.unlimited() ? Integer.MAX_VALUE : availability.available();
    }

    public static Availability availability(final ServerLevel level,
                                            final BlockPos cannonPos,
                                            final ItemStack required) {
        int total = 0;
        for (final BlueprintCannonMaterialSource source : BlueprintCannonMaterialSources.sources(level, cannonPos)) {
            if (source.unlimited()) {
                return new Availability(total, true);
            }
            total = saturatedAdd(total, source.available(required));
        }
        return new Availability(total, false);
    }

    private static int saturatedAdd(final int left, final int right) {
        final long sum = (long) left + right;
        return sum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }

    public record Availability(int available, boolean unlimited) {
    }
}

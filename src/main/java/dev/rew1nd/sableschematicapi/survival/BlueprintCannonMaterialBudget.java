package dev.rew1nd.sableschematicapi.survival;

import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBuildMaterialBudget;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.ConsumeResult;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostLine;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostQuote;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Material budget backed by item-handler capabilities around and below the cannon.
 */
public final class BlueprintCannonMaterialBudget implements BlueprintBuildMaterialBudget {
    private static final Direction[] SOURCE_DIRECTIONS = {
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST,
            Direction.DOWN
    };

    private final ServerLevel level;
    private final BlockPos cannonPos;

    public BlueprintCannonMaterialBudget(final ServerLevel level, final BlockPos cannonPos) {
        this.level = level;
        this.cannonPos = cannonPos.immutable();
    }

    public static int countMaterialSources(final ServerLevel level, final BlockPos cannonPos) {
        int count = 0;
        for (final Source source : sources(level, cannonPos)) {
            if (source.handler() != null) {
                count++;
            }
        }
        return count;
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
        for (final Source source : sources(this.level, this.cannonPos)) {
            final IItemHandler handler = source.handler();
            if (handler == null) {
                continue;
            }
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                final ItemStack stack = handler.getStackInSlot(slot);
                if (ItemStack.isSameItemSameComponents(required, stack)) {
                    available += stack.getCount();
                    if (available >= required.getCount()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void consume(final ItemStack required) {
        int remaining = required.getCount();
        for (final Source source : sources(this.level, this.cannonPos)) {
            final IItemHandler handler = source.handler();
            if (handler == null) {
                continue;
            }
            for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
                final ItemStack stack = handler.getStackInSlot(slot);
                if (!ItemStack.isSameItemSameComponents(required, stack)) {
                    continue;
                }

                final int requested = Math.min(remaining, stack.getCount());
                final ItemStack extracted = handler.extractItem(slot, requested, false);
                if (ItemStack.isSameItemSameComponents(required, extracted)) {
                    remaining -= extracted.getCount();
                }
            }
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

    private static List<Source> sources(final ServerLevel level, final BlockPos cannonPos) {
        final List<Source> sources = new ArrayList<>(SOURCE_DIRECTIONS.length);
        for (final Direction direction : SOURCE_DIRECTIONS) {
            final BlockPos sourcePos = cannonPos.relative(direction);
            final Direction side = sideFor(direction);
            sources.add(new Source(sourcePos, capability(level, sourcePos, side)));
        }
        return sources;
    }

    private static Direction sideFor(final Direction sourceDirection) {
        return sourceDirection == Direction.DOWN ? Direction.UP : sourceDirection.getOpposite();
    }

    /**
     * Counts how many of the given item are available in inventories around the cannon.
     */
    public static int countAvailable(final ServerLevel level,
                                      final BlockPos cannonPos,
                                      final ItemStack required) {
        int total = 0;
        for (final Source source : sources(level, cannonPos)) {
            final IItemHandler handler = source.handler();
            if (handler == null) {
                continue;
            }
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                final ItemStack stack = handler.getStackInSlot(slot);
                if (ItemStack.isSameItemSameComponents(required, stack)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    private static @Nullable IItemHandler capability(final ServerLevel level, final BlockPos pos, final Direction side) {
        final BlockState state = level.getBlockState(pos);
        final BlockEntity blockEntity = level.getBlockEntity(pos);
        return Capabilities.ItemHandler.BLOCK.getCapability(level, pos, state, blockEntity, side);
    }

    private record Source(BlockPos pos, @Nullable IItemHandler handler) {
    }
}

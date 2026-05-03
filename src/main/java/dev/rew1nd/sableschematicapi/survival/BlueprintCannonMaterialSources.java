package dev.rew1nd.sableschematicapi.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * Source discovery for blueprint cannon material budgets.
 */
public final class BlueprintCannonMaterialSources {
    private static final Direction[] SOURCE_DIRECTIONS = {
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST,
            Direction.DOWN
    };
    private static final List<BlueprintCannonMaterialSourceProvider> PROVIDERS = new ArrayList<>();
    private static final BlueprintCannonMaterialSource UNLIMITED = new UnlimitedMaterialSource();

    private BlueprintCannonMaterialSources() {
    }

    public static void registerProvider(final BlueprintCannonMaterialSourceProvider provider) {
        PROVIDERS.add(provider);
    }

    public static BlueprintCannonMaterialSource unlimited() {
        return UNLIMITED;
    }

    public static List<BlueprintCannonMaterialSource> sources(final ServerLevel level, final BlockPos cannonPos) {
        final List<BlueprintCannonMaterialSource> sources = new ArrayList<>(SOURCE_DIRECTIONS.length);
        for (final Direction direction : SOURCE_DIRECTIONS) {
            final BlockPos sourcePos = cannonPos.relative(direction);
            final Direction side = sideFor(direction);
            final BlockState state = level.getBlockState(sourcePos);
            final BlockEntity blockEntity = level.getBlockEntity(sourcePos);
            final BlueprintCannonMaterialSourceContext context = new BlueprintCannonMaterialSourceContext(
                    level,
                    cannonPos,
                    sourcePos,
                    direction,
                    side,
                    state,
                    blockEntity
            );

            final BlueprintCannonMaterialSource special = providerSource(context);
            if (special != null) {
                sources.add(special);
                continue;
            }

            final IItemHandler handler = Capabilities.ItemHandler.BLOCK.getCapability(level, sourcePos, state, blockEntity, side);
            if (handler != null) {
                sources.add(new ItemHandlerMaterialSource(handler));
            }
        }
        return sources;
    }

    private static @Nullable BlueprintCannonMaterialSource providerSource(final BlueprintCannonMaterialSourceContext context) {
        for (final BlueprintCannonMaterialSourceProvider provider : PROVIDERS) {
            final BlueprintCannonMaterialSource source = provider.create(context);
            if (source != null) {
                return source;
            }
        }
        return null;
    }

    private static Direction sideFor(final Direction sourceDirection) {
        return sourceDirection == Direction.DOWN ? Direction.UP : sourceDirection.getOpposite();
    }

    private static final class ItemHandlerMaterialSource implements BlueprintCannonMaterialSource {
        private final IItemHandler handler;

        private ItemHandlerMaterialSource(final IItemHandler handler) {
            this.handler = handler;
        }

        @Override
        public boolean unlimited() {
            return false;
        }

        @Override
        public int available(final ItemStack required) {
            if (required.isEmpty()) {
                return 0;
            }

            int available = 0;
            for (int slot = 0; slot < this.handler.getSlots(); slot++) {
                final ItemStack stack = this.handler.getStackInSlot(slot);
                if (ItemStack.isSameItemSameComponents(required, stack)) {
                    available = saturatedAdd(available, stack.getCount());
                }
            }
            return available;
        }

        @Override
        public int consume(final ItemStack required, final int count) {
            if (required.isEmpty() || count <= 0) {
                return 0;
            }

            int consumed = 0;
            for (int slot = 0; slot < this.handler.getSlots() && consumed < count; slot++) {
                final ItemStack stack = this.handler.getStackInSlot(slot);
                if (!ItemStack.isSameItemSameComponents(required, stack)) {
                    continue;
                }

                final int requested = Math.min(count - consumed, stack.getCount());
                final ItemStack extracted = this.handler.extractItem(slot, requested, false);
                if (ItemStack.isSameItemSameComponents(required, extracted)) {
                    consumed += extracted.getCount();
                }
            }
            return consumed;
        }
    }

    private static final class UnlimitedMaterialSource implements BlueprintCannonMaterialSource {
        @Override
        public boolean unlimited() {
            return true;
        }

        @Override
        public int available(final ItemStack required) {
            return Integer.MAX_VALUE;
        }

        @Override
        public int consume(final ItemStack required, final int count) {
            return Math.max(0, count);
        }
    }

    private static int saturatedAdd(final int left, final int right) {
        final long sum = (long) left + right;
        return sum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }
}

package dev.rew1nd.sableschematicapi.compat.create;

import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBlockCostContext;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostQuote;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostTiming;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintPostProcessCostContext;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintIncrementalPostProcessCostStrategy;
import dev.rew1nd.sableschematicapi.survival.BlueprintBlockCostRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Computes the material cost for a contraption entity by iterating over
 * the blocks inside the contraption and delegating to {@link BlueprintBlockCostRules}.
 */
public final class ContraptionPlaceCost implements BlueprintIncrementalPostProcessCostStrategy<ContraptionPlaceOperation> {

    @Override
    public ResourceLocation type() {
        return ContraptionPlaceOperation.TYPE;
    }

    @Override
    public CostQuote quote(final ContraptionPlaceOperation operation,
                            final BlueprintPostProcessCostContext context) {
        final Cursor cursor = this.begin(operation, context);
        CostQuote total = CostQuote.empty(CostTiming.COMMIT);
        while (true) {
            final Step step = cursor.step(Integer.MAX_VALUE);
            total = total.merge(step.quote());
            if (step.done()) {
                return total;
            }
        }
    }

    @Override
    public Cursor begin(final ContraptionPlaceOperation operation,
                        final BlueprintPostProcessCostContext context) {
        final CompoundTag entityTag = operation.entityTag();
        if (!entityTag.contains("Contraption", Tag.TAG_COMPOUND)) {
            return EmptyCursor.INSTANCE;
        }

        final CompoundTag contraption = entityTag.getCompound("Contraption");
        if (!contraption.contains("Blocks", Tag.TAG_COMPOUND)) {
            return EmptyCursor.INSTANCE;
        }

        final CompoundTag blocksTag = contraption.getCompound("Blocks");
        final ListTag paletteList = blocksTag.getList("Palette", Tag.TAG_COMPOUND);
        final BlockState[] palette = new BlockState[paletteList.size()];
        for (int i = 0; i < paletteList.size(); i++) {
            palette[i] = NbtUtils.readBlockState(
                    context.level().holderLookup(Registries.BLOCK),
                    paletteList.getCompound(i)
            );
        }

        final BlueprintBlockCostContext blockCtx =
                new BlueprintBlockCostContext(
                        context.level(),
                        new Vec3(
                                context.session().origin().x(),
                                context.session().origin().y(),
                                context.session().origin().z()
                        )
                );
        final ListTag blockList = blocksTag.getList("BlockList", Tag.TAG_COMPOUND);
        return new ContraptionCursor(palette, blockList, blockCtx);
    }

    private static final class ContraptionCursor implements Cursor {
        private final BlockState[] palette;
        private final ListTag blocks;
        private final BlueprintBlockCostContext context;
        private int index;

        private ContraptionCursor(final BlockState[] palette,
                                  final ListTag blocks,
                                  final BlueprintBlockCostContext context) {
            this.palette = palette;
            this.blocks = blocks;
            this.context = context;
        }

        @Override
        public Step step(final int maxWorkUnits) {
            if (maxWorkUnits <= 0 || this.index >= this.blocks.size()) {
                return new Step(CostQuote.empty(CostTiming.COMMIT), 0, this.index >= this.blocks.size());
            }
            CostQuote total = CostQuote.empty(CostTiming.COMMIT);
            int work = 0;
            while (work < maxWorkUnits && this.index < this.blocks.size()) {
                final CompoundTag entry = this.blocks.getCompound(this.index++);
                final int stateId = entry.getInt("State");
                if (stateId < 0 || stateId >= this.palette.length) {
                    throw new IllegalStateException("Contraption block references invalid palette state " + stateId);
                }
                total = total.merge(
                        BlueprintBlockCostRules.quotePlacementForState(this.palette[stateId], this.context)
                                .withTiming(CostTiming.COMMIT));
                work++;
            }
            return new Step(total, work, this.index >= this.blocks.size());
        }
    }

    private enum EmptyCursor implements Cursor {
        INSTANCE;

        @Override
        public Step step(final int maxWorkUnits) {
            return new Step(CostQuote.empty(CostTiming.COMMIT), 0, true);
        }
    }
}

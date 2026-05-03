package dev.rew1nd.sableschematicapi.compat.create;

import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBlockCostContext;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostQuote;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostTiming;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintPostProcessCostContext;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintPostProcessCostStrategy;
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
public final class ContraptionPlaceCost implements BlueprintPostProcessCostStrategy<ContraptionPlaceOperation> {

    @Override
    public ResourceLocation type() {
        return ContraptionPlaceOperation.TYPE;
    }

    @Override
    public CostQuote quote(final ContraptionPlaceOperation operation,
                            final BlueprintPostProcessCostContext context) {
        final CompoundTag entityTag = operation.entityTag();
        if (!entityTag.contains("Contraption", Tag.TAG_COMPOUND)) {
            return CostQuote.empty(CostTiming.COMMIT);
        }

        final CompoundTag contraption = entityTag.getCompound("Contraption");
        if (!contraption.contains("Blocks", Tag.TAG_COMPOUND)) {
            return CostQuote.empty(CostTiming.COMMIT);
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
                new BlueprintBlockCostContext(context.level(), Vec3.ZERO);
        final ListTag blockList = blocksTag.getList("BlockList", Tag.TAG_COMPOUND);
        CostQuote total = CostQuote.empty(CostTiming.COMMIT);

        for (int i = 0; i < blockList.size(); i++) {
            final CompoundTag entry = blockList.getCompound(i);
            final int stateId = entry.getInt("State");
            if (stateId >= 0 && stateId < palette.length) {
                total = total.merge(
                        BlueprintBlockCostRules.quoteForState(palette[stateId], blockCtx));
            }
        }

        return total;
    }
}

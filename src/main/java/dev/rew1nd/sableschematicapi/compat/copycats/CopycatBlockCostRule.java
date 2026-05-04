package dev.rew1nd.sableschematicapi.compat.copycats;

import com.simibubi.create.AllBlocks;
import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBlockCostContext;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBlockCostRule;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBuildBlockPayload;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostLine;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostQuote;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostTiming;
import dev.rew1nd.sableschematicapi.survival.BlueprintBlockCostRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class CopycatBlockCostRule implements BlueprintBlockCostRule {
    private static final ResourceLocation ID = SableSchematicApi.id("copycats_copycat_material");
    private static final ResourceLocation MATERIAL_ITEM_ID = SableSchematicApi.id("copycats_material_item");

    private static final String SINGLE_ITEM_KEY = "Item";
    private static final String SINGLE_MATERIAL_KEY = "Material";
    private static final String MULTI_MATERIAL_DATA_KEY = "material_data";
    private static final String MULTI_ITEM_KEY = "consumedItem";
    private static final String MULTI_MATERIAL_KEY = "material";

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public CostQuote quotePlacement(final BlueprintBuildBlockPayload payload,
                                    final BlueprintBlockCostContext context) {
        return BlueprintBlockCostRules.quoteDefaultPlacement(payload, context);
    }

    @Override
    public CostQuote quoteNbtLoad(final BlueprintBuildBlockPayload payload,
                                  final BlueprintBlockCostContext context) {
        final CompoundTag tag = payload.effectiveBlockEntityTag();
        if (tag == null) {
            return CostQuote.empty(CostTiming.COMMIT);
        }

        return tag.contains(MULTI_MATERIAL_DATA_KEY, Tag.TAG_COMPOUND)
                ? quoteMultiStateMaterials(tag.getCompound(MULTI_MATERIAL_DATA_KEY), context)
                : quoteSingleStateMaterial(tag, context);
    }

    private CostQuote quoteSingleStateMaterial(final CompoundTag tag, final BlueprintBlockCostContext context) {
        return quoteMaterialEntry(
                readItemStack(tag, SINGLE_ITEM_KEY, context),
                readBlockState(tag, SINGLE_MATERIAL_KEY, context),
                context
        );
    }

    private CostQuote quoteMultiStateMaterials(final CompoundTag materialData,
                                               final BlueprintBlockCostContext context) {
        CostQuote total = CostQuote.empty(CostTiming.COMMIT);
        final List<String> keys = new ArrayList<>(materialData.getAllKeys());
        keys.sort(String::compareTo);

        for (final String key : keys) {
            final CompoundTag entry = materialData.getCompound(key);
            total = total.merge(quoteMaterialEntry(
                    readItemStack(entry, MULTI_ITEM_KEY, context),
                    readBlockState(entry, MULTI_MATERIAL_KEY, context),
                    context
            ));
        }

        return total.compact();
    }

    private CostQuote quoteMaterialEntry(final ItemStack consumedItem,
                                         final @Nullable BlockState materialState,
                                         final BlueprintBlockCostContext context) {
        if (!consumedItem.isEmpty()) {
            final ItemStack stack = consumedItem.copy();
            if (stack.getCount() < 1) {
                stack.setCount(1);
            }
            return CostQuote.of(
                    new CostLine(MATERIAL_ITEM_ID, stack, stack.getHoverName()),
                    CostTiming.COMMIT
            );
        }

        if (materialState == null || isDefaultCopycatMaterial(materialState)) {
            return CostQuote.empty(CostTiming.COMMIT);
        }

        return BlueprintBlockCostRules.quotePlacementForState(materialState, context)
                .withTiming(CostTiming.COMMIT);
    }

    private static ItemStack readItemStack(final CompoundTag tag,
                                           final String key,
                                           final BlueprintBlockCostContext context) {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            return ItemStack.EMPTY;
        }

        try {
            return ItemStack.parseOptional(context.level().registryAccess(), tag.getCompound(key));
        } catch (final RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static @Nullable BlockState readBlockState(final CompoundTag tag,
                                                       final String key,
                                                       final BlueprintBlockCostContext context) {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            return null;
        }

        try {
            return NbtUtils.readBlockState(
                    context.level().holderLookup(Registries.BLOCK),
                    tag.getCompound(key)
            );
        } catch (final RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isDefaultCopycatMaterial(final BlockState state) {
        return AllBlocks.COPYCAT_BASE.has(state);
    }
}

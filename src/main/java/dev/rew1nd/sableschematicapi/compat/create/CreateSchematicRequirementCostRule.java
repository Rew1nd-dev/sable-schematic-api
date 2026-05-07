package dev.rew1nd.sableschematicapi.compat.create;

import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBlockCostContext;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBlockCostRule;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBuildBlockPayload;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostLine;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostQuote;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostTiming;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class CreateSchematicRequirementCostRule implements BlueprintBlockCostRule {
    private static final ResourceLocation PLACEMENT_ID = SableSchematicApi.id("create_schematic_requirement");
    private static final ResourceLocation NBT_LOAD_ID = SableSchematicApi.id("create_schematic_nbt_requirement");

    @Override
    public ResourceLocation id() {
        return PLACEMENT_ID;
    }

    @Override
    public CostQuote quotePlacement(final BlueprintBuildBlockPayload payload,
                                    final BlueprintBlockCostContext context) {
        return quote(ItemRequirement.of(payload.state(), null), CostTiming.IMMEDIATE_BLOCK, PLACEMENT_ID);
    }

    @Override
    public CostQuote quoteNbtLoad(final BlueprintBuildBlockPayload payload,
                                  final BlueprintBlockCostContext context) {
        final BlockEntity blockEntity = loadBlockEntity(payload, context);
        if (blockEntity == null) {
            return CostQuote.empty(CostTiming.COMMIT);
        }

        final ItemRequirement base = ItemRequirement.of(payload.state(), null);
        final ItemRequirement full = ItemRequirement.of(payload.state(), blockEntity);
        return quote(difference(full, base), CostTiming.COMMIT, NBT_LOAD_ID);
    }

    private static CostQuote quote(final ItemRequirement requirement,
                                   final CostTiming timing,
                                   final ResourceLocation id) {
        if (requirement.isEmpty() || requirement.isInvalid()) {
            return CostQuote.empty(timing);
        }

        CostQuote total = CostQuote.empty(timing);
        for (final ItemRequirement.StackRequirement required : requirement.getRequiredItems()) {
            total = total.merge(quote(required.stack, timing, id));
        }
        return total.compact();
    }

    private static CostQuote quote(final List<MutableRequirement> requirements,
                                   final CostTiming timing,
                                   final ResourceLocation id) {
        CostQuote total = CostQuote.empty(timing);
        for (final MutableRequirement required : requirements) {
            total = total.merge(quote(required.stack, timing, id));
        }
        return total.compact();
    }

    private static CostQuote quote(final ItemStack required,
                                   final CostTiming timing,
                                   final ResourceLocation id) {
        if (required.isEmpty()) {
            return CostQuote.empty(timing);
        }

        final ItemStack stack = required.copy();
        if (stack.getCount() < 1) {
            stack.setCount(1);
        }
        return CostQuote.of(new CostLine(id, stack, stack.getHoverName()), timing);
    }

    private static List<MutableRequirement> difference(final ItemRequirement full,
                                                       final ItemRequirement base) {
        final List<MutableRequirement> remaining = mutable(full);
        if (remaining.isEmpty() || base.isEmpty() || base.isInvalid()) {
            return remaining;
        }

        for (final ItemRequirement.StackRequirement baseRequirement : base.getRequiredItems()) {
            int amount = baseRequirement.stack.getCount();
            for (final MutableRequirement candidate : remaining) {
                if (amount <= 0) {
                    break;
                }
                if (!candidate.sameKind(baseRequirement)) {
                    continue;
                }

                final int removed = Math.min(amount, candidate.stack.getCount());
                candidate.stack.shrink(removed);
                amount -= removed;
            }
        }

        remaining.removeIf(requirement -> requirement.stack.isEmpty());
        return remaining;
    }

    private static List<MutableRequirement> mutable(final ItemRequirement requirement) {
        if (requirement.isEmpty() || requirement.isInvalid()) {
            return List.of();
        }

        final List<MutableRequirement> result = new ArrayList<>();
        for (final ItemRequirement.StackRequirement required : requirement.getRequiredItems()) {
            if (!required.stack.isEmpty()) {
                result.add(new MutableRequirement(required));
            }
        }
        return result;
    }

    private static @Nullable BlockEntity loadBlockEntity(final BlueprintBuildBlockPayload payload,
                                                         final BlueprintBlockCostContext context) {
        final CompoundTag effectiveTag = payload.effectiveBlockEntityTag();
        if (effectiveTag == null) {
            return null;
        }

        final CompoundTag tag = effectiveTag.copy();
        tag.putInt("x", BlockPos.ZERO.getX());
        tag.putInt("y", BlockPos.ZERO.getY());
        tag.putInt("z", BlockPos.ZERO.getZ());
        try {
            return BlockEntity.loadStatic(BlockPos.ZERO, payload.state(), tag, context.level().registryAccess());
        } catch (final RuntimeException ignored) {
            return null;
        }
    }

    private static final class MutableRequirement {
        private final ItemRequirement.StackRequirement source;
        private final ItemStack stack;

        private MutableRequirement(final ItemRequirement.StackRequirement source) {
            this.source = source;
            this.stack = source.stack.copy();
        }

        private boolean sameKind(final ItemRequirement.StackRequirement other) {
            return this.source.usage == other.usage
                    && this.source.matches(other.stack)
                    && other.matches(this.source.stack);
        }
    }
}

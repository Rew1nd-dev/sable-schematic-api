package dev.rew1nd.sableschematicapi.survival;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBlockCostContext;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBlockCostRule;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBuildBlockPayload;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostLine;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostQuote;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostTiming;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockRef;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintNbtLoadDecision;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintNbtLoadMode;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.Map;

/**
 * Process-global registry for survival block material cost rules.
 */
public final class BlueprintBlockCostRules {
    private static final Map<Block, BlueprintBlockCostRule> BLOCK_RULES = new Object2ObjectOpenHashMap<>();
    private static final BlueprintBlockCostRule DEFAULT_RULE = new DefaultBlockItemCostRule();

    private BlueprintBlockCostRules() {
    }

    public static void register(final Block block, final BlueprintBlockCostRule rule) {
        BLOCK_RULES.put(block, rule);
    }

    public static CostQuote quote(final BlueprintBuildBlockPayload payload, final BlueprintBlockCostContext context) {
        final BlueprintBlockCostRule rule = BLOCK_RULES.getOrDefault(payload.state().getBlock(), DEFAULT_RULE);
        return rule.quote(payload, context);
    }

    /**
     * Queries the material cost for a raw {@link net.minecraft.world.level.block.state.BlockState}
     * without requiring a full {@link BlueprintBuildBlockPayload}.
     *
     * <p>Used by post-process cost strategies (e.g. contraption block iteration)
     * that have access to block states but not the full blueprint payload.</p>
     */
    public static CostQuote quoteForState(final net.minecraft.world.level.block.state.BlockState state,
                                          final BlueprintBlockCostContext context) {
        final BlueprintBlockCostRule rule = BLOCK_RULES.getOrDefault(state.getBlock(), DEFAULT_RULE);
        final var payload = new BlueprintBuildBlockPayload(
                new BlueprintBlockRef(-1, net.minecraft.core.BlockPos.ZERO),
                null,
                null,
                state,
                null,
                new BlueprintNbtLoadDecision(BlueprintNbtLoadMode.NONE, null)
        );
        return rule.quote(payload, context);
    }

    private static final class DefaultBlockItemCostRule implements BlueprintBlockCostRule {
        @Override
        public ResourceLocation id() {
            return SableSchematicApi.id("default_block_item");
        }

        @Override
        public CostQuote quote(final BlueprintBuildBlockPayload payload, final BlueprintBlockCostContext context) {
            final Item item = payload.state().getBlock().asItem();
            if (item == Items.AIR) {
                return CostQuote.empty(CostTiming.IMMEDIATE_BLOCK);
            }

            return CostQuote.of(
                    new CostLine(this.id(), new ItemStack(item), Component.translatable(item.getDescriptionId())),
                    CostTiming.IMMEDIATE_BLOCK
            );
        }
    }
}

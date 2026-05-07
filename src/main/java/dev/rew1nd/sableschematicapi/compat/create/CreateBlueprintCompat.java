package dev.rew1nd.sableschematicapi.compat.create;

import com.simibubi.create.AllEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.Create;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.schematics.cannon.MaterialChecklist;
import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintEventRegistry;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintMapperRegistry;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBlockCostRuleCacheMode;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintPostProcessRegistry;
import dev.rew1nd.sableschematicapi.survival.BlueprintBlockCostRules;
import dev.rew1nd.sableschematicapi.survival.BlueprintCannonMaterialSources;
import dev.rew1nd.sableschematicapi.survival.BudgetLine;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public final class CreateBlueprintCompat {
    private static boolean registered;

    private CreateBlueprintCompat() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        final CreateContraptionEntityMapper mapper = new CreateContraptionEntityMapper();
        SableBlueprintMapperRegistry.register(AllEntityTypes.ORIENTED_CONTRAPTION.get(), mapper);
        SableBlueprintMapperRegistry.register(AllEntityTypes.CONTROLLED_CONTRAPTION.get(), mapper);
        SableBlueprintMapperRegistry.register(AllEntityTypes.GANTRY_CONTRAPTION.get(), mapper);
        SableBlueprintMapperRegistry.register(AllEntityTypes.CARRIAGE_CONTRAPTION.get(), mapper);
        SableBlueprintMapperRegistry.register(AllEntityTypes.SUPER_GLUE.get(), new CreateSuperGlueEntityMapper());
        SableBlueprintEventRegistry.register(new CreateSuperGlueBlueprintEvent());

        // Typed post-process operations for survival material costs
        BlueprintPostProcessRegistry.registerParser(
                ContraptionPlaceParser.SIDECAR_ID, new ContraptionPlaceParser());
        BlueprintPostProcessRegistry.registerMapper(
                ContraptionPlaceOperation.TYPE, new ContraptionPlaceMapper());
        BlueprintPostProcessRegistry.registerCost(
                ContraptionPlaceOperation.TYPE, new ContraptionPlaceCost());
        BlueprintCannonMaterialSources.registerProvider(new CreateCreativeCrateMaterialSourceProvider());
        final CreateSchematicRequirementCostRule createCostRule = new CreateSchematicRequirementCostRule();
        BlueprintBlockCostRules.register(
                createCostRule.id(),
                500,
                payload -> Create.ID.equals(BuiltInRegistries.BLOCK.getKey(payload.state().getBlock()).getNamespace()),
                createCostRule,
                BlueprintBlockCostRuleCacheMode.BLOCK
        );

        registered = true;

        SableSchematicApi.LOGGER.info("Registered Create blueprint compatibility mappers");
    }

    public static boolean isClipboard(final ItemStack stack) {
        return AllBlocks.CLIPBOARD.isIn(stack);
    }

    public static boolean writeBudgetToClipboard(final ItemStack clipboardStack,
                                                 final List<BudgetLine> budgetLines) {
        if (!isClipboard(clipboardStack)) {
            return false;
        }

        final MaterialChecklist checklist = new MaterialChecklist();
        for (final BudgetLine line : budgetLines) {
            final ItemStack stack = line.item();
            final Item item = stack.getItem();
            if (stack.isEmpty() || item == Items.AIR || line.required() <= 0) {
                continue;
            }

            checklist.required.put(item, checklist.required.getInt(item) + line.required());
            final int gathered = line.unlimited()
                    ? line.required()
                    : Math.min(line.available(), line.required());
            if (gathered > 0) {
                checklist.gathered.put(item, checklist.gathered.getInt(item) + gathered);
            }
        }

        final ItemStack writtenClipboard = checklist.createWrittenClipboard();
        final ClipboardContent content = writtenClipboard.getOrDefault(
                AllDataComponents.CLIPBOARD_CONTENT, ClipboardContent.EMPTY);
        clipboardStack.set(AllDataComponents.CLIPBOARD_CONTENT, content);

        final Component customName = writtenClipboard.get(DataComponents.CUSTOM_NAME);
        if (customName != null) {
            clipboardStack.set(DataComponents.CUSTOM_NAME, customName);
        }
        return true;
    }
}

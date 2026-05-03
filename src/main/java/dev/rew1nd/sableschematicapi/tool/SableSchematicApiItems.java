package dev.rew1nd.sableschematicapi.tool;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.survival.BlueprintDataItem;
import dev.rew1nd.sableschematicapi.survival.SableSchematicApiBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SableSchematicApiItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, SableSchematicApi.MODID);

    public static final DeferredHolder<Item, BlueprintToolItem> BLUEPRINT_TOOL = ITEMS.register(
            "blueprint_tool",
            () -> new BlueprintToolItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredHolder<Item, BlueprintDataItem> SURVIVAL_BLUEPRINT = ITEMS.register(
            "survival_blueprint",
            () -> new BlueprintDataItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredHolder<Item, BlockItem> BLUEPRINT_CANNON = ITEMS.register(
            "blueprint_cannon",
            () -> new BlockItem(SableSchematicApiBlocks.BLUEPRINT_CANNON.get(), new Item.Properties())
    );

    public static final DeferredHolder<Item, BlockItem> BLUEPRINT_TABLE = ITEMS.register(
            "blueprint_table",
            () -> new BlockItem(SableSchematicApiBlocks.BLUEPRINT_TABLE.get(), new Item.Properties())
    );

    private SableSchematicApiItems() {
    }

    public static void register(final IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}

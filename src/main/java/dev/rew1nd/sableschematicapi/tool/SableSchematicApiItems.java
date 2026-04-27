package dev.rew1nd.sableschematicapi.tool;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import net.minecraft.core.registries.Registries;
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

    private SableSchematicApiItems() {
    }

    public static void register(final IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}

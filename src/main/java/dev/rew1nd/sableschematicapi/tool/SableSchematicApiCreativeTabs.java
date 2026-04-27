package dev.rew1nd.sableschematicapi.tool;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SableSchematicApiCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SableSchematicApi.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.sable_schematic_api.main"))
                    .icon(() -> new ItemStack(SableSchematicApiItems.BLUEPRINT_TOOL.get()))
                    .displayItems((parameters, output) -> output.accept(SableSchematicApiItems.BLUEPRINT_TOOL.get()))
                    .build()
    );

    private SableSchematicApiCreativeTabs() {
    }

    public static void register(final IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}

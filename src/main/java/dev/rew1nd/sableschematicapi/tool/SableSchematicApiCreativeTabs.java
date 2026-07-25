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
                    .displayItems((parameters, output) -> {
                        output.accept(SableSchematicApiItems.BLUEPRINT_TOOL.get());
                        output.accept(SableSchematicApiItems.PHYSICS_MANAGER.get());
                        output.accept(SableSchematicApiItems.CAMERA.get());
                        output.accept(SableSchematicApiItems.HONEY_WELDER.get());
                        output.accept(SableSchematicApiItems.BLUEPRINT_MANUSCRIPT.get());
                        output.accept(SableSchematicApiItems.FILM.get());
//                        output.accept(SableSchematicApiItems.SURVIVAL_BLUEPRINT.get());
//                        output.accept(SableSchematicApiItems.BLUEPRINT_CANNON.get());
//                        output.accept(SableSchematicApiItems.BLUEPRINT_TABLE.get());
                        output.accept(SableSchematicApiItems.FILM_PRINTER.get());
                        output.accept(SableSchematicApiItems.PROJECTOR.get());
                        output.accept(SableSchematicApiItems.PROJECTOR_BASE.get());
                        output.accept(SableSchematicApiItems.PROJECTOR_SCREEN.get());
                        output.accept(SableSchematicApiItems.PROJECTOR_SCREEN_LOCATOR.get());
                    })
                    .build()
    );

    private SableSchematicApiCreativeTabs() {
    }

    public static void register(final IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}

package dev.rew1nd.sableschematicapi;

import com.mojang.logging.LogUtils;
import dev.rew1nd.sableschematicapi.command.SableBlueprintCommands;
import dev.rew1nd.sableschematicapi.compat.simulated.SimulatedBlueprintCompat;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(SableSchematicApi.MODID)
public final class SableSchematicApi {
    public static final String MODID = "sable_schematic_api";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SableSchematicApi(final IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(SableBlueprintCommands::register);
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (ModList.get().isLoaded("simulated")) {
                SimulatedBlueprintCompat.register();
            }
        });
    }
}

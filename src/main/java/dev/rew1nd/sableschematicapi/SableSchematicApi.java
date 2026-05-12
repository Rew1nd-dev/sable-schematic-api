package dev.rew1nd.sableschematicapi;

import com.mojang.logging.LogUtils;
import dev.rew1nd.sableschematicapi.command.SableBlueprintCommands;
import dev.rew1nd.sableschematicapi.compat.aeronautics.AeronauticsBlueprintCompat;
import dev.rew1nd.sableschematicapi.compat.copycats.CopycatsBlueprintCompat;
import dev.rew1nd.sableschematicapi.compat.create.CreateBlueprintCompat;
import dev.rew1nd.sableschematicapi.compat.drivebywire.DriveByWireBlueprintCompat;
import dev.rew1nd.sableschematicapi.compat.simulated.SimulatedBlueprintCompat;
import dev.rew1nd.sableschematicapi.network.SableSchematicApiPackets;
import dev.rew1nd.sableschematicapi.survival.SableSchematicApiBlockEntities;
import dev.rew1nd.sableschematicapi.survival.SableSchematicApiBlocks;
import dev.rew1nd.sableschematicapi.sublevel.PendingSubLevelLoadTeleportService;
import dev.rew1nd.sableschematicapi.sublevel.RuntimeSubLevelStaticService;
import dev.rew1nd.sableschematicapi.tool.SableSchematicApiCreativeTabs;
import dev.rew1nd.sableschematicapi.tool.SableSchematicApiItems;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import net.minecraft.resources.ResourceLocation;
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
        SableSchematicApiBlocks.register(modEventBus);
        SableSchematicApiBlockEntities.register(modEventBus);
        SableSchematicApiItems.register(modEventBus);
        SableSchematicApiCreativeTabs.register(modEventBus);
        SableSchematicApiPackets.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(SableBlueprintCommands::register);
        NeoForge.EVENT_BUS.addListener(PendingSubLevelLoadTeleportService::tick);
        NeoForge.EVENT_BUS.addListener(RuntimeSubLevelStaticService::onServerStopped);
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            SableEventPlatform.INSTANCE.onSubLevelContainerReady(RuntimeSubLevelStaticService::onSubLevelContainerReady);

            if (ModList.get().isLoaded("create")) {
                CreateBlueprintCompat.register();
            }

            if (ModList.get().isLoaded("simulated")) {
                SimulatedBlueprintCompat.register();
            }

            if (ModList.get().isLoaded("aeronautics")) {
                AeronauticsBlueprintCompat.register();
            }

            if (ModList.get().isLoaded("copycats")) {
                CopycatsBlueprintCompat.register();
            }

            if (ModList.get().isLoaded("drivebywire")) {
                DriveByWireBlueprintCompat.register();
            }
        });
    }

    public static ResourceLocation id(final String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}

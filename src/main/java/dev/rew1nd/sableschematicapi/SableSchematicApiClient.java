package dev.rew1nd.sableschematicapi;

import dev.rew1nd.sableschematicapi.compat.create.client.CreateBlueprintToolClientCompat;
import dev.rew1nd.sableschematicapi.tool.client.BlueprintToolClientEvents;
import dev.rew1nd.sableschematicapi.tool.client.input.BlueprintToolKeyMappings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(value = SableSchematicApi.MODID, dist = Dist.CLIENT)
public final class SableSchematicApiClient {
    public SableSchematicApiClient(final IEventBus modEventBus) {
        modEventBus.addListener(BlueprintToolKeyMappings::register);
        modEventBus.addListener(SableSchematicApiClient::clientSetup);
        BlueprintToolClientEvents.register();
    }

    private static void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (ModList.get().isLoaded("create")) {
                CreateBlueprintToolClientCompat.register();
            }
        });
    }
}

package dev.rew1nd.sableschematicapi;

import dev.rew1nd.sableschematicapi.tool.client.BlueprintToolClientEvents;
import dev.rew1nd.sableschematicapi.tool.client.BlueprintToolKeyMappings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(value = SableSchematicApi.MODID, dist = Dist.CLIENT)
public final class SableSchematicApiClient {
    public SableSchematicApiClient(final IEventBus modEventBus) {
        modEventBus.addListener(BlueprintToolKeyMappings::register);
        BlueprintToolClientEvents.register();
    }
}

package dev.rew1nd.sableschematicapi;

import dev.rew1nd.sableschematicapi.client.frontier.SableFrontierAnimations;
import dev.rew1nd.sableschematicapi.client.frontier.SableFrontierClientCommands;
import dev.rew1nd.sableschematicapi.client.frontier.SableFrontierLaserRenderer;
import dev.rew1nd.sableschematicapi.client.frontier.SableFrontierShaderPreProcessor;
import dev.rew1nd.sableschematicapi.compat.create.client.CreateBlueprintToolClientCompat;
import dev.rew1nd.sableschematicapi.survival.client.BlueprintTableClientLocalFiles;
import dev.rew1nd.sableschematicapi.tool.client.BlueprintToolClientEvents;
import dev.rew1nd.sableschematicapi.tool.client.input.BlueprintToolKeyMappings;
import foundry.veil.platform.VeilEventPlatform;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = SableSchematicApi.MODID, dist = Dist.CLIENT)
public final class SableSchematicApiClient {
    public SableSchematicApiClient(final IEventBus modEventBus) {
        modEventBus.addListener(BlueprintToolKeyMappings::register);
        modEventBus.addListener(SableSchematicApiClient::clientSetup);
        NeoForge.EVENT_BUS.addListener(SableFrontierClientCommands::register);
        NeoForge.EVENT_BUS.addListener(SableFrontierAnimations::tick);
        VeilEventPlatform.INSTANCE.onVeilRenderLevelStage(SableFrontierLaserRenderer::render);
        VeilEventPlatform.INSTANCE.onVeilAddShaderProcessors((provider, registry) ->
                registry.addPreprocessor(new SableFrontierShaderPreProcessor(), false));
        BlueprintToolClientEvents.register();
    }

    private static void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            BlueprintTableClientLocalFiles.register();

            if (ModList.get().isLoaded("create")) {
                CreateBlueprintToolClientCompat.register();
            }
        });
    }
}

package dev.rew1nd.sableschematicapi.compat.create.client;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.tool.client.BlueprintToolSelectionPreviewRenderers;

public final class CreateBlueprintToolClientCompat {
    private static boolean registered;

    private CreateBlueprintToolClientCompat() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        BlueprintToolSelectionPreviewRenderers.register(CreateBlueprintToolSelectionRenderer.INSTANCE);
        registered = true;
        SableSchematicApi.LOGGER.info("Registered Create blueprint tool client compatibility");
    }
}

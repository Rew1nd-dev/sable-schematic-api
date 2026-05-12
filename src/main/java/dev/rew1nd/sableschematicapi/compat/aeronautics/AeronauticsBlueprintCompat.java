package dev.rew1nd.sableschematicapi.compat.aeronautics;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintEventRegistry;

public final class AeronauticsBlueprintCompat {
    private static boolean registered;

    private AeronauticsBlueprintCompat() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        SableBlueprintEventRegistry.register(new AeronauticsHotAirBalloonBlueprintEvent());
        registered = true;

        SableSchematicApi.LOGGER.info("Registered Create Aeronautics blueprint compatibility events");
    }
}

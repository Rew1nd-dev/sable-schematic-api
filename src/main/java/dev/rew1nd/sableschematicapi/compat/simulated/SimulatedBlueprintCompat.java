package dev.rew1nd.sableschematicapi.compat.simulated;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintEventRegistry;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintMapperRegistry;
import dev.simulated_team.simulated.index.SimBlockEntityTypes;

public final class SimulatedBlueprintCompat {
    private static boolean registered;

    private SimulatedBlueprintCompat() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        final SwivelBearingBlueprintMapper mapper = new SwivelBearingBlueprintMapper();
        final RopeStrandBlueprintMapper ropeMapper = new RopeStrandBlueprintMapper();
        SableBlueprintMapperRegistry.register(SimBlockEntityTypes.SWIVEL_BEARING.get(), mapper);
        SableBlueprintMapperRegistry.register(SimBlockEntityTypes.SWIVEL_BEARING_LINK_BLOCK.get(), mapper);
        SableBlueprintMapperRegistry.register(SimBlockEntityTypes.ROPE_WINCH.get(), ropeMapper);
        SableBlueprintMapperRegistry.register(SimBlockEntityTypes.ROPE_CONNECTOR.get(), ropeMapper);
        SableBlueprintEventRegistry.register(new SwivelBearingBlueprintEvent());
        SableBlueprintEventRegistry.register(new RopeStrandBlueprintEvent());
        registered = true;

        SableSchematicApi.LOGGER.info("Registered Simulated Project blueprint compatibility mappers");
    }
}

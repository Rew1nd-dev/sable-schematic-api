package dev.rew1nd.sableschematicapi.compat.create;

import com.simibubi.create.AllEntityTypes;
import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintEventRegistry;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintMapperRegistry;

public final class CreateBlueprintCompat {
    private static boolean registered;

    private CreateBlueprintCompat() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        final CreateContraptionEntityMapper mapper = new CreateContraptionEntityMapper();
        SableBlueprintMapperRegistry.register(AllEntityTypes.ORIENTED_CONTRAPTION.get(), mapper);
        SableBlueprintMapperRegistry.register(AllEntityTypes.CONTROLLED_CONTRAPTION.get(), mapper);
        SableBlueprintMapperRegistry.register(AllEntityTypes.GANTRY_CONTRAPTION.get(), mapper);
        SableBlueprintMapperRegistry.register(AllEntityTypes.CARRIAGE_CONTRAPTION.get(), mapper);
        SableBlueprintMapperRegistry.register(AllEntityTypes.SUPER_GLUE.get(), new CreateSuperGlueEntityMapper());
        SableBlueprintEventRegistry.register(new CreateSuperGlueBlueprintEvent());
        registered = true;

        SableSchematicApi.LOGGER.info("Registered Create blueprint compatibility mappers");
    }
}

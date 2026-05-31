package dev.rew1nd.sableschematicapi.compat.universalJoint;

import com.enxv.aerouniversaljoint.ModBlockEntities;
import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintMapperRegistry;

public final class UniversalJointCompat {
    private static boolean registered;

    private UniversalJointCompat() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        SableBlueprintMapperRegistry.register(ModBlockEntities.UNIVERSAL_JOINT.get(), new UniversalJointBlueprintMapper());

        registered = true;

        SableSchematicApi.LOGGER.info("Registered Create Aeronautics: Transmission & Linkage blueprint compatibility mappers");
    }
}

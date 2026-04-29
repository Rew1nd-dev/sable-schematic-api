package dev.rew1nd.sableschematicapi.compat.drivebywire;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintEventRegistry;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintMapperRegistry;
import edn.stratodonut.drivebywire.WireBlockEntities;

public final class DriveByWireBlueprintCompat {
    private static boolean registered;

    private DriveByWireBlueprintCompat() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        SableBlueprintMapperRegistry.register(WireBlockEntities.BACKUP_BLOCK.get(), new DriveByWireBackupBlockMapper());
        SableBlueprintEventRegistry.register(new DriveByWireBlueprintEvent());
        registered = true;

        SableSchematicApi.LOGGER.info("Registered Drive By Wire blueprint compatibility mappers");
    }
}

package dev.rew1nd.sableschematicapi.survival.honeywelder;

import dev.rew1nd.sableschematicapi.survival.SableServerConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side fuel balance for the honey welder. */
public final class HoneyWelderConfig {
    public static final ModConfigSpec.IntValue CAPACITY =
            SableServerConfig.HONEY_WELDER_CAPACITY;
    public static final ModConfigSpec.IntValue FUEL_PER_BOTTLE =
            SableServerConfig.HONEY_WELDER_FUEL_PER_BOTTLE;

    private HoneyWelderConfig() {
    }

    public static int capacity() {
        return Math.max(1, CAPACITY.get());
    }

    public static int fuelPerBottle() {
        return Math.clamp(FUEL_PER_BOTTLE.get(), 1, capacity());
    }
}

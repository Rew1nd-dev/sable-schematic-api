package dev.rew1nd.sableschematicapi.survival.honeywelder;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side fuel balance for the honey welder. */
public final class HoneyWelderConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue CAPACITY = BUILDER
            .comment("Maximum fuel stored by one honey welder.")
            .defineInRange("honeyWelder.capacity", 1000, 1, 1_000_000);
    public static final ModConfigSpec.IntValue FUEL_PER_BOTTLE = BUILDER
            .comment("Fuel granted by one vanilla honey bottle.")
            .defineInRange("honeyWelder.fuelPerBottle", 250, 1, 1_000_000);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private HoneyWelderConfig() {
    }

    public static int capacity() {
        return Math.max(1, CAPACITY.get());
    }

    public static int fuelPerBottle() {
        return Math.clamp(FUEL_PER_BOTTLE.get(), 1, capacity());
    }
}

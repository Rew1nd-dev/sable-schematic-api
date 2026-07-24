package dev.rew1nd.sableschematicapi.survival.manuscript;

import com.mojang.serialization.Codec;

/** Clockwise rotation applied to a manuscript or recorded film. */
public enum QuarterTurn {
    DEG_0(0),
    DEG_90(90),
    DEG_180(180),
    DEG_270(270);

    public static final Codec<QuarterTurn> CODEC = Codec.INT.xmap(QuarterTurn::fromDegrees, QuarterTurn::degrees);

    private final int degrees;

    QuarterTurn(final int degrees) {
        this.degrees = degrees;
    }

    public int degrees() {
        return this.degrees;
    }

    public QuarterTurn clockwise() {
        return values()[(this.ordinal() + 1) & 3];
    }

    public QuarterTurn counterClockwise() {
        return values()[(this.ordinal() + 3) & 3];
    }

    public static QuarterTurn fromDegrees(final int degrees) {
        return values()[Math.floorMod(Math.round(degrees / 90.0F), 4)];
    }
}

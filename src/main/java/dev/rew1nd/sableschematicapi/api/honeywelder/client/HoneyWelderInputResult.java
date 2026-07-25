package dev.rew1nd.sableschematicapi.api.honeywelder.client;

public record HoneyWelderInputResult(boolean consumed, boolean swingHand) {
    public static final HoneyWelderInputResult PASS = new HoneyWelderInputResult(false, false);

    public static HoneyWelderInputResult consume(final boolean swingHand) {
        return new HoneyWelderInputResult(true, swingHand);
    }
}

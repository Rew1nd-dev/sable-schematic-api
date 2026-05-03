package dev.rew1nd.sableschematicapi.tool.client.input;

public record BlueprintToolInputResult(boolean consumed, boolean swingHand) {
    public static final BlueprintToolInputResult PASS = new BlueprintToolInputResult(false, false);

    public static BlueprintToolInputResult consume(final boolean swingHand) {
        return new BlueprintToolInputResult(true, swingHand);
    }
}

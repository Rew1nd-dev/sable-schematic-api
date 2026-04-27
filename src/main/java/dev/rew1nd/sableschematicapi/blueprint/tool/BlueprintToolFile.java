package dev.rew1nd.sableschematicapi.blueprint.tool;

public record BlueprintToolFile(BlueprintToolResult result, byte[] data) {
    public static BlueprintToolFile failure(final String message) {
        return new BlueprintToolFile(BlueprintToolResult.failure(message), new byte[0]);
    }

    public boolean success() {
        return this.result.success();
    }
}

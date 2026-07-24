package dev.rew1nd.sableschematicapi.survival.filmprinter;

import net.minecraft.network.chat.Component;

import java.util.Objects;

/** A terminal reason why a blueprint cannot be admitted by the film printer. */
public record BlueprintQuoteIssue(String code, Component message) {
    public BlueprintQuoteIssue {
        code = Objects.requireNonNullElse(code, "unknown");
        message = Objects.requireNonNull(message, "message");
    }

    public static BlueprintQuoteIssue of(final String code, final String message) {
        return new BlueprintQuoteIssue(code, Component.literal(message));
    }
}

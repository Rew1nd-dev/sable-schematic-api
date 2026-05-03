package dev.rew1nd.sableschematicapi.api.blueprint.survival.operation;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Result of an admission check for a post-process operation.
 */
public record AdmissionResult(boolean admitted, @Nullable Component reason) {
    private static final AdmissionResult ADMITTED = new AdmissionResult(true, null);

    public static AdmissionResult admit() {
        return ADMITTED;
    }

    public static AdmissionResult deny(final Component reason) {
        return new AdmissionResult(false, reason);
    }
}

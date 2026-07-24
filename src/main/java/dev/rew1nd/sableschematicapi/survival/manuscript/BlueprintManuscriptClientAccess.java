package dev.rew1nd.sableschematicapi.survival.manuscript;

import net.minecraft.world.InteractionHand;

import java.util.function.Consumer;

/**
 * Common-side bridge installed by the client entrypoint. It keeps client UI
 * classes out of the dedicated-server item class.
 */
public final class BlueprintManuscriptClientAccess {
    private static Consumer<InteractionHand> opener = hand -> {
    };

    private BlueprintManuscriptClientAccess() {
    }

    public static void register(final Consumer<InteractionHand> newOpener) {
        opener = newOpener == null ? hand -> {
        } : newOpener;
    }

    public static void open(final InteractionHand hand) {
        opener.accept(hand);
    }
}

package dev.rew1nd.sableschematicapi.client.frontier;

import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class SableFrontierAnimations {
    private static final Map<ClientSubLevel, Animation> ANIMATIONS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private SableFrontierAnimations() {
    }

    public static void reveal(final ClientSubLevel subLevel,
                              final Vector3fc normal,
                              final float seconds,
                              final float gradientWidth) {
        final SableFrontierData data = FrontierDataStore.getOrCreate(subLevel);
        final Vector3f normalizedNormal = new Vector3f(normal);
        if (normalizedNormal.lengthSquared() == 0.0f) {
            normalizedNormal.set(0.0f, 1.0f, 0.0f);
        }
        normalizedNormal.normalize();

        final SableFrontierCoordinates.ProjectionRange range =
                SableFrontierCoordinates.projectedRange(subLevel, normalizedNormal, gradientWidth);

        data.setEnabled(true);
        data.setNormal(normalizedNormal);
        data.setGradientWidth(gradientWidth);
        data.setDistance(range.start());

        final int durationTicks = Math.max(1, Math.round(seconds * 20.0f));
        ANIMATIONS.put(subLevel, new Animation(range.start(), range.end(), durationTicks));
    }

    public static void clear(final ClientSubLevel subLevel) {
        ANIMATIONS.remove(subLevel);
        FrontierLaserStore.clear(subLevel);
        final SableFrontierData data = FrontierDataStore.get(subLevel);
        if (data != null) {
            data.setEnabled(false);
        }
    }

    public static void clearAll() {
        ANIMATIONS.clear();
        FrontierLaserStore.clearAll();
        FrontierDataStore.clearAll();
    }

    public static void tick(final ClientTickEvent.Post event) {
        FrontierLaserStore.tick();
        ANIMATIONS.entrySet().removeIf(entry -> tick(entry.getKey(), entry.getValue()));
    }

    private static boolean tick(final ClientSubLevel subLevel, final Animation animation) {
        if (subLevel.isRemoved()) {
            FrontierDataStore.clear(subLevel);
            FrontierLaserStore.clear(subLevel);
            return true;
        }

        final SableFrontierData data = FrontierDataStore.get(subLevel);
        if (data == null) {
            return true;
        }

        animation.elapsedTicks++;
        final float progress = Math.min(1.0f, animation.elapsedTicks / (float) animation.durationTicks);
        data.setDistance(animation.startDistance + (animation.endDistance - animation.startDistance) * progress);

        if (progress >= 1.0f) {
            data.setEnabled(false);
            FrontierLaserStore.startRetract(subLevel);
            return true;
        }
        return false;
    }

    private static final class Animation {
        private final float startDistance;
        private final float endDistance;
        private final int durationTicks;
        private int elapsedTicks;

        private Animation(final float startDistance, final float endDistance, final int durationTicks) {
            this.startDistance = startDistance;
            this.endDistance = endDistance;
            this.durationTicks = durationTicks;
        }
    }
}

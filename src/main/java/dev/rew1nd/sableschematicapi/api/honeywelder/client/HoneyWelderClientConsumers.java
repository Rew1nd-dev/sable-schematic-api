package dev.rew1nd.sableschematicapi.api.honeywelder.client;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class HoneyWelderClientConsumers {
    private static final Map<ResourceLocation, HoneyWelderClientConsumer> CONSUMERS = new LinkedHashMap<>();

    private HoneyWelderClientConsumers() {
    }

    public static synchronized void register(final HoneyWelderClientConsumer consumer) {
        if (consumer == null || consumer.id() == null) {
            throw new IllegalArgumentException("Honey welder client consumer and id must be non-null.");
        }
        final HoneyWelderClientConsumer previous = CONSUMERS.putIfAbsent(consumer.id(), consumer);
        if (previous != null && previous != consumer) {
            throw new IllegalStateException("Duplicate honey welder client consumer: " + consumer.id());
        }
    }

    public static synchronized Optional<HoneyWelderClientConsumer> find(final ResourceLocation id) {
        return Optional.ofNullable(id == null ? null : CONSUMERS.get(id));
    }
}

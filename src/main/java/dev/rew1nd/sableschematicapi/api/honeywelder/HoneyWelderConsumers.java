package dev.rew1nd.sableschematicapi.api.honeywelder;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Process-wide registry populated by common setup in downstream mods. */
public final class HoneyWelderConsumers {
    private static final Map<ResourceLocation, HoneyWelderConsumer> CONSUMERS = new LinkedHashMap<>();

    private HoneyWelderConsumers() {
    }

    public static synchronized void register(final HoneyWelderConsumer consumer) {
        if (consumer == null || consumer.id() == null) {
            throw new IllegalArgumentException("Honey welder consumer and id must be non-null.");
        }
        final HoneyWelderConsumer previous = CONSUMERS.putIfAbsent(consumer.id(), consumer);
        if (previous != null && previous != consumer) {
            throw new IllegalStateException("Duplicate honey welder consumer: " + consumer.id());
        }
    }

    public static synchronized Optional<HoneyWelderConsumer> find(final ResourceLocation id) {
        return Optional.ofNullable(id == null ? null : CONSUMERS.get(id));
    }

    public static synchronized Collection<HoneyWelderConsumer> values() {
        final ArrayList<HoneyWelderConsumer> values = new ArrayList<>(CONSUMERS.values());
        values.sort(Comparator.comparing(value -> value.id().toString()));
        return java.util.List.copyOf(values);
    }
}

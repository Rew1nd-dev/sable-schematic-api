package dev.rew1nd.sableschematicapi.survival.physicsmanager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/** Pure deterministic algorithms used by both the server admission layer and client presentation. */
public final class PhysicsManagerAlgorithms {
    private PhysicsManagerAlgorithms() {
    }

    public static String groupSignature(final String dimension,
                                        final UUID groupId,
                                        final Collection<UUID> memberIds) {
        final StringBuilder source = new StringBuilder()
                .append(dimension)
                .append('\n')
                .append(groupId)
                .append('\n');
        memberIds.stream()
                .map(UUID::toString)
                .sorted()
                .forEach(uuid -> source.append(uuid).append('\n'));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(source.toString().getBytes(StandardCharsets.UTF_8))
            );
        } catch (final NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
    }

    public static <T> Comparator<T> distanceComparator(final ToDoubleFunction<T> distance,
                                                        final Function<T, String> stableId) {
        return Comparator
                .comparingDouble((T value) -> {
                    final double measured = distance.applyAsDouble(value);
                    return measured < 0.0D ? Double.POSITIVE_INFINITY : measured;
                })
                .thenComparing(stableId);
    }

    public static <T> Comparator<T> nameComparator(final Function<T, String> name,
                                                    final Function<T, String> stableId) {
        return Comparator
                .comparing((T value) -> name.apply(value).toLowerCase(Locale.ROOT))
                .thenComparing(stableId);
    }

    public static <T> T largest(final Collection<T> values,
                                final ToDoubleFunction<T> volume,
                                final Function<T, String> stableId) {
        return values.stream()
                .sorted(Comparator
                        .comparingDouble(volume)
                        .reversed()
                        .thenComparing(stableId))
                .findFirst()
                .orElseThrow();
    }

    public static double volume(final double minX,
                                final double minY,
                                final double minZ,
                                final double maxX,
                                final double maxY,
                                final double maxZ) {
        return Math.max(0.0D, maxX - minX)
                * Math.max(0.0D, maxY - minY)
                * Math.max(0.0D, maxZ - minZ);
    }

    public static Point maxCorner(final double maxX, final double maxY, final double maxZ) {
        return new Point(maxX, maxY, maxZ);
    }

    public static <T> AggregateStatic aggregateStatic(final Collection<T> values,
                                                      final Predicate<T> isStatic) {
        boolean anyStatic = false;
        boolean anyDynamic = false;
        for (final T value : values) {
            anyStatic |= isStatic.test(value);
            anyDynamic |= !isStatic.test(value);
        }
        if (anyStatic && anyDynamic) {
            return AggregateStatic.MIXED;
        }
        return anyStatic ? AggregateStatic.STATIC : AggregateStatic.DYNAMIC;
    }

    public enum AggregateStatic {
        STATIC,
        DYNAMIC,
        MIXED
    }

    public record Point(double x, double y, double z) {
    }
}

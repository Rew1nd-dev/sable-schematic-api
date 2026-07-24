package dev.rew1nd.sableschematicapi.survival.film;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintSummary;
import net.minecraft.core.UUIDUtil;

import java.util.Objects;
import java.util.UUID;

/** Lightweight immutable metadata retained on a recorded film. */
public record FilmSnapshot(String name,
                           String author,
                           UUID ownerId,
                           String ownerName,
                           BlueprintSummary summary) {
    public static final Codec<FilmSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(FilmSnapshot::name),
            Codec.STRING.fieldOf("author").forGetter(FilmSnapshot::author),
            UUIDUtil.CODEC.fieldOf("owner_id").forGetter(FilmSnapshot::ownerId),
            Codec.STRING.fieldOf("owner_name").forGetter(FilmSnapshot::ownerName),
            BlueprintSummary.CODEC.fieldOf("summary").forGetter(FilmSnapshot::summary)
    ).apply(instance, FilmSnapshot::new));

    public FilmSnapshot {
        name = clamp(name, 128);
        author = clamp(author, 128);
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        ownerName = clamp(ownerName, 64);
        summary = Objects.requireNonNull(summary, "summary");
    }

    private static String clamp(final String value, final int maximumLength) {
        final String safe = value == null ? "" : value;
        return safe.length() <= maximumLength ? safe : safe.substring(0, maximumLength);
    }
}

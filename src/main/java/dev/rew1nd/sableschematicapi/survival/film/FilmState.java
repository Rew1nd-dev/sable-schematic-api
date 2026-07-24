package dev.rew1nd.sableschematicapi.survival.film;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.rew1nd.sableschematicapi.survival.manuscript.QuarterTurn;

import java.util.Locale;
import java.util.Optional;

/** Strong item state for empty and recorded film. */
public record FilmState(Optional<String> objectSha256,
                        QuarterTurn rotation,
                        Optional<FilmSnapshot> snapshot) {
    public static final FilmState EMPTY = new FilmState(Optional.empty(), QuarterTurn.DEG_0, Optional.empty());
    public static final Codec<FilmState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("object_sha256").forGetter(FilmState::objectSha256),
            QuarterTurn.CODEC.optionalFieldOf("rotation", QuarterTurn.DEG_0).forGetter(FilmState::rotation),
            FilmSnapshot.CODEC.optionalFieldOf("snapshot").forGetter(FilmState::snapshot)
    ).apply(instance, FilmState::new));

    public FilmState {
        objectSha256 = objectSha256 == null ? Optional.empty() : objectSha256.map(value -> value.toLowerCase(Locale.ROOT));
        rotation = rotation == null ? QuarterTurn.DEG_0 : rotation;
        snapshot = snapshot == null ? Optional.empty() : snapshot;
        if (objectSha256.isEmpty() || snapshot.isEmpty()) {
            objectSha256 = Optional.empty();
            snapshot = Optional.empty();
            rotation = QuarterTurn.DEG_0;
        } else if (!objectSha256.get().matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid blueprint object SHA-256.");
        }
    }

    public static FilmState recorded(final String sha256,
                                     final QuarterTurn rotation,
                                     final FilmSnapshot snapshot) {
        return new FilmState(Optional.of(sha256), rotation, Optional.of(snapshot));
    }

    public boolean isRecorded() {
        return this.objectSha256.isPresent();
    }
}

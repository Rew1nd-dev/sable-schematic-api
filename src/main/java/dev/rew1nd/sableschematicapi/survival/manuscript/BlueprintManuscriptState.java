package dev.rew1nd.sableschematicapi.survival.manuscript;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/** Strong item state for both empty and filled blueprint manuscripts. */
public record BlueprintManuscriptState(Optional<BlueprintAlias> alias, QuarterTurn rotation) {
    public static final BlueprintManuscriptState EMPTY =
            new BlueprintManuscriptState(Optional.empty(), QuarterTurn.DEG_0);
    public static final Codec<BlueprintManuscriptState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlueprintAlias.CODEC.optionalFieldOf("alias").forGetter(BlueprintManuscriptState::alias),
            QuarterTurn.CODEC.optionalFieldOf("rotation", QuarterTurn.DEG_0).forGetter(BlueprintManuscriptState::rotation)
    ).apply(instance, BlueprintManuscriptState::new));

    public BlueprintManuscriptState {
        alias = alias == null ? Optional.empty() : alias;
        rotation = rotation == null ? QuarterTurn.DEG_0 : rotation;
        if (alias.isEmpty()) {
            rotation = QuarterTurn.DEG_0;
        }
    }

    public static BlueprintManuscriptState filled(final BlueprintAlias alias) {
        return new BlueprintManuscriptState(Optional.of(alias), QuarterTurn.DEG_0);
    }

    public boolean isFilled() {
        return this.alias.isPresent();
    }

    public BlueprintManuscriptState rotateClockwise() {
        return this.isFilled() ? new BlueprintManuscriptState(this.alias, this.rotation.clockwise()) : this;
    }

    public BlueprintManuscriptState rotateCounterClockwise() {
        return this.isFilled() ? new BlueprintManuscriptState(this.alias, this.rotation.counterClockwise()) : this;
    }
}

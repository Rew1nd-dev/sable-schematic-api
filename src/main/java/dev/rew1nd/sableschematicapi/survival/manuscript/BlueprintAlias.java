package dev.rew1nd.sableschematicapi.survival.manuscript;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.Objects;
import java.util.UUID;

/** Mutable server blueprint identity referenced by a filled manuscript. */
public record BlueprintAlias(UUID ownerId, String ownerName, String blueprintName) {
    public static final Codec<BlueprintAlias> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("owner_id").forGetter(BlueprintAlias::ownerId),
            Codec.STRING.fieldOf("owner_name").forGetter(BlueprintAlias::ownerName),
            Codec.STRING.fieldOf("blueprint_name").forGetter(BlueprintAlias::blueprintName)
    ).apply(instance, BlueprintAlias::new));

    public BlueprintAlias {
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        ownerName = clamp(ownerName, 64);
        blueprintName = clamp(Objects.requireNonNull(blueprintName, "blueprintName"), 128);
        if (blueprintName.isBlank()) {
            throw new IllegalArgumentException("Blueprint name may not be blank.");
        }
    }

    public String cacheKey() {
        return this.ownerId + "/" + this.blueprintName;
    }

    private static String clamp(final String value, final int maximumLength) {
        final String safe = value == null ? "" : value;
        return safe.length() <= maximumLength ? safe : safe.substring(0, maximumLength);
    }
}

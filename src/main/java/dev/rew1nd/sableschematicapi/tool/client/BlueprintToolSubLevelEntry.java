package dev.rew1nd.sableschematicapi.tool.client;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Locale;
import java.util.UUID;

public record BlueprintToolSubLevelEntry(String dimension,
                                         UUID uuid,
                                         String name,
                                         String loadState,
                                         boolean staticLocked,
                                         double x,
                                         double y,
                                         double z,
                                         double distance) {
    public static BlueprintToolSubLevelEntry fromTag(final CompoundTag tag) {
        return new BlueprintToolSubLevelEntry(
                tag.getString("dimension"),
                tag.getUUID("uuid"),
                tag.contains("name", Tag.TAG_STRING) ? tag.getString("name") : "",
                tag.getString("load_state"),
                tag.getBoolean("static"),
                tag.getDouble("x"),
                tag.getDouble("y"),
                tag.getDouble("z"),
                tag.getDouble("distance")
        );
    }

    public String displayName() {
        return this.name == null || this.name.isBlank() ? shortUuid() : this.name;
    }

    public String shortUuid() {
        final String value = this.uuid.toString();
        return value.substring(0, Math.min(8, value.length()));
    }

    public String stateLabel() {
        return this.loadState.toLowerCase(Locale.ROOT);
    }

    public String distanceLabel() {
        return this.distance < 0.0 ? "--" : String.format(Locale.ROOT, "%.2f", this.distance);
    }
}

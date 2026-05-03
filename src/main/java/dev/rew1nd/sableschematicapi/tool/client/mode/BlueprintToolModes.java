package dev.rew1nd.sableschematicapi.tool.client.mode;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BlueprintToolModes {
    public static final BlueprintToolMode BLUEPRINT = new BlueprintLibraryToolMode();
    public static final BlueprintToolMode DELETE = new BlueprintDeleteToolMode();
    public static final BlueprintToolMode SUBLEVELS = new SubLevelToolMode();

    private static final List<BlueprintToolMode> MODES = List.of(BLUEPRINT, DELETE, SUBLEVELS);
    private static final Map<ResourceLocation, BlueprintToolMode> BY_ID = new LinkedHashMap<>();

    static {
        for (final BlueprintToolMode mode : MODES) {
            BY_ID.put(mode.id(), mode);
        }
    }

    private BlueprintToolModes() {
    }

    public static List<BlueprintToolMode> all() {
        return MODES;
    }

    public static BlueprintToolMode byId(final ResourceLocation id) {
        return BY_ID.getOrDefault(id, BLUEPRINT);
    }

    public static ResourceLocation id(final String path) {
        return SableSchematicApi.id("blueprint_tool/" + path);
    }
}

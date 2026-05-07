package dev.rew1nd.sableschematicapi.tool.client.mode;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BlueprintToolModes {
    public static final BlueprintToolMode BLUEPRINT = new BlueprintLibraryToolMode();
    public static final BlueprintToolMode DELETE = new BlueprintDeleteToolMode();
    public static final BlueprintToolMode SUBLEVELS = new SubLevelToolMode();

    private static final List<BlueprintToolMode> MODES = new ArrayList<>();
    private static final Map<ResourceLocation, BlueprintToolMode> BY_ID = new LinkedHashMap<>();
    private static boolean defaultsRegistered;

    private BlueprintToolModes() {
    }

    public static synchronized void registerDefaults() {
        if (defaultsRegistered) {
            return;
        }

        defaultsRegistered = true;
        registerInternal(BLUEPRINT);
        registerInternal(DELETE);
        registerInternal(SUBLEVELS);
    }

    public static synchronized void register(final BlueprintToolMode mode) {
        registerDefaults();
        registerInternal(mode);
    }

    public static synchronized List<BlueprintToolMode> all() {
        registerDefaults();
        return List.copyOf(MODES);
    }

    public static synchronized BlueprintToolMode byId(final ResourceLocation id) {
        registerDefaults();
        return BY_ID.getOrDefault(id, BLUEPRINT);
    }

    public static ResourceLocation id(final String path) {
        return SableSchematicApi.id("blueprint_tool/" + path);
    }

    private static void registerInternal(final BlueprintToolMode mode) {
        final BlueprintToolMode safeMode = Objects.requireNonNull(mode, "mode");
        final ResourceLocation id = Objects.requireNonNull(safeMode.id(), "mode id");
        if (BY_ID.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate blueprint tool mode: " + id);
        }

        MODES.add(safeMode);
        BY_ID.put(id, safeMode);
    }
}

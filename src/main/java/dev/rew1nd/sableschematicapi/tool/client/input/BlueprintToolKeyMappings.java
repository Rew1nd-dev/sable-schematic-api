package dev.rew1nd.sableschematicapi.tool.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class BlueprintToolKeyMappings {
    private static final String CATEGORY = "key.categories.sable_schematic_api";

    public static final KeyMapping OPEN_BLUEPRINT_TOOL = new KeyMapping(
            "key.sable_schematic_api.blueprint_tool.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_TAB,
            CATEGORY
    );

    private BlueprintToolKeyMappings() {
    }

    public static void register(final RegisterKeyMappingsEvent event) {
        event.register(OPEN_BLUEPRINT_TOOL);
    }
}

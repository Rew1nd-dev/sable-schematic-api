package dev.rew1nd.sableschematicapi.survival.camera.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class CameraKeyMappings {
    public static final KeyMapping OPEN_CAMERA = new KeyMapping(
            "key.sable_schematic_api.camera.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_TAB,
            "key.categories.sable_schematic_api"
    );

    private CameraKeyMappings() {
    }

    public static void register(final RegisterKeyMappingsEvent event) {
        event.register(OPEN_CAMERA);
    }
}

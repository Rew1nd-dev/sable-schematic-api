package dev.rew1nd.sableschematicapi.tool.client.preview.camera;

import dev.rew1nd.sableschematicapi.api.client.preview.SablePreviewProjectionFrame;
import org.joml.Matrix4f;
import org.joml.Vector3d;

public record BlueprintToolPreviewCamera(
        Matrix4f projection,
        Matrix4f modelView,
        Vector3d position,
        SablePreviewProjectionFrame projectionFrame
) {
}

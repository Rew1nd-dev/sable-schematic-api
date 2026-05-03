package dev.rew1nd.sableschematicapi.tool.client.preview.camera;

import org.joml.Matrix4f;
import org.joml.Vector3d;

public record BlueprintToolPreviewCamera(Matrix4f projection, Matrix4f modelView, Vector3d position) {
}

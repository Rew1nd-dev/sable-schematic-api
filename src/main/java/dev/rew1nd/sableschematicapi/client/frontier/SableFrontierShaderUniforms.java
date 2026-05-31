package dev.rew1nd.sableschematicapi.client.frontier;

import com.mojang.blaze3d.shaders.Uniform;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3fc;

public final class SableFrontierShaderUniforms {
    private SableFrontierShaderUniforms() {
    }

    public static void applyVanillaChunked(final ShaderInstance shader,
                                           final ClientSubLevel subLevel,
                                           final Vector3dc renderOrigin,
                                           final double cameraX,
                                           final double cameraY,
                                           final double cameraZ) {
        final SableFrontierData data = FrontierDataStore.get(subLevel);
        if (data == null || !data.enabled()) {
            disable(shader);
            return;
        }

        final Vector3fc normal = data.normal();
        setFloat(shader, SableFrontierShaderPreProcessor.ENABLED_UNIFORM, 1.0f);
        setVec4(shader, SableFrontierShaderPreProcessor.PLANE_UNIFORM,
                normal.x(), normal.y(), normal.z(), data.distance());
        setFloat(shader, SableFrontierShaderPreProcessor.GRADIENT_WIDTH_UNIFORM, data.gradientWidth());

        final Pose3dc renderPose = subLevel.renderPose();
        final Quaterniondc renderRot = renderPose.orientation();
        final Vector3d renderPos = new Vector3d(renderPose.position());
        final Vector3d renderCenterOfRotation = renderRot.transform(
                new Vector3d(renderPose.rotationPoint()).sub(renderOrigin)
        );
        renderPos.sub(renderCenterOfRotation);

        final Vector3d localCameraOffset = renderRot.transformInverse(
                new Vector3d(cameraX, cameraY, cameraZ).sub(renderPos).mul(-1.0),
                new Vector3d()
        );
        setVec3(shader, SableFrontierShaderPreProcessor.LOCAL_OFFSET_UNIFORM,
                (float) localCameraOffset.x(),
                (float) localCameraOffset.y(),
                (float) localCameraOffset.z());
    }

    public static void disable(final ShaderInstance shader) {
        setFloat(shader, SableFrontierShaderPreProcessor.ENABLED_UNIFORM, 0.0f);
    }

    private static void setFloat(final ShaderInstance shader, final String name, final float value) {
        final Uniform uniform = shader.getUniform(name);
        if (uniform == null) {
            return;
        }
        uniform.set(value);
        uniform.upload();
    }

    private static void setVec3(final ShaderInstance shader, final String name, final float x, final float y, final float z) {
        final Uniform uniform = shader.getUniform(name);
        if (uniform == null) {
            return;
        }
        uniform.set(x, y, z);
        uniform.upload();
    }

    private static void setVec4(final ShaderInstance shader, final String name, final float x, final float y, final float z, final float w) {
        final Uniform uniform = shader.getUniform(name);
        if (uniform == null) {
            return;
        }
        uniform.set(x, y, z, w);
        uniform.upload();
    }
}

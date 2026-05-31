package dev.rew1nd.sableschematicapi.client.frontier;

import foundry.veil.api.client.render.shader.processor.ShaderPreProcessor;
import io.github.ocelot.glslprocessor.api.GlslInjectionPoint;
import io.github.ocelot.glslprocessor.api.GlslParser;
import io.github.ocelot.glslprocessor.api.GlslSyntaxException;
import io.github.ocelot.glslprocessor.api.node.GlslNodeList;
import io.github.ocelot.glslprocessor.api.node.GlslTree;
import net.minecraft.client.renderer.RenderType;

import java.util.List;

public final class SableFrontierShaderPreProcessor implements ShaderPreProcessor {
    public static final String ENABLED_UNIFORM = "SableFrontierEnabled";
    public static final String PLANE_UNIFORM = "SableFrontierPlane";
    public static final String GRADIENT_WIDTH_UNIFORM = "SableFrontierGradientWidth";
    public static final String LOCAL_OFFSET_UNIFORM = "SableFrontierLocalOffset";
    private static final String DISTANCE_VARYING = "sableFrontierDistance";

    @Override
    public void modify(final Context ctx, final GlslTree tree) throws GlslSyntaxException {
        if (!ctx.isSourceFile()) {
            return;
        }

        if (!(ctx instanceof final MinecraftContext minecraftContext)) {
            return;
        }

        if (!matchesChunkShader(minecraftContext)) {
            return;
        }

        if (ctx.isVertex()) {
            this.modifyVertexShader(tree);
        } else if (ctx.isFragment()) {
            this.modifyFragmentShader(tree);
        }
    }

    private static boolean matchesChunkShader(final MinecraftContext minecraftContext) {
        final List<RenderType> renderTypes = RenderType.chunkBufferLayers();
        for (final RenderType renderType : renderTypes) {
            if (minecraftContext.shaderInstance().equals("rendertype_%s".formatted(renderType.name))) {
                return true;
            }
        }
        return false;
    }

    private void modifyVertexShader(final GlslTree tree) throws GlslSyntaxException {
        final GlslNodeList body = tree.mainFunction().orElseThrow().getBody();
        assert body != null;

        tree.getBody().add(GlslInjectionPoint.BEFORE_MAIN, GlslParser.parseExpression("uniform float %s;".formatted(ENABLED_UNIFORM)));
        tree.getBody().add(GlslInjectionPoint.BEFORE_MAIN, GlslParser.parseExpression("uniform vec4 %s;".formatted(PLANE_UNIFORM)));
        tree.getBody().add(GlslInjectionPoint.BEFORE_MAIN, GlslParser.parseExpression("uniform vec3 %s;".formatted(LOCAL_OFFSET_UNIFORM)));
        tree.getBody().add(GlslInjectionPoint.BEFORE_MAIN, GlslParser.parseExpression("out float %s;".formatted(DISTANCE_VARYING)));

        body.add(1, GlslParser.parseExpression("""
                if (%s > 0.5) {
                    vec3 sableFrontierLocalPos = pos - %s;
                    %s = %s.w - dot(%s.xyz, sableFrontierLocalPos);
                } else {
                    %s = 1.0;
                }
                """.formatted(
                ENABLED_UNIFORM,
                LOCAL_OFFSET_UNIFORM,
                DISTANCE_VARYING,
                PLANE_UNIFORM,
                PLANE_UNIFORM,
                DISTANCE_VARYING
        ).trim()));
    }

    private void modifyFragmentShader(final GlslTree tree) throws GlslSyntaxException {
        final GlslNodeList body = tree.mainFunction().orElseThrow().getBody();
        assert body != null;

        tree.getBody().add(GlslInjectionPoint.BEFORE_MAIN, GlslParser.parseExpression("uniform float %s;".formatted(ENABLED_UNIFORM)));
        tree.getBody().add(GlslInjectionPoint.BEFORE_MAIN, GlslParser.parseExpression("uniform float %s;".formatted(GRADIENT_WIDTH_UNIFORM)));
        tree.getBody().add(GlslInjectionPoint.BEFORE_MAIN, GlslParser.parseExpression("in float %s;".formatted(DISTANCE_VARYING)));

        body.add(GlslParser.parseExpression("""
                if (%s > 0.5) {
                    float sableFrontierWidth = max(%s, 0.0001);
                    float sableFrontierReveal = smoothstep(-sableFrontierWidth, 0.0, %s);
                    float sableFrontierDither = fract(sin(dot(floor(gl_FragCoord.xy), vec2(12.9898, 78.233))) * 43758.5453);
                    if (sableFrontierReveal <= sableFrontierDither) {
                        discard;
                    }
                    fragColor.a *= sableFrontierReveal;
                }
                """.formatted(
                ENABLED_UNIFORM,
                GRADIENT_WIDTH_UNIFORM,
                DISTANCE_VARYING
        ).trim()));
    }
}

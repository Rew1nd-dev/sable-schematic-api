package com.mojang.blaze3d.systems;

import com.google.common.collect.Queues;
import com.mojang.blaze3d.DontObfuscate;
import com.mojang.blaze3d.pipeline.RenderCall;
import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.TimeSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallbackI;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
@DontObfuscate
public class RenderSystem {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final ConcurrentLinkedQueue<RenderCall> recordingQueue = Queues.newConcurrentLinkedQueue();
    private static final Tesselator RENDER_THREAD_TESSELATOR = new Tesselator(1536);
    private static final int MINIMUM_ATLAS_TEXTURE_SIZE = 1024;
    @Nullable
    private static Thread renderThread;
    private static int MAX_SUPPORTED_TEXTURE_SIZE = -1;
    private static boolean isInInit;
    private static double lastDrawTime = Double.MIN_VALUE;
    private static final RenderSystem.AutoStorageIndexBuffer sharedSequential = new RenderSystem.AutoStorageIndexBuffer(1, 1, IntConsumer::accept);
    private static final RenderSystem.AutoStorageIndexBuffer sharedSequentialQuad = new RenderSystem.AutoStorageIndexBuffer(4, 6, (p_157398_, p_157399_) -> {
        p_157398_.accept(p_157399_ + 0);
        p_157398_.accept(p_157399_ + 1);
        p_157398_.accept(p_157399_ + 2);
        p_157398_.accept(p_157399_ + 2);
        p_157398_.accept(p_157399_ + 3);
        p_157398_.accept(p_157399_ + 0);
    });
    private static final RenderSystem.AutoStorageIndexBuffer sharedSequentialLines = new RenderSystem.AutoStorageIndexBuffer(4, 6, (p_157401_, p_157402_) -> {
        p_157401_.accept(p_157402_ + 0);
        p_157401_.accept(p_157402_ + 1);
        p_157401_.accept(p_157402_ + 2);
        p_157401_.accept(p_157402_ + 3);
        p_157401_.accept(p_157402_ + 2);
        p_157401_.accept(p_157402_ + 1);
    });
    private static Matrix4f projectionMatrix = new Matrix4f();
    private static Matrix4f savedProjectionMatrix = new Matrix4f();
    private static VertexSorting vertexSorting = VertexSorting.DISTANCE_TO_ORIGIN;
    private static VertexSorting savedVertexSorting = VertexSorting.DISTANCE_TO_ORIGIN;
    private static final Matrix4fStack modelViewStack = new Matrix4fStack(16);
    private static Matrix4f modelViewMatrix = new Matrix4f();
    private static Matrix4f textureMatrix = new Matrix4f();
    private static final int[] shaderTextures = new int[12];
    private static final float[] shaderColor = new float[]{1.0F, 1.0F, 1.0F, 1.0F};
    private static float shaderGlintAlpha = 1.0F;
    private static float shaderFogStart;
    private static float shaderFogEnd = 1.0F;
    private static final float[] shaderFogColor = new float[]{0.0F, 0.0F, 0.0F, 0.0F};
    private static FogShape shaderFogShape = FogShape.SPHERE;
    private static final Vector3f[] shaderLightDirections = new Vector3f[2];
    private static float shaderGameTime;
    private static float shaderLineWidth = 1.0F;
    private static String apiDescription = "Unknown";
    @Nullable
    private static ShaderInstance shader;
    private static final AtomicLong pollEventsWaitStart = new AtomicLong();
    private static final AtomicBoolean pollingEvents = new AtomicBoolean(false);

    public static void initRenderThread() {
        if (renderThread != null) {
            throw new IllegalStateException("Could not initialize render thread");
        } else {
            renderThread = Thread.currentThread();
        }
    }

    public static boolean isOnRenderThread() {
        return Thread.currentThread() == renderThread;
    }

    public static boolean isOnRenderThreadOrInit() {
        return isInInit || isOnRenderThread();
    }

    public static void assertOnRenderThreadOrInit() {
        if (!isInInit && !isOnRenderThread()) {
            throw constructThreadException();
        }
    }

    public static void assertOnRenderThread() {
        if (!isOnRenderThread()) {
            throw constructThreadException();
        }
    }

    private static IllegalStateException constructThreadException() {
        return new IllegalStateException("Rendersystem called from wrong thread");
    }

    public static void recordRenderCall(RenderCall renderCall) {
        recordingQueue.add(renderCall);
    }

    private static void pollEvents() {
        pollEventsWaitStart.set(Util.getMillis());
        pollingEvents.set(true);
        GLFW.glfwPollEvents();
        pollingEvents.set(false);
    }

    public static boolean isFrozenAtPollEvents() {
        return pollingEvents.get() && Util.getMillis() - pollEventsWaitStart.get() > 200L;
    }

    public static void flipFrame(long windowId) {
        pollEvents();
        replayQueue();
        Tesselator.getInstance().clear();
        GLFW.glfwSwapBuffers(windowId);
        pollEvents();
    }

    public static void replayQueue() {
        while (!recordingQueue.isEmpty()) {
            RenderCall rendercall = recordingQueue.poll();
            rendercall.execute();
        }
    }

    public static void limitDisplayFPS(int frameRateLimit) {
        double d0 = lastDrawTime + 1.0 / (double)frameRateLimit;

        double d1;
        for (d1 = GLFW.glfwGetTime(); d1 < d0; d1 = GLFW.glfwGetTime()) {
            GLFW.glfwWaitEventsTimeout(d0 - d1);
        }

        lastDrawTime = d1;
    }

    public static void disableDepthTest() {
        assertOnRenderThread();
        GlStateManager._disableDepthTest();
    }

    public static void enableDepthTest() {
        GlStateManager._enableDepthTest();
    }

    public static void enableScissor(int x, int y, int width, int height) {
        GlStateManager._enableScissorTest();
        GlStateManager._scissorBox(x, y, width, height);
    }

    public static void disableScissor() {
        GlStateManager._disableScissorTest();
    }

    public static void depthFunc(int depthFunc) {
        assertOnRenderThread();
        GlStateManager._depthFunc(depthFunc);
    }

    public static void depthMask(boolean flag) {
        assertOnRenderThread();
        GlStateManager._depthMask(flag);
    }

    public static void enableBlend() {
        assertOnRenderThread();
        GlStateManager._enableBlend();
    }

    public static void disableBlend() {
        assertOnRenderThread();
        GlStateManager._disableBlend();
    }

    public static void blendFunc(GlStateManager.SourceFactor sourceFactor, GlStateManager.DestFactor destFactor) {
        assertOnRenderThread();
        GlStateManager._blendFunc(sourceFactor.value, destFactor.value);
    }

    public static void blendFunc(int sourceFactor, int destFactor) {
        assertOnRenderThread();
        GlStateManager._blendFunc(sourceFactor, destFactor);
    }

    public static void blendFuncSeparate(
        GlStateManager.SourceFactor sourceFactor, GlStateManager.DestFactor destFactor, GlStateManager.SourceFactor sourceFactorAlpha, GlStateManager.DestFactor destFactorAlpha
    ) {
        assertOnRenderThread();
        GlStateManager._blendFuncSeparate(sourceFactor.value, destFactor.value, sourceFactorAlpha.value, destFactorAlpha.value);
    }

    public static void blendFuncSeparate(int sourceFactor, int destFactor, int sourceFactorAlpha, int destFactorAlpha) {
        assertOnRenderThread();
        GlStateManager._blendFuncSeparate(sourceFactor, destFactor, sourceFactorAlpha, destFactorAlpha);
    }

    public static void blendEquation(int mode) {
        assertOnRenderThread();
        GlStateManager._blendEquation(mode);
    }

    public static void enableCull() {
        assertOnRenderThread();
        GlStateManager._enableCull();
    }

    public static void disableCull() {
        assertOnRenderThread();
        GlStateManager._disableCull();
    }

    public static void polygonMode(int face, int mode) {
        assertOnRenderThread();
        GlStateManager._polygonMode(face, mode);
    }

    public static void enablePolygonOffset() {
        assertOnRenderThread();
        GlStateManager._enablePolygonOffset();
    }

    public static void disablePolygonOffset() {
        assertOnRenderThread();
        GlStateManager._disablePolygonOffset();
    }

    public static void polygonOffset(float factor, float units) {
        assertOnRenderThread();
        GlStateManager._polygonOffset(factor, units);
    }

    public static void enableColorLogicOp() {
        assertOnRenderThread();
        GlStateManager._enableColorLogicOp();
    }

    public static void disableColorLogicOp() {
        assertOnRenderThread();
        GlStateManager._disableColorLogicOp();
    }

    public static void logicOp(GlStateManager.LogicOp op) {
        assertOnRenderThread();
        GlStateManager._logicOp(op.value);
    }

    public static void activeTexture(int texture) {
        assertOnRenderThread();
        GlStateManager._activeTexture(texture);
    }

    public static void texParameter(int target, int parameterName, int parameter) {
        GlStateManager._texParameter(target, parameterName, parameter);
    }

    public static void deleteTexture(int texture) {
        GlStateManager._deleteTexture(texture);
    }

    public static void bindTextureForSetup(int texture) {
        bindTexture(texture);
    }

    public static void bindTexture(int texture) {
        GlStateManager._bindTexture(texture);
    }

    public static void viewport(int x, int y, int width, int height) {
        GlStateManager._viewport(x, y, width, height);
    }

    public static void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        assertOnRenderThread();
        GlStateManager._colorMask(red, green, blue, alpha);
    }

    public static void stencilFunc(int func, int ref, int mask) {
        assertOnRenderThread();
        GlStateManager._stencilFunc(func, ref, mask);
    }

    public static void stencilMask(int mask) {
        assertOnRenderThread();
        GlStateManager._stencilMask(mask);
    }

    public static void stencilOp(int sFail, int dpFail, int dpPass) {
        assertOnRenderThread();
        GlStateManager._stencilOp(sFail, dpFail, dpPass);
    }

    public static void clearDepth(double depth) {
        GlStateManager._clearDepth(depth);
    }

    public static void clearColor(float red, float green, float blue, float alpha) {
        GlStateManager._clearColor(red, green, blue, alpha);
    }

    public static void clearStencil(int index) {
        assertOnRenderThread();
        GlStateManager._clearStencil(index);
    }

    public static void clear(int mask, boolean checkError) {
        GlStateManager._clear(mask, checkError);
    }

    public static void setShaderFogStart(float p_shaderFogStart) {
        assertOnRenderThread();
        shaderFogStart = p_shaderFogStart;
    }

    public static float getShaderFogStart() {
        assertOnRenderThread();
        return shaderFogStart;
    }

    public static void setShaderGlintAlpha(double shaderGlintAlpha) {
        setShaderGlintAlpha((float)shaderGlintAlpha);
    }

    public static void setShaderGlintAlpha(float p_shaderGlintAlpha) {
        assertOnRenderThread();
        shaderGlintAlpha = p_shaderGlintAlpha;
    }

    public static float getShaderGlintAlpha() {
        assertOnRenderThread();
        return shaderGlintAlpha;
    }

    public static void setShaderFogEnd(float p_shaderFogEnd) {
        assertOnRenderThread();
        shaderFogEnd = p_shaderFogEnd;
    }

    public static float getShaderFogEnd() {
        assertOnRenderThread();
        return shaderFogEnd;
    }

    public static void setShaderFogColor(float red, float green, float blue, float alpha) {
        assertOnRenderThread();
        shaderFogColor[0] = red;
        shaderFogColor[1] = green;
        shaderFogColor[2] = blue;
        shaderFogColor[3] = alpha;
    }

    public static void setShaderFogColor(float red, float green, float blue) {
        setShaderFogColor(red, green, blue, 1.0F);
    }

    public static float[] getShaderFogColor() {
        assertOnRenderThread();
        return shaderFogColor;
    }

    public static void setShaderFogShape(FogShape p_shaderFogShape) {
        assertOnRenderThread();
        shaderFogShape = p_shaderFogShape;
    }

    public static FogShape getShaderFogShape() {
        assertOnRenderThread();
        return shaderFogShape;
    }

    public static void setShaderLights(Vector3f lightingVector0, Vector3f lightingVector1) {
        assertOnRenderThread();
        shaderLightDirections[0] = lightingVector0;
        shaderLightDirections[1] = lightingVector1;
    }

    public static void setupShaderLights(ShaderInstance instance) {
        assertOnRenderThread();
        if (instance.LIGHT0_DIRECTION != null) {
            instance.LIGHT0_DIRECTION.set(shaderLightDirections[0]);
        }

        if (instance.LIGHT1_DIRECTION != null) {
            instance.LIGHT1_DIRECTION.set(shaderLightDirections[1]);
        }
    }

    public static void setShaderColor(float red, float green, float blue, float alpha) {
        if (!isOnRenderThread()) {
            recordRenderCall(() -> _setShaderColor(red, green, blue, alpha));
        } else {
            _setShaderColor(red, green, blue, alpha);
        }
    }

    private static void _setShaderColor(float red, float green, float blue, float alpha) {
        shaderColor[0] = red;
        shaderColor[1] = green;
        shaderColor[2] = blue;
        shaderColor[3] = alpha;
    }

    public static float[] getShaderColor() {
        assertOnRenderThread();
        return shaderColor;
    }

    public static void drawElements(int mode, int count, int type) {
        assertOnRenderThread();
        GlStateManager._drawElements(mode, count, type, 0L);
    }

    public static void lineWidth(float p_shaderLineWidth) {
        if (!isOnRenderThread()) {
            recordRenderCall(() -> shaderLineWidth = p_shaderLineWidth);
        } else {
            shaderLineWidth = p_shaderLineWidth;
        }
    }

    public static float getShaderLineWidth() {
        assertOnRenderThread();
        return shaderLineWidth;
    }

    public static void pixelStore(int parameterName, int parameter) {
        GlStateManager._pixelStore(parameterName, parameter);
    }

    public static void readPixels(int x, int y, int width, int height, int format, int type, ByteBuffer pixels) {
        assertOnRenderThread();
        GlStateManager._readPixels(x, y, width, height, format, type, pixels);
    }

    public static void getString(int name, Consumer<String> consumer) {
        assertOnRenderThread();
        consumer.accept(GlStateManager._getString(name));
    }

    public static String getBackendDescription() {
        return String.format(Locale.ROOT, "LWJGL version %s", GLX._getLWJGLVersion());
    }

    public static String getApiDescription() {
        return apiDescription;
    }

    public static TimeSource.NanoTimeSource initBackendSystem() {
        return GLX._initGlfw()::getAsLong;
    }

    public static void initRenderer(int debugVerbosity, boolean synchronous) {
        GLX._init(debugVerbosity, synchronous);
        apiDescription = GLX.getOpenGLVersionString();
    }

    public static void setErrorCallback(GLFWErrorCallbackI callback) {
        GLX._setGlfwErrorCallback(callback);
    }

    public static void renderCrosshair(int lineLength) {
        assertOnRenderThread();
        GLX._renderCrosshair(lineLength, true, true, true);
    }

    public static String getCapsString() {
        assertOnRenderThread();
        return "Using framebuffer using OpenGL 3.2";
    }

    public static void setupDefaultState(int x, int y, int width, int height) {
        GlStateManager._clearDepth(1.0);
        GlStateManager._enableDepthTest();
        GlStateManager._depthFunc(515);
        projectionMatrix.identity();
        savedProjectionMatrix.identity();
        modelViewMatrix.identity();
        textureMatrix.identity();
        GlStateManager._viewport(x, y, width, height);
    }

    public static int maxSupportedTextureSize() {
        if (MAX_SUPPORTED_TEXTURE_SIZE == -1) {
            assertOnRenderThreadOrInit();
            int i = GlStateManager._getInteger(3379);

            for (int j = Math.max(32768, i); j >= 1024; j >>= 1) {
                GlStateManager._texImage2D(32868, 0, 6408, j, j, 0, 6408, 5121, null);
                int k = GlStateManager._getTexLevelParameter(32868, 0, 4096);
                if (k != 0) {
                    MAX_SUPPORTED_TEXTURE_SIZE = j;
                    return j;
                }
            }

            MAX_SUPPORTED_TEXTURE_SIZE = Math.max(i, 1024);
            LOGGER.info("Failed to determine maximum texture size by probing, trying GL_MAX_TEXTURE_SIZE = {}", MAX_SUPPORTED_TEXTURE_SIZE);
        }

        return MAX_SUPPORTED_TEXTURE_SIZE;
    }

    public static void glBindBuffer(int target, int buffer) {
        GlStateManager._glBindBuffer(target, buffer);
    }

    public static void glBindVertexArray(int array) {
        GlStateManager._glBindVertexArray(array);
    }

    public static void glBufferData(int target, ByteBuffer data, int usage) {
        assertOnRenderThreadOrInit();
        GlStateManager._glBufferData(target, data, usage);
    }

    public static void glDeleteBuffers(int buffer) {
        assertOnRenderThread();
        GlStateManager._glDeleteBuffers(buffer);
    }

    public static void glDeleteVertexArrays(int array) {
        assertOnRenderThread();
        GlStateManager._glDeleteVertexArrays(array);
    }

    public static void glUniform1i(int location, int value) {
        assertOnRenderThread();
        GlStateManager._glUniform1i(location, value);
    }

    public static void glUniform1(int location, IntBuffer value) {
        assertOnRenderThread();
        GlStateManager._glUniform1(location, value);
    }

    public static void glUniform2(int location, IntBuffer value) {
        assertOnRenderThread();
        GlStateManager._glUniform2(location, value);
    }

    public static void glUniform3(int location, IntBuffer value) {
        assertOnRenderThread();
        GlStateManager._glUniform3(location, value);
    }

    public static void glUniform4(int location, IntBuffer value) {
        assertOnRenderThread();
        GlStateManager._glUniform4(location, value);
    }

    public static void glUniform1(int location, FloatBuffer value) {
        assertOnRenderThread();
        GlStateManager._glUniform1(location, value);
    }

    public static void glUniform2(int location, FloatBuffer value) {
        assertOnRenderThread();
        GlStateManager._glUniform2(location, value);
    }

    public static void glUniform3(int location, FloatBuffer value) {
        assertOnRenderThread();
        GlStateManager._glUniform3(location, value);
    }

    public static void glUniform4(int location, FloatBuffer value) {
        assertOnRenderThread();
        GlStateManager._glUniform4(location, value);
    }

    public static void glUniformMatrix2(int location, boolean transpose, FloatBuffer value) {
        assertOnRenderThread();
        GlStateManager._glUniformMatrix2(location, transpose, value);
    }

    public static void glUniformMatrix3(int location, boolean transpose, FloatBuffer value) {
        assertOnRenderThread();
        GlStateManager._glUniformMatrix3(location, transpose, value);
    }

    public static void glUniformMatrix4(int location, boolean transpose, FloatBuffer value) {
        assertOnRenderThread();
        GlStateManager._glUniformMatrix4(location, transpose, value);
    }

    public static void setupOverlayColor(int textureId, int color) {
        assertOnRenderThread();
        setShaderTexture(1, textureId);
    }

    public static void teardownOverlayColor() {
        assertOnRenderThread();
        setShaderTexture(1, 0);
    }

    public static void setupLevelDiffuseLighting(Vector3f lightingVector0, Vector3f lightingVector1) {
        assertOnRenderThread();
        setShaderLights(lightingVector0, lightingVector1);
    }

    public static void setupGuiFlatDiffuseLighting(Vector3f lightingVector1, Vector3f lightingVector2) {
        assertOnRenderThread();
        GlStateManager.setupGuiFlatDiffuseLighting(lightingVector1, lightingVector2);
    }

    public static void setupGui3DDiffuseLighting(Vector3f lightingVector1, Vector3f lightingVector2) {
        assertOnRenderThread();
        GlStateManager.setupGui3DDiffuseLighting(lightingVector1, lightingVector2);
    }

    public static void beginInitialization() {
        isInInit = true;
    }

    public static void finishInitialization() {
        isInInit = false;
        if (!recordingQueue.isEmpty()) {
            replayQueue();
        }

        if (!recordingQueue.isEmpty()) {
            throw new IllegalStateException("Recorded to render queue during initialization");
        }
    }

    public static void glGenBuffers(Consumer<Integer> bufferIdConsumer) {
        if (!isOnRenderThread()) {
            recordRenderCall(() -> bufferIdConsumer.accept(GlStateManager._glGenBuffers()));
        } else {
            bufferIdConsumer.accept(GlStateManager._glGenBuffers());
        }
    }

    public static void glGenVertexArrays(Consumer<Integer> arrayObjectIdConsumer) {
        if (!isOnRenderThread()) {
            recordRenderCall(() -> arrayObjectIdConsumer.accept(GlStateManager._glGenVertexArrays()));
        } else {
            arrayObjectIdConsumer.accept(GlStateManager._glGenVertexArrays());
        }
    }

    public static Tesselator renderThreadTesselator() {
        assertOnRenderThread();
        return RENDER_THREAD_TESSELATOR;
    }

    public static void defaultBlendFunc() {
        blendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO
        );
    }

    @Deprecated
    public static void runAsFancy(Runnable fancyRunnable) {
        boolean flag = Minecraft.useShaderTransparency();
        if (!flag) {
            fancyRunnable.run();
        } else {
            OptionInstance<GraphicsStatus> optioninstance = Minecraft.getInstance().options.graphicsMode();
            GraphicsStatus graphicsstatus = optioninstance.get();
            optioninstance.set(GraphicsStatus.FANCY);
            fancyRunnable.run();
            optioninstance.set(graphicsstatus);
        }
    }

    public static void setShader(Supplier<ShaderInstance> shaderSupplier) {
        if (!isOnRenderThread()) {
            recordRenderCall(() -> shader = shaderSupplier.get());
        } else {
            shader = shaderSupplier.get();
        }
    }

    @Nullable
    public static ShaderInstance getShader() {
        assertOnRenderThread();
        return shader;
    }

    public static void setShaderTexture(int shaderTexture, ResourceLocation textureId) {
        if (!isOnRenderThread()) {
            recordRenderCall(() -> _setShaderTexture(shaderTexture, textureId));
        } else {
            _setShaderTexture(shaderTexture, textureId);
        }
    }

    public static void _setShaderTexture(int shaderTexture, ResourceLocation textureId) {
        if (shaderTexture >= 0 && shaderTexture < shaderTextures.length) {
            TextureManager texturemanager = Minecraft.getInstance().getTextureManager();
            AbstractTexture abstracttexture = texturemanager.getTexture(textureId);
            shaderTextures[shaderTexture] = abstracttexture.getId();
        }
    }

    public static void setShaderTexture(int shaderTexture, int textureId) {
        if (!isOnRenderThread()) {
            recordRenderCall(() -> _setShaderTexture(shaderTexture, textureId));
        } else {
            _setShaderTexture(shaderTexture, textureId);
        }
    }

    public static void _setShaderTexture(int shaderTexture, int textureId) {
        if (shaderTexture >= 0 && shaderTexture < shaderTextures.length) {
            shaderTextures[shaderTexture] = textureId;
        }
    }

    public static int getShaderTexture(int shaderTexture) {
        assertOnRenderThread();
        return shaderTexture >= 0 && shaderTexture < shaderTextures.length ? shaderTextures[shaderTexture] : 0;
    }

    public static void setProjectionMatrix(Matrix4f p_projectionMatrix, VertexSorting p_vertexSorting) {
        Matrix4f matrix4f = new Matrix4f(p_projectionMatrix);
        if (!isOnRenderThread()) {
            recordRenderCall(() -> {
                projectionMatrix = matrix4f;
                vertexSorting = p_vertexSorting;
            });
        } else {
            projectionMatrix = matrix4f;
            vertexSorting = p_vertexSorting;
        }
    }

    public static void setTextureMatrix(Matrix4f p_textureMatrix) {
        Matrix4f matrix4f = new Matrix4f(p_textureMatrix);
        if (!isOnRenderThread()) {
            recordRenderCall(() -> textureMatrix = matrix4f);
        } else {
            textureMatrix = matrix4f;
        }
    }

    public static void resetTextureMatrix() {
        if (!isOnRenderThread()) {
            recordRenderCall(() -> textureMatrix.identity());
        } else {
            textureMatrix.identity();
        }
    }

    public static void applyModelViewMatrix() {
        Matrix4f matrix4f = new Matrix4f(modelViewStack);
        if (!isOnRenderThread()) {
            recordRenderCall(() -> modelViewMatrix = matrix4f);
        } else {
            modelViewMatrix = matrix4f;
        }
    }

    public static void backupProjectionMatrix() {
        if (!isOnRenderThread()) {
            recordRenderCall(() -> _backupProjectionMatrix());
        } else {
            _backupProjectionMatrix();
        }
    }

    private static void _backupProjectionMatrix() {
        savedProjectionMatrix = projectionMatrix;
        savedVertexSorting = vertexSorting;
    }

    public static void restoreProjectionMatrix() {
        if (!isOnRenderThread()) {
            recordRenderCall(() -> _restoreProjectionMatrix());
        } else {
            _restoreProjectionMatrix();
        }
    }

    private static void _restoreProjectionMatrix() {
        projectionMatrix = savedProjectionMatrix;
        vertexSorting = savedVertexSorting;
    }

    public static Matrix4f getProjectionMatrix() {
        assertOnRenderThread();
        return projectionMatrix;
    }

    public static Matrix4f getModelViewMatrix() {
        assertOnRenderThread();
        return modelViewMatrix;
    }

    public static Matrix4fStack getModelViewStack() {
        return modelViewStack;
    }

    public static Matrix4f getTextureMatrix() {
        assertOnRenderThread();
        return textureMatrix;
    }

    public static RenderSystem.AutoStorageIndexBuffer getSequentialBuffer(VertexFormat.Mode formatMode) {
        assertOnRenderThread();

        return switch (formatMode) {
            case QUADS -> sharedSequentialQuad;
            case LINES -> sharedSequentialLines;
            default -> sharedSequential;
        };
    }

    public static void setShaderGameTime(long tickTime, float partialTicks) {
        float f = ((float)(tickTime % 24000L) + partialTicks) / 24000.0F;
        if (!isOnRenderThread()) {
            recordRenderCall(() -> shaderGameTime = f);
        } else {
            shaderGameTime = f;
        }
    }

    public static float getShaderGameTime() {
        assertOnRenderThread();
        return shaderGameTime;
    }

    public static VertexSorting getVertexSorting() {
        assertOnRenderThread();
        return vertexSorting;
    }

    @OnlyIn(Dist.CLIENT)
    public static final class AutoStorageIndexBuffer {
        private final int vertexStride;
        private final int indexStride;
        private final RenderSystem.AutoStorageIndexBuffer.IndexGenerator generator;
        private int name;
        private VertexFormat.IndexType type = VertexFormat.IndexType.SHORT;
        private int indexCount;

        AutoStorageIndexBuffer(int vertexStride, int indexStride, RenderSystem.AutoStorageIndexBuffer.IndexGenerator generator) {
            this.vertexStride = vertexStride;
            this.indexStride = indexStride;
            this.generator = generator;
        }

        public boolean hasStorage(int index) {
            return index <= this.indexCount;
        }

        public void bind(int index) {
            if (this.name == 0) {
                this.name = GlStateManager._glGenBuffers();
            }

            GlStateManager._glBindBuffer(34963, this.name);
            this.ensureStorage(index);
        }

        private void ensureStorage(int neededIndexCount) {
            if (!this.hasStorage(neededIndexCount)) {
                neededIndexCount = Mth.roundToward(neededIndexCount * 2, this.indexStride);
                RenderSystem.LOGGER.debug("Growing IndexBuffer: Old limit {}, new limit {}.", this.indexCount, neededIndexCount);
                int i = neededIndexCount / this.indexStride;
                int j = i * this.vertexStride;
                VertexFormat.IndexType vertexformat$indextype = VertexFormat.IndexType.least(j);
                int k = Mth.roundToward(neededIndexCount * vertexformat$indextype.bytes, 4);
                GlStateManager._glBufferData(34963, (long)k, 35048);
                ByteBuffer bytebuffer = GlStateManager._glMapBuffer(34963, 35001);
                if (bytebuffer == null) {
                    throw new RuntimeException("Failed to map GL buffer");
                } else {
                    this.type = vertexformat$indextype;
                    it.unimi.dsi.fastutil.ints.IntConsumer intconsumer = this.intConsumer(bytebuffer);

                    for (int l = 0; l < neededIndexCount; l += this.indexStride) {
                        this.generator.accept(intconsumer, l * this.vertexStride / this.indexStride);
                    }

                    GlStateManager._glUnmapBuffer(34963);
                    this.indexCount = neededIndexCount;
                }
            }
        }

        private it.unimi.dsi.fastutil.ints.IntConsumer intConsumer(ByteBuffer buffer) {
            switch (this.type) {
                case SHORT:
                    return p_157482_ -> buffer.putShort((short)p_157482_);
                case INT:
                default:
                    return buffer::putInt;
            }
        }

        public VertexFormat.IndexType type() {
            return this.type;
        }

        @OnlyIn(Dist.CLIENT)
        interface IndexGenerator {
            void accept(it.unimi.dsi.fastutil.ints.IntConsumer consumer, int index);
        }
    }

    public static void backupGlState(net.neoforged.neoforge.client.GlStateBackup state) {
        assertOnRenderThread();
        GlStateManager._backupGlState(state);
    }

    public static void restoreGlState(net.neoforged.neoforge.client.GlStateBackup state) {
        assertOnRenderThread();
        GlStateManager._restoreGlState(state);
    }
}

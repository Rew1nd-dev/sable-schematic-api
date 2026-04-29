package net.minecraft.client.renderer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.MapRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class GameRenderer implements AutoCloseable {
    private static final ResourceLocation NAUSEA_LOCATION = ResourceLocation.withDefaultNamespace("textures/misc/nausea.png");
    private static final ResourceLocation BLUR_LOCATION = ResourceLocation.withDefaultNamespace("shaders/post/blur.json");
    public static final int MAX_BLUR_RADIUS = 10;
    static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean DEPTH_BUFFER_DEBUG = false;
    public static final float PROJECTION_Z_NEAR = 0.05F;
    private static final float GUI_Z_NEAR = 1000.0F;
    final Minecraft minecraft;
    private final ResourceManager resourceManager;
    private final RandomSource random = RandomSource.create();
    private float renderDistance;
    public final ItemInHandRenderer itemInHandRenderer;
    private final MapRenderer mapRenderer;
    private final RenderBuffers renderBuffers;
    private int confusionAnimationTick;
    private float fov;
    private float oldFov;
    private float darkenWorldAmount;
    private float darkenWorldAmountO;
    private boolean renderHand = true;
    private boolean renderBlockOutline = true;
    private long lastScreenshotAttempt;
    private boolean hasWorldScreenshot;
    private long lastActiveTime = Util.getMillis();
    private final LightTexture lightTexture;
    private final OverlayTexture overlayTexture = new OverlayTexture();
    private boolean panoramicMode;
    private float zoom = 1.0F;
    private float zoomX;
    private float zoomY;
    public static final int ITEM_ACTIVATION_ANIMATION_LENGTH = 40;
    @Nullable
    private ItemStack itemActivationItem;
    private int itemActivationTicks;
    private float itemActivationOffX;
    private float itemActivationOffY;
    @Nullable
    PostChain postEffect;
    @Nullable
    private PostChain blurEffect;
    private boolean effectActive;
    private final Camera mainCamera = new Camera();
    @Nullable
    public ShaderInstance blitShader;
    private final Map<String, ShaderInstance> shaders = Maps.newHashMap();
    @Nullable
    private static ShaderInstance positionShader;
    @Nullable
    private static ShaderInstance positionColorShader;
    @Nullable
    private static ShaderInstance positionTexShader;
    @Nullable
    private static ShaderInstance positionTexColorShader;
    @Nullable
    private static ShaderInstance particleShader;
    @Nullable
    private static ShaderInstance positionColorLightmapShader;
    @Nullable
    private static ShaderInstance positionColorTexLightmapShader;
    @Nullable
    private static ShaderInstance rendertypeSolidShader;
    @Nullable
    private static ShaderInstance rendertypeCutoutMippedShader;
    @Nullable
    private static ShaderInstance rendertypeCutoutShader;
    @Nullable
    private static ShaderInstance rendertypeTranslucentShader;
    @Nullable
    private static ShaderInstance rendertypeTranslucentMovingBlockShader;
    @Nullable
    private static ShaderInstance rendertypeArmorCutoutNoCullShader;
    @Nullable
    private static ShaderInstance rendertypeEntitySolidShader;
    @Nullable
    private static ShaderInstance rendertypeEntityCutoutShader;
    @Nullable
    private static ShaderInstance rendertypeEntityCutoutNoCullShader;
    @Nullable
    private static ShaderInstance rendertypeEntityCutoutNoCullZOffsetShader;
    @Nullable
    private static ShaderInstance rendertypeItemEntityTranslucentCullShader;
    @Nullable
    private static ShaderInstance rendertypeEntityTranslucentCullShader;
    @Nullable
    private static ShaderInstance rendertypeEntityTranslucentShader;
    @Nullable
    private static ShaderInstance rendertypeEntityTranslucentEmissiveShader;
    @Nullable
    private static ShaderInstance rendertypeEntitySmoothCutoutShader;
    @Nullable
    private static ShaderInstance rendertypeBeaconBeamShader;
    @Nullable
    private static ShaderInstance rendertypeEntityDecalShader;
    @Nullable
    private static ShaderInstance rendertypeEntityNoOutlineShader;
    @Nullable
    private static ShaderInstance rendertypeEntityShadowShader;
    @Nullable
    private static ShaderInstance rendertypeEntityAlphaShader;
    @Nullable
    private static ShaderInstance rendertypeEyesShader;
    @Nullable
    private static ShaderInstance rendertypeEnergySwirlShader;
    @Nullable
    private static ShaderInstance rendertypeBreezeWindShader;
    @Nullable
    private static ShaderInstance rendertypeLeashShader;
    @Nullable
    private static ShaderInstance rendertypeWaterMaskShader;
    @Nullable
    private static ShaderInstance rendertypeOutlineShader;
    @Nullable
    private static ShaderInstance rendertypeArmorGlintShader;
    @Nullable
    private static ShaderInstance rendertypeArmorEntityGlintShader;
    @Nullable
    private static ShaderInstance rendertypeGlintTranslucentShader;
    @Nullable
    private static ShaderInstance rendertypeGlintShader;
    @Nullable
    private static ShaderInstance rendertypeGlintDirectShader;
    @Nullable
    private static ShaderInstance rendertypeEntityGlintShader;
    @Nullable
    private static ShaderInstance rendertypeEntityGlintDirectShader;
    @Nullable
    private static ShaderInstance rendertypeTextShader;
    @Nullable
    private static ShaderInstance rendertypeTextBackgroundShader;
    @Nullable
    private static ShaderInstance rendertypeTextIntensityShader;
    @Nullable
    private static ShaderInstance rendertypeTextSeeThroughShader;
    @Nullable
    private static ShaderInstance rendertypeTextBackgroundSeeThroughShader;
    @Nullable
    private static ShaderInstance rendertypeTextIntensitySeeThroughShader;
    @Nullable
    private static ShaderInstance rendertypeLightningShader;
    @Nullable
    private static ShaderInstance rendertypeTripwireShader;
    @Nullable
    private static ShaderInstance rendertypeEndPortalShader;
    @Nullable
    private static ShaderInstance rendertypeEndGatewayShader;
    @Nullable
    private static ShaderInstance rendertypeCloudsShader;
    @Nullable
    private static ShaderInstance rendertypeLinesShader;
    @Nullable
    private static ShaderInstance rendertypeCrumblingShader;
    @Nullable
    private static ShaderInstance rendertypeGuiShader;
    @Nullable
    private static ShaderInstance rendertypeGuiOverlayShader;
    @Nullable
    private static ShaderInstance rendertypeGuiTextHighlightShader;
    @Nullable
    private static ShaderInstance rendertypeGuiGhostRecipeOverlayShader;

    public GameRenderer(Minecraft minecraft, ItemInHandRenderer itemInHandRenderer, ResourceManager resourceManager, RenderBuffers renderBuffers) {
        this.minecraft = minecraft;
        this.resourceManager = resourceManager;
        this.itemInHandRenderer = itemInHandRenderer;
        this.mapRenderer = new MapRenderer(minecraft.getTextureManager(), minecraft.getMapDecorationTextures());
        this.lightTexture = new LightTexture(this, minecraft);
        this.renderBuffers = renderBuffers;
        this.postEffect = null;
    }

    @Override
    public void close() {
        this.lightTexture.close();
        this.mapRenderer.close();
        this.overlayTexture.close();
        this.shutdownEffect();
        this.shutdownShaders();
        if (this.blurEffect != null) {
            this.blurEffect.close();
        }

        if (this.blitShader != null) {
            this.blitShader.close();
        }
    }

    public void setRenderHand(boolean renderHand) {
        this.renderHand = renderHand;
    }

    public void setRenderBlockOutline(boolean renderBlockOutline) {
        this.renderBlockOutline = renderBlockOutline;
    }

    public void setPanoramicMode(boolean panoramicMode) {
        this.panoramicMode = panoramicMode;
    }

    public boolean isPanoramicMode() {
        return this.panoramicMode;
    }

    public void shutdownEffect() {
        if (this.postEffect != null) {
            this.postEffect.close();
        }

        this.postEffect = null;
    }

    public void togglePostEffect() {
        this.effectActive = !this.effectActive;
    }

    /**
     * What shader to use when spectating this entity
     */
    public void checkEntityPostEffect(@Nullable Entity entity) {
        if (this.postEffect != null) {
            this.postEffect.close();
        }

        this.postEffect = null;
        if (entity instanceof Creeper) {
            this.loadEffect(ResourceLocation.withDefaultNamespace("shaders/post/creeper.json"));
        } else if (entity instanceof Spider) {
            this.loadEffect(ResourceLocation.withDefaultNamespace("shaders/post/spider.json"));
        } else if (entity instanceof EnderMan) {
            this.loadEffect(ResourceLocation.withDefaultNamespace("shaders/post/invert.json"));
        } else {
            net.neoforged.neoforge.client.ClientHooks.loadEntityShader(entity, this);
        }
    }

    public void loadEffect(ResourceLocation resourceLocation) {
        if (this.postEffect != null) {
            this.postEffect.close();
        }

        try {
            this.postEffect = new PostChain(this.minecraft.getTextureManager(), this.resourceManager, this.minecraft.getMainRenderTarget(), resourceLocation);
            this.postEffect.resize(this.minecraft.getWindow().getWidth(), this.minecraft.getWindow().getHeight());
            this.effectActive = true;
        } catch (IOException ioexception) {
            LOGGER.warn("Failed to load shader: {}", resourceLocation, ioexception);
            this.effectActive = false;
        } catch (JsonSyntaxException jsonsyntaxexception) {
            LOGGER.warn("Failed to parse shader: {}", resourceLocation, jsonsyntaxexception);
            this.effectActive = false;
        }
    }

    private void loadBlurEffect(ResourceProvider resourceProvider) {
        if (this.blurEffect != null) {
            this.blurEffect.close();
        }

        try {
            this.blurEffect = new PostChain(this.minecraft.getTextureManager(), resourceProvider, this.minecraft.getMainRenderTarget(), BLUR_LOCATION);
            this.blurEffect.resize(this.minecraft.getWindow().getWidth(), this.minecraft.getWindow().getHeight());
        } catch (IOException ioexception) {
            LOGGER.warn("Failed to load shader: {}", BLUR_LOCATION, ioexception);
        } catch (JsonSyntaxException jsonsyntaxexception) {
            LOGGER.warn("Failed to parse shader: {}", BLUR_LOCATION, jsonsyntaxexception);
        }
    }

    public void processBlurEffect(float partialTick) {
        float f = (float)this.minecraft.options.getMenuBackgroundBlurriness();
        if (this.blurEffect != null && f >= 1.0F) {
            this.blurEffect.setUniform("Radius", f);
            this.blurEffect.process(partialTick);
        }
    }

    public PreparableReloadListener createReloadListener() {
        return new SimplePreparableReloadListener<GameRenderer.ResourceCache>() {
            protected GameRenderer.ResourceCache prepare(ResourceManager p_251213_, ProfilerFiller p_251006_) {
                Map<ResourceLocation, Resource> map = p_251213_.listResources(
                    "shaders",
                    p_251575_ -> {
                        String s = p_251575_.getPath();
                        return s.endsWith(".json")
                            || s.endsWith(Program.Type.FRAGMENT.getExtension())
                            || s.endsWith(Program.Type.VERTEX.getExtension())
                            || s.endsWith(".glsl");
                    }
                );
                Map<ResourceLocation, Resource> map1 = new HashMap<>();
                map.forEach((p_250354_, p_250712_) -> {
                    try (InputStream inputstream = p_250712_.open()) {
                        byte[] abyte = inputstream.readAllBytes();
                        map1.put(p_250354_, new Resource(p_250712_.source(), () -> new ByteArrayInputStream(abyte)));
                    } catch (Exception exception) {
                        GameRenderer.LOGGER.warn("Failed to read resource {}", p_250354_, exception);
                    }
                });
                return new GameRenderer.ResourceCache(p_251213_, map1);
            }

            protected void apply(GameRenderer.ResourceCache p_251168_, ResourceManager p_248902_, ProfilerFiller p_251909_) {
                GameRenderer.this.reloadShaders(p_251168_);
                if (GameRenderer.this.postEffect != null) {
                    GameRenderer.this.postEffect.close();
                }

                GameRenderer.this.postEffect = null;
                GameRenderer.this.checkEntityPostEffect(GameRenderer.this.minecraft.getCameraEntity());
            }

            @Override
            public String getName() {
                return "Shader Loader";
            }
        };
    }

    public void preloadUiShader(ResourceProvider resourceProvider) {
        if (this.blitShader != null) {
            throw new RuntimeException("Blit shader already preloaded");
        } else {
            try {
                this.blitShader = new ShaderInstance(resourceProvider, "blit_screen", DefaultVertexFormat.BLIT_SCREEN);
            } catch (IOException ioexception) {
                throw new RuntimeException("could not preload blit shader", ioexception);
            }

            rendertypeGuiShader = this.preloadShader(resourceProvider, "rendertype_gui", DefaultVertexFormat.POSITION_COLOR);
            rendertypeGuiOverlayShader = this.preloadShader(resourceProvider, "rendertype_gui_overlay", DefaultVertexFormat.POSITION_COLOR);
            positionShader = this.preloadShader(resourceProvider, "position", DefaultVertexFormat.POSITION);
            positionColorShader = this.preloadShader(resourceProvider, "position_color", DefaultVertexFormat.POSITION_COLOR);
            positionTexShader = this.preloadShader(resourceProvider, "position_tex", DefaultVertexFormat.POSITION_TEX);
            positionTexColorShader = this.preloadShader(resourceProvider, "position_tex_color", DefaultVertexFormat.POSITION_TEX_COLOR);
            rendertypeTextShader = this.preloadShader(resourceProvider, "rendertype_text", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
        }
    }

    private ShaderInstance preloadShader(ResourceProvider resourceProvider, String name, VertexFormat format) {
        try {
            ShaderInstance shaderinstance = new ShaderInstance(resourceProvider, name, format);
            this.shaders.put(name, shaderinstance);
            return shaderinstance;
        } catch (Exception exception) {
            throw new IllegalStateException("could not preload shader " + name, exception);
        }
    }

    void reloadShaders(ResourceProvider resourceProvider) {
        RenderSystem.assertOnRenderThread();
        List<Program> list = Lists.newArrayList();
        list.addAll(Program.Type.FRAGMENT.getPrograms().values());
        list.addAll(Program.Type.VERTEX.getPrograms().values());
        list.forEach(Program::close);
        List<Pair<ShaderInstance, Consumer<ShaderInstance>>> list1 = Lists.newArrayListWithCapacity(this.shaders.size());

        try {
            list1.add(Pair.of(new ShaderInstance(resourceProvider, "particle", DefaultVertexFormat.PARTICLE), p_172714_ -> particleShader = p_172714_));
            list1.add(Pair.of(new ShaderInstance(resourceProvider, "position", DefaultVertexFormat.POSITION), p_172711_ -> positionShader = p_172711_));
            list1.add(
                Pair.of(new ShaderInstance(resourceProvider, "position_color", DefaultVertexFormat.POSITION_COLOR), p_172708_ -> positionColorShader = p_172708_)
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "position_color_lightmap", DefaultVertexFormat.POSITION_COLOR_LIGHTMAP),
                    p_172705_ -> positionColorLightmapShader = p_172705_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "position_color_tex_lightmap", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),
                    p_172699_ -> positionColorTexLightmapShader = p_172699_
                )
            );
            list1.add(Pair.of(new ShaderInstance(resourceProvider, "position_tex", DefaultVertexFormat.POSITION_TEX), p_172696_ -> positionTexShader = p_172696_));
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "position_tex_color", DefaultVertexFormat.POSITION_TEX_COLOR),
                    p_172693_ -> positionTexColorShader = p_172693_
                )
            );
            list1.add(Pair.of(new ShaderInstance(resourceProvider, "rendertype_solid", DefaultVertexFormat.BLOCK), p_172684_ -> rendertypeSolidShader = p_172684_));
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_cutout_mipped", DefaultVertexFormat.BLOCK), p_172681_ -> rendertypeCutoutMippedShader = p_172681_
                )
            );
            list1.add(Pair.of(new ShaderInstance(resourceProvider, "rendertype_cutout", DefaultVertexFormat.BLOCK), p_172678_ -> rendertypeCutoutShader = p_172678_));
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_translucent", DefaultVertexFormat.BLOCK), p_172675_ -> rendertypeTranslucentShader = p_172675_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_translucent_moving_block", DefaultVertexFormat.BLOCK),
                    p_172672_ -> rendertypeTranslucentMovingBlockShader = p_172672_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_armor_cutout_no_cull", DefaultVertexFormat.NEW_ENTITY),
                    p_172666_ -> rendertypeArmorCutoutNoCullShader = p_172666_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_entity_solid", DefaultVertexFormat.NEW_ENTITY),
                    p_172663_ -> rendertypeEntitySolidShader = p_172663_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_entity_cutout", DefaultVertexFormat.NEW_ENTITY),
                    p_172660_ -> rendertypeEntityCutoutShader = p_172660_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_entity_cutout_no_cull", DefaultVertexFormat.NEW_ENTITY),
                    p_172657_ -> rendertypeEntityCutoutNoCullShader = p_172657_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_entity_cutout_no_cull_z_offset", DefaultVertexFormat.NEW_ENTITY),
                    p_172654_ -> rendertypeEntityCutoutNoCullZOffsetShader = p_172654_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_item_entity_translucent_cull", DefaultVertexFormat.NEW_ENTITY),
                    p_172651_ -> rendertypeItemEntityTranslucentCullShader = p_172651_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_entity_translucent_cull", DefaultVertexFormat.NEW_ENTITY),
                    p_172648_ -> rendertypeEntityTranslucentCullShader = p_172648_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_entity_translucent", DefaultVertexFormat.NEW_ENTITY),
                    p_172645_ -> rendertypeEntityTranslucentShader = p_172645_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_entity_translucent_emissive", DefaultVertexFormat.NEW_ENTITY),
                    p_172642_ -> rendertypeEntityTranslucentEmissiveShader = p_172642_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_entity_smooth_cutout", DefaultVertexFormat.NEW_ENTITY),
                    p_172639_ -> rendertypeEntitySmoothCutoutShader = p_172639_
                )
            );
            list1.add(
                Pair.of(new ShaderInstance(resourceProvider, "rendertype_beacon_beam", DefaultVertexFormat.BLOCK), p_172840_ -> rendertypeBeaconBeamShader = p_172840_)
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_entity_decal", DefaultVertexFormat.NEW_ENTITY),
                    p_172837_ -> rendertypeEntityDecalShader = p_172837_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_entity_no_outline", DefaultVertexFormat.NEW_ENTITY),
                    p_172834_ -> rendertypeEntityNoOutlineShader = p_172834_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_entity_shadow", DefaultVertexFormat.NEW_ENTITY),
                    p_172831_ -> rendertypeEntityShadowShader = p_172831_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_entity_alpha", DefaultVertexFormat.NEW_ENTITY),
                    p_172828_ -> rendertypeEntityAlphaShader = p_172828_
                )
            );
            list1.add(Pair.of(new ShaderInstance(resourceProvider, "rendertype_eyes", DefaultVertexFormat.NEW_ENTITY), p_172825_ -> rendertypeEyesShader = p_172825_));
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_energy_swirl", DefaultVertexFormat.NEW_ENTITY),
                    p_172822_ -> rendertypeEnergySwirlShader = p_172822_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_leash", DefaultVertexFormat.POSITION_COLOR_LIGHTMAP),
                    p_172819_ -> rendertypeLeashShader = p_172819_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_water_mask", DefaultVertexFormat.POSITION), p_172816_ -> rendertypeWaterMaskShader = p_172816_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_outline", DefaultVertexFormat.POSITION_TEX_COLOR),
                    p_172813_ -> rendertypeOutlineShader = p_172813_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_armor_entity_glint", DefaultVertexFormat.POSITION_TEX),
                    p_172807_ -> rendertypeArmorEntityGlintShader = p_172807_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_glint_translucent", DefaultVertexFormat.POSITION_TEX),
                    p_172805_ -> rendertypeGlintTranslucentShader = p_172805_
                )
            );
            list1.add(
                Pair.of(new ShaderInstance(resourceProvider, "rendertype_glint", DefaultVertexFormat.POSITION_TEX), p_172803_ -> rendertypeGlintShader = p_172803_)
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_entity_glint", DefaultVertexFormat.POSITION_TEX),
                    p_172799_ -> rendertypeEntityGlintShader = p_172799_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_entity_glint_direct", DefaultVertexFormat.POSITION_TEX),
                    p_172796_ -> rendertypeEntityGlintDirectShader = p_172796_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_text", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),
                    p_172794_ -> rendertypeTextShader = p_172794_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_text_background", DefaultVertexFormat.POSITION_COLOR_LIGHTMAP),
                    p_269657_ -> rendertypeTextBackgroundShader = p_269657_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_text_intensity", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),
                    p_172792_ -> rendertypeTextIntensityShader = p_172792_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_text_see_through", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),
                    p_172789_ -> rendertypeTextSeeThroughShader = p_172789_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_text_background_see_through", DefaultVertexFormat.POSITION_COLOR_LIGHTMAP),
                    p_269656_ -> rendertypeTextBackgroundSeeThroughShader = p_269656_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_text_intensity_see_through", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),
                    p_172787_ -> rendertypeTextIntensitySeeThroughShader = p_172787_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_lightning", DefaultVertexFormat.POSITION_COLOR),
                    p_172785_ -> rendertypeLightningShader = p_172785_
                )
            );
            list1.add(
                Pair.of(new ShaderInstance(resourceProvider, "rendertype_tripwire", DefaultVertexFormat.BLOCK), p_172782_ -> rendertypeTripwireShader = p_172782_)
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_end_portal", DefaultVertexFormat.POSITION), p_172778_ -> rendertypeEndPortalShader = p_172778_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_end_gateway", DefaultVertexFormat.POSITION), p_172774_ -> rendertypeEndGatewayShader = p_172774_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_clouds", DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL),
                    p_323061_ -> rendertypeCloudsShader = p_323061_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_lines", DefaultVertexFormat.POSITION_COLOR_NORMAL),
                    p_172733_ -> rendertypeLinesShader = p_172733_
                )
            );
            list1.add(
                Pair.of(new ShaderInstance(resourceProvider, "rendertype_crumbling", DefaultVertexFormat.BLOCK), p_234230_ -> rendertypeCrumblingShader = p_234230_)
            );
            list1.add(
                Pair.of(new ShaderInstance(resourceProvider, "rendertype_gui", DefaultVertexFormat.POSITION_COLOR), p_286148_ -> rendertypeGuiShader = p_286148_)
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_gui_overlay", DefaultVertexFormat.POSITION_COLOR),
                    p_286146_ -> rendertypeGuiOverlayShader = p_286146_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_gui_text_highlight", DefaultVertexFormat.POSITION_COLOR),
                    p_286145_ -> rendertypeGuiTextHighlightShader = p_286145_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_gui_ghost_recipe_overlay", DefaultVertexFormat.POSITION_COLOR),
                    p_286147_ -> rendertypeGuiGhostRecipeOverlayShader = p_286147_
                )
            );
            list1.add(
                Pair.of(
                    new ShaderInstance(resourceProvider, "rendertype_breeze_wind", DefaultVertexFormat.NEW_ENTITY),
                    p_311462_ -> rendertypeBreezeWindShader = p_311462_
                )
            );
            this.loadBlurEffect(resourceProvider);
            net.neoforged.fml.ModLoader.postEvent(new net.neoforged.neoforge.client.event.RegisterShadersEvent(resourceProvider, list1));
        } catch (IOException ioexception) {
            list1.forEach(p_172729_ -> p_172729_.getFirst().close());
            throw new RuntimeException("could not reload shaders", ioexception);
        }

        this.shutdownShaders();
        list1.forEach(p_234225_ -> {
            ShaderInstance shaderinstance = p_234225_.getFirst();
            this.shaders.put(shaderinstance.getName(), shaderinstance);
            p_234225_.getSecond().accept(shaderinstance);
        });
    }

    private void shutdownShaders() {
        RenderSystem.assertOnRenderThread();
        this.shaders.values().forEach(ShaderInstance::close);
        this.shaders.clear();
    }

    @Nullable
    public ShaderInstance getShader(@Nullable String name) {
        return name == null ? null : this.shaders.get(name);
    }

    public void tick() {
        this.tickFov();
        this.lightTexture.tick();
        if (this.minecraft.getCameraEntity() == null) {
            this.minecraft.setCameraEntity(this.minecraft.player);
        }

        this.mainCamera.tick();
        this.itemInHandRenderer.tick();
        this.confusionAnimationTick++;
        if (this.minecraft.level.tickRateManager().runsNormally()) {
            this.minecraft.levelRenderer.tickRain(this.mainCamera);
            this.darkenWorldAmountO = this.darkenWorldAmount;
            if (this.minecraft.gui.getBossOverlay().shouldDarkenScreen()) {
                this.darkenWorldAmount += 0.05F;
                if (this.darkenWorldAmount > 1.0F) {
                    this.darkenWorldAmount = 1.0F;
                }
            } else if (this.darkenWorldAmount > 0.0F) {
                this.darkenWorldAmount -= 0.0125F;
            }

            if (this.itemActivationTicks > 0) {
                this.itemActivationTicks--;
                if (this.itemActivationTicks == 0) {
                    this.itemActivationItem = null;
                }
            }
        }
    }

    @Nullable
    public PostChain currentEffect() {
        return this.postEffect;
    }

    public void resize(int width, int height) {
        if (this.postEffect != null) {
            this.postEffect.resize(width, height);
        }

        if (this.blurEffect != null) {
            this.blurEffect.resize(width, height);
        }

        this.minecraft.levelRenderer.resize(width, height);
    }

    /**
     * Gets the block or object that is being moused over.
     */
    public void pick(float partialTicks) {
        Entity entity = this.minecraft.getCameraEntity();
        if (entity != null) {
            if (this.minecraft.level != null && this.minecraft.player != null) {
                this.minecraft.getProfiler().push("pick");
                double d0 = this.minecraft.player.blockInteractionRange();
                double d1 = this.minecraft.player.entityInteractionRange();
                HitResult hitresult = this.pick(entity, d0, d1, partialTicks);
                this.minecraft.hitResult = hitresult;
                this.minecraft.crosshairPickEntity = hitresult instanceof EntityHitResult entityhitresult ? entityhitresult.getEntity() : null;
                this.minecraft.getProfiler().pop();
            }
        }
    }

    private HitResult pick(Entity entity, double blockInteractionRange, double entityInteractionRange, float partialTick) {
        double d0 = Math.max(blockInteractionRange, entityInteractionRange);
        double d1 = Mth.square(d0);
        Vec3 vec3 = entity.getEyePosition(partialTick);
        HitResult hitresult = entity.pick(d0, partialTick, false);
        double d2 = hitresult.getLocation().distanceToSqr(vec3);
        if (hitresult.getType() != HitResult.Type.MISS) {
            d1 = d2;
            d0 = Math.sqrt(d2);
        }

        Vec3 vec31 = entity.getViewVector(partialTick);
        Vec3 vec32 = vec3.add(vec31.x * d0, vec31.y * d0, vec31.z * d0);
        float f = 1.0F;
        AABB aabb = entity.getBoundingBox().expandTowards(vec31.scale(d0)).inflate(1.0, 1.0, 1.0);
        EntityHitResult entityhitresult = ProjectileUtil.getEntityHitResult(
            entity, vec3, vec32, aabb, p_234237_ -> !p_234237_.isSpectator() && p_234237_.isPickable(), d1
        );
        return entityhitresult != null && entityhitresult.getLocation().distanceToSqr(vec3) < d2
            ? filterHitResult(entityhitresult, vec3, entityInteractionRange)
            : filterHitResult(hitresult, vec3, blockInteractionRange);
    }

    private static HitResult filterHitResult(HitResult hitResult, Vec3 pos, double blockInteractionRange) {
        Vec3 vec3 = hitResult.getLocation();
        if (!vec3.closerThan(pos, blockInteractionRange)) {
            Vec3 vec31 = hitResult.getLocation();
            Direction direction = Direction.getNearest(vec31.x - pos.x, vec31.y - pos.y, vec31.z - pos.z);
            return BlockHitResult.miss(vec31, direction, BlockPos.containing(vec31));
        } else {
            return hitResult;
        }
    }

    private void tickFov() {
        float f = 1.0F;
        if (this.minecraft.getCameraEntity() instanceof AbstractClientPlayer abstractclientplayer) {
            f = abstractclientplayer.getFieldOfViewModifier();
        }

        this.oldFov = this.fov;
        this.fov = this.fov + (f - this.fov) * 0.5F;
        if (this.fov > 1.5F) {
            this.fov = 1.5F;
        }

        if (this.fov < 0.1F) {
            this.fov = 0.1F;
        }
    }

    private double getFov(Camera activeRenderInfo, float partialTicks, boolean useFOVSetting) {
        if (this.panoramicMode) {
            return 90.0;
        } else {
            double d0 = 70.0;
            if (useFOVSetting) {
                d0 = (double)this.minecraft.options.fov().get().intValue();
                d0 *= (double)Mth.lerp(partialTicks, this.oldFov, this.fov);
            }

            if (activeRenderInfo.getEntity() instanceof LivingEntity && ((LivingEntity)activeRenderInfo.getEntity()).isDeadOrDying()) {
                float f = Math.min((float)((LivingEntity)activeRenderInfo.getEntity()).deathTime + partialTicks, 20.0F);
                d0 /= (double)((1.0F - 500.0F / (f + 500.0F)) * 2.0F + 1.0F);
            }

            FogType fogtype = activeRenderInfo.getFluidInCamera();
            if (fogtype == FogType.LAVA || fogtype == FogType.WATER) {
                d0 *= Mth.lerp(this.minecraft.options.fovEffectScale().get(), 1.0, 0.85714287F);
            }

            return net.neoforged.neoforge.client.ClientHooks.getFieldOfView(this, activeRenderInfo, partialTicks, d0, useFOVSetting);
        }
    }

    private void bobHurt(PoseStack poseStack, float partialTicks) {
        if (this.minecraft.getCameraEntity() instanceof LivingEntity livingentity) {
            float f2 = (float)livingentity.hurtTime - partialTicks;
            if (livingentity.isDeadOrDying()) {
                float f = Math.min((float)livingentity.deathTime + partialTicks, 20.0F);
                poseStack.mulPose(Axis.ZP.rotationDegrees(40.0F - 8000.0F / (f + 200.0F)));
            }

            if (f2 < 0.0F) {
                return;
            }

            // Neo: Prevent screen shake if the damage type is marked as "forge:no_flinch"
            var lastSrc = livingentity.getLastDamageSource();
            if (lastSrc != null && lastSrc.is(net.neoforged.neoforge.common.Tags.DamageTypes.NO_FLINCH)) return;

            f2 /= (float)livingentity.hurtDuration;
            f2 = Mth.sin(f2 * f2 * f2 * f2 * (float) Math.PI);
            float f3 = livingentity.getHurtDir();
            poseStack.mulPose(Axis.YP.rotationDegrees(-f3));
            float f1 = (float)((double)(-f2) * 14.0 * this.minecraft.options.damageTiltStrength().get());
            poseStack.mulPose(Axis.ZP.rotationDegrees(f1));
            poseStack.mulPose(Axis.YP.rotationDegrees(f3));
        }
    }

    private void bobView(PoseStack poseStack, float partialTicks) {
        if (this.minecraft.getCameraEntity() instanceof Player) {
            Player player = (Player)this.minecraft.getCameraEntity();
            float f = player.walkDist - player.walkDistO;
            float f1 = -(player.walkDist + f * partialTicks);
            float f2 = Mth.lerp(partialTicks, player.oBob, player.bob);
            poseStack.translate(Mth.sin(f1 * (float) Math.PI) * f2 * 0.5F, -Math.abs(Mth.cos(f1 * (float) Math.PI) * f2), 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(f1 * (float) Math.PI) * f2 * 3.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(Math.abs(Mth.cos(f1 * (float) Math.PI - 0.2F) * f2) * 5.0F));
        }
    }

    public void renderZoomed(float zoom, float zoomX, float zoomY) {
        this.zoom = zoom;
        this.zoomX = zoomX;
        this.zoomY = zoomY;
        this.setRenderBlockOutline(false);
        this.setRenderHand(false);
        this.renderLevel(DeltaTracker.ZERO);
        this.zoom = 1.0F;
    }

    private void renderItemInHand(Camera camera, float partialTick, Matrix4f projectionMatrix) {
        if (!this.panoramicMode) {
            this.resetProjectionMatrix(this.getProjectionMatrix(this.getFov(camera, partialTick, false)));
            PoseStack posestack = new PoseStack();
            posestack.pushPose();
            posestack.mulPose(projectionMatrix.invert(new Matrix4f()));
            Matrix4fStack matrix4fstack = RenderSystem.getModelViewStack();
            matrix4fstack.pushMatrix().mul(projectionMatrix);
            RenderSystem.applyModelViewMatrix();
            this.bobHurt(posestack, partialTick);
            if (this.minecraft.options.bobView().get()) {
                this.bobView(posestack, partialTick);
            }

            boolean flag = this.minecraft.getCameraEntity() instanceof LivingEntity && ((LivingEntity)this.minecraft.getCameraEntity()).isSleeping();
            if (this.minecraft.options.getCameraType().isFirstPerson()
                && !flag
                && !this.minecraft.options.hideGui
                && this.minecraft.gameMode.getPlayerMode() != GameType.SPECTATOR) {
                this.lightTexture.turnOnLightLayer();
                this.itemInHandRenderer
                    .renderHandsWithItems(
                        partialTick,
                        posestack,
                        this.renderBuffers.bufferSource(),
                        this.minecraft.player,
                        this.minecraft.getEntityRenderDispatcher().getPackedLightCoords(this.minecraft.player, partialTick)
                    );
                this.lightTexture.turnOffLightLayer();
            }

            matrix4fstack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            posestack.popPose();
            if (this.minecraft.options.getCameraType().isFirstPerson() && !flag) {
                ScreenEffectRenderer.renderScreenEffect(this.minecraft, posestack);
            }
        }
    }

    public void resetProjectionMatrix(Matrix4f matrix) {
        RenderSystem.setProjectionMatrix(matrix, VertexSorting.DISTANCE_TO_ORIGIN);
    }

    public Matrix4f getProjectionMatrix(double fov) {
        Matrix4f matrix4f = new Matrix4f();
        if (this.zoom != 1.0F) {
            matrix4f.translate(this.zoomX, -this.zoomY, 0.0F);
            matrix4f.scale(this.zoom, this.zoom, 1.0F);
        }

        return matrix4f.perspective(
            (float)(fov * (float) (Math.PI / 180.0)),
            (float)this.minecraft.getWindow().getWidth() / (float)this.minecraft.getWindow().getHeight(),
            0.05F,
            this.getDepthFar()
        );
    }

    public float getDepthFar() {
        return this.renderDistance * 4.0F;
    }

    public static float getNightVisionScale(LivingEntity livingEntity, float nanoTime) {
        MobEffectInstance mobeffectinstance = livingEntity.getEffect(MobEffects.NIGHT_VISION);
        return !mobeffectinstance.endsWithin(200) ? 1.0F : 0.7F + Mth.sin(((float)mobeffectinstance.getDuration() - nanoTime) * (float) Math.PI * 0.2F) * 0.3F;
    }

    public void render(DeltaTracker deltaTracker, boolean renderLevel) {
        if (!this.minecraft.isWindowActive()
            && this.minecraft.options.pauseOnLostFocus
            && (!this.minecraft.options.touchscreen().get() || !this.minecraft.mouseHandler.isRightPressed())) {
            if (Util.getMillis() - this.lastActiveTime > 500L) {
                this.minecraft.pauseGame(false);
            }
        } else {
            this.lastActiveTime = Util.getMillis();
        }

        if (!this.minecraft.noRender) {
            boolean flag = this.minecraft.isGameLoadFinished();
            int i = (int)(
                this.minecraft.mouseHandler.xpos()
                    * (double)this.minecraft.getWindow().getGuiScaledWidth()
                    / (double)this.minecraft.getWindow().getScreenWidth()
            );
            int j = (int)(
                this.minecraft.mouseHandler.ypos()
                    * (double)this.minecraft.getWindow().getGuiScaledHeight()
                    / (double)this.minecraft.getWindow().getScreenHeight()
            );
            RenderSystem.viewport(0, 0, this.minecraft.getWindow().getWidth(), this.minecraft.getWindow().getHeight());
            if (flag && renderLevel && this.minecraft.level != null) {
                this.minecraft.getProfiler().push("level");
                this.renderLevel(deltaTracker);
                this.tryTakeScreenshotIfNeeded();
                this.minecraft.levelRenderer.doEntityOutline();
                if (this.postEffect != null && this.effectActive) {
                    RenderSystem.disableBlend();
                    RenderSystem.disableDepthTest();
                    RenderSystem.resetTextureMatrix();
                    this.postEffect.process(deltaTracker.getGameTimeDeltaTicks());
                }

                this.minecraft.getMainRenderTarget().bindWrite(true);
            }

            Window window = this.minecraft.getWindow();
            RenderSystem.clear(256, Minecraft.ON_OSX);
            Matrix4f matrix4f = new Matrix4f()
                .setOrtho(
                    0.0F,
                    (float)((double)window.getWidth() / window.getGuiScale()),
                    (float)((double)window.getHeight() / window.getGuiScale()),
                    0.0F,
                    1000.0F,
                    net.neoforged.neoforge.client.ClientHooks.getGuiFarPlane()
                );
            RenderSystem.setProjectionMatrix(matrix4f, VertexSorting.ORTHOGRAPHIC_Z);
            Matrix4fStack matrix4fstack = RenderSystem.getModelViewStack();
            matrix4fstack.pushMatrix();
            matrix4fstack.translation(0.0F, 0.0F, 10000F - net.neoforged.neoforge.client.ClientHooks.getGuiFarPlane());
            RenderSystem.applyModelViewMatrix();
            Lighting.setupFor3DItems();
            GuiGraphics guigraphics = new GuiGraphics(this.minecraft, this.renderBuffers.bufferSource());
            if (flag && renderLevel && this.minecraft.level != null) {
                this.minecraft.getProfiler().popPush("gui");
                if (this.minecraft.player != null) {
                    float f = Mth.lerp(
                        deltaTracker.getGameTimeDeltaPartialTick(false),
                        this.minecraft.player.oSpinningEffectIntensity,
                        this.minecraft.player.spinningEffectIntensity
                    );
                    float f1 = this.minecraft.options.screenEffectScale().get().floatValue();
                    if (f > 0.0F && this.minecraft.player.hasEffect(MobEffects.CONFUSION) && f1 < 1.0F) {
                        this.renderConfusionOverlay(guigraphics, f * (1.0F - f1));
                    }
                }

                if (!this.minecraft.options.hideGui) {
                    this.renderItemActivationAnimation(guigraphics, deltaTracker.getGameTimeDeltaPartialTick(false));
                }

                this.minecraft.gui.render(guigraphics, deltaTracker);
                RenderSystem.clear(256, Minecraft.ON_OSX);
                this.minecraft.getProfiler().pop();
            }

            if (this.minecraft.getOverlay() != null) {
                try {
                    // Neo: Fix https://bugs.mojang.com/browse/MC-273464
                    this.minecraft.getOverlay().render(guigraphics, i, j, deltaTracker.getGameTimeDeltaTicks());
                } catch (Throwable throwable2) {
                    CrashReport crashreport = CrashReport.forThrowable(throwable2, "Rendering overlay");
                    CrashReportCategory crashreportcategory = crashreport.addCategory("Overlay render details");
                    crashreportcategory.setDetail("Overlay name", () -> this.minecraft.getOverlay().getClass().getCanonicalName());
                    throw new ReportedException(crashreport);
                }
            } else if (flag && this.minecraft.screen != null) {
                try {
                    // Neo: Wrap Screen#render to allow for GUI Layers and ScreenEvent.Render.[Pre/Post]
                    // Also fixes https://bugs.mojang.com/browse/MC-273464
                    net.neoforged.neoforge.client.ClientHooks.drawScreen(this.minecraft.screen, guigraphics, i, j, deltaTracker.getGameTimeDeltaTicks());
                } catch (Throwable throwable1) {
                    CrashReport crashreport1 = CrashReport.forThrowable(throwable1, "Rendering screen");
                    CrashReportCategory crashreportcategory1 = crashreport1.addCategory("Screen render details");
                    crashreportcategory1.setDetail("Screen name", () -> this.minecraft.screen.getClass().getCanonicalName());
                    crashreportcategory1.setDetail(
                        "Mouse location",
                        () -> String.format(
                                Locale.ROOT,
                                "Scaled: (%d, %d). Absolute: (%f, %f)",
                                i,
                                j,
                                this.minecraft.mouseHandler.xpos(),
                                this.minecraft.mouseHandler.ypos()
                            )
                    );
                    crashreportcategory1.setDetail(
                        "Screen size",
                        () -> String.format(
                                Locale.ROOT,
                                "Scaled: (%d, %d). Absolute: (%d, %d). Scale factor of %f",
                                this.minecraft.getWindow().getGuiScaledWidth(),
                                this.minecraft.getWindow().getGuiScaledHeight(),
                                this.minecraft.getWindow().getWidth(),
                                this.minecraft.getWindow().getHeight(),
                                this.minecraft.getWindow().getGuiScale()
                            )
                    );
                    throw new ReportedException(crashreport1);
                }

                try {
                    if (this.minecraft.screen != null) {
                        this.minecraft.screen.handleDelayedNarration();
                    }
                } catch (Throwable throwable) {
                    CrashReport crashreport2 = CrashReport.forThrowable(throwable, "Narrating screen");
                    CrashReportCategory crashreportcategory2 = crashreport2.addCategory("Screen details");
                    crashreportcategory2.setDetail("Screen name", () -> this.minecraft.screen.getClass().getCanonicalName());
                    throw new ReportedException(crashreport2);
                }
            }

            if (flag && renderLevel && this.minecraft.level != null) {
                this.minecraft.gui.renderSavingIndicator(guigraphics, deltaTracker);
            }

            if (flag) {
                this.minecraft.getProfiler().push("toasts");
                this.minecraft.getToasts().render(guigraphics);
                this.minecraft.getProfiler().pop();
            }

            guigraphics.flush();
            matrix4fstack.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private void tryTakeScreenshotIfNeeded() {
        if (!this.hasWorldScreenshot && this.minecraft.isLocalServer()) {
            long i = Util.getMillis();
            if (i - this.lastScreenshotAttempt >= 1000L) {
                this.lastScreenshotAttempt = i;
                IntegratedServer integratedserver = this.minecraft.getSingleplayerServer();
                if (integratedserver != null && !integratedserver.isStopped()) {
                    integratedserver.getWorldScreenshotFile().ifPresent(p_234239_ -> {
                        if (Files.isRegularFile(p_234239_)) {
                            this.hasWorldScreenshot = true;
                        } else {
                            this.takeAutoScreenshot(p_234239_);
                        }
                    });
                }
            }
        }
    }

    private void takeAutoScreenshot(Path path) {
        if (this.minecraft.levelRenderer.countRenderedSections() > 10 && this.minecraft.levelRenderer.hasRenderedAllSections()) {
            NativeImage nativeimage = Screenshot.takeScreenshot(this.minecraft.getMainRenderTarget());
            Util.ioPool().execute(() -> {
                int i = nativeimage.getWidth();
                int j = nativeimage.getHeight();
                int k = 0;
                int l = 0;
                if (i > j) {
                    k = (i - j) / 2;
                    i = j;
                } else {
                    l = (j - i) / 2;
                    j = i;
                }

                try (NativeImage nativeimage1 = new NativeImage(64, 64, false)) {
                    nativeimage.resizeSubRectTo(k, l, i, j, nativeimage1);
                    nativeimage1.writeToFile(path);
                } catch (IOException ioexception) {
                    LOGGER.warn("Couldn't save auto screenshot", (Throwable)ioexception);
                } finally {
                    nativeimage.close();
                }
            });
        }
    }

    private boolean shouldRenderBlockOutline() {
        if (!this.renderBlockOutline) {
            return false;
        } else {
            Entity entity = this.minecraft.getCameraEntity();
            boolean flag = entity instanceof Player && !this.minecraft.options.hideGui;
            if (flag && !((Player)entity).getAbilities().mayBuild) {
                ItemStack itemstack = ((LivingEntity)entity).getMainHandItem();
                HitResult hitresult = this.minecraft.hitResult;
                if (hitresult != null && hitresult.getType() == HitResult.Type.BLOCK) {
                    BlockPos blockpos = ((BlockHitResult)hitresult).getBlockPos();
                    BlockState blockstate = this.minecraft.level.getBlockState(blockpos);
                    if (this.minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) {
                        flag = blockstate.getMenuProvider(this.minecraft.level, blockpos) != null;
                    } else {
                        BlockInWorld blockinworld = new BlockInWorld(this.minecraft.level, blockpos, false);
                        Registry<Block> registry = this.minecraft.level.registryAccess().registryOrThrow(Registries.BLOCK);
                        flag = !itemstack.isEmpty()
                            && (itemstack.canBreakBlockInAdventureMode(blockinworld) || itemstack.canPlaceOnBlockInAdventureMode(blockinworld));
                    }
                }
            }

            return flag;
        }
    }

    public void renderLevel(DeltaTracker deltaTracker) {
        float f = deltaTracker.getGameTimeDeltaPartialTick(true);
        this.lightTexture.updateLightTexture(f);
        if (this.minecraft.getCameraEntity() == null) {
            this.minecraft.setCameraEntity(this.minecraft.player);
        }

        this.pick(f);
        this.minecraft.getProfiler().push("center");
        boolean flag = this.shouldRenderBlockOutline();
        this.minecraft.getProfiler().popPush("camera");
        Camera camera = this.mainCamera;
        Entity entity = (Entity)(this.minecraft.getCameraEntity() == null ? this.minecraft.player : this.minecraft.getCameraEntity());
        float f1 = this.minecraft.level.tickRateManager().isEntityFrozen(entity) ? 1.0F : f;
        camera.setup(
            this.minecraft.level, entity, !this.minecraft.options.getCameraType().isFirstPerson(), this.minecraft.options.getCameraType().isMirrored(), f1
        );
        this.renderDistance = (float)(this.minecraft.options.getEffectiveRenderDistance() * 16);
        double d0 = this.getFov(camera, f, true);
        Matrix4f matrix4f = this.getProjectionMatrix(d0);
        PoseStack posestack = new PoseStack();
        this.bobHurt(posestack, camera.getPartialTickTime());
        if (this.minecraft.options.bobView().get()) {
            this.bobView(posestack, camera.getPartialTickTime());
        }

        matrix4f.mul(posestack.last().pose());
        float f2 = this.minecraft.options.screenEffectScale().get().floatValue();
        float f3 = Mth.lerp(f, this.minecraft.player.oSpinningEffectIntensity, this.minecraft.player.spinningEffectIntensity) * f2 * f2;
        if (f3 > 0.0F) {
            int i = this.minecraft.player.hasEffect(MobEffects.CONFUSION) ? 7 : 20;
            float f4 = 5.0F / (f3 * f3 + 5.0F) - f3 * 0.04F;
            f4 *= f4;
            Vector3f vector3f = new Vector3f(0.0F, Mth.SQRT_OF_TWO / 2.0F, Mth.SQRT_OF_TWO / 2.0F);
            float f5 = ((float)this.confusionAnimationTick + f) * (float)i * (float) (Math.PI / 180.0);
            matrix4f.rotate(f5, vector3f);
            matrix4f.scale(1.0F / f4, 1.0F, 1.0F);
            matrix4f.rotate(-f5, vector3f);
        }

        this.resetProjectionMatrix(matrix4f);
        Quaternionf quaternionf = camera.rotation().conjugate(new Quaternionf());
        Matrix4f matrix4f1 = new Matrix4f().rotation(quaternionf);
        this.minecraft
            .levelRenderer
            .prepareCullFrustum(camera.getPosition(), matrix4f1, this.getProjectionMatrix(Math.max(d0, (double)this.minecraft.options.fov().get().intValue())));
        this.minecraft.levelRenderer.renderLevel(deltaTracker, flag, camera, this, this.lightTexture, matrix4f1, matrix4f);
        this.minecraft.getProfiler().popPush("neoforge_render_last");
        net.neoforged.neoforge.client.ClientHooks.dispatchRenderStage(net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL, this.minecraft.levelRenderer, null, matrix4f1, matrix4f, this.minecraft.levelRenderer.getTicks(), camera, this.minecraft.levelRenderer.getFrustum());
        this.minecraft.getProfiler().popPush("hand");
        if (this.renderHand) {
            RenderSystem.clear(256, Minecraft.ON_OSX);
            this.renderItemInHand(camera, f, matrix4f1);
        }

        this.minecraft.getProfiler().pop();
    }

    public void resetData() {
        this.itemActivationItem = null;
        this.mapRenderer.resetData();
        this.mainCamera.reset();
        this.hasWorldScreenshot = false;
    }

    public MapRenderer getMapRenderer() {
        return this.mapRenderer;
    }

    public void displayItemActivation(ItemStack stack) {
        this.itemActivationItem = stack;
        this.itemActivationTicks = 40;
        this.itemActivationOffX = this.random.nextFloat() * 2.0F - 1.0F;
        this.itemActivationOffY = this.random.nextFloat() * 2.0F - 1.0F;
    }

    private void renderItemActivationAnimation(GuiGraphics guiGraphics, float partialTick) {
        if (this.itemActivationItem != null && this.itemActivationTicks > 0) {
            int i = 40 - this.itemActivationTicks;
            float f = ((float)i + partialTick) / 40.0F;
            float f1 = f * f;
            float f2 = f * f1;
            float f3 = 10.25F * f2 * f1 - 24.95F * f1 * f1 + 25.5F * f2 - 13.8F * f1 + 4.0F * f;
            float f4 = f3 * (float) Math.PI;
            float f5 = this.itemActivationOffX * (float)(guiGraphics.guiWidth() / 4);
            float f6 = this.itemActivationOffY * (float)(guiGraphics.guiHeight() / 4);
            PoseStack posestack = new PoseStack();
            posestack.pushPose();
            posestack.translate(
                (float)(guiGraphics.guiWidth() / 2) + f5 * Mth.abs(Mth.sin(f4 * 2.0F)),
                (float)(guiGraphics.guiHeight() / 2) + f6 * Mth.abs(Mth.sin(f4 * 2.0F)),
                -50.0F
            );
            float f7 = 50.0F + 175.0F * Mth.sin(f4);
            posestack.scale(f7, -f7, f7);
            posestack.mulPose(Axis.YP.rotationDegrees(900.0F * Mth.abs(Mth.sin(f4))));
            posestack.mulPose(Axis.XP.rotationDegrees(6.0F * Mth.cos(f * 8.0F)));
            posestack.mulPose(Axis.ZP.rotationDegrees(6.0F * Mth.cos(f * 8.0F)));
            guiGraphics.drawManaged(
                () -> this.minecraft
                        .getItemRenderer()
                        .renderStatic(
                            this.itemActivationItem,
                            ItemDisplayContext.FIXED,
                            15728880,
                            OverlayTexture.NO_OVERLAY,
                            posestack,
                            guiGraphics.bufferSource(),
                            this.minecraft.level,
                            0
                        )
            );
            posestack.popPose();
        }
    }

    private void renderConfusionOverlay(GuiGraphics guiGraphics, float scalar) {
        int i = guiGraphics.guiWidth();
        int j = guiGraphics.guiHeight();
        guiGraphics.pose().pushPose();
        float f = Mth.lerp(scalar, 2.0F, 1.0F);
        guiGraphics.pose().translate((float)i / 2.0F, (float)j / 2.0F, 0.0F);
        guiGraphics.pose().scale(f, f, f);
        guiGraphics.pose().translate((float)(-i) / 2.0F, (float)(-j) / 2.0F, 0.0F);
        float f1 = 0.2F * scalar;
        float f2 = 0.4F * scalar;
        float f3 = 0.2F * scalar;
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE
        );
        guiGraphics.setColor(f1, f2, f3, 1.0F);
        guiGraphics.blit(NAUSEA_LOCATION, 0, 0, -90, 0.0F, 0.0F, i, j, i, j);
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        guiGraphics.pose().popPose();
    }

    public Minecraft getMinecraft() {
        return this.minecraft;
    }

    public float getDarkenWorldAmount(float partialTicks) {
        return Mth.lerp(partialTicks, this.darkenWorldAmountO, this.darkenWorldAmount);
    }

    public float getRenderDistance() {
        return this.renderDistance;
    }

    public Camera getMainCamera() {
        return this.mainCamera;
    }

    public LightTexture lightTexture() {
        return this.lightTexture;
    }

    public OverlayTexture overlayTexture() {
        return this.overlayTexture;
    }

    @Nullable
    public static ShaderInstance getPositionShader() {
        return positionShader;
    }

    @Nullable
    public static ShaderInstance getPositionColorShader() {
        return positionColorShader;
    }

    @Nullable
    public static ShaderInstance getPositionTexShader() {
        return positionTexShader;
    }

    @Nullable
    public static ShaderInstance getPositionTexColorShader() {
        return positionTexColorShader;
    }

    @Nullable
    public static ShaderInstance getParticleShader() {
        return particleShader;
    }

    @Nullable
    public static ShaderInstance getPositionColorLightmapShader() {
        return positionColorLightmapShader;
    }

    @Nullable
    public static ShaderInstance getPositionColorTexLightmapShader() {
        return positionColorTexLightmapShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeSolidShader() {
        return rendertypeSolidShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeCutoutMippedShader() {
        return rendertypeCutoutMippedShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeCutoutShader() {
        return rendertypeCutoutShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeTranslucentShader() {
        return rendertypeTranslucentShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeTranslucentMovingBlockShader() {
        return rendertypeTranslucentMovingBlockShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeArmorCutoutNoCullShader() {
        return rendertypeArmorCutoutNoCullShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEntitySolidShader() {
        return rendertypeEntitySolidShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEntityCutoutShader() {
        return rendertypeEntityCutoutShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEntityCutoutNoCullShader() {
        return rendertypeEntityCutoutNoCullShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEntityCutoutNoCullZOffsetShader() {
        return rendertypeEntityCutoutNoCullZOffsetShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeItemEntityTranslucentCullShader() {
        return rendertypeItemEntityTranslucentCullShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEntityTranslucentCullShader() {
        return rendertypeEntityTranslucentCullShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEntityTranslucentShader() {
        return rendertypeEntityTranslucentShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEntityTranslucentEmissiveShader() {
        return rendertypeEntityTranslucentEmissiveShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEntitySmoothCutoutShader() {
        return rendertypeEntitySmoothCutoutShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeBeaconBeamShader() {
        return rendertypeBeaconBeamShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEntityDecalShader() {
        return rendertypeEntityDecalShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEntityNoOutlineShader() {
        return rendertypeEntityNoOutlineShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEntityShadowShader() {
        return rendertypeEntityShadowShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEntityAlphaShader() {
        return rendertypeEntityAlphaShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEyesShader() {
        return rendertypeEyesShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEnergySwirlShader() {
        return rendertypeEnergySwirlShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeBreezeWindShader() {
        return rendertypeBreezeWindShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeLeashShader() {
        return rendertypeLeashShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeWaterMaskShader() {
        return rendertypeWaterMaskShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeOutlineShader() {
        return rendertypeOutlineShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeArmorGlintShader() {
        return rendertypeArmorGlintShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeArmorEntityGlintShader() {
        return rendertypeArmorEntityGlintShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeGlintTranslucentShader() {
        return rendertypeGlintTranslucentShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeGlintShader() {
        return rendertypeGlintShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeGlintDirectShader() {
        return rendertypeGlintDirectShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEntityGlintShader() {
        return rendertypeEntityGlintShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEntityGlintDirectShader() {
        return rendertypeEntityGlintDirectShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeTextShader() {
        return rendertypeTextShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeTextBackgroundShader() {
        return rendertypeTextBackgroundShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeTextIntensityShader() {
        return rendertypeTextIntensityShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeTextSeeThroughShader() {
        return rendertypeTextSeeThroughShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeTextBackgroundSeeThroughShader() {
        return rendertypeTextBackgroundSeeThroughShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeTextIntensitySeeThroughShader() {
        return rendertypeTextIntensitySeeThroughShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeLightningShader() {
        return rendertypeLightningShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeTripwireShader() {
        return rendertypeTripwireShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEndPortalShader() {
        return rendertypeEndPortalShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeEndGatewayShader() {
        return rendertypeEndGatewayShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeCloudsShader() {
        return rendertypeCloudsShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeLinesShader() {
        return rendertypeLinesShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeCrumblingShader() {
        return rendertypeCrumblingShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeGuiShader() {
        return rendertypeGuiShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeGuiOverlayShader() {
        return rendertypeGuiOverlayShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeGuiTextHighlightShader() {
        return rendertypeGuiTextHighlightShader;
    }

    @Nullable
    public static ShaderInstance getRendertypeGuiGhostRecipeOverlayShader() {
        return rendertypeGuiGhostRecipeOverlayShader;
    }

    @OnlyIn(Dist.CLIENT)
    public static record ResourceCache(ResourceProvider original, Map<ResourceLocation, Resource> cache) implements ResourceProvider {
        @Override
        public Optional<Resource> getResource(ResourceLocation p_251007_) {
            Resource resource = this.cache.get(p_251007_);
            return resource != null ? Optional.of(resource) : this.original.getResource(p_251007_);
        }
    }
}

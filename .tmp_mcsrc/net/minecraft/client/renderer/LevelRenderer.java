package net.minecraft.client.renderer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SculkChargeParticleOptions;
import net.minecraft.core.particles.ShriekParticleOption;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class LevelRenderer implements ResourceManagerReloadListener, AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int SECTION_SIZE = 16;
    public static final int HALF_SECTION_SIZE = 8;
    private static final float SKY_DISC_RADIUS = 512.0F;
    private static final int MIN_FOG_DISTANCE = 32;
    private static final int RAIN_RADIUS = 10;
    private static final int RAIN_DIAMETER = 21;
    private static final int TRANSPARENT_SORT_COUNT = 15;
    private static final ResourceLocation MOON_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
    private static final ResourceLocation SUN_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/sun.png");
    protected static final ResourceLocation CLOUDS_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/clouds.png");
    private static final ResourceLocation END_SKY_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/end_sky.png");
    private static final ResourceLocation FORCEFIELD_LOCATION = ResourceLocation.withDefaultNamespace("textures/misc/forcefield.png");
    private static final ResourceLocation RAIN_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/rain.png");
    private static final ResourceLocation SNOW_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/snow.png");
    public static final Direction[] DIRECTIONS = Direction.values();
    private final Minecraft minecraft;
    private final EntityRenderDispatcher entityRenderDispatcher;
    private final BlockEntityRenderDispatcher blockEntityRenderDispatcher;
    private final RenderBuffers renderBuffers;
    @Nullable
    private ClientLevel level;
    private final SectionOcclusionGraph sectionOcclusionGraph = new SectionOcclusionGraph();
    private final ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections = new ObjectArrayList<>(10000);
    /**
     * Global block entities; these are always rendered, even if off-screen.
     * Any block entity is added to this if {@link net.minecraft.client.renderer.blockentity.BlockEntityRenderer#shouldRenderOffScreen(net.minecraft.world.level.block.entity.BlockEntity)} returns {@code true}.
     */
    private final Set<BlockEntity> globalBlockEntities = Sets.newHashSet();
    @Nullable
    private ViewArea viewArea;
    @Nullable
    private VertexBuffer starBuffer;
    @Nullable
    private VertexBuffer skyBuffer;
    @Nullable
    private VertexBuffer darkBuffer;
    private boolean generateClouds = true;
    @Nullable
    private VertexBuffer cloudBuffer;
    private final RunningTrimmedMean frameTimes = new RunningTrimmedMean(100);
    private int ticks;
    private final Int2ObjectMap<BlockDestructionProgress> destroyingBlocks = new Int2ObjectOpenHashMap<>();
    private final Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress = new Long2ObjectOpenHashMap<>();
    private final Map<BlockPos, SoundInstance> playingJukeboxSongs = Maps.newHashMap();
    @Nullable
    private RenderTarget entityTarget;
    @Nullable
    private PostChain entityEffect;
    @Nullable
    private RenderTarget translucentTarget;
    @Nullable
    private RenderTarget itemEntityTarget;
    @Nullable
    private RenderTarget particlesTarget;
    @Nullable
    private RenderTarget weatherTarget;
    @Nullable
    private RenderTarget cloudsTarget;
    @Nullable
    private PostChain transparencyChain;
    private int lastCameraSectionX = Integer.MIN_VALUE;
    private int lastCameraSectionY = Integer.MIN_VALUE;
    private int lastCameraSectionZ = Integer.MIN_VALUE;
    private double prevCamX = Double.MIN_VALUE;
    private double prevCamY = Double.MIN_VALUE;
    private double prevCamZ = Double.MIN_VALUE;
    private double prevCamRotX = Double.MIN_VALUE;
    private double prevCamRotY = Double.MIN_VALUE;
    private int prevCloudX = Integer.MIN_VALUE;
    private int prevCloudY = Integer.MIN_VALUE;
    private int prevCloudZ = Integer.MIN_VALUE;
    private Vec3 prevCloudColor = Vec3.ZERO;
    @Nullable
    private CloudStatus prevCloudsType;
    @Nullable
    private SectionRenderDispatcher sectionRenderDispatcher;
    private int lastViewDistance = -1;
    private int renderedEntities;
    private int culledEntities;
    private Frustum cullingFrustum;
    private boolean captureFrustum;
    @Nullable
    private Frustum capturedFrustum;
    private final Vector4f[] frustumPoints = new Vector4f[8];
    private final Vector3d frustumPos = new Vector3d(0.0, 0.0, 0.0);
    private double xTransparentOld;
    private double yTransparentOld;
    private double zTransparentOld;
    private int rainSoundTime;
    private final float[] rainSizeX = new float[1024];
    private final float[] rainSizeZ = new float[1024];
    /**
     * Neo: Indicates whether outline effect post-processing was requested for the current frame outside of vanilla codepaths
     */
    private boolean outlineEffectRequested = false;

    public LevelRenderer(Minecraft minecraft, EntityRenderDispatcher entityRenderDispatcher, BlockEntityRenderDispatcher blockEntityRenderDispatcher, RenderBuffers renderBuffers) {
        this.minecraft = minecraft;
        this.entityRenderDispatcher = entityRenderDispatcher;
        this.blockEntityRenderDispatcher = blockEntityRenderDispatcher;
        this.renderBuffers = renderBuffers;

        for (int i = 0; i < 32; i++) {
            for (int j = 0; j < 32; j++) {
                float f = (float)(j - 16);
                float f1 = (float)(i - 16);
                float f2 = Mth.sqrt(f * f + f1 * f1);
                this.rainSizeX[i << 5 | j] = -f1 / f2;
                this.rainSizeZ[i << 5 | j] = f / f2;
            }
        }

        this.createStars();
        this.createLightSky();
        this.createDarkSky();
    }

    private void renderSnowAndRain(LightTexture lightTexture, float partialTick, double camX, double camY, double camZ) {
        if (level.effects().renderSnowAndRain(level, ticks, partialTick, lightTexture, camX, camY, camZ))
            return;
        float f = this.minecraft.level.getRainLevel(partialTick);
        if (!(f <= 0.0F)) {
            lightTexture.turnOnLightLayer();
            Level level = this.minecraft.level;
            int i = Mth.floor(camX);
            int j = Mth.floor(camY);
            int k = Mth.floor(camZ);
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferbuilder = null;
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            int l = 5;
            if (Minecraft.useFancyGraphics()) {
                l = 10;
            }

            RenderSystem.depthMask(Minecraft.useShaderTransparency());
            int i1 = -1;
            float f1 = (float)this.ticks + partialTick;
            RenderSystem.setShader(GameRenderer::getParticleShader);
            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

            for (int j1 = k - l; j1 <= k + l; j1++) {
                for (int k1 = i - l; k1 <= i + l; k1++) {
                    int l1 = (j1 - k + 16) * 32 + k1 - i + 16;
                    double d0 = (double)this.rainSizeX[l1] * 0.5;
                    double d1 = (double)this.rainSizeZ[l1] * 0.5;
                    blockpos$mutableblockpos.set((double)k1, camY, (double)j1);
                    Biome biome = level.getBiome(blockpos$mutableblockpos).value();
                    if (biome.hasPrecipitation()) {
                        int i2 = level.getHeight(Heightmap.Types.MOTION_BLOCKING, k1, j1);
                        int j2 = j - l;
                        int k2 = j + l;
                        if (j2 < i2) {
                            j2 = i2;
                        }

                        if (k2 < i2) {
                            k2 = i2;
                        }

                        int l2 = i2;
                        if (i2 < j) {
                            l2 = j;
                        }

                        if (j2 != k2) {
                            RandomSource randomsource = RandomSource.create((long)(k1 * k1 * 3121 + k1 * 45238971 ^ j1 * j1 * 418711 + j1 * 13761));
                            blockpos$mutableblockpos.set(k1, j2, j1);
                            Biome.Precipitation biome$precipitation = biome.getPrecipitationAt(blockpos$mutableblockpos);
                            if (biome$precipitation == Biome.Precipitation.RAIN) {
                                if (i1 != 0) {
                                    if (i1 >= 0) {
                                        BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
                                    }

                                    i1 = 0;
                                    RenderSystem.setShaderTexture(0, RAIN_LOCATION);
                                    bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
                                }

                                int i3 = this.ticks & 131071;
                                int j3 = k1 * k1 * 3121 + k1 * 45238971 + j1 * j1 * 418711 + j1 * 13761 & 0xFF;
                                float f2 = 3.0F + randomsource.nextFloat();
                                float f3 = -((float)(i3 + j3) + partialTick) / 32.0F * f2;
                                float f4 = f3 % 32.0F;
                                double d2 = (double)k1 + 0.5 - camX;
                                double d3 = (double)j1 + 0.5 - camZ;
                                float f6 = (float)Math.sqrt(d2 * d2 + d3 * d3) / (float)l;
                                float f7 = ((1.0F - f6 * f6) * 0.5F + 0.5F) * f;
                                blockpos$mutableblockpos.set(k1, l2, j1);
                                int k3 = getLightColor(level, blockpos$mutableblockpos);
                                bufferbuilder.addVertex(
                                        (float)((double)k1 - camX - d0 + 0.5), (float)((double)k2 - camY), (float)((double)j1 - camZ - d1 + 0.5)
                                    )
                                    .setUv(0.0F, (float)j2 * 0.25F + f4)
                                    .setColor(1.0F, 1.0F, 1.0F, f7)
                                    .setLight(k3);
                                bufferbuilder.addVertex(
                                        (float)((double)k1 - camX + d0 + 0.5), (float)((double)k2 - camY), (float)((double)j1 - camZ + d1 + 0.5)
                                    )
                                    .setUv(1.0F, (float)j2 * 0.25F + f4)
                                    .setColor(1.0F, 1.0F, 1.0F, f7)
                                    .setLight(k3);
                                bufferbuilder.addVertex(
                                        (float)((double)k1 - camX + d0 + 0.5), (float)((double)j2 - camY), (float)((double)j1 - camZ + d1 + 0.5)
                                    )
                                    .setUv(1.0F, (float)k2 * 0.25F + f4)
                                    .setColor(1.0F, 1.0F, 1.0F, f7)
                                    .setLight(k3);
                                bufferbuilder.addVertex(
                                        (float)((double)k1 - camX - d0 + 0.5), (float)((double)j2 - camY), (float)((double)j1 - camZ - d1 + 0.5)
                                    )
                                    .setUv(0.0F, (float)k2 * 0.25F + f4)
                                    .setColor(1.0F, 1.0F, 1.0F, f7)
                                    .setLight(k3);
                            } else if (biome$precipitation == Biome.Precipitation.SNOW) {
                                if (i1 != 1) {
                                    if (i1 >= 0) {
                                        BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
                                    }

                                    i1 = 1;
                                    RenderSystem.setShaderTexture(0, SNOW_LOCATION);
                                    bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
                                }

                                float f8 = -((float)(this.ticks & 511) + partialTick) / 512.0F;
                                float f9 = (float)(randomsource.nextDouble() + (double)f1 * 0.01 * (double)((float)randomsource.nextGaussian()));
                                float f10 = (float)(randomsource.nextDouble() + (double)(f1 * (float)randomsource.nextGaussian()) * 0.001);
                                double d4 = (double)k1 + 0.5 - camX;
                                double d5 = (double)j1 + 0.5 - camZ;
                                float f11 = (float)Math.sqrt(d4 * d4 + d5 * d5) / (float)l;
                                float f5 = ((1.0F - f11 * f11) * 0.3F + 0.5F) * f;
                                blockpos$mutableblockpos.set(k1, l2, j1);
                                int j4 = getLightColor(level, blockpos$mutableblockpos);
                                int k4 = j4 >> 16 & 65535;
                                int l4 = j4 & 65535;
                                int l3 = (k4 * 3 + 240) / 4;
                                int i4 = (l4 * 3 + 240) / 4;
                                bufferbuilder.addVertex(
                                        (float)((double)k1 - camX - d0 + 0.5), (float)((double)k2 - camY), (float)((double)j1 - camZ - d1 + 0.5)
                                    )
                                    .setUv(0.0F + f9, (float)j2 * 0.25F + f8 + f10)
                                    .setColor(1.0F, 1.0F, 1.0F, f5)
                                    .setUv2(i4, l3);
                                bufferbuilder.addVertex(
                                        (float)((double)k1 - camX + d0 + 0.5), (float)((double)k2 - camY), (float)((double)j1 - camZ + d1 + 0.5)
                                    )
                                    .setUv(1.0F + f9, (float)j2 * 0.25F + f8 + f10)
                                    .setColor(1.0F, 1.0F, 1.0F, f5)
                                    .setUv2(i4, l3);
                                bufferbuilder.addVertex(
                                        (float)((double)k1 - camX + d0 + 0.5), (float)((double)j2 - camY), (float)((double)j1 - camZ + d1 + 0.5)
                                    )
                                    .setUv(1.0F + f9, (float)k2 * 0.25F + f8 + f10)
                                    .setColor(1.0F, 1.0F, 1.0F, f5)
                                    .setUv2(i4, l3);
                                bufferbuilder.addVertex(
                                        (float)((double)k1 - camX - d0 + 0.5), (float)((double)j2 - camY), (float)((double)j1 - camZ - d1 + 0.5)
                                    )
                                    .setUv(0.0F + f9, (float)k2 * 0.25F + f8 + f10)
                                    .setColor(1.0F, 1.0F, 1.0F, f5)
                                    .setUv2(i4, l3);
                            }
                        }
                    }
                }
            }

            if (i1 >= 0) {
                BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
            }

            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            lightTexture.turnOffLightLayer();
        }
    }

    public void tickRain(Camera camera) {
        if (level.effects().tickRain(level, ticks, camera))
            return;
        float f = this.minecraft.level.getRainLevel(1.0F) / (Minecraft.useFancyGraphics() ? 1.0F : 2.0F);
        if (!(f <= 0.0F)) {
            RandomSource randomsource = RandomSource.create((long)this.ticks * 312987231L);
            LevelReader levelreader = this.minecraft.level;
            BlockPos blockpos = BlockPos.containing(camera.getPosition());
            BlockPos blockpos1 = null;
            int i = (int)(100.0F * f * f) / (this.minecraft.options.particles().get() == ParticleStatus.DECREASED ? 2 : 1);

            for (int j = 0; j < i; j++) {
                int k = randomsource.nextInt(21) - 10;
                int l = randomsource.nextInt(21) - 10;
                BlockPos blockpos2 = levelreader.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, blockpos.offset(k, 0, l));
                if (blockpos2.getY() > levelreader.getMinBuildHeight() && blockpos2.getY() <= blockpos.getY() + 10 && blockpos2.getY() >= blockpos.getY() - 10) {
                    Biome biome = levelreader.getBiome(blockpos2).value();
                    if (biome.getPrecipitationAt(blockpos2) == Biome.Precipitation.RAIN) {
                        blockpos1 = blockpos2.below();
                        if (this.minecraft.options.particles().get() == ParticleStatus.MINIMAL) {
                            break;
                        }

                        double d0 = randomsource.nextDouble();
                        double d1 = randomsource.nextDouble();
                        BlockState blockstate = levelreader.getBlockState(blockpos1);
                        FluidState fluidstate = levelreader.getFluidState(blockpos1);
                        VoxelShape voxelshape = blockstate.getCollisionShape(levelreader, blockpos1);
                        double d2 = voxelshape.max(Direction.Axis.Y, d0, d1);
                        double d3 = (double)fluidstate.getHeight(levelreader, blockpos1);
                        double d4 = Math.max(d2, d3);
                        ParticleOptions particleoptions = !fluidstate.is(FluidTags.LAVA)
                                && !blockstate.is(Blocks.MAGMA_BLOCK)
                                && !CampfireBlock.isLitCampfire(blockstate)
                            ? ParticleTypes.RAIN
                            : ParticleTypes.SMOKE;
                        this.minecraft
                            .level
                            .addParticle(
                                particleoptions, (double)blockpos1.getX() + d0, (double)blockpos1.getY() + d4, (double)blockpos1.getZ() + d1, 0.0, 0.0, 0.0
                            );
                    }
                }
            }

            if (blockpos1 != null && randomsource.nextInt(3) < this.rainSoundTime++) {
                this.rainSoundTime = 0;
                if (blockpos1.getY() > blockpos.getY() + 1
                    && levelreader.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, blockpos).getY() > Mth.floor((float)blockpos.getY())) {
                    this.minecraft.level.playLocalSound(blockpos1, SoundEvents.WEATHER_RAIN_ABOVE, SoundSource.WEATHER, 0.1F, 0.5F, false);
                } else {
                    this.minecraft.level.playLocalSound(blockpos1, SoundEvents.WEATHER_RAIN, SoundSource.WEATHER, 0.2F, 1.0F, false);
                }
            }
        }
    }

    @Override
    public void close() {
        if (this.entityEffect != null) {
            this.entityEffect.close();
        }

        if (this.transparencyChain != null) {
            this.transparencyChain.close();
        }
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        this.initOutline();
        if (Minecraft.useShaderTransparency()) {
            this.initTransparency();
        }
    }

    public void initOutline() {
        if (this.entityEffect != null) {
            this.entityEffect.close();
        }

        ResourceLocation resourcelocation = ResourceLocation.withDefaultNamespace("shaders/post/entity_outline.json");

        try {
            this.entityEffect = new PostChain(
                this.minecraft.getTextureManager(), this.minecraft.getResourceManager(), this.minecraft.getMainRenderTarget(), resourcelocation
            );
            this.entityEffect.resize(this.minecraft.getWindow().getWidth(), this.minecraft.getWindow().getHeight());
            this.entityTarget = this.entityEffect.getTempTarget("final");
        } catch (IOException ioexception) {
            LOGGER.warn("Failed to load shader: {}", resourcelocation, ioexception);
            this.entityEffect = null;
            this.entityTarget = null;
        } catch (JsonSyntaxException jsonsyntaxexception) {
            LOGGER.warn("Failed to parse shader: {}", resourcelocation, jsonsyntaxexception);
            this.entityEffect = null;
            this.entityTarget = null;
        }
    }

    private void initTransparency() {
        this.deinitTransparency();
        ResourceLocation resourcelocation = ResourceLocation.withDefaultNamespace("shaders/post/transparency.json");

        try {
            PostChain postchain = new PostChain(
                this.minecraft.getTextureManager(), this.minecraft.getResourceManager(), this.minecraft.getMainRenderTarget(), resourcelocation
            );
            postchain.resize(this.minecraft.getWindow().getWidth(), this.minecraft.getWindow().getHeight());
            RenderTarget rendertarget1 = postchain.getTempTarget("translucent");
            RenderTarget rendertarget2 = postchain.getTempTarget("itemEntity");
            RenderTarget rendertarget3 = postchain.getTempTarget("particles");
            RenderTarget rendertarget4 = postchain.getTempTarget("weather");
            RenderTarget rendertarget = postchain.getTempTarget("clouds");
            this.transparencyChain = postchain;
            this.translucentTarget = rendertarget1;
            this.itemEntityTarget = rendertarget2;
            this.particlesTarget = rendertarget3;
            this.weatherTarget = rendertarget4;
            this.cloudsTarget = rendertarget;
        } catch (Exception exception) {
            String s = exception instanceof JsonSyntaxException ? "parse" : "load";
            String s1 = "Failed to " + s + " shader: " + resourcelocation;
            LevelRenderer.TransparencyShaderException levelrenderer$transparencyshaderexception = new LevelRenderer.TransparencyShaderException(s1, exception);
            if (this.minecraft.getResourcePackRepository().getSelectedIds().size() > 1) {
                Component component = this.minecraft
                    .getResourceManager()
                    .listPacks()
                    .findFirst()
                    .map(p_234256_ -> Component.literal(p_234256_.packId()))
                    .orElse(null);
                this.minecraft.options.graphicsMode().set(GraphicsStatus.FANCY);
                this.minecraft.clearResourcePacksOnError(levelrenderer$transparencyshaderexception, component, null);
            } else {
                this.minecraft.options.graphicsMode().set(GraphicsStatus.FANCY);
                this.minecraft.options.save();
                LOGGER.error(LogUtils.FATAL_MARKER, s1, (Throwable)levelrenderer$transparencyshaderexception);
                this.minecraft.emergencySaveAndCrash(new CrashReport(s1, levelrenderer$transparencyshaderexception));
            }
        }
    }

    private void deinitTransparency() {
        if (this.transparencyChain != null) {
            this.transparencyChain.close();
            this.translucentTarget.destroyBuffers();
            this.itemEntityTarget.destroyBuffers();
            this.particlesTarget.destroyBuffers();
            this.weatherTarget.destroyBuffers();
            this.cloudsTarget.destroyBuffers();
            this.transparencyChain = null;
            this.translucentTarget = null;
            this.itemEntityTarget = null;
            this.particlesTarget = null;
            this.weatherTarget = null;
            this.cloudsTarget = null;
        }
    }

    public void doEntityOutline() {
        if (this.shouldShowEntityOutlines()) {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ZERO,
                GlStateManager.DestFactor.ONE
            );
            this.entityTarget.blitToScreen(this.minecraft.getWindow().getWidth(), this.minecraft.getWindow().getHeight(), false);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    public boolean shouldShowEntityOutlines() {
        return !this.minecraft.gameRenderer.isPanoramicMode() && this.entityTarget != null && this.entityEffect != null && this.minecraft.player != null;
    }

    private void createDarkSky() {
        if (this.darkBuffer != null) {
            this.darkBuffer.close();
        }

        this.darkBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        this.darkBuffer.bind();
        this.darkBuffer.upload(buildSkyDisc(Tesselator.getInstance(), -16.0F));
        VertexBuffer.unbind();
    }

    private void createLightSky() {
        if (this.skyBuffer != null) {
            this.skyBuffer.close();
        }

        this.skyBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        this.skyBuffer.bind();
        this.skyBuffer.upload(buildSkyDisc(Tesselator.getInstance(), 16.0F));
        VertexBuffer.unbind();
    }

    private static MeshData buildSkyDisc(Tesselator tesselator, float y) {
        float f = Math.signum(y) * 512.0F;
        float f1 = 512.0F;
        BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
        bufferbuilder.addVertex(0.0F, y, 0.0F);

        for (int i = -180; i <= 180; i += 45) {
            bufferbuilder.addVertex(f * Mth.cos((float)i * (float) (Math.PI / 180.0)), y, 512.0F * Mth.sin((float)i * (float) (Math.PI / 180.0)));
        }

        return bufferbuilder.buildOrThrow();
    }

    private void createStars() {
        if (this.starBuffer != null) {
            this.starBuffer.close();
        }

        this.starBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        this.starBuffer.bind();
        this.starBuffer.upload(this.drawStars(Tesselator.getInstance()));
        VertexBuffer.unbind();
    }

    private MeshData drawStars(Tesselator tesselator) {
        RandomSource randomsource = RandomSource.create(10842L);
        int i = 1500;
        float f = 100.0F;
        BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);

        for (int j = 0; j < 1500; j++) {
            float f1 = randomsource.nextFloat() * 2.0F - 1.0F;
            float f2 = randomsource.nextFloat() * 2.0F - 1.0F;
            float f3 = randomsource.nextFloat() * 2.0F - 1.0F;
            float f4 = 0.15F + randomsource.nextFloat() * 0.1F;
            float f5 = Mth.lengthSquared(f1, f2, f3);
            if (!(f5 <= 0.010000001F) && !(f5 >= 1.0F)) {
                Vector3f vector3f = new Vector3f(f1, f2, f3).normalize(100.0F);
                float f6 = (float)(randomsource.nextDouble() * (float) Math.PI * 2.0);
                Quaternionf quaternionf = new Quaternionf().rotateTo(new Vector3f(0.0F, 0.0F, -1.0F), vector3f).rotateZ(f6);
                bufferbuilder.addVertex(vector3f.add(new Vector3f(f4, -f4, 0.0F).rotate(quaternionf)));
                bufferbuilder.addVertex(vector3f.add(new Vector3f(f4, f4, 0.0F).rotate(quaternionf)));
                bufferbuilder.addVertex(vector3f.add(new Vector3f(-f4, f4, 0.0F).rotate(quaternionf)));
                bufferbuilder.addVertex(vector3f.add(new Vector3f(-f4, -f4, 0.0F).rotate(quaternionf)));
            }
        }

        return bufferbuilder.buildOrThrow();
    }

    /**
     * @param level the level to set, or {@code null} to clear
     */
    public void setLevel(@Nullable ClientLevel level) {
        this.lastCameraSectionX = Integer.MIN_VALUE;
        this.lastCameraSectionY = Integer.MIN_VALUE;
        this.lastCameraSectionZ = Integer.MIN_VALUE;
        this.entityRenderDispatcher.setLevel(level);
        this.level = level;
        if (level != null) {
            this.allChanged();
        } else {
            if (this.viewArea != null) {
                this.viewArea.releaseAllBuffers();
                this.viewArea = null;
            }

            if (this.sectionRenderDispatcher != null) {
                this.sectionRenderDispatcher.dispose();
            }

            this.sectionRenderDispatcher = null;
            this.globalBlockEntities.clear();
            this.sectionOcclusionGraph.waitAndReset(null);
            this.visibleSections.clear();
        }
    }

    public void graphicsChanged() {
        if (Minecraft.useShaderTransparency()) {
            this.initTransparency();
        } else {
            this.deinitTransparency();
        }
    }

    public void allChanged() {
        if (this.level != null) {
            this.graphicsChanged();
            this.level.clearTintCaches();
            if (this.sectionRenderDispatcher == null) {
                this.sectionRenderDispatcher = new SectionRenderDispatcher(
                    this.level,
                    this,
                    Util.backgroundExecutor(),
                    this.renderBuffers,
                    this.minecraft.getBlockRenderer(),
                    this.minecraft.getBlockEntityRenderDispatcher()
                );
            } else {
                this.sectionRenderDispatcher.setLevel(this.level);
            }

            this.generateClouds = true;
            ItemBlockRenderTypes.setFancy(Minecraft.useFancyGraphics());
            this.lastViewDistance = this.minecraft.options.getEffectiveRenderDistance();
            if (this.viewArea != null) {
                this.viewArea.releaseAllBuffers();
            }

            this.sectionRenderDispatcher.blockUntilClear();
            synchronized (this.globalBlockEntities) {
                this.globalBlockEntities.clear();
            }

            this.viewArea = new ViewArea(this.sectionRenderDispatcher, this.level, this.minecraft.options.getEffectiveRenderDistance(), this);
            this.sectionOcclusionGraph.waitAndReset(this.viewArea);
            this.visibleSections.clear();
            Entity entity = this.minecraft.getCameraEntity();
            if (entity != null) {
                this.viewArea.repositionCamera(entity.getX(), entity.getZ());
            }
        }
    }

    public void resize(int width, int height) {
        this.needsUpdate();
        if (this.entityEffect != null) {
            this.entityEffect.resize(width, height);
        }

        if (this.transparencyChain != null) {
            this.transparencyChain.resize(width, height);
        }
    }

    public String getSectionStatistics() {
        int i = this.viewArea.sections.length;
        int j = this.countRenderedSections();
        return String.format(
            Locale.ROOT,
            "C: %d/%d %sD: %d, %s",
            j,
            i,
            this.minecraft.smartCull ? "(s) " : "",
            this.lastViewDistance,
            this.sectionRenderDispatcher == null ? "null" : this.sectionRenderDispatcher.getStats()
        );
    }

    public SectionRenderDispatcher getSectionRenderDispatcher() {
        return this.sectionRenderDispatcher;
    }

    public double getTotalSections() {
        return (double)this.viewArea.sections.length;
    }

    public double getLastViewDistance() {
        return (double)this.lastViewDistance;
    }

    public int countRenderedSections() {
        int i = 0;

        for (SectionRenderDispatcher.RenderSection sectionrenderdispatcher$rendersection : this.visibleSections) {
            if (!sectionrenderdispatcher$rendersection.getCompiled().hasNoRenderableLayers()) {
                i++;
            }
        }

        return i;
    }

    public String getEntityStatistics() {
        return "E: "
            + this.renderedEntities
            + "/"
            + this.level.getEntityCount()
            + ", B: "
            + this.culledEntities
            + ", SD: "
            + this.level.getServerSimulationDistance();
    }

    private void setupRender(Camera camera, Frustum frustum, boolean hasCapturedFrustum, boolean isSpectator) {
        Vec3 vec3 = camera.getPosition();
        if (this.minecraft.options.getEffectiveRenderDistance() != this.lastViewDistance) {
            this.allChanged();
        }

        this.level.getProfiler().push("camera");
        double d0 = this.minecraft.player.getX();
        double d1 = this.minecraft.player.getY();
        double d2 = this.minecraft.player.getZ();
        int i = SectionPos.posToSectionCoord(d0);
        int j = SectionPos.posToSectionCoord(d1);
        int k = SectionPos.posToSectionCoord(d2);
        if (this.lastCameraSectionX != i || this.lastCameraSectionY != j || this.lastCameraSectionZ != k) {
            this.lastCameraSectionX = i;
            this.lastCameraSectionY = j;
            this.lastCameraSectionZ = k;
            this.viewArea.repositionCamera(d0, d2);
        }

        this.sectionRenderDispatcher.setCamera(vec3);
        this.level.getProfiler().popPush("cull");
        this.minecraft.getProfiler().popPush("culling");
        BlockPos blockpos = camera.getBlockPosition();
        double d3 = Math.floor(vec3.x / 8.0);
        double d4 = Math.floor(vec3.y / 8.0);
        double d5 = Math.floor(vec3.z / 8.0);
        if (d3 != this.prevCamX || d4 != this.prevCamY || d5 != this.prevCamZ) {
            this.sectionOcclusionGraph.invalidate();
        }

        this.prevCamX = d3;
        this.prevCamY = d4;
        this.prevCamZ = d5;
        this.minecraft.getProfiler().popPush("update");
        if (!hasCapturedFrustum) {
            boolean flag = this.minecraft.smartCull;
            if (isSpectator && this.level.getBlockState(blockpos).isSolidRender(this.level, blockpos)) {
                flag = false;
            }

            Entity.setViewScale(
                Mth.clamp((double)this.minecraft.options.getEffectiveRenderDistance() / 8.0, 1.0, 2.5) * this.minecraft.options.entityDistanceScaling().get()
            );
            this.minecraft.getProfiler().push("section_occlusion_graph");
            this.sectionOcclusionGraph.update(flag, camera, frustum, this.visibleSections);
            this.minecraft.getProfiler().pop();
            double d6 = Math.floor((double)(camera.getXRot() / 2.0F));
            double d7 = Math.floor((double)(camera.getYRot() / 2.0F));
            if (this.sectionOcclusionGraph.consumeFrustumUpdate() || d6 != this.prevCamRotX || d7 != this.prevCamRotY) {
                this.applyFrustum(offsetFrustum(frustum));
                this.prevCamRotX = d6;
                this.prevCamRotY = d7;
            }
        }

        this.minecraft.getProfiler().pop();
    }

    public static Frustum offsetFrustum(Frustum frustum) {
        return new Frustum(frustum).offsetToFullyIncludeCameraCube(8);
    }

    private void applyFrustum(Frustum frustum) {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("applyFrustum called from wrong thread: " + Thread.currentThread().getName());
        } else {
            this.minecraft.getProfiler().push("apply_frustum");
            this.visibleSections.clear();
            this.sectionOcclusionGraph.addSectionsInFrustum(frustum, this.visibleSections);
            this.minecraft.getProfiler().pop();
        }
    }

    public void addRecentlyCompiledSection(SectionRenderDispatcher.RenderSection renderSection) {
        this.sectionOcclusionGraph.onSectionCompiled(renderSection);
    }

    private void captureFrustum(Matrix4f viewMatrix, Matrix4f projectionMatrix, double camX, double camY, double camZ, Frustum capturedFrustrum) {
        this.capturedFrustum = capturedFrustrum;
        Matrix4f matrix4f = new Matrix4f(projectionMatrix);
        matrix4f.mul(viewMatrix);
        matrix4f.invert();
        this.frustumPos.x = camX;
        this.frustumPos.y = camY;
        this.frustumPos.z = camZ;
        this.frustumPoints[0] = new Vector4f(-1.0F, -1.0F, -1.0F, 1.0F);
        this.frustumPoints[1] = new Vector4f(1.0F, -1.0F, -1.0F, 1.0F);
        this.frustumPoints[2] = new Vector4f(1.0F, 1.0F, -1.0F, 1.0F);
        this.frustumPoints[3] = new Vector4f(-1.0F, 1.0F, -1.0F, 1.0F);
        this.frustumPoints[4] = new Vector4f(-1.0F, -1.0F, 1.0F, 1.0F);
        this.frustumPoints[5] = new Vector4f(1.0F, -1.0F, 1.0F, 1.0F);
        this.frustumPoints[6] = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.frustumPoints[7] = new Vector4f(-1.0F, 1.0F, 1.0F, 1.0F);

        for (int i = 0; i < 8; i++) {
            matrix4f.transform(this.frustumPoints[i]);
            this.frustumPoints[i].div(this.frustumPoints[i].w());
        }
    }

    public void prepareCullFrustum(Vec3 cameraPosition, Matrix4f frustumMatrix, Matrix4f projectionMatrix) {
        this.cullingFrustum = new Frustum(frustumMatrix, projectionMatrix);
        this.cullingFrustum.prepare(cameraPosition.x(), cameraPosition.y(), cameraPosition.z());
    }

    public void renderLevel(
        DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix
    ) {
        TickRateManager tickratemanager = this.minecraft.level.tickRateManager();
        float f = deltaTracker.getGameTimeDeltaPartialTick(false);
        RenderSystem.setShaderGameTime(this.level.getGameTime(), f);
        this.blockEntityRenderDispatcher.prepare(this.level, camera, this.minecraft.hitResult);
        this.entityRenderDispatcher.prepare(this.level, camera, this.minecraft.crosshairPickEntity);
        ProfilerFiller profilerfiller = this.level.getProfiler();
        profilerfiller.popPush("light_update_queue");
        this.level.pollLightUpdates();
        profilerfiller.popPush("light_updates");
        this.level.getChunkSource().getLightEngine().runLightUpdates();
        Vec3 vec3 = camera.getPosition();
        double d0 = vec3.x();
        double d1 = vec3.y();
        double d2 = vec3.z();
        profilerfiller.popPush("culling");
        boolean flag = this.capturedFrustum != null;
        Frustum frustum;
        if (flag) {
            frustum = this.capturedFrustum;
            frustum.prepare(this.frustumPos.x, this.frustumPos.y, this.frustumPos.z);
        } else {
            frustum = this.cullingFrustum;
        }

        this.minecraft.getProfiler().popPush("captureFrustum");
        if (this.captureFrustum) {
            this.captureFrustum(frustumMatrix, projectionMatrix, vec3.x, vec3.y, vec3.z, flag ? new Frustum(frustumMatrix, projectionMatrix) : frustum);
            this.captureFrustum = false;
        }

        profilerfiller.popPush("clear");
        FogRenderer.setupColor(camera, f, this.minecraft.level, this.minecraft.options.getEffectiveRenderDistance(), gameRenderer.getDarkenWorldAmount(f));
        FogRenderer.levelFogColor();
        RenderSystem.clear(16640, Minecraft.ON_OSX);
        float f1 = gameRenderer.getRenderDistance();
        boolean flag1 = this.minecraft.level.effects().isFoggyAt(Mth.floor(d0), Mth.floor(d1)) || this.minecraft.gui.getBossOverlay().shouldCreateWorldFog();
        FogRenderer.setupFog(camera, FogRenderer.FogMode.FOG_SKY, f1, flag1, f);
        profilerfiller.popPush("sky");
        RenderSystem.setShader(GameRenderer::getPositionShader);
        this.renderSky(frustumMatrix, projectionMatrix, f, camera, flag1, () -> FogRenderer.setupFog(camera, FogRenderer.FogMode.FOG_SKY, f1, flag1, f));
        net.neoforged.neoforge.client.ClientHooks.dispatchRenderStage(net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_SKY, this, null, frustumMatrix, projectionMatrix, this.ticks, camera, frustum);
        profilerfiller.popPush("fog");
        FogRenderer.setupFog(camera, FogRenderer.FogMode.FOG_TERRAIN, Math.max(f1, 32.0F), flag1, f);
        profilerfiller.popPush("terrain_setup");
        this.setupRender(camera, frustum, flag, this.minecraft.player.isSpectator());
        profilerfiller.popPush("compile_sections");
        this.compileSections(camera);
        profilerfiller.popPush("terrain");
        this.renderSectionLayer(RenderType.solid(), d0, d1, d2, frustumMatrix, projectionMatrix);
        this.minecraft.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS).setBlurMipmap(false, this.minecraft.options.mipmapLevels().get() > 0); // Neo: fix flickering leaves when mods mess up the blurMipmap settings
        this.renderSectionLayer(RenderType.cutoutMipped(), d0, d1, d2, frustumMatrix, projectionMatrix);
        this.minecraft.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS).restoreLastBlurMipmap();
        this.renderSectionLayer(RenderType.cutout(), d0, d1, d2, frustumMatrix, projectionMatrix);
        if (this.level.effects().constantAmbientLight()) {
            Lighting.setupNetherLevel();
        } else {
            Lighting.setupLevel();
        }

        profilerfiller.popPush("entities");
        this.renderedEntities = 0;
        this.culledEntities = 0;
        if (this.itemEntityTarget != null) {
            this.itemEntityTarget.clear(Minecraft.ON_OSX);
            this.itemEntityTarget.copyDepthFrom(this.minecraft.getMainRenderTarget());
            this.minecraft.getMainRenderTarget().bindWrite(false);
        }

        if (this.weatherTarget != null) {
            this.weatherTarget.clear(Minecraft.ON_OSX);
        }

        if (this.shouldShowEntityOutlines()) {
            this.entityTarget.clear(Minecraft.ON_OSX);
            this.minecraft.getMainRenderTarget().bindWrite(false);
        }

        Matrix4fStack matrix4fstack = RenderSystem.getModelViewStack();
        matrix4fstack.pushMatrix();
        matrix4fstack.mul(frustumMatrix);
        RenderSystem.applyModelViewMatrix();
        boolean flag2 = false;
        PoseStack posestack = new PoseStack();
        MultiBufferSource.BufferSource multibuffersource$buffersource = this.renderBuffers.bufferSource();

        for (Entity entity : this.level.entitiesForRendering()) {
            if (this.entityRenderDispatcher.shouldRender(entity, frustum, d0, d1, d2) || entity.hasIndirectPassenger(this.minecraft.player)) {
                BlockPos blockpos = entity.blockPosition();
                if ((this.level.isOutsideBuildHeight(blockpos.getY()) || this.isSectionCompiled(blockpos))
                    && (
                        entity != camera.getEntity()
                            || camera.isDetached()
                            || camera.getEntity() instanceof LivingEntity && ((LivingEntity)camera.getEntity()).isSleeping()
                    )
                    && (!(entity instanceof LocalPlayer) || camera.getEntity() == entity || (entity == minecraft.player && !minecraft.player.isSpectator()))) { // Neo: render local player entity when it is not the camera entity
                    this.renderedEntities++;
                    if (entity.tickCount == 0) {
                        entity.xOld = entity.getX();
                        entity.yOld = entity.getY();
                        entity.zOld = entity.getZ();
                    }

                    MultiBufferSource multibuffersource;
                    if (this.shouldShowEntityOutlines() && this.minecraft.shouldEntityAppearGlowing(entity)) {
                        flag2 = true;
                        OutlineBufferSource outlinebuffersource = this.renderBuffers.outlineBufferSource();
                        multibuffersource = outlinebuffersource;
                        int i = entity.getTeamColor();
                        outlinebuffersource.setColor(FastColor.ARGB32.red(i), FastColor.ARGB32.green(i), FastColor.ARGB32.blue(i), 255);
                    } else {
                        if (this.shouldShowEntityOutlines() && entity.hasCustomOutlineRendering(this.minecraft.player)) { // FORGE: allow custom outline rendering
                            flag2 = true;
                        }
                        multibuffersource = multibuffersource$buffersource;
                    }

                    float f2 = deltaTracker.getGameTimeDeltaPartialTick(!tickratemanager.isEntityFrozen(entity));
                    this.renderEntity(entity, d0, d1, d2, f2, posestack, multibuffersource);
                }
            }
        }

        multibuffersource$buffersource.endLastBatch();
        this.checkPoseStack(posestack);
        multibuffersource$buffersource.endBatch(RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS));
        multibuffersource$buffersource.endBatch(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        multibuffersource$buffersource.endBatch(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        multibuffersource$buffersource.endBatch(RenderType.entitySmoothCutout(TextureAtlas.LOCATION_BLOCKS));
        net.neoforged.neoforge.client.ClientHooks.dispatchRenderStage(net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES, this, posestack, frustumMatrix, projectionMatrix, this.ticks, camera, frustum);
        profilerfiller.popPush("blockentities");

        for (SectionRenderDispatcher.RenderSection sectionrenderdispatcher$rendersection : this.visibleSections) {
            List<BlockEntity> list = sectionrenderdispatcher$rendersection.getCompiled().getRenderableBlockEntities();
            if (!list.isEmpty()) {
                for (BlockEntity blockentity1 : list) {
                    if (!net.neoforged.neoforge.client.ClientHooks.isBlockEntityRendererVisible(blockEntityRenderDispatcher, blockentity1, frustum)) continue;
                    BlockPos blockpos4 = blockentity1.getBlockPos();
                    MultiBufferSource multibuffersource1 = multibuffersource$buffersource;
                    posestack.pushPose();
                    posestack.translate((double)blockpos4.getX() - d0, (double)blockpos4.getY() - d1, (double)blockpos4.getZ() - d2);
                    SortedSet<BlockDestructionProgress> sortedset = this.destructionProgress.get(blockpos4.asLong());
                    if (sortedset != null && !sortedset.isEmpty()) {
                        int j = sortedset.last().getProgress();
                        if (j >= 0) {
                            PoseStack.Pose posestack$pose = posestack.last();
                            VertexConsumer vertexconsumer = new SheetedDecalTextureGenerator(
                                this.renderBuffers.crumblingBufferSource().getBuffer(ModelBakery.DESTROY_TYPES.get(j)), posestack$pose, 1.0F
                            );
                            multibuffersource1 = p_234298_ -> {
                                VertexConsumer vertexconsumer3 = multibuffersource$buffersource.getBuffer(p_234298_);
                                return p_234298_.affectsCrumbling() ? VertexMultiConsumer.create(vertexconsumer, vertexconsumer3) : vertexconsumer3;
                            };
                        }
                    }
                    if (this.shouldShowEntityOutlines() && blockentity1.hasCustomOutlineRendering(this.minecraft.player)) { // Neo: allow custom outline rendering
                        flag2 = true;
                    }

                    this.blockEntityRenderDispatcher.render(blockentity1, f, posestack, multibuffersource1);
                    posestack.popPose();
                }
            }
        }

        synchronized (this.globalBlockEntities) {
            for (BlockEntity blockentity : this.globalBlockEntities) {
                if (!net.neoforged.neoforge.client.ClientHooks.isBlockEntityRendererVisible(blockEntityRenderDispatcher, blockentity, frustum)) continue;
                BlockPos blockpos3 = blockentity.getBlockPos();
                posestack.pushPose();
                posestack.translate((double)blockpos3.getX() - d0, (double)blockpos3.getY() - d1, (double)blockpos3.getZ() - d2);
                if (this.shouldShowEntityOutlines() && blockentity.hasCustomOutlineRendering(this.minecraft.player)) { // Neo: allow custom outline rendering
                    flag2 = true;
                }
                this.blockEntityRenderDispatcher.render(blockentity, f, posestack, multibuffersource$buffersource);
                posestack.popPose();
            }
        }

        this.checkPoseStack(posestack);
        multibuffersource$buffersource.endBatch(RenderType.solid());
        multibuffersource$buffersource.endBatch(RenderType.endPortal());
        multibuffersource$buffersource.endBatch(RenderType.endGateway());
        multibuffersource$buffersource.endBatch(Sheets.solidBlockSheet());
        multibuffersource$buffersource.endBatch(Sheets.cutoutBlockSheet());
        multibuffersource$buffersource.endBatch(Sheets.bedSheet());
        multibuffersource$buffersource.endBatch(Sheets.shulkerBoxSheet());
        multibuffersource$buffersource.endBatch(Sheets.signSheet());
        multibuffersource$buffersource.endBatch(Sheets.hangingSignSheet());
        multibuffersource$buffersource.endBatch(Sheets.chestSheet());
        this.renderBuffers.outlineBufferSource().endOutlineBatch();
        // Neo: handle outline effect requests outside glowing entities
        if (this.outlineEffectRequested) {
            flag2 |= this.shouldShowEntityOutlines();
            this.outlineEffectRequested = false;
        }
        if (flag2) {
            this.entityEffect.process(deltaTracker.getGameTimeDeltaTicks());
            this.minecraft.getMainRenderTarget().bindWrite(false);
        }

        net.neoforged.neoforge.client.ClientHooks.dispatchRenderStage(net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES, this, posestack, frustumMatrix, projectionMatrix, this.ticks, camera, frustum);
        profilerfiller.popPush("destroyProgress");

        for (Entry<SortedSet<BlockDestructionProgress>> entry : this.destructionProgress.long2ObjectEntrySet()) {
            BlockPos blockpos2 = BlockPos.of(entry.getLongKey());
            double d3 = (double)blockpos2.getX() - d0;
            double d4 = (double)blockpos2.getY() - d1;
            double d5 = (double)blockpos2.getZ() - d2;
            if (!(d3 * d3 + d4 * d4 + d5 * d5 > 1024.0)) {
                SortedSet<BlockDestructionProgress> sortedset1 = entry.getValue();
                if (sortedset1 != null && !sortedset1.isEmpty()) {
                    int k = sortedset1.last().getProgress();
                    posestack.pushPose();
                    posestack.translate((double)blockpos2.getX() - d0, (double)blockpos2.getY() - d1, (double)blockpos2.getZ() - d2);
                    PoseStack.Pose posestack$pose1 = posestack.last();
                    VertexConsumer vertexconsumer1 = new SheetedDecalTextureGenerator(
                        this.renderBuffers.crumblingBufferSource().getBuffer(ModelBakery.DESTROY_TYPES.get(k)), posestack$pose1, 1.0F
                    );
                    net.neoforged.neoforge.client.model.data.ModelData modelData = level.getModelData(blockpos2);
                    this.minecraft
                        .getBlockRenderer()
                        .renderBreakingTexture(this.level.getBlockState(blockpos2), blockpos2, this.level, posestack, vertexconsumer1, modelData);
                    posestack.popPose();
                }
            }
        }

        this.checkPoseStack(posestack);
        HitResult hitresult = this.minecraft.hitResult;
        if (renderBlockOutline && hitresult != null && hitresult.getType() == HitResult.Type.BLOCK) {
            profilerfiller.popPush("outline");
            BlockPos blockpos1 = ((BlockHitResult)hitresult).getBlockPos();
            BlockState blockstate = this.level.getBlockState(blockpos1);
            if (!net.neoforged.neoforge.client.ClientHooks.onDrawHighlight(this, camera, hitresult, deltaTracker, posestack, multibuffersource$buffersource))
            if (!blockstate.isAir() && this.level.getWorldBorder().isWithinBounds(blockpos1)) {
                VertexConsumer vertexconsumer2 = multibuffersource$buffersource.getBuffer(RenderType.lines());
                this.renderHitOutline(posestack, vertexconsumer2, camera.getEntity(), d0, d1, d2, blockpos1, blockstate);
            }
        } else if (hitresult != null && hitresult.getType() == HitResult.Type.ENTITY) {
            net.neoforged.neoforge.client.ClientHooks.onDrawHighlight(this, camera, hitresult, deltaTracker, posestack, multibuffersource$buffersource);
        }

        this.minecraft.debugRenderer.render(posestack, multibuffersource$buffersource, d0, d1, d2);
        multibuffersource$buffersource.endLastBatch();
        multibuffersource$buffersource.endBatch(Sheets.translucentCullBlockSheet());
        multibuffersource$buffersource.endBatch(Sheets.bannerSheet());
        multibuffersource$buffersource.endBatch(Sheets.shieldSheet());
        multibuffersource$buffersource.endBatch(RenderType.armorEntityGlint());
        multibuffersource$buffersource.endBatch(RenderType.glint());
        multibuffersource$buffersource.endBatch(RenderType.glintTranslucent());
        multibuffersource$buffersource.endBatch(RenderType.entityGlint());
        multibuffersource$buffersource.endBatch(RenderType.entityGlintDirect());
        multibuffersource$buffersource.endBatch(RenderType.waterMask());
        this.renderBuffers.crumblingBufferSource().endBatch();
        if (this.transparencyChain != null) {
            multibuffersource$buffersource.endBatch(RenderType.lines());
            multibuffersource$buffersource.endBatch();
            this.translucentTarget.clear(Minecraft.ON_OSX);
            this.translucentTarget.copyDepthFrom(this.minecraft.getMainRenderTarget());
            profilerfiller.popPush("translucent");
            this.renderSectionLayer(RenderType.translucent(), d0, d1, d2, frustumMatrix, projectionMatrix);
            profilerfiller.popPush("string");
            this.renderSectionLayer(RenderType.tripwire(), d0, d1, d2, frustumMatrix, projectionMatrix);
            this.particlesTarget.clear(Minecraft.ON_OSX);
            this.particlesTarget.copyDepthFrom(this.minecraft.getMainRenderTarget());
            RenderStateShard.PARTICLES_TARGET.setupRenderState();
            profilerfiller.popPush("particles");
            this.minecraft.particleEngine.render(lightTexture, camera, f, frustum, type -> true);
            net.neoforged.neoforge.client.ClientHooks.dispatchRenderStage(net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_PARTICLES, this, posestack, frustumMatrix, projectionMatrix, this.ticks, camera, frustum);
            RenderStateShard.PARTICLES_TARGET.clearRenderState();
        } else {
            // Neo: render solid particles before translucent geometry to match order of chunk render types, fixes solid particles disappearing underwater in Fast/Fancy (MC-161917)
            profilerfiller.popPush("solid_particles");
            this.minecraft.particleEngine.render(lightTexture, camera, f, frustum, type -> !type.isTranslucent());
            profilerfiller.popPush("translucent");
            if (this.translucentTarget != null) {
                this.translucentTarget.clear(Minecraft.ON_OSX);
            }

            this.renderSectionLayer(RenderType.translucent(), d0, d1, d2, frustumMatrix, projectionMatrix);
            multibuffersource$buffersource.endBatch(RenderType.lines());
            multibuffersource$buffersource.endBatch();
            profilerfiller.popPush("string");
            this.renderSectionLayer(RenderType.tripwire(), d0, d1, d2, frustumMatrix, projectionMatrix);
            profilerfiller.popPush("particles");
            this.minecraft.particleEngine.render(lightTexture, camera, f, frustum, type -> type.isTranslucent()); // Neo: only render translucent particles at this stage
            net.neoforged.neoforge.client.ClientHooks.dispatchRenderStage(net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_PARTICLES, this, posestack, frustumMatrix, projectionMatrix, this.ticks, camera, frustum);
        }

        if (this.minecraft.options.getCloudsType() != CloudStatus.OFF) {
            if (this.transparencyChain != null) {
                this.cloudsTarget.clear(Minecraft.ON_OSX);
            }

            profilerfiller.popPush("clouds");
            this.renderClouds(posestack, frustumMatrix, projectionMatrix, f, d0, d1, d2);
        }

        if (this.transparencyChain != null) {
            RenderStateShard.WEATHER_TARGET.setupRenderState();
            profilerfiller.popPush("weather");
            this.renderSnowAndRain(lightTexture, f, d0, d1, d2);
            net.neoforged.neoforge.client.ClientHooks.dispatchRenderStage(net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_WEATHER, this, posestack, frustumMatrix, projectionMatrix, this.ticks, camera, frustum);
            this.renderWorldBorder(camera);
            RenderStateShard.WEATHER_TARGET.clearRenderState();
            this.transparencyChain.process(deltaTracker.getGameTimeDeltaTicks());
            this.minecraft.getMainRenderTarget().bindWrite(false);
        } else {
            RenderSystem.depthMask(false);
            profilerfiller.popPush("weather");
            this.renderSnowAndRain(lightTexture, f, d0, d1, d2);
            net.neoforged.neoforge.client.ClientHooks.dispatchRenderStage(net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_WEATHER, this, posestack, frustumMatrix, projectionMatrix, this.ticks, camera, frustum);
            this.renderWorldBorder(camera);
            RenderSystem.depthMask(true);
        }

        this.renderDebug(posestack, multibuffersource$buffersource, camera);
        multibuffersource$buffersource.endLastBatch();
        matrix4fstack.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        FogRenderer.setupNoFog();
    }

    /**
     * Asserts that the specified {@code poseStack} is {@linkplain com.mojang.blaze3d.vertex.PoseStack#clear() clear}.
     * @throws java.lang.IllegalStateException if the specified {@code poseStack} is not clear
     */
    private void checkPoseStack(PoseStack poseStack) {
        if (!poseStack.clear()) {
            throw new IllegalStateException("Pose stack not empty");
        }
    }

    private void renderEntity(
        Entity entity, double camX, double camY, double camZ, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource
    ) {
        double d0 = Mth.lerp((double)partialTick, entity.xOld, entity.getX());
        double d1 = Mth.lerp((double)partialTick, entity.yOld, entity.getY());
        double d2 = Mth.lerp((double)partialTick, entity.zOld, entity.getZ());
        float f = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        this.entityRenderDispatcher
            .render(
                entity,
                d0 - camX,
                d1 - camY,
                d2 - camZ,
                f,
                partialTick,
                poseStack,
                bufferSource,
                this.entityRenderDispatcher.getPackedLightCoords(entity, partialTick)
            );
    }

    private void renderSectionLayer(RenderType renderType, double x, double y, double z, Matrix4f frustrumMatrix, Matrix4f projectionMatrix) {
        RenderSystem.assertOnRenderThread();
        renderType.setupRenderState();
        if (renderType == RenderType.translucent()) {
            this.minecraft.getProfiler().push("translucent_sort");
            double d0 = x - this.xTransparentOld;
            double d1 = y - this.yTransparentOld;
            double d2 = z - this.zTransparentOld;
            if (d0 * d0 + d1 * d1 + d2 * d2 > 1.0) {
                int i = SectionPos.posToSectionCoord(x);
                int j = SectionPos.posToSectionCoord(y);
                int k = SectionPos.posToSectionCoord(z);
                boolean flag = i != SectionPos.posToSectionCoord(this.xTransparentOld)
                    || k != SectionPos.posToSectionCoord(this.zTransparentOld)
                    || j != SectionPos.posToSectionCoord(this.yTransparentOld);
                this.xTransparentOld = x;
                this.yTransparentOld = y;
                this.zTransparentOld = z;
                int l = 0;

                for (SectionRenderDispatcher.RenderSection sectionrenderdispatcher$rendersection : this.visibleSections) {
                    if (l < 15
                        && (flag || sectionrenderdispatcher$rendersection.isAxisAlignedWith(i, j, k))
                        && sectionrenderdispatcher$rendersection.resortTransparency(renderType, this.sectionRenderDispatcher)) {
                        l++;
                    }
                }
            }

            this.minecraft.getProfiler().pop();
        }

        this.minecraft.getProfiler().push("filterempty");
        this.minecraft.getProfiler().popPush(() -> "render_" + renderType);
        boolean flag1 = renderType != RenderType.translucent();
        ObjectListIterator<SectionRenderDispatcher.RenderSection> objectlistiterator = this.visibleSections
            .listIterator(flag1 ? 0 : this.visibleSections.size());
        ShaderInstance shaderinstance = RenderSystem.getShader();
        shaderinstance.setDefaultUniforms(VertexFormat.Mode.QUADS, frustrumMatrix, projectionMatrix, this.minecraft.getWindow());
        shaderinstance.apply();
        Uniform uniform = shaderinstance.CHUNK_OFFSET;

        while (flag1 ? objectlistiterator.hasNext() : objectlistiterator.hasPrevious()) {
            SectionRenderDispatcher.RenderSection sectionrenderdispatcher$rendersection1 = flag1 ? objectlistiterator.next() : objectlistiterator.previous();
            if (!sectionrenderdispatcher$rendersection1.getCompiled().isEmpty(renderType)) {
                VertexBuffer vertexbuffer = sectionrenderdispatcher$rendersection1.getBuffer(renderType);
                BlockPos blockpos = sectionrenderdispatcher$rendersection1.getOrigin();
                if (uniform != null) {
                    uniform.set(
                        (float)((double)blockpos.getX() - x),
                        (float)((double)blockpos.getY() - y),
                        (float)((double)blockpos.getZ() - z)
                    );
                    uniform.upload();
                }

                vertexbuffer.bind();
                vertexbuffer.draw();
            }
        }

        if (uniform != null) {
            uniform.set(0.0F, 0.0F, 0.0F);
        }

        shaderinstance.clear();
        VertexBuffer.unbind();
        this.minecraft.getProfiler().pop();
        net.neoforged.neoforge.client.ClientHooks.dispatchRenderStage(renderType, this, frustrumMatrix, projectionMatrix, this.ticks, this.minecraft.gameRenderer.getMainCamera(), this.getFrustum());
        renderType.clearRenderState();
    }

    private void renderDebug(PoseStack poseStack, MultiBufferSource buffer, Camera camera) {
        if (this.minecraft.sectionPath || this.minecraft.sectionVisibility) {
            double d0 = camera.getPosition().x();
            double d1 = camera.getPosition().y();
            double d2 = camera.getPosition().z();

            for (SectionRenderDispatcher.RenderSection sectionrenderdispatcher$rendersection : this.visibleSections) {
                SectionOcclusionGraph.Node sectionocclusiongraph$node = this.sectionOcclusionGraph.getNode(sectionrenderdispatcher$rendersection);
                if (sectionocclusiongraph$node != null) {
                    BlockPos blockpos = sectionrenderdispatcher$rendersection.getOrigin();
                    poseStack.pushPose();
                    poseStack.translate((double)blockpos.getX() - d0, (double)blockpos.getY() - d1, (double)blockpos.getZ() - d2);
                    Matrix4f matrix4f = poseStack.last().pose();
                    if (this.minecraft.sectionPath) {
                        VertexConsumer vertexconsumer1 = buffer.getBuffer(RenderType.lines());
                        int i = sectionocclusiongraph$node.step == 0 ? 0 : Mth.hsvToRgb((float)sectionocclusiongraph$node.step / 50.0F, 0.9F, 0.9F);
                        int j = i >> 16 & 0xFF;
                        int k = i >> 8 & 0xFF;
                        int l = i & 0xFF;

                        for (int i1 = 0; i1 < DIRECTIONS.length; i1++) {
                            if (sectionocclusiongraph$node.hasSourceDirection(i1)) {
                                Direction direction = DIRECTIONS[i1];
                                vertexconsumer1.addVertex(matrix4f, 8.0F, 8.0F, 8.0F)
                                    .setColor(j, k, l, 255)
                                    .setNormal((float)direction.getStepX(), (float)direction.getStepY(), (float)direction.getStepZ());
                                vertexconsumer1.addVertex(
                                        matrix4f,
                                        (float)(8 - 16 * direction.getStepX()),
                                        (float)(8 - 16 * direction.getStepY()),
                                        (float)(8 - 16 * direction.getStepZ())
                                    )
                                    .setColor(j, k, l, 255)
                                    .setNormal((float)direction.getStepX(), (float)direction.getStepY(), (float)direction.getStepZ());
                            }
                        }
                    }

                    if (this.minecraft.sectionVisibility && !sectionrenderdispatcher$rendersection.getCompiled().hasNoRenderableLayers()) {
                        VertexConsumer vertexconsumer3 = buffer.getBuffer(RenderType.lines());
                        int j1 = 0;

                        for (Direction direction2 : DIRECTIONS) {
                            for (Direction direction1 : DIRECTIONS) {
                                boolean flag = sectionrenderdispatcher$rendersection.getCompiled().facesCanSeeEachother(direction2, direction1);
                                if (!flag) {
                                    j1++;
                                    vertexconsumer3.addVertex(
                                            matrix4f,
                                            (float)(8 + 8 * direction2.getStepX()),
                                            (float)(8 + 8 * direction2.getStepY()),
                                            (float)(8 + 8 * direction2.getStepZ())
                                        )
                                        .setColor(255, 0, 0, 255)
                                        .setNormal((float)direction2.getStepX(), (float)direction2.getStepY(), (float)direction2.getStepZ());
                                    vertexconsumer3.addVertex(
                                            matrix4f,
                                            (float)(8 + 8 * direction1.getStepX()),
                                            (float)(8 + 8 * direction1.getStepY()),
                                            (float)(8 + 8 * direction1.getStepZ())
                                        )
                                        .setColor(255, 0, 0, 255)
                                        .setNormal((float)direction1.getStepX(), (float)direction1.getStepY(), (float)direction1.getStepZ());
                                }
                            }
                        }

                        if (j1 > 0) {
                            VertexConsumer vertexconsumer4 = buffer.getBuffer(RenderType.debugQuads());
                            float f = 0.5F;
                            float f1 = 0.2F;
                            vertexconsumer4.addVertex(matrix4f, 0.5F, 15.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexconsumer4.addVertex(matrix4f, 15.5F, 15.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexconsumer4.addVertex(matrix4f, 15.5F, 15.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexconsumer4.addVertex(matrix4f, 0.5F, 15.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexconsumer4.addVertex(matrix4f, 0.5F, 0.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexconsumer4.addVertex(matrix4f, 15.5F, 0.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexconsumer4.addVertex(matrix4f, 15.5F, 0.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexconsumer4.addVertex(matrix4f, 0.5F, 0.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexconsumer4.addVertex(matrix4f, 0.5F, 15.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexconsumer4.addVertex(matrix4f, 0.5F, 15.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexconsumer4.addVertex(matrix4f, 0.5F, 0.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexconsumer4.addVertex(matrix4f, 0.5F, 0.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexconsumer4.addVertex(matrix4f, 15.5F, 0.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexconsumer4.addVertex(matrix4f, 15.5F, 0.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexconsumer4.addVertex(matrix4f, 15.5F, 15.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexconsumer4.addVertex(matrix4f, 15.5F, 15.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexconsumer4.addVertex(matrix4f, 0.5F, 0.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexconsumer4.addVertex(matrix4f, 15.5F, 0.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexconsumer4.addVertex(matrix4f, 15.5F, 15.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexconsumer4.addVertex(matrix4f, 0.5F, 15.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexconsumer4.addVertex(matrix4f, 0.5F, 15.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexconsumer4.addVertex(matrix4f, 15.5F, 15.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexconsumer4.addVertex(matrix4f, 15.5F, 0.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                            vertexconsumer4.addVertex(matrix4f, 0.5F, 0.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
                        }
                    }

                    poseStack.popPose();
                }
            }
        }

        if (this.capturedFrustum != null) {
            poseStack.pushPose();
            poseStack.translate(
                (float)(this.frustumPos.x - camera.getPosition().x),
                (float)(this.frustumPos.y - camera.getPosition().y),
                (float)(this.frustumPos.z - camera.getPosition().z)
            );
            Matrix4f matrix4f1 = poseStack.last().pose();
            VertexConsumer vertexconsumer = buffer.getBuffer(RenderType.debugQuads());
            this.addFrustumQuad(vertexconsumer, matrix4f1, 0, 1, 2, 3, 0, 1, 1);
            this.addFrustumQuad(vertexconsumer, matrix4f1, 4, 5, 6, 7, 1, 0, 0);
            this.addFrustumQuad(vertexconsumer, matrix4f1, 0, 1, 5, 4, 1, 1, 0);
            this.addFrustumQuad(vertexconsumer, matrix4f1, 2, 3, 7, 6, 0, 0, 1);
            this.addFrustumQuad(vertexconsumer, matrix4f1, 0, 4, 7, 3, 0, 1, 0);
            this.addFrustumQuad(vertexconsumer, matrix4f1, 1, 5, 6, 2, 1, 0, 1);
            VertexConsumer vertexconsumer2 = buffer.getBuffer(RenderType.lines());
            this.addFrustumVertex(vertexconsumer2, matrix4f1, 0);
            this.addFrustumVertex(vertexconsumer2, matrix4f1, 1);
            this.addFrustumVertex(vertexconsumer2, matrix4f1, 1);
            this.addFrustumVertex(vertexconsumer2, matrix4f1, 2);
            this.addFrustumVertex(vertexconsumer2, matrix4f1, 2);
            this.addFrustumVertex(vertexconsumer2, matrix4f1, 3);
            this.addFrustumVertex(vertexconsumer2, matrix4f1, 3);
            this.addFrustumVertex(vertexconsumer2, matrix4f1, 0);
            this.addFrustumVertex(vertexconsumer2, matrix4f1, 4);
            this.addFrustumVertex(vertexconsumer2, matrix4f1, 5);
            this.addFrustumVertex(vertexconsumer2, matrix4f1, 5);
            this.addFrustumVertex(vertexconsumer2, matrix4f1, 6);
            this.addFrustumVertex(vertexconsumer2, matrix4f1, 6);
            this.addFrustumVertex(vertexconsumer2, matrix4f1, 7);
            this.addFrustumVertex(vertexconsumer2, matrix4f1, 7);
            this.addFrustumVertex(vertexconsumer2, matrix4f1, 4);
            this.addFrustumVertex(vertexconsumer2, matrix4f1, 0);
            this.addFrustumVertex(vertexconsumer2, matrix4f1, 4);
            this.addFrustumVertex(vertexconsumer2, matrix4f1, 1);
            this.addFrustumVertex(vertexconsumer2, matrix4f1, 5);
            this.addFrustumVertex(vertexconsumer2, matrix4f1, 2);
            this.addFrustumVertex(vertexconsumer2, matrix4f1, 6);
            this.addFrustumVertex(vertexconsumer2, matrix4f1, 3);
            this.addFrustumVertex(vertexconsumer2, matrix4f1, 7);
            poseStack.popPose();
        }
    }

    private void addFrustumVertex(VertexConsumer consumer, Matrix4f matrix, int vertexIndex) {
        consumer.addVertex(matrix, this.frustumPoints[vertexIndex].x(), this.frustumPoints[vertexIndex].y(), this.frustumPoints[vertexIndex].z())
            .setColor(-16777216)
            .setNormal(0.0F, 0.0F, -1.0F);
    }

    private void addFrustumQuad(
        VertexConsumer consumer, Matrix4f matrix, int index1, int index2, int index3, int index4, int red, int green, int blue
    ) {
        float f = 0.25F;
        consumer.addVertex(matrix, this.frustumPoints[index1].x(), this.frustumPoints[index1].y(), this.frustumPoints[index1].z())
            .setColor((float)red, (float)green, (float)blue, 0.25F);
        consumer.addVertex(matrix, this.frustumPoints[index2].x(), this.frustumPoints[index2].y(), this.frustumPoints[index2].z())
            .setColor((float)red, (float)green, (float)blue, 0.25F);
        consumer.addVertex(matrix, this.frustumPoints[index3].x(), this.frustumPoints[index3].y(), this.frustumPoints[index3].z())
            .setColor((float)red, (float)green, (float)blue, 0.25F);
        consumer.addVertex(matrix, this.frustumPoints[index4].x(), this.frustumPoints[index4].y(), this.frustumPoints[index4].z())
            .setColor((float)red, (float)green, (float)blue, 0.25F);
    }

    public void captureFrustum() {
        this.captureFrustum = true;
    }

    public void killFrustum() {
        this.capturedFrustum = null;
    }

    public void tick() {
        if (this.level.tickRateManager().runsNormally()) {
            this.ticks++;
        }

        if (this.ticks % 20 == 0) {
            Iterator<BlockDestructionProgress> iterator = this.destroyingBlocks.values().iterator();

            while (iterator.hasNext()) {
                BlockDestructionProgress blockdestructionprogress = iterator.next();
                int i = blockdestructionprogress.getUpdatedRenderTick();
                if (this.ticks - i > 400) {
                    iterator.remove();
                    this.removeProgress(blockdestructionprogress);
                }
            }
        }
    }

    private void removeProgress(BlockDestructionProgress progress) {
        long i = progress.getPos().asLong();
        Set<BlockDestructionProgress> set = this.destructionProgress.get(i);
        set.remove(progress);
        if (set.isEmpty()) {
            this.destructionProgress.remove(i);
        }
    }

    private void renderEndSky(PoseStack poseStack) {
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, END_SKY_LOCATION);
        Tesselator tesselator = Tesselator.getInstance();

        for (int i = 0; i < 6; i++) {
            poseStack.pushPose();
            if (i == 1) {
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            }

            if (i == 2) {
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            }

            if (i == 3) {
                poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
            }

            if (i == 4) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
            }

            if (i == 5) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(-90.0F));
            }

            Matrix4f matrix4f = poseStack.last().pose();
            BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            bufferbuilder.addVertex(matrix4f, -100.0F, -100.0F, -100.0F).setUv(0.0F, 0.0F).setColor(-14145496);
            bufferbuilder.addVertex(matrix4f, -100.0F, -100.0F, 100.0F).setUv(0.0F, 16.0F).setColor(-14145496);
            bufferbuilder.addVertex(matrix4f, 100.0F, -100.0F, 100.0F).setUv(16.0F, 16.0F).setColor(-14145496);
            bufferbuilder.addVertex(matrix4f, 100.0F, -100.0F, -100.0F).setUv(16.0F, 0.0F).setColor(-14145496);
            BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
            poseStack.popPose();
        }

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    public void renderSky(Matrix4f frustumMatrix, Matrix4f projectionMatrix, float partialTick, Camera camera, boolean isFoggy, Runnable skyFogSetup) {
        if (level.effects().renderSky(level, ticks, partialTick, frustumMatrix, camera, projectionMatrix, isFoggy, skyFogSetup))
            return;
        skyFogSetup.run();
        if (!isFoggy) {
            FogType fogtype = camera.getFluidInCamera();
            if (fogtype != FogType.POWDER_SNOW && fogtype != FogType.LAVA && !this.doesMobEffectBlockSky(camera)) {
                PoseStack posestack = new PoseStack();
                posestack.mulPose(frustumMatrix);
                if (this.minecraft.level.effects().skyType() == DimensionSpecialEffects.SkyType.END) {
                    this.renderEndSky(posestack);
                } else if (this.minecraft.level.effects().skyType() == DimensionSpecialEffects.SkyType.NORMAL) {
                    Vec3 vec3 = this.level.getSkyColor(this.minecraft.gameRenderer.getMainCamera().getPosition(), partialTick);
                    float f = (float)vec3.x;
                    float f1 = (float)vec3.y;
                    float f2 = (float)vec3.z;
                    FogRenderer.levelFogColor();
                    Tesselator tesselator = Tesselator.getInstance();
                    RenderSystem.depthMask(false);
                    RenderSystem.setShaderColor(f, f1, f2, 1.0F);
                    ShaderInstance shaderinstance = RenderSystem.getShader();
                    this.skyBuffer.bind();
                    this.skyBuffer.drawWithShader(posestack.last().pose(), projectionMatrix, shaderinstance);
                    VertexBuffer.unbind();
                    RenderSystem.enableBlend();
                    float[] afloat = this.level.effects().getSunriseColor(this.level.getTimeOfDay(partialTick), partialTick);
                    if (afloat != null) {
                        RenderSystem.setShader(GameRenderer::getPositionColorShader);
                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                        posestack.pushPose();
                        posestack.mulPose(Axis.XP.rotationDegrees(90.0F));
                        float f3 = Mth.sin(this.level.getSunAngle(partialTick)) < 0.0F ? 180.0F : 0.0F;
                        posestack.mulPose(Axis.ZP.rotationDegrees(f3));
                        posestack.mulPose(Axis.ZP.rotationDegrees(90.0F));
                        float f4 = afloat[0];
                        float f5 = afloat[1];
                        float f6 = afloat[2];
                        Matrix4f matrix4f = posestack.last().pose();
                        BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
                        bufferbuilder.addVertex(matrix4f, 0.0F, 100.0F, 0.0F).setColor(f4, f5, f6, afloat[3]);
                        int i = 16;

                        for (int j = 0; j <= 16; j++) {
                            float f7 = (float)j * (float) (Math.PI * 2) / 16.0F;
                            float f8 = Mth.sin(f7);
                            float f9 = Mth.cos(f7);
                            bufferbuilder.addVertex(matrix4f, f8 * 120.0F, f9 * 120.0F, -f9 * 40.0F * afloat[3])
                                .setColor(afloat[0], afloat[1], afloat[2], 0.0F);
                        }

                        BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
                        posestack.popPose();
                    }

                    RenderSystem.blendFuncSeparate(
                        GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
                    );
                    posestack.pushPose();
                    float f11 = 1.0F - this.level.getRainLevel(partialTick);
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, f11);
                    posestack.mulPose(Axis.YP.rotationDegrees(-90.0F));
                    posestack.mulPose(Axis.XP.rotationDegrees(this.level.getTimeOfDay(partialTick) * 360.0F));
                    Matrix4f matrix4f1 = posestack.last().pose();
                    float f12 = 30.0F;
                    RenderSystem.setShader(GameRenderer::getPositionTexShader);
                    RenderSystem.setShaderTexture(0, SUN_LOCATION);
                    BufferBuilder bufferbuilder1 = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                    bufferbuilder1.addVertex(matrix4f1, -f12, 100.0F, -f12).setUv(0.0F, 0.0F);
                    bufferbuilder1.addVertex(matrix4f1, f12, 100.0F, -f12).setUv(1.0F, 0.0F);
                    bufferbuilder1.addVertex(matrix4f1, f12, 100.0F, f12).setUv(1.0F, 1.0F);
                    bufferbuilder1.addVertex(matrix4f1, -f12, 100.0F, f12).setUv(0.0F, 1.0F);
                    BufferUploader.drawWithShader(bufferbuilder1.buildOrThrow());
                    f12 = 20.0F;
                    RenderSystem.setShaderTexture(0, MOON_LOCATION);
                    int k = this.level.getMoonPhase();
                    int l = k % 4;
                    int i1 = k / 4 % 2;
                    float f13 = (float)(l + 0) / 4.0F;
                    float f14 = (float)(i1 + 0) / 2.0F;
                    float f15 = (float)(l + 1) / 4.0F;
                    float f16 = (float)(i1 + 1) / 2.0F;
                    bufferbuilder1 = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                    bufferbuilder1.addVertex(matrix4f1, -f12, -100.0F, f12).setUv(f15, f16);
                    bufferbuilder1.addVertex(matrix4f1, f12, -100.0F, f12).setUv(f13, f16);
                    bufferbuilder1.addVertex(matrix4f1, f12, -100.0F, -f12).setUv(f13, f14);
                    bufferbuilder1.addVertex(matrix4f1, -f12, -100.0F, -f12).setUv(f15, f14);
                    BufferUploader.drawWithShader(bufferbuilder1.buildOrThrow());
                    float f10 = this.level.getStarBrightness(partialTick) * f11;
                    if (f10 > 0.0F) {
                        RenderSystem.setShaderColor(f10, f10, f10, f10);
                        FogRenderer.setupNoFog();
                        this.starBuffer.bind();
                        this.starBuffer.drawWithShader(posestack.last().pose(), projectionMatrix, GameRenderer.getPositionShader());
                        VertexBuffer.unbind();
                        skyFogSetup.run();
                    }

                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                    RenderSystem.disableBlend();
                    RenderSystem.defaultBlendFunc();
                    posestack.popPose();
                    RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, 1.0F);
                    double d0 = this.minecraft.player.getEyePosition(partialTick).y - this.level.getLevelData().getHorizonHeight(this.level);
                    if (d0 < 0.0) {
                        posestack.pushPose();
                        posestack.translate(0.0F, 12.0F, 0.0F);
                        this.darkBuffer.bind();
                        this.darkBuffer.drawWithShader(posestack.last().pose(), projectionMatrix, shaderinstance);
                        VertexBuffer.unbind();
                        posestack.popPose();
                    }

                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                    RenderSystem.depthMask(true);
                }
            }
        }
    }

    private boolean doesMobEffectBlockSky(Camera camera) {
        return !(camera.getEntity() instanceof LivingEntity livingentity)
            ? false
            : livingentity.hasEffect(MobEffects.BLINDNESS) || livingentity.hasEffect(MobEffects.DARKNESS);
    }

    public void renderClouds(PoseStack poseStack, Matrix4f frustumMatrix, Matrix4f projectionMatrix, float partialTick, double camX, double camY, double camZ) {
        if (level.effects().renderClouds(level, ticks, partialTick, poseStack, camX, camY, camZ, frustumMatrix, projectionMatrix))
            return;
        float f = this.level.effects().getCloudHeight();
        if (!Float.isNaN(f)) {
            float f1 = 12.0F;
            float f2 = 4.0F;
            double d0 = 2.0E-4;
            double d1 = (double)(((float)this.ticks + partialTick) * 0.03F);
            double d2 = (camX + d1) / 12.0;
            double d3 = (double)(f - (float)camY + 0.33F);
            double d4 = camZ / 12.0 + 0.33F;
            d2 -= (double)(Mth.floor(d2 / 2048.0) * 2048);
            d4 -= (double)(Mth.floor(d4 / 2048.0) * 2048);
            float f3 = (float)(d2 - (double)Mth.floor(d2));
            float f4 = (float)(d3 / 4.0 - (double)Mth.floor(d3 / 4.0)) * 4.0F;
            float f5 = (float)(d4 - (double)Mth.floor(d4));
            Vec3 vec3 = this.level.getCloudColor(partialTick);
            int i = (int)Math.floor(d2);
            int j = (int)Math.floor(d3 / 4.0);
            int k = (int)Math.floor(d4);
            if (i != this.prevCloudX
                || j != this.prevCloudY
                || k != this.prevCloudZ
                || this.minecraft.options.getCloudsType() != this.prevCloudsType
                || this.prevCloudColor.distanceToSqr(vec3) > 2.0E-4) {
                this.prevCloudX = i;
                this.prevCloudY = j;
                this.prevCloudZ = k;
                this.prevCloudColor = vec3;
                this.prevCloudsType = this.minecraft.options.getCloudsType();
                this.generateClouds = true;
            }

            if (this.generateClouds) {
                this.generateClouds = false;
                if (this.cloudBuffer != null) {
                    this.cloudBuffer.close();
                }

                this.cloudBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                this.cloudBuffer.bind();
                this.cloudBuffer.upload(this.buildClouds(Tesselator.getInstance(), d2, d3, d4, vec3));
                VertexBuffer.unbind();
            }

            FogRenderer.levelFogColor();
            poseStack.pushPose();
            poseStack.mulPose(frustumMatrix);
            poseStack.scale(12.0F, 1.0F, 12.0F);
            poseStack.translate(-f3, f4, -f5);
            if (this.cloudBuffer != null) {
                this.cloudBuffer.bind();
                int l = this.prevCloudsType == CloudStatus.FANCY ? 0 : 1;

                for (int i1 = l; i1 < 2; i1++) {
                    RenderType rendertype = i1 == 0 ? RenderType.cloudsDepthOnly() : RenderType.clouds();
                    rendertype.setupRenderState();
                    ShaderInstance shaderinstance = RenderSystem.getShader();
                    this.cloudBuffer.drawWithShader(poseStack.last().pose(), projectionMatrix, shaderinstance);
                    rendertype.clearRenderState();
                }

                VertexBuffer.unbind();
            }

            poseStack.popPose();
        }
    }

    private MeshData buildClouds(Tesselator tesselator, double x, double y, double z, Vec3 cloudColor) {
        float f = 4.0F;
        float f1 = 0.00390625F;
        int i = 8;
        int j = 4;
        float f2 = 9.765625E-4F;
        float f3 = (float)Mth.floor(x) * 0.00390625F;
        float f4 = (float)Mth.floor(z) * 0.00390625F;
        float f5 = (float)cloudColor.x;
        float f6 = (float)cloudColor.y;
        float f7 = (float)cloudColor.z;
        float f8 = f5 * 0.9F;
        float f9 = f6 * 0.9F;
        float f10 = f7 * 0.9F;
        float f11 = f5 * 0.7F;
        float f12 = f6 * 0.7F;
        float f13 = f7 * 0.7F;
        float f14 = f5 * 0.8F;
        float f15 = f6 * 0.8F;
        float f16 = f7 * 0.8F;
        BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        float f17 = (float)Math.floor(y / 4.0) * 4.0F;
        if (this.prevCloudsType == CloudStatus.FANCY) {
            for (int k = -3; k <= 4; k++) {
                for (int l = -3; l <= 4; l++) {
                    float f18 = (float)(k * 8);
                    float f19 = (float)(l * 8);
                    if (f17 > -5.0F) {
                        bufferbuilder.addVertex(f18 + 0.0F, f17 + 0.0F, f19 + 8.0F)
                            .setUv((f18 + 0.0F) * 0.00390625F + f3, (f19 + 8.0F) * 0.00390625F + f4)
                            .setColor(f11, f12, f13, 0.8F)
                            .setNormal(0.0F, -1.0F, 0.0F);
                        bufferbuilder.addVertex(f18 + 8.0F, f17 + 0.0F, f19 + 8.0F)
                            .setUv((f18 + 8.0F) * 0.00390625F + f3, (f19 + 8.0F) * 0.00390625F + f4)
                            .setColor(f11, f12, f13, 0.8F)
                            .setNormal(0.0F, -1.0F, 0.0F);
                        bufferbuilder.addVertex(f18 + 8.0F, f17 + 0.0F, f19 + 0.0F)
                            .setUv((f18 + 8.0F) * 0.00390625F + f3, (f19 + 0.0F) * 0.00390625F + f4)
                            .setColor(f11, f12, f13, 0.8F)
                            .setNormal(0.0F, -1.0F, 0.0F);
                        bufferbuilder.addVertex(f18 + 0.0F, f17 + 0.0F, f19 + 0.0F)
                            .setUv((f18 + 0.0F) * 0.00390625F + f3, (f19 + 0.0F) * 0.00390625F + f4)
                            .setColor(f11, f12, f13, 0.8F)
                            .setNormal(0.0F, -1.0F, 0.0F);
                    }

                    if (f17 <= 5.0F) {
                        bufferbuilder.addVertex(f18 + 0.0F, f17 + 4.0F - 9.765625E-4F, f19 + 8.0F)
                            .setUv((f18 + 0.0F) * 0.00390625F + f3, (f19 + 8.0F) * 0.00390625F + f4)
                            .setColor(f5, f6, f7, 0.8F)
                            .setNormal(0.0F, 1.0F, 0.0F);
                        bufferbuilder.addVertex(f18 + 8.0F, f17 + 4.0F - 9.765625E-4F, f19 + 8.0F)
                            .setUv((f18 + 8.0F) * 0.00390625F + f3, (f19 + 8.0F) * 0.00390625F + f4)
                            .setColor(f5, f6, f7, 0.8F)
                            .setNormal(0.0F, 1.0F, 0.0F);
                        bufferbuilder.addVertex(f18 + 8.0F, f17 + 4.0F - 9.765625E-4F, f19 + 0.0F)
                            .setUv((f18 + 8.0F) * 0.00390625F + f3, (f19 + 0.0F) * 0.00390625F + f4)
                            .setColor(f5, f6, f7, 0.8F)
                            .setNormal(0.0F, 1.0F, 0.0F);
                        bufferbuilder.addVertex(f18 + 0.0F, f17 + 4.0F - 9.765625E-4F, f19 + 0.0F)
                            .setUv((f18 + 0.0F) * 0.00390625F + f3, (f19 + 0.0F) * 0.00390625F + f4)
                            .setColor(f5, f6, f7, 0.8F)
                            .setNormal(0.0F, 1.0F, 0.0F);
                    }

                    if (k > -1) {
                        for (int i1 = 0; i1 < 8; i1++) {
                            bufferbuilder.addVertex(f18 + (float)i1 + 0.0F, f17 + 0.0F, f19 + 8.0F)
                                .setUv((f18 + (float)i1 + 0.5F) * 0.00390625F + f3, (f19 + 8.0F) * 0.00390625F + f4)
                                .setColor(f8, f9, f10, 0.8F)
                                .setNormal(-1.0F, 0.0F, 0.0F);
                            bufferbuilder.addVertex(f18 + (float)i1 + 0.0F, f17 + 4.0F, f19 + 8.0F)
                                .setUv((f18 + (float)i1 + 0.5F) * 0.00390625F + f3, (f19 + 8.0F) * 0.00390625F + f4)
                                .setColor(f8, f9, f10, 0.8F)
                                .setNormal(-1.0F, 0.0F, 0.0F);
                            bufferbuilder.addVertex(f18 + (float)i1 + 0.0F, f17 + 4.0F, f19 + 0.0F)
                                .setUv((f18 + (float)i1 + 0.5F) * 0.00390625F + f3, (f19 + 0.0F) * 0.00390625F + f4)
                                .setColor(f8, f9, f10, 0.8F)
                                .setNormal(-1.0F, 0.0F, 0.0F);
                            bufferbuilder.addVertex(f18 + (float)i1 + 0.0F, f17 + 0.0F, f19 + 0.0F)
                                .setUv((f18 + (float)i1 + 0.5F) * 0.00390625F + f3, (f19 + 0.0F) * 0.00390625F + f4)
                                .setColor(f8, f9, f10, 0.8F)
                                .setNormal(-1.0F, 0.0F, 0.0F);
                        }
                    }

                    if (k <= 1) {
                        for (int j2 = 0; j2 < 8; j2++) {
                            bufferbuilder.addVertex(f18 + (float)j2 + 1.0F - 9.765625E-4F, f17 + 0.0F, f19 + 8.0F)
                                .setUv((f18 + (float)j2 + 0.5F) * 0.00390625F + f3, (f19 + 8.0F) * 0.00390625F + f4)
                                .setColor(f8, f9, f10, 0.8F)
                                .setNormal(1.0F, 0.0F, 0.0F);
                            bufferbuilder.addVertex(f18 + (float)j2 + 1.0F - 9.765625E-4F, f17 + 4.0F, f19 + 8.0F)
                                .setUv((f18 + (float)j2 + 0.5F) * 0.00390625F + f3, (f19 + 8.0F) * 0.00390625F + f4)
                                .setColor(f8, f9, f10, 0.8F)
                                .setNormal(1.0F, 0.0F, 0.0F);
                            bufferbuilder.addVertex(f18 + (float)j2 + 1.0F - 9.765625E-4F, f17 + 4.0F, f19 + 0.0F)
                                .setUv((f18 + (float)j2 + 0.5F) * 0.00390625F + f3, (f19 + 0.0F) * 0.00390625F + f4)
                                .setColor(f8, f9, f10, 0.8F)
                                .setNormal(1.0F, 0.0F, 0.0F);
                            bufferbuilder.addVertex(f18 + (float)j2 + 1.0F - 9.765625E-4F, f17 + 0.0F, f19 + 0.0F)
                                .setUv((f18 + (float)j2 + 0.5F) * 0.00390625F + f3, (f19 + 0.0F) * 0.00390625F + f4)
                                .setColor(f8, f9, f10, 0.8F)
                                .setNormal(1.0F, 0.0F, 0.0F);
                        }
                    }

                    if (l > -1) {
                        for (int k2 = 0; k2 < 8; k2++) {
                            bufferbuilder.addVertex(f18 + 0.0F, f17 + 4.0F, f19 + (float)k2 + 0.0F)
                                .setUv((f18 + 0.0F) * 0.00390625F + f3, (f19 + (float)k2 + 0.5F) * 0.00390625F + f4)
                                .setColor(f14, f15, f16, 0.8F)
                                .setNormal(0.0F, 0.0F, -1.0F);
                            bufferbuilder.addVertex(f18 + 8.0F, f17 + 4.0F, f19 + (float)k2 + 0.0F)
                                .setUv((f18 + 8.0F) * 0.00390625F + f3, (f19 + (float)k2 + 0.5F) * 0.00390625F + f4)
                                .setColor(f14, f15, f16, 0.8F)
                                .setNormal(0.0F, 0.0F, -1.0F);
                            bufferbuilder.addVertex(f18 + 8.0F, f17 + 0.0F, f19 + (float)k2 + 0.0F)
                                .setUv((f18 + 8.0F) * 0.00390625F + f3, (f19 + (float)k2 + 0.5F) * 0.00390625F + f4)
                                .setColor(f14, f15, f16, 0.8F)
                                .setNormal(0.0F, 0.0F, -1.0F);
                            bufferbuilder.addVertex(f18 + 0.0F, f17 + 0.0F, f19 + (float)k2 + 0.0F)
                                .setUv((f18 + 0.0F) * 0.00390625F + f3, (f19 + (float)k2 + 0.5F) * 0.00390625F + f4)
                                .setColor(f14, f15, f16, 0.8F)
                                .setNormal(0.0F, 0.0F, -1.0F);
                        }
                    }

                    if (l <= 1) {
                        for (int l2 = 0; l2 < 8; l2++) {
                            bufferbuilder.addVertex(f18 + 0.0F, f17 + 4.0F, f19 + (float)l2 + 1.0F - 9.765625E-4F)
                                .setUv((f18 + 0.0F) * 0.00390625F + f3, (f19 + (float)l2 + 0.5F) * 0.00390625F + f4)
                                .setColor(f14, f15, f16, 0.8F)
                                .setNormal(0.0F, 0.0F, 1.0F);
                            bufferbuilder.addVertex(f18 + 8.0F, f17 + 4.0F, f19 + (float)l2 + 1.0F - 9.765625E-4F)
                                .setUv((f18 + 8.0F) * 0.00390625F + f3, (f19 + (float)l2 + 0.5F) * 0.00390625F + f4)
                                .setColor(f14, f15, f16, 0.8F)
                                .setNormal(0.0F, 0.0F, 1.0F);
                            bufferbuilder.addVertex(f18 + 8.0F, f17 + 0.0F, f19 + (float)l2 + 1.0F - 9.765625E-4F)
                                .setUv((f18 + 8.0F) * 0.00390625F + f3, (f19 + (float)l2 + 0.5F) * 0.00390625F + f4)
                                .setColor(f14, f15, f16, 0.8F)
                                .setNormal(0.0F, 0.0F, 1.0F);
                            bufferbuilder.addVertex(f18 + 0.0F, f17 + 0.0F, f19 + (float)l2 + 1.0F - 9.765625E-4F)
                                .setUv((f18 + 0.0F) * 0.00390625F + f3, (f19 + (float)l2 + 0.5F) * 0.00390625F + f4)
                                .setColor(f14, f15, f16, 0.8F)
                                .setNormal(0.0F, 0.0F, 1.0F);
                        }
                    }
                }
            }
        } else {
            int j1 = 1;
            int k1 = 32;

            for (int l1 = -32; l1 < 32; l1 += 32) {
                for (int i2 = -32; i2 < 32; i2 += 32) {
                    bufferbuilder.addVertex((float)(l1 + 0), f17, (float)(i2 + 32))
                        .setUv((float)(l1 + 0) * 0.00390625F + f3, (float)(i2 + 32) * 0.00390625F + f4)
                        .setColor(f5, f6, f7, 0.8F)
                        .setNormal(0.0F, -1.0F, 0.0F);
                    bufferbuilder.addVertex((float)(l1 + 32), f17, (float)(i2 + 32))
                        .setUv((float)(l1 + 32) * 0.00390625F + f3, (float)(i2 + 32) * 0.00390625F + f4)
                        .setColor(f5, f6, f7, 0.8F)
                        .setNormal(0.0F, -1.0F, 0.0F);
                    bufferbuilder.addVertex((float)(l1 + 32), f17, (float)(i2 + 0))
                        .setUv((float)(l1 + 32) * 0.00390625F + f3, (float)(i2 + 0) * 0.00390625F + f4)
                        .setColor(f5, f6, f7, 0.8F)
                        .setNormal(0.0F, -1.0F, 0.0F);
                    bufferbuilder.addVertex((float)(l1 + 0), f17, (float)(i2 + 0))
                        .setUv((float)(l1 + 0) * 0.00390625F + f3, (float)(i2 + 0) * 0.00390625F + f4)
                        .setColor(f5, f6, f7, 0.8F)
                        .setNormal(0.0F, -1.0F, 0.0F);
                }
            }
        }

        return bufferbuilder.buildOrThrow();
    }

    private void compileSections(Camera camera) {
        this.minecraft.getProfiler().push("populate_sections_to_compile");
        LevelLightEngine levellightengine = this.level.getLightEngine();
        RenderRegionCache renderregioncache = new RenderRegionCache();
        BlockPos blockpos = camera.getBlockPosition();
        List<SectionRenderDispatcher.RenderSection> list = Lists.newArrayList();

        for (SectionRenderDispatcher.RenderSection sectionrenderdispatcher$rendersection : this.visibleSections) {
            SectionPos sectionpos = SectionPos.of(sectionrenderdispatcher$rendersection.getOrigin());
            if (sectionrenderdispatcher$rendersection.isDirty() && levellightengine.lightOnInSection(sectionpos)) {
                boolean flag = false;
                if (this.minecraft.options.prioritizeChunkUpdates().get() == PrioritizeChunkUpdates.NEARBY) {
                    BlockPos blockpos1 = sectionrenderdispatcher$rendersection.getOrigin().offset(8, 8, 8);
                    flag = blockpos1.distSqr(blockpos) < 768.0 || sectionrenderdispatcher$rendersection.isDirtyFromPlayer();
                } else if (this.minecraft.options.prioritizeChunkUpdates().get() == PrioritizeChunkUpdates.PLAYER_AFFECTED) {
                    flag = sectionrenderdispatcher$rendersection.isDirtyFromPlayer();
                }

                if (flag) {
                    this.minecraft.getProfiler().push("build_near_sync");
                    this.sectionRenderDispatcher.rebuildSectionSync(sectionrenderdispatcher$rendersection, renderregioncache);
                    sectionrenderdispatcher$rendersection.setNotDirty();
                    this.minecraft.getProfiler().pop();
                } else {
                    list.add(sectionrenderdispatcher$rendersection);
                }
            }
        }

        this.minecraft.getProfiler().popPush("upload");
        this.sectionRenderDispatcher.uploadAllPendingUploads();
        this.minecraft.getProfiler().popPush("schedule_async_compile");

        for (SectionRenderDispatcher.RenderSection sectionrenderdispatcher$rendersection1 : list) {
            sectionrenderdispatcher$rendersection1.rebuildSectionAsync(this.sectionRenderDispatcher, renderregioncache);
            sectionrenderdispatcher$rendersection1.setNotDirty();
        }

        this.minecraft.getProfiler().pop();
    }

    private void renderWorldBorder(Camera camera) {
        WorldBorder worldborder = this.level.getWorldBorder();
        double d0 = (double)(this.minecraft.options.getEffectiveRenderDistance() * 16);
        if (!(camera.getPosition().x < worldborder.getMaxX() - d0)
            || !(camera.getPosition().x > worldborder.getMinX() + d0)
            || !(camera.getPosition().z < worldborder.getMaxZ() - d0)
            || !(camera.getPosition().z > worldborder.getMinZ() + d0)) {
            double d1 = 1.0 - worldborder.getDistanceToBorder(camera.getPosition().x, camera.getPosition().z) / d0;
            d1 = Math.pow(d1, 4.0);
            d1 = Mth.clamp(d1, 0.0, 1.0);
            double d2 = camera.getPosition().x;
            double d3 = camera.getPosition().z;
            double d4 = (double)this.minecraft.gameRenderer.getDepthFar();
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
            );
            RenderSystem.setShaderTexture(0, FORCEFIELD_LOCATION);
            RenderSystem.depthMask(Minecraft.useShaderTransparency());
            int i = worldborder.getStatus().getColor();
            float f = (float)(i >> 16 & 0xFF) / 255.0F;
            float f1 = (float)(i >> 8 & 0xFF) / 255.0F;
            float f2 = (float)(i & 0xFF) / 255.0F;
            RenderSystem.setShaderColor(f, f1, f2, (float)d1);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.polygonOffset(-3.0F, -3.0F);
            RenderSystem.enablePolygonOffset();
            RenderSystem.disableCull();
            float f3 = (float)(Util.getMillis() % 3000L) / 3000.0F;
            float f4 = (float)(-Mth.frac(camera.getPosition().y * 0.5));
            float f5 = f4 + (float)d4;
            BufferBuilder bufferbuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            double d5 = Math.max((double)Mth.floor(d3 - d0), worldborder.getMinZ());
            double d6 = Math.min((double)Mth.ceil(d3 + d0), worldborder.getMaxZ());
            float f6 = (float)(Mth.floor(d5) & 1) * 0.5F;
            if (d2 > worldborder.getMaxX() - d0) {
                float f7 = f6;

                for (double d7 = d5; d7 < d6; f7 += 0.5F) {
                    double d8 = Math.min(1.0, d6 - d7);
                    float f8 = (float)d8 * 0.5F;
                    bufferbuilder.addVertex((float)(worldborder.getMaxX() - d2), (float)(-d4), (float)(d7 - d3)).setUv(f3 - f7, f3 + f5);
                    bufferbuilder.addVertex((float)(worldborder.getMaxX() - d2), (float)(-d4), (float)(d7 + d8 - d3)).setUv(f3 - (f8 + f7), f3 + f5);
                    bufferbuilder.addVertex((float)(worldborder.getMaxX() - d2), (float)d4, (float)(d7 + d8 - d3)).setUv(f3 - (f8 + f7), f3 + f4);
                    bufferbuilder.addVertex((float)(worldborder.getMaxX() - d2), (float)d4, (float)(d7 - d3)).setUv(f3 - f7, f3 + f4);
                    d7++;
                }
            }

            if (d2 < worldborder.getMinX() + d0) {
                float f9 = f6;

                for (double d9 = d5; d9 < d6; f9 += 0.5F) {
                    double d12 = Math.min(1.0, d6 - d9);
                    float f12 = (float)d12 * 0.5F;
                    bufferbuilder.addVertex((float)(worldborder.getMinX() - d2), (float)(-d4), (float)(d9 - d3)).setUv(f3 + f9, f3 + f5);
                    bufferbuilder.addVertex((float)(worldborder.getMinX() - d2), (float)(-d4), (float)(d9 + d12 - d3)).setUv(f3 + f12 + f9, f3 + f5);
                    bufferbuilder.addVertex((float)(worldborder.getMinX() - d2), (float)d4, (float)(d9 + d12 - d3)).setUv(f3 + f12 + f9, f3 + f4);
                    bufferbuilder.addVertex((float)(worldborder.getMinX() - d2), (float)d4, (float)(d9 - d3)).setUv(f3 + f9, f3 + f4);
                    d9++;
                }
            }

            d5 = Math.max((double)Mth.floor(d2 - d0), worldborder.getMinX());
            d6 = Math.min((double)Mth.ceil(d2 + d0), worldborder.getMaxX());
            f6 = (float)(Mth.floor(d5) & 1) * 0.5F;
            if (d3 > worldborder.getMaxZ() - d0) {
                float f10 = f6;

                for (double d10 = d5; d10 < d6; f10 += 0.5F) {
                    double d13 = Math.min(1.0, d6 - d10);
                    float f13 = (float)d13 * 0.5F;
                    bufferbuilder.addVertex((float)(d10 - d2), (float)(-d4), (float)(worldborder.getMaxZ() - d3)).setUv(f3 + f10, f3 + f5);
                    bufferbuilder.addVertex((float)(d10 + d13 - d2), (float)(-d4), (float)(worldborder.getMaxZ() - d3)).setUv(f3 + f13 + f10, f3 + f5);
                    bufferbuilder.addVertex((float)(d10 + d13 - d2), (float)d4, (float)(worldborder.getMaxZ() - d3)).setUv(f3 + f13 + f10, f3 + f4);
                    bufferbuilder.addVertex((float)(d10 - d2), (float)d4, (float)(worldborder.getMaxZ() - d3)).setUv(f3 + f10, f3 + f4);
                    d10++;
                }
            }

            if (d3 < worldborder.getMinZ() + d0) {
                float f11 = f6;

                for (double d11 = d5; d11 < d6; f11 += 0.5F) {
                    double d14 = Math.min(1.0, d6 - d11);
                    float f14 = (float)d14 * 0.5F;
                    bufferbuilder.addVertex((float)(d11 - d2), (float)(-d4), (float)(worldborder.getMinZ() - d3)).setUv(f3 - f11, f3 + f5);
                    bufferbuilder.addVertex((float)(d11 + d14 - d2), (float)(-d4), (float)(worldborder.getMinZ() - d3)).setUv(f3 - (f14 + f11), f3 + f5);
                    bufferbuilder.addVertex((float)(d11 + d14 - d2), (float)d4, (float)(worldborder.getMinZ() - d3)).setUv(f3 - (f14 + f11), f3 + f4);
                    bufferbuilder.addVertex((float)(d11 - d2), (float)d4, (float)(worldborder.getMinZ() - d3)).setUv(f3 - f11, f3 + f4);
                    d11++;
                }
            }

            MeshData meshdata = bufferbuilder.build();
            if (meshdata != null) {
                BufferUploader.drawWithShader(meshdata);
            }

            RenderSystem.enableCull();
            RenderSystem.polygonOffset(0.0F, 0.0F);
            RenderSystem.disablePolygonOffset();
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.depthMask(true);
        }
    }

    private void renderHitOutline(
        PoseStack poseStack,
        VertexConsumer consumer,
        Entity entity,
        double camX,
        double camY,
        double camZ,
        BlockPos pos,
        BlockState state
    ) {
        renderShape(
            poseStack,
            consumer,
            state.getShape(this.level, pos, CollisionContext.of(entity)),
            (double)pos.getX() - camX,
            (double)pos.getY() - camY,
            (double)pos.getZ() - camZ,
            0.0F,
            0.0F,
            0.0F,
            0.4F
        );
    }

    private static Vec3 mixColor(float hue) {
        float f = 5.99999F;
        int i = (int)(Mth.clamp(hue, 0.0F, 1.0F) * 5.99999F);
        float f1 = hue * 5.99999F - (float)i;

        return switch (i) {
            case 0 -> new Vec3(1.0, (double)f1, 0.0);
            case 1 -> new Vec3((double)(1.0F - f1), 1.0, 0.0);
            case 2 -> new Vec3(0.0, 1.0, (double)f1);
            case 3 -> new Vec3(0.0, 1.0 - (double)f1, 1.0);
            case 4 -> new Vec3((double)f1, 0.0, 1.0);
            case 5 -> new Vec3(1.0, 0.0, 1.0 - (double)f1);
            default -> throw new IllegalStateException("Unexpected value: " + i);
        };
    }

    private static Vec3 shiftHue(float red, float green, float blue, float hue) {
        Vec3 vec3 = mixColor(hue).scale((double)red);
        Vec3 vec31 = mixColor((hue + 0.33333334F) % 1.0F).scale((double)green);
        Vec3 vec32 = mixColor((hue + 0.6666667F) % 1.0F).scale((double)blue);
        Vec3 vec33 = vec3.add(vec31).add(vec32);
        double d0 = Math.max(Math.max(1.0, vec33.x), Math.max(vec33.y, vec33.z));
        return new Vec3(vec33.x / d0, vec33.y / d0, vec33.z / d0);
    }

    public static void renderVoxelShape(
        PoseStack poseStack,
        VertexConsumer consumer,
        VoxelShape shape,
        double x,
        double y,
        double z,
        float red,
        float green,
        float blue,
        float alpha,
        boolean p_286443_
    ) {
        List<AABB> list = shape.toAabbs();
        if (!list.isEmpty()) {
            int i = p_286443_ ? list.size() : list.size() * 8;
            renderShape(poseStack, consumer, Shapes.create(list.get(0)), x, y, z, red, green, blue, alpha);

            for (int j = 1; j < list.size(); j++) {
                AABB aabb = list.get(j);
                float f = (float)j / (float)i;
                Vec3 vec3 = shiftHue(red, green, blue, f);
                renderShape(poseStack, consumer, Shapes.create(aabb), x, y, z, (float)vec3.x, (float)vec3.y, (float)vec3.z, alpha);
            }
        }
    }

    private static void renderShape(
        PoseStack poseStack,
        VertexConsumer consumer,
        VoxelShape shape,
        double x,
        double y,
        double z,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        PoseStack.Pose posestack$pose = poseStack.last();
        shape.forAllEdges(
            (p_323073_, p_323074_, p_323075_, p_323076_, p_323077_, p_323078_) -> {
                float f = (float)(p_323076_ - p_323073_);
                float f1 = (float)(p_323077_ - p_323074_);
                float f2 = (float)(p_323078_ - p_323075_);
                float f3 = Mth.sqrt(f * f + f1 * f1 + f2 * f2);
                f /= f3;
                f1 /= f3;
                f2 /= f3;
                consumer.addVertex(posestack$pose, (float)(p_323073_ + x), (float)(p_323074_ + y), (float)(p_323075_ + z))
                    .setColor(red, green, blue, alpha)
                    .setNormal(posestack$pose, f, f1, f2);
                consumer.addVertex(posestack$pose, (float)(p_323076_ + x), (float)(p_323077_ + y), (float)(p_323078_ + z))
                    .setColor(red, green, blue, alpha)
                    .setNormal(posestack$pose, f, f1, f2);
            }
        );
    }

    public static void renderLineBox(
        VertexConsumer consumer,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        renderLineBox(
            new PoseStack(),
            consumer,
            minX,
            minY,
            minZ,
            maxX,
            maxY,
            maxZ,
            red,
            green,
            blue,
            alpha,
            red,
            green,
            blue
        );
    }

    public static void renderLineBox(
        PoseStack poseStack, VertexConsumer buffer, AABB box, float red, float green, float blue, float alpha
    ) {
        renderLineBox(
            poseStack,
            buffer,
            box.minX,
            box.minY,
            box.minZ,
            box.maxX,
            box.maxY,
            box.maxZ,
            red,
            green,
            blue,
            alpha,
            red,
            green,
            blue
        );
    }

    public static void renderLineBox(
        PoseStack poseStack,
        VertexConsumer consumer,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        renderLineBox(
            poseStack,
            consumer,
            minX,
            minY,
            minZ,
            maxX,
            maxY,
            maxZ,
            red,
            green,
            blue,
            alpha,
            red,
            green,
            blue
        );
    }

    public static void renderLineBox(
        PoseStack poseStack,
        VertexConsumer consumer,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        float red,
        float green,
        float blue,
        float alpha,
        float red2,
        float green2,
        float blue2
    ) {
        PoseStack.Pose posestack$pose = poseStack.last();
        float f = (float)minX;
        float f1 = (float)minY;
        float f2 = (float)minZ;
        float f3 = (float)maxX;
        float f4 = (float)maxY;
        float f5 = (float)maxZ;
        consumer.addVertex(posestack$pose, f, f1, f2).setColor(red, green2, blue2, alpha).setNormal(posestack$pose, 1.0F, 0.0F, 0.0F);
        consumer.addVertex(posestack$pose, f3, f1, f2).setColor(red, green2, blue2, alpha).setNormal(posestack$pose, 1.0F, 0.0F, 0.0F);
        consumer.addVertex(posestack$pose, f, f1, f2).setColor(red2, green, blue2, alpha).setNormal(posestack$pose, 0.0F, 1.0F, 0.0F);
        consumer.addVertex(posestack$pose, f, f4, f2).setColor(red2, green, blue2, alpha).setNormal(posestack$pose, 0.0F, 1.0F, 0.0F);
        consumer.addVertex(posestack$pose, f, f1, f2).setColor(red2, green2, blue, alpha).setNormal(posestack$pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(posestack$pose, f, f1, f5).setColor(red2, green2, blue, alpha).setNormal(posestack$pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(posestack$pose, f3, f1, f2).setColor(red, green, blue, alpha).setNormal(posestack$pose, 0.0F, 1.0F, 0.0F);
        consumer.addVertex(posestack$pose, f3, f4, f2).setColor(red, green, blue, alpha).setNormal(posestack$pose, 0.0F, 1.0F, 0.0F);
        consumer.addVertex(posestack$pose, f3, f4, f2).setColor(red, green, blue, alpha).setNormal(posestack$pose, -1.0F, 0.0F, 0.0F);
        consumer.addVertex(posestack$pose, f, f4, f2).setColor(red, green, blue, alpha).setNormal(posestack$pose, -1.0F, 0.0F, 0.0F);
        consumer.addVertex(posestack$pose, f, f4, f2).setColor(red, green, blue, alpha).setNormal(posestack$pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(posestack$pose, f, f4, f5).setColor(red, green, blue, alpha).setNormal(posestack$pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(posestack$pose, f, f4, f5).setColor(red, green, blue, alpha).setNormal(posestack$pose, 0.0F, -1.0F, 0.0F);
        consumer.addVertex(posestack$pose, f, f1, f5).setColor(red, green, blue, alpha).setNormal(posestack$pose, 0.0F, -1.0F, 0.0F);
        consumer.addVertex(posestack$pose, f, f1, f5).setColor(red, green, blue, alpha).setNormal(posestack$pose, 1.0F, 0.0F, 0.0F);
        consumer.addVertex(posestack$pose, f3, f1, f5).setColor(red, green, blue, alpha).setNormal(posestack$pose, 1.0F, 0.0F, 0.0F);
        consumer.addVertex(posestack$pose, f3, f1, f5).setColor(red, green, blue, alpha).setNormal(posestack$pose, 0.0F, 0.0F, -1.0F);
        consumer.addVertex(posestack$pose, f3, f1, f2).setColor(red, green, blue, alpha).setNormal(posestack$pose, 0.0F, 0.0F, -1.0F);
        consumer.addVertex(posestack$pose, f, f4, f5).setColor(red, green, blue, alpha).setNormal(posestack$pose, 1.0F, 0.0F, 0.0F);
        consumer.addVertex(posestack$pose, f3, f4, f5).setColor(red, green, blue, alpha).setNormal(posestack$pose, 1.0F, 0.0F, 0.0F);
        consumer.addVertex(posestack$pose, f3, f1, f5).setColor(red, green, blue, alpha).setNormal(posestack$pose, 0.0F, 1.0F, 0.0F);
        consumer.addVertex(posestack$pose, f3, f4, f5).setColor(red, green, blue, alpha).setNormal(posestack$pose, 0.0F, 1.0F, 0.0F);
        consumer.addVertex(posestack$pose, f3, f4, f2).setColor(red, green, blue, alpha).setNormal(posestack$pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(posestack$pose, f3, f4, f5).setColor(red, green, blue, alpha).setNormal(posestack$pose, 0.0F, 0.0F, 1.0F);
    }

    public static void addChainedFilledBoxVertices(
        PoseStack poseStack,
        VertexConsumer consumer,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        addChainedFilledBoxVertices(
            poseStack,
            consumer,
            (float)minX,
            (float)minY,
            (float)minZ,
            (float)maxX,
            (float)maxY,
            (float)maxZ,
            red,
            green,
            blue,
            alpha
        );
    }

    public static void addChainedFilledBoxVertices(
        PoseStack poseStack,
        VertexConsumer consumer,
        float minX,
        float minY,
        float minZ,
        float maxX,
        float maxY,
        float maxZ,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        Matrix4f matrix4f = poseStack.last().pose();
        consumer.addVertex(matrix4f, minX, minY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, minX, minY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, minX, minY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, minX, minY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, minX, maxY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, minX, minY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, minY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, minY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, minX, maxY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, minX, minY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, minX, minY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, minY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, minX, minY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, minX, maxY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, minX, maxY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix4f, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
    }

    public static void renderFace(
        PoseStack poseStack,
        VertexConsumer buffer,
        Direction face,
        float x1,
        float y1,
        float z1,
        float x2,
        float y2,
        float z2,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        Matrix4f matrix4f = poseStack.last().pose();
        switch (face) {
            case DOWN:
                buffer.addVertex(matrix4f, x1, y1, z1).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x2, y1, z1).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x2, y1, z2).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x1, y1, z2).setColor(red, green, blue, alpha);
                break;
            case UP:
                buffer.addVertex(matrix4f, x1, y2, z1).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x1, y2, z2).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x2, y2, z2).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x2, y2, z1).setColor(red, green, blue, alpha);
                break;
            case NORTH:
                buffer.addVertex(matrix4f, x1, y1, z1).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x1, y2, z1).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x2, y2, z1).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x2, y1, z1).setColor(red, green, blue, alpha);
                break;
            case SOUTH:
                buffer.addVertex(matrix4f, x1, y1, z2).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x2, y1, z2).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x2, y2, z2).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x1, y2, z2).setColor(red, green, blue, alpha);
                break;
            case WEST:
                buffer.addVertex(matrix4f, x1, y1, z1).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x1, y1, z2).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x1, y2, z2).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x1, y2, z1).setColor(red, green, blue, alpha);
                break;
            case EAST:
                buffer.addVertex(matrix4f, x2, y1, z1).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x2, y2, z1).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x2, y2, z2).setColor(red, green, blue, alpha);
                buffer.addVertex(matrix4f, x2, y1, z2).setColor(red, green, blue, alpha);
        }
    }

    public void blockChanged(BlockGetter level, BlockPos pos, BlockState oldState, BlockState newState, int flags) {
        this.setBlockDirty(pos, (flags & 8) != 0);
    }

    private void setBlockDirty(BlockPos pos, boolean reRenderOnMainThread) {
        for (int i = pos.getZ() - 1; i <= pos.getZ() + 1; i++) {
            for (int j = pos.getX() - 1; j <= pos.getX() + 1; j++) {
                for (int k = pos.getY() - 1; k <= pos.getY() + 1; k++) {
                    this.setSectionDirty(SectionPos.blockToSectionCoord(j), SectionPos.blockToSectionCoord(k), SectionPos.blockToSectionCoord(i), reRenderOnMainThread);
                }
            }
        }
    }

    /**
     * Re-renders all blocks in the specified range.
     */
    public void setBlocksDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (int i = minZ - 1; i <= maxZ + 1; i++) {
            for (int j = minX - 1; j <= maxX + 1; j++) {
                for (int k = minY - 1; k <= maxY + 1; k++) {
                    this.setSectionDirty(SectionPos.blockToSectionCoord(j), SectionPos.blockToSectionCoord(k), SectionPos.blockToSectionCoord(i));
                }
            }
        }
    }

    public void setBlockDirty(BlockPos pos, BlockState oldState, BlockState newState) {
        if (this.minecraft.getModelManager().requiresRender(oldState, newState)) {
            this.setBlocksDirty(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
        }
    }

    public void setSectionDirtyWithNeighbors(int sectionX, int sectionY, int sectionZ) {
        for (int i = sectionZ - 1; i <= sectionZ + 1; i++) {
            for (int j = sectionX - 1; j <= sectionX + 1; j++) {
                for (int k = sectionY - 1; k <= sectionY + 1; k++) {
                    this.setSectionDirty(j, k, i);
                }
            }
        }
    }

    public void setSectionDirty(int sectionX, int sectionY, int sectionZ) {
        this.setSectionDirty(sectionX, sectionY, sectionZ, false);
    }

    private void setSectionDirty(int sectionX, int sectionY, int sectionZ, boolean reRenderOnMainThread) {
        this.viewArea.setDirty(sectionX, sectionY, sectionZ, reRenderOnMainThread);
    }

    public Frustum getFrustum() {
        return this.capturedFrustum != null ? this.capturedFrustum : this.cullingFrustum;
    }

    public int getTicks() {
        return this.ticks;
    }

    public void iterateVisibleBlockEntities(java.util.function.Consumer<BlockEntity> blockEntityConsumer) {
        for (var chunkInfo : this.visibleSections) {
            chunkInfo.getCompiled().getRenderableBlockEntities().forEach(blockEntityConsumer);
        }
        synchronized (this.globalBlockEntities) {
            this.globalBlockEntities.forEach(blockEntityConsumer);
        }
    }

    /**
     * Neo: Request outline effect post-processing to be enabled for the current frame. Must be called before block
     * entities are done rendering, ideally early during the frame
     */
    public void requestOutlineEffect() {
        this.outlineEffectRequested = true;
    }

    public void playJukeboxSong(Holder<JukeboxSong> song, BlockPos pos) {
        if (this.level != null) {
            this.stopJukeboxSong(pos);
            JukeboxSong jukeboxsong = song.value();
            SoundEvent soundevent = jukeboxsong.soundEvent().value();
            SoundInstance soundinstance = SimpleSoundInstance.forJukeboxSong(soundevent, Vec3.atCenterOf(pos));
            this.playingJukeboxSongs.put(pos, soundinstance);
            this.minecraft.getSoundManager().play(soundinstance);
            this.minecraft.gui.setNowPlaying(jukeboxsong.description());
            this.notifyNearbyEntities(this.level, pos, true);
        }
    }

    private void stopJukeboxSong(BlockPos pos) {
        SoundInstance soundinstance = this.playingJukeboxSongs.remove(pos);
        if (soundinstance != null) {
            this.minecraft.getSoundManager().stop(soundinstance);
        }
    }

    public void stopJukeboxSongAndNotifyNearby(BlockPos pos) {
        this.stopJukeboxSong(pos);
        if (this.level != null) {
            this.notifyNearbyEntities(this.level, pos, false);
        }
    }

    /**
     * Notifies living entities in a 3 block range of the specified {@code pos} that a record is or isn't playing nearby, dependent on the specified {@code playing} parameter.
     * This is used to make parrots start or stop partying.
     */
    private void notifyNearbyEntities(Level level, BlockPos pos, boolean playing) {
        for (LivingEntity livingentity : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(3.0))) {
            livingentity.setRecordPlayingNearby(pos, playing);
        }
    }

    /**
     * @param force if {@code true}, the particle will be created regardless of its
     *              distance from the camera and the {@linkplain #
     *              calculateParticleLevel(boolean) calculated particle level}
     */
    public void addParticle(
        ParticleOptions options,
        boolean force,
        double x,
        double y,
        double z,
        double xSpeed,
        double ySpeed,
        double zSpeed
    ) {
        this.addParticle(options, force, false, x, y, z, xSpeed, ySpeed, zSpeed);
    }

    /**
     * @param force     if {@code true}, the particle will be created regardless of
     *                  its distance from the camera and the {@linkplain #
     *                  calculateParticleLevel(boolean) calculated particle level}
     * @param decreased if {@code true}, and the {@linkplain
     *                  net.minecraft.client.Options#particles particles option} is
     *                  set to minimal, attempts to spawn the particle at a decreased
     *                  level
     */
    public void addParticle(
        ParticleOptions options,
        boolean force,
        boolean decreased,
        double x,
        double y,
        double z,
        double xSpeed,
        double ySpeed,
        double zSpeed
    ) {
        try {
            this.addParticleInternal(options, force, decreased, x, y, z, xSpeed, ySpeed, zSpeed);
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Exception while adding particle");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Particle being added");
            crashreportcategory.setDetail("ID", BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType()));
            crashreportcategory.setDetail(
                "Parameters",
                () -> ParticleTypes.CODEC.encodeStart(this.level.registryAccess().createSerializationContext(NbtOps.INSTANCE), options).toString()
            );
            crashreportcategory.setDetail("Position", () -> CrashReportCategory.formatLocation(this.level, x, y, z));
            throw new ReportedException(crashreport);
        }
    }

    private <T extends ParticleOptions> void addParticle(
        T options, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
    ) {
        this.addParticle(options, options.getType().getOverrideLimiter(), x, y, z, xSpeed, ySpeed, zSpeed);
    }

    /**
     * @param force if {@code true}, the particle will be created regardless of its
     *              distance from the camera and the {@linkplain #
     *              calculateParticleLevel(boolean) calculated particle level}
     */
    @Nullable
    private Particle addParticleInternal(
        ParticleOptions options,
        boolean force,
        double x,
        double y,
        double z,
        double xSpeed,
        double ySpeed,
        double zSpeed
    ) {
        return this.addParticleInternal(options, force, false, x, y, z, xSpeed, ySpeed, zSpeed);
    }

    /**
     * @param force     if {@code true}, the particle will be created regardless of
     *                  its distance from the camera and the {@linkplain #
     *                  calculateParticleLevel(boolean) calculated particle level}
     * @param decreased if {@code true}, and the {@linkplain
     *                  net.minecraft.client.Options#particles particles option} is
     *                  set to minimal, attempts to spawn the particle at a decreased
     *                  level
     */
    @Nullable
    private Particle addParticleInternal(
        ParticleOptions options,
        boolean force,
        boolean decreased,
        double x,
        double y,
        double z,
        double xSpeed,
        double ySpeed,
        double zSpeed
    ) {
        Camera camera = this.minecraft.gameRenderer.getMainCamera();
        ParticleStatus particlestatus = this.calculateParticleLevel(decreased);
        if (force) {
            return this.minecraft.particleEngine.createParticle(options, x, y, z, xSpeed, ySpeed, zSpeed);
        } else if (camera.getPosition().distanceToSqr(x, y, z) > 1024.0) {
            return null;
        } else {
            return particlestatus == ParticleStatus.MINIMAL
                ? null
                : this.minecraft.particleEngine.createParticle(options, x, y, z, xSpeed, ySpeed, zSpeed);
        }
    }

    /**
     * Calculates the level of particles to use based on the {@linkplain net.minecraft.client.Options#particles particles option} and the specified {@code decreased} parameter. This leads to randomly generating more or less particles than the set option.
     *
     * @param decreased if {@code true}, and the {@linkplain
     *                  net.minecraft.client.Options#particles particles option} is
     *                  set to minimal, has a 1 in 10 chance to return a decreased
     *                  level and a further 1 in 3 chance to minimise it
     */
    private ParticleStatus calculateParticleLevel(boolean decreased) {
        ParticleStatus particlestatus = this.minecraft.options.particles().get();
        if (decreased && particlestatus == ParticleStatus.MINIMAL && this.level.random.nextInt(10) == 0) {
            particlestatus = ParticleStatus.DECREASED;
        }

        if (particlestatus == ParticleStatus.DECREASED && this.level.random.nextInt(3) == 0) {
            particlestatus = ParticleStatus.MINIMAL;
        }

        return particlestatus;
    }

    public void clear() {
    }

    /**
     * Handles a global level event. This includes playing sounds that should be heard by any player, regardless of position and dimension, such as the Wither spawning.
     *
     * @param type the type of level event to handle. This method only handles {@
     *             linkplain net.minecraft.world.level.block.LevelEvent#
     *             SOUND_WITHER_BOSS_SPAWN the wither boss spawn sound}, {@linkplain
     *             net.minecraft.world.level.block.LevelEvent#SOUND_DRAGON_DEATH the
     *             dragon's death sound}, and {@linkplain
     *             net.minecraft.world.level.block.LevelEvent#SOUND_END_PORTAL_SPAWN
     *             the end portal spawn sound}.
     */
    public void globalLevelEvent(int type, BlockPos pos, int data) {
        switch (type) {
            case 1023:
            case 1028:
            case 1038:
                Camera camera = this.minecraft.gameRenderer.getMainCamera();
                if (camera.isInitialized()) {
                    double d0 = (double)pos.getX() - camera.getPosition().x;
                    double d1 = (double)pos.getY() - camera.getPosition().y;
                    double d2 = (double)pos.getZ() - camera.getPosition().z;
                    double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
                    double d4 = camera.getPosition().x;
                    double d5 = camera.getPosition().y;
                    double d6 = camera.getPosition().z;
                    if (d3 > 0.0) {
                        d4 += d0 / d3 * 2.0;
                        d5 += d1 / d3 * 2.0;
                        d6 += d2 / d3 * 2.0;
                    }

                    if (type == 1023) {
                        this.level.playLocalSound(d4, d5, d6, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0F, 1.0F, false);
                    } else if (type == 1038) {
                        this.level.playLocalSound(d4, d5, d6, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 1.0F, 1.0F, false);
                    } else {
                        this.level.playLocalSound(d4, d5, d6, SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 5.0F, 1.0F, false);
                    }
                }
        }
    }

    public void levelEvent(int type, BlockPos pos, int data) {
        RandomSource randomsource = this.level.random;
        switch (type) {
            case 1000:
                this.level.playLocalSound(pos, SoundEvents.DISPENSER_DISPENSE, SoundSource.BLOCKS, 1.0F, 1.0F, false);
                break;
            case 1001:
                this.level.playLocalSound(pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 1.0F, 1.2F, false);
                break;
            case 1002:
                this.level.playLocalSound(pos, SoundEvents.DISPENSER_LAUNCH, SoundSource.BLOCKS, 1.0F, 1.2F, false);
                break;
            case 1004:
                this.level.playLocalSound(pos, SoundEvents.FIREWORK_ROCKET_SHOOT, SoundSource.NEUTRAL, 1.0F, 1.2F, false);
                break;
            case 1009:
                if (data == 0) {
                    this.level
                        .playLocalSound(
                            pos,
                            SoundEvents.FIRE_EXTINGUISH,
                            SoundSource.BLOCKS,
                            0.5F,
                            2.6F + (randomsource.nextFloat() - randomsource.nextFloat()) * 0.8F,
                            false
                        );
                } else if (data == 1) {
                    this.level
                        .playLocalSound(
                            pos,
                            SoundEvents.GENERIC_EXTINGUISH_FIRE,
                            SoundSource.BLOCKS,
                            0.7F,
                            1.6F + (randomsource.nextFloat() - randomsource.nextFloat()) * 0.4F,
                            false
                        );
                }
                break;
            case 1010:
                this.level
                    .registryAccess()
                    .registryOrThrow(Registries.JUKEBOX_SONG)
                    .getHolder(data)
                    .ifPresent(p_349870_ -> this.playJukeboxSong(p_349870_, pos));
                break;
            case 1011:
                this.stopJukeboxSongAndNotifyNearby(pos);
                break;
            case 1015:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.GHAST_WARN,
                        SoundSource.HOSTILE,
                        10.0F,
                        (randomsource.nextFloat() - randomsource.nextFloat()) * 0.2F + 1.0F,
                        false
                    );
                break;
            case 1016:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.GHAST_SHOOT,
                        SoundSource.HOSTILE,
                        10.0F,
                        (randomsource.nextFloat() - randomsource.nextFloat()) * 0.2F + 1.0F,
                        false
                    );
                break;
            case 1017:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.ENDER_DRAGON_SHOOT,
                        SoundSource.HOSTILE,
                        10.0F,
                        (randomsource.nextFloat() - randomsource.nextFloat()) * 0.2F + 1.0F,
                        false
                    );
                break;
            case 1018:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.BLAZE_SHOOT,
                        SoundSource.HOSTILE,
                        2.0F,
                        (randomsource.nextFloat() - randomsource.nextFloat()) * 0.2F + 1.0F,
                        false
                    );
                break;
            case 1019:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR,
                        SoundSource.HOSTILE,
                        2.0F,
                        (randomsource.nextFloat() - randomsource.nextFloat()) * 0.2F + 1.0F,
                        false
                    );
                break;
            case 1020:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.ZOMBIE_ATTACK_IRON_DOOR,
                        SoundSource.HOSTILE,
                        2.0F,
                        (randomsource.nextFloat() - randomsource.nextFloat()) * 0.2F + 1.0F,
                        false
                    );
                break;
            case 1021:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR,
                        SoundSource.HOSTILE,
                        2.0F,
                        (randomsource.nextFloat() - randomsource.nextFloat()) * 0.2F + 1.0F,
                        false
                    );
                break;
            case 1022:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.WITHER_BREAK_BLOCK,
                        SoundSource.HOSTILE,
                        2.0F,
                        (randomsource.nextFloat() - randomsource.nextFloat()) * 0.2F + 1.0F,
                        false
                    );
                break;
            case 1024:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.WITHER_SHOOT,
                        SoundSource.HOSTILE,
                        2.0F,
                        (randomsource.nextFloat() - randomsource.nextFloat()) * 0.2F + 1.0F,
                        false
                    );
                break;
            case 1025:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.BAT_TAKEOFF,
                        SoundSource.NEUTRAL,
                        0.05F,
                        (randomsource.nextFloat() - randomsource.nextFloat()) * 0.2F + 1.0F,
                        false
                    );
                break;
            case 1026:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.ZOMBIE_INFECT,
                        SoundSource.HOSTILE,
                        2.0F,
                        (randomsource.nextFloat() - randomsource.nextFloat()) * 0.2F + 1.0F,
                        false
                    );
                break;
            case 1027:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.ZOMBIE_VILLAGER_CONVERTED,
                        SoundSource.HOSTILE,
                        2.0F,
                        (randomsource.nextFloat() - randomsource.nextFloat()) * 0.2F + 1.0F,
                        false
                    );
                break;
            case 1029:
                this.level.playLocalSound(pos, SoundEvents.ANVIL_DESTROY, SoundSource.BLOCKS, 1.0F, randomsource.nextFloat() * 0.1F + 0.9F, false);
                break;
            case 1030:
                this.level.playLocalSound(pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0F, randomsource.nextFloat() * 0.1F + 0.9F, false);
                break;
            case 1031:
                this.level.playLocalSound(pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.3F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
                break;
            case 1032:
                this.minecraft
                    .getSoundManager()
                    .play(SimpleSoundInstance.forLocalAmbience(SoundEvents.PORTAL_TRAVEL, randomsource.nextFloat() * 0.4F + 0.8F, 0.25F));
                break;
            case 1033:
                this.level.playLocalSound(pos, SoundEvents.CHORUS_FLOWER_GROW, SoundSource.BLOCKS, 1.0F, 1.0F, false);
                break;
            case 1034:
                this.level.playLocalSound(pos, SoundEvents.CHORUS_FLOWER_DEATH, SoundSource.BLOCKS, 1.0F, 1.0F, false);
                break;
            case 1035:
                this.level.playLocalSound(pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0F, 1.0F, false);
                break;
            case 1039:
                this.level.playLocalSound(pos, SoundEvents.PHANTOM_BITE, SoundSource.HOSTILE, 0.3F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
                break;
            case 1040:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.ZOMBIE_CONVERTED_TO_DROWNED,
                        SoundSource.HOSTILE,
                        2.0F,
                        (randomsource.nextFloat() - randomsource.nextFloat()) * 0.2F + 1.0F,
                        false
                    );
                break;
            case 1041:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.HUSK_CONVERTED_TO_ZOMBIE,
                        SoundSource.HOSTILE,
                        2.0F,
                        (randomsource.nextFloat() - randomsource.nextFloat()) * 0.2F + 1.0F,
                        false
                    );
                break;
            case 1042:
                this.level.playLocalSound(pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 1.0F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
                break;
            case 1043:
                this.level.playLocalSound(pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
                break;
            case 1044:
                this.level
                    .playLocalSound(pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
                break;
            case 1045:
                this.level
                    .playLocalSound(pos, SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.BLOCKS, 2.0F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
                break;
            case 1046:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.POINTED_DRIPSTONE_DRIP_LAVA_INTO_CAULDRON,
                        SoundSource.BLOCKS,
                        2.0F,
                        this.level.random.nextFloat() * 0.1F + 0.9F,
                        false
                    );
                break;
            case 1047:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON,
                        SoundSource.BLOCKS,
                        2.0F,
                        this.level.random.nextFloat() * 0.1F + 0.9F,
                        false
                    );
                break;
            case 1048:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.SKELETON_CONVERTED_TO_STRAY,
                        SoundSource.HOSTILE,
                        2.0F,
                        (randomsource.nextFloat() - randomsource.nextFloat()) * 0.2F + 1.0F,
                        false
                    );
                break;
            case 1049:
                this.level.playLocalSound(pos, SoundEvents.CRAFTER_CRAFT, SoundSource.BLOCKS, 1.0F, 1.0F, false);
                break;
            case 1050:
                this.level.playLocalSound(pos, SoundEvents.CRAFTER_FAIL, SoundSource.BLOCKS, 1.0F, 1.0F, false);
                break;
            case 1051:
                this.level
                    .playLocalSound(
                        pos, SoundEvents.WIND_CHARGE_THROW, SoundSource.BLOCKS, 0.5F, 0.4F / (this.level.getRandom().nextFloat() * 0.4F + 0.8F), false
                    );
            case 2010:
                this.shootParticles(data, pos, randomsource, ParticleTypes.WHITE_SMOKE);
                break;
            case 1500:
                ComposterBlock.handleFill(this.level, pos, data > 0);
                break;
            case 1501:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.LAVA_EXTINGUISH,
                        SoundSource.BLOCKS,
                        0.5F,
                        2.6F + (randomsource.nextFloat() - randomsource.nextFloat()) * 0.8F,
                        false
                    );

                for (int l2 = 0; l2 < 8; l2++) {
                    this.level
                        .addParticle(
                            ParticleTypes.LARGE_SMOKE,
                            (double)pos.getX() + randomsource.nextDouble(),
                            (double)pos.getY() + 1.2,
                            (double)pos.getZ() + randomsource.nextDouble(),
                            0.0,
                            0.0,
                            0.0
                        );
                }
                break;
            case 1502:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.REDSTONE_TORCH_BURNOUT,
                        SoundSource.BLOCKS,
                        0.5F,
                        2.6F + (randomsource.nextFloat() - randomsource.nextFloat()) * 0.8F,
                        false
                    );

                for (int k2 = 0; k2 < 5; k2++) {
                    double d12 = (double)pos.getX() + randomsource.nextDouble() * 0.6 + 0.2;
                    double d17 = (double)pos.getY() + randomsource.nextDouble() * 0.6 + 0.2;
                    double d22 = (double)pos.getZ() + randomsource.nextDouble() * 0.6 + 0.2;
                    this.level.addParticle(ParticleTypes.SMOKE, d12, d17, d22, 0.0, 0.0, 0.0);
                }
                break;
            case 1503:
                this.level.playLocalSound(pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.0F, 1.0F, false);

                for (int j2 = 0; j2 < 16; j2++) {
                    double d11 = (double)pos.getX() + (5.0 + randomsource.nextDouble() * 6.0) / 16.0;
                    double d16 = (double)pos.getY() + 0.8125;
                    double d21 = (double)pos.getZ() + (5.0 + randomsource.nextDouble() * 6.0) / 16.0;
                    this.level.addParticle(ParticleTypes.SMOKE, d11, d16, d21, 0.0, 0.0, 0.0);
                }
                break;
            case 1504:
                PointedDripstoneBlock.spawnDripParticle(this.level, pos, this.level.getBlockState(pos));
                break;
            case 1505:
                BoneMealItem.addGrowthParticles(this.level, pos, data);
                this.level.playLocalSound(pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0F, 1.0F, false);
                break;
            case 2000:
                this.shootParticles(data, pos, randomsource, ParticleTypes.SMOKE);
                break;
            case 2001:
                BlockState blockstate1 = Block.stateById(data);
                if (!blockstate1.isAir() && !net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions.of(blockstate1).playBreakSound(blockstate1, this.level, pos)) {
                    SoundType soundtype = blockstate1.getSoundType(this.level, pos, null);
                    this.level
                        .playLocalSound(
                            pos, soundtype.getBreakSound(), SoundSource.BLOCKS, (soundtype.getVolume() + 1.0F) / 2.0F, soundtype.getPitch() * 0.8F, false
                        );
                }

                this.level.addDestroyBlockEffect(pos, blockstate1);
                break;
            case 2002:
            case 2007:
                Vec3 vec3 = Vec3.atBottomCenterOf(pos);

                for (int j = 0; j < 8; j++) {
                    this.addParticle(
                        new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.SPLASH_POTION)),
                        vec3.x,
                        vec3.y,
                        vec3.z,
                        randomsource.nextGaussian() * 0.15,
                        randomsource.nextDouble() * 0.2,
                        randomsource.nextGaussian() * 0.15
                    );
                }

                float f2 = (float)(data >> 16 & 0xFF) / 255.0F;
                float f3 = (float)(data >> 8 & 0xFF) / 255.0F;
                float f5 = (float)(data >> 0 & 0xFF) / 255.0F;
                ParticleOptions particleoptions = type == 2007 ? ParticleTypes.INSTANT_EFFECT : ParticleTypes.EFFECT;

                for (int i2 = 0; i2 < 100; i2++) {
                    double d10 = randomsource.nextDouble() * 4.0;
                    double d15 = randomsource.nextDouble() * Math.PI * 2.0;
                    double d20 = Math.cos(d15) * d10;
                    double d24 = 0.01 + randomsource.nextDouble() * 0.5;
                    double d25 = Math.sin(d15) * d10;
                    Particle particle1 = this.addParticleInternal(
                        particleoptions, particleoptions.getType().getOverrideLimiter(), vec3.x + d20 * 0.1, vec3.y + 0.3, vec3.z + d25 * 0.1, d20, d24, d25
                    );
                    if (particle1 != null) {
                        float f1 = 0.75F + randomsource.nextFloat() * 0.25F;
                        particle1.setColor(f2 * f1, f3 * f1, f5 * f1);
                        particle1.setPower((float)d10);
                    }
                }

                this.level.playLocalSound(pos, SoundEvents.SPLASH_POTION_BREAK, SoundSource.NEUTRAL, 1.0F, randomsource.nextFloat() * 0.1F + 0.9F, false);
                break;
            case 2003:
                double d0 = (double)pos.getX() + 0.5;
                double d5 = (double)pos.getY();
                double d7 = (double)pos.getZ() + 0.5;

                for (int i3 = 0; i3 < 8; i3++) {
                    this.addParticle(
                        new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.ENDER_EYE)),
                        d0,
                        d5,
                        d7,
                        randomsource.nextGaussian() * 0.15,
                        randomsource.nextDouble() * 0.2,
                        randomsource.nextGaussian() * 0.15
                    );
                }

                for (double d9 = 0.0; d9 < Math.PI * 2; d9 += Math.PI / 20) {
                    this.addParticle(
                        ParticleTypes.PORTAL, d0 + Math.cos(d9) * 5.0, d5 - 0.4, d7 + Math.sin(d9) * 5.0, Math.cos(d9) * -5.0, 0.0, Math.sin(d9) * -5.0
                    );
                    this.addParticle(
                        ParticleTypes.PORTAL, d0 + Math.cos(d9) * 5.0, d5 - 0.4, d7 + Math.sin(d9) * 5.0, Math.cos(d9) * -7.0, 0.0, Math.sin(d9) * -7.0
                    );
                }
                break;
            case 2004:
                for (int l = 0; l < 20; l++) {
                    double d6 = (double)pos.getX() + 0.5 + (randomsource.nextDouble() - 0.5) * 2.0;
                    double d8 = (double)pos.getY() + 0.5 + (randomsource.nextDouble() - 0.5) * 2.0;
                    double d13 = (double)pos.getZ() + 0.5 + (randomsource.nextDouble() - 0.5) * 2.0;
                    this.level.addParticle(ParticleTypes.SMOKE, d6, d8, d13, 0.0, 0.0, 0.0);
                    this.level.addParticle(ParticleTypes.FLAME, d6, d8, d13, 0.0, 0.0, 0.0);
                }
                break;
            case 2006:
                for (int l1 = 0; l1 < 200; l1++) {
                    float f10 = randomsource.nextFloat() * 4.0F;
                    float f11 = randomsource.nextFloat() * (float) (Math.PI * 2);
                    double d14 = (double)(Mth.cos(f11) * f10);
                    double d19 = 0.01 + randomsource.nextDouble() * 0.5;
                    double d23 = (double)(Mth.sin(f11) * f10);
                    Particle particle = this.addParticleInternal(
                        ParticleTypes.DRAGON_BREATH,
                        false,
                        (double)pos.getX() + d14 * 0.1,
                        (double)pos.getY() + 0.3,
                        (double)pos.getZ() + d23 * 0.1,
                        d14,
                        d19,
                        d23
                    );
                    if (particle != null) {
                        particle.setPower(f10);
                    }
                }

                if (data == 1) {
                    this.level
                        .playLocalSound(
                            pos, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.HOSTILE, 1.0F, randomsource.nextFloat() * 0.1F + 0.9F, false
                        );
                }
                break;
            case 2008:
                this.level
                    .addParticle(
                        ParticleTypes.EXPLOSION, (double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5, 0.0, 0.0, 0.0
                    );
                break;
            case 2009:
                for (int k1 = 0; k1 < 8; k1++) {
                    this.level
                        .addParticle(
                            ParticleTypes.CLOUD,
                            (double)pos.getX() + randomsource.nextDouble(),
                            (double)pos.getY() + 1.2,
                            (double)pos.getZ() + randomsource.nextDouble(),
                            0.0,
                            0.0,
                            0.0
                        );
                }
                break;
            case 2011:
                ParticleUtils.spawnParticleInBlock(this.level, pos, data, ParticleTypes.HAPPY_VILLAGER);
                break;
            case 2012:
                ParticleUtils.spawnParticleInBlock(this.level, pos, data, ParticleTypes.HAPPY_VILLAGER);
                break;
            case 2013:
                ParticleUtils.spawnSmashAttackParticles(this.level, pos, data);
                break;
            case 3000:
                this.level
                    .addParticle(
                        ParticleTypes.EXPLOSION_EMITTER,
                        true,
                        (double)pos.getX() + 0.5,
                        (double)pos.getY() + 0.5,
                        (double)pos.getZ() + 0.5,
                        0.0,
                        0.0,
                        0.0
                    );
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.END_GATEWAY_SPAWN,
                        SoundSource.BLOCKS,
                        10.0F,
                        (1.0F + (this.level.random.nextFloat() - this.level.random.nextFloat()) * 0.2F) * 0.7F,
                        false
                    );
                break;
            case 3001:
                this.level
                    .playLocalSound(pos, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 64.0F, 0.8F + this.level.random.nextFloat() * 0.3F, false);
                break;
            case 3002:
                if (data >= 0 && data < Direction.Axis.VALUES.length) {
                    ParticleUtils.spawnParticlesAlongAxis(
                        Direction.Axis.VALUES[data], this.level, pos, 0.125, ParticleTypes.ELECTRIC_SPARK, UniformInt.of(10, 19)
                    );
                } else {
                    ParticleUtils.spawnParticlesOnBlockFaces(this.level, pos, ParticleTypes.ELECTRIC_SPARK, UniformInt.of(3, 5));
                }
                break;
            case 3003:
                ParticleUtils.spawnParticlesOnBlockFaces(this.level, pos, ParticleTypes.WAX_ON, UniformInt.of(3, 5));
                this.level.playLocalSound(pos, SoundEvents.HONEYCOMB_WAX_ON, SoundSource.BLOCKS, 1.0F, 1.0F, false);
                break;
            case 3004:
                ParticleUtils.spawnParticlesOnBlockFaces(this.level, pos, ParticleTypes.WAX_OFF, UniformInt.of(3, 5));
                break;
            case 3005:
                ParticleUtils.spawnParticlesOnBlockFaces(this.level, pos, ParticleTypes.SCRAPE, UniformInt.of(3, 5));
                break;
            case 3006:
                int k = data >> 6;
                if (k > 0) {
                    if (randomsource.nextFloat() < 0.3F + (float)k * 0.1F) {
                        float f4 = 0.15F + 0.02F * (float)k * (float)k * randomsource.nextFloat();
                        float f6 = 0.4F + 0.3F * (float)k * randomsource.nextFloat();
                        this.level.playLocalSound(pos, SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.BLOCKS, f4, f6, false);
                    }

                    byte b0 = (byte)(data & 63);
                    IntProvider intprovider = UniformInt.of(0, k);
                    float f7 = 0.005F;
                    Supplier<Vec3> supplier = () -> new Vec3(
                            Mth.nextDouble(randomsource, -0.005F, 0.005F),
                            Mth.nextDouble(randomsource, -0.005F, 0.005F),
                            Mth.nextDouble(randomsource, -0.005F, 0.005F)
                        );
                    if (b0 == 0) {
                        for (Direction direction : Direction.values()) {
                            float f = direction == Direction.DOWN ? (float) Math.PI : 0.0F;
                            double d4 = direction.getAxis() == Direction.Axis.Y ? 0.65 : 0.57;
                            ParticleUtils.spawnParticlesOnBlockFace(
                                this.level, pos, new SculkChargeParticleOptions(f), intprovider, direction, supplier, d4
                            );
                        }
                    } else {
                        for (Direction direction1 : MultifaceBlock.unpack(b0)) {
                            float f13 = direction1 == Direction.UP ? (float) Math.PI : 0.0F;
                            double d18 = 0.35;
                            ParticleUtils.spawnParticlesOnBlockFace(
                                this.level, pos, new SculkChargeParticleOptions(f13), intprovider, direction1, supplier, 0.35
                            );
                        }
                    }
                } else {
                    this.level.playLocalSound(pos, SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.BLOCKS, 1.0F, 1.0F, false);
                    boolean flag1 = this.level.getBlockState(pos).isCollisionShapeFullBlock(this.level, pos);
                    int j1 = flag1 ? 40 : 20;
                    float f8 = flag1 ? 0.45F : 0.25F;
                    float f9 = 0.07F;

                    for (int j3 = 0; j3 < j1; j3++) {
                        float f12 = 2.0F * randomsource.nextFloat() - 1.0F;
                        float f14 = 2.0F * randomsource.nextFloat() - 1.0F;
                        float f15 = 2.0F * randomsource.nextFloat() - 1.0F;
                        this.level
                            .addParticle(
                                ParticleTypes.SCULK_CHARGE_POP,
                                (double)pos.getX() + 0.5 + (double)(f12 * f8),
                                (double)pos.getY() + 0.5 + (double)(f14 * f8),
                                (double)pos.getZ() + 0.5 + (double)(f15 * f8),
                                (double)(f12 * 0.07F),
                                (double)(f14 * 0.07F),
                                (double)(f15 * 0.07F)
                            );
                    }
                }
                break;
            case 3007:
                for (int i1 = 0; i1 < 10; i1++) {
                    this.level
                        .addParticle(
                            new ShriekParticleOption(i1 * 5),
                            false,
                            (double)pos.getX() + 0.5,
                            (double)pos.getY() + SculkShriekerBlock.TOP_Y,
                            (double)pos.getZ() + 0.5,
                            0.0,
                            0.0,
                            0.0
                        );
                }

                BlockState blockstate2 = this.level.getBlockState(pos);
                boolean flag = blockstate2.hasProperty(BlockStateProperties.WATERLOGGED) && blockstate2.getValue(BlockStateProperties.WATERLOGGED);
                if (!flag) {
                    this.level
                        .playLocalSound(
                            (double)pos.getX() + 0.5,
                            (double)pos.getY() + SculkShriekerBlock.TOP_Y,
                            (double)pos.getZ() + 0.5,
                            SoundEvents.SCULK_SHRIEKER_SHRIEK,
                            SoundSource.BLOCKS,
                            2.0F,
                            0.6F + this.level.random.nextFloat() * 0.4F,
                            false
                        );
                }
                break;
            case 3008:
                BlockState blockstate = Block.stateById(data);
                if (blockstate.getBlock() instanceof BrushableBlock brushableblock) {
                    this.level.playLocalSound(pos, brushableblock.getBrushCompletedSound(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
                }

                this.level.addDestroyBlockEffect(pos, blockstate);
                break;
            case 3009:
                ParticleUtils.spawnParticlesOnBlockFaces(this.level, pos, ParticleTypes.EGG_CRACK, UniformInt.of(3, 6));
                break;
            case 3011:
                TrialSpawner.addSpawnParticles(this.level, pos, randomsource, TrialSpawner.FlameParticle.decode(data).particleType);
                break;
            case 3012:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.TRIAL_SPAWNER_SPAWN_MOB,
                        SoundSource.BLOCKS,
                        1.0F,
                        (randomsource.nextFloat() - randomsource.nextFloat()) * 0.2F + 1.0F,
                        true
                    );
                TrialSpawner.addSpawnParticles(this.level, pos, randomsource, TrialSpawner.FlameParticle.decode(data).particleType);
                break;
            case 3013:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.TRIAL_SPAWNER_DETECT_PLAYER,
                        SoundSource.BLOCKS,
                        1.0F,
                        (randomsource.nextFloat() - randomsource.nextFloat()) * 0.2F + 1.0F,
                        true
                    );
                TrialSpawner.addDetectPlayerParticles(this.level, pos, randomsource, data, ParticleTypes.TRIAL_SPAWNER_DETECTED_PLAYER);
                break;
            case 3014:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.TRIAL_SPAWNER_EJECT_ITEM,
                        SoundSource.BLOCKS,
                        1.0F,
                        (randomsource.nextFloat() - randomsource.nextFloat()) * 0.2F + 1.0F,
                        true
                    );
                TrialSpawner.addEjectItemParticles(this.level, pos, randomsource);
                break;
            case 3015:
                if (this.level.getBlockEntity(pos) instanceof VaultBlockEntity vaultblockentity) {
                    VaultBlockEntity.Client.emitActivationParticles(
                        this.level,
                        vaultblockentity.getBlockPos(),
                        vaultblockentity.getBlockState(),
                        vaultblockentity.getSharedData(),
                        data == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.SOUL_FIRE_FLAME
                    );
                    this.level
                        .playLocalSound(
                            pos,
                            SoundEvents.VAULT_ACTIVATE,
                            SoundSource.BLOCKS,
                            1.0F,
                            (randomsource.nextFloat() - randomsource.nextFloat()) * 0.2F + 1.0F,
                            true
                        );
                }
                break;
            case 3016:
                VaultBlockEntity.Client.emitDeactivationParticles(
                    this.level, pos, data == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.SOUL_FIRE_FLAME
                );
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.VAULT_DEACTIVATE,
                        SoundSource.BLOCKS,
                        1.0F,
                        (randomsource.nextFloat() - randomsource.nextFloat()) * 0.2F + 1.0F,
                        true
                    );
                break;
            case 3017:
                TrialSpawner.addEjectItemParticles(this.level, pos, randomsource);
                break;
            case 3018:
                for (int i = 0; i < 10; i++) {
                    double d1 = randomsource.nextGaussian() * 0.02;
                    double d2 = randomsource.nextGaussian() * 0.02;
                    double d3 = randomsource.nextGaussian() * 0.02;
                    this.level
                        .addParticle(
                            ParticleTypes.POOF,
                            (double)pos.getX() + randomsource.nextDouble(),
                            (double)pos.getY() + randomsource.nextDouble(),
                            (double)pos.getZ() + randomsource.nextDouble(),
                            d1,
                            d2,
                            d3
                        );
                }

                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.COBWEB_PLACE,
                        SoundSource.BLOCKS,
                        1.0F,
                        (randomsource.nextFloat() - randomsource.nextFloat()) * 0.2F + 1.0F,
                        true
                    );
                break;
            case 3019:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.TRIAL_SPAWNER_DETECT_PLAYER,
                        SoundSource.BLOCKS,
                        1.0F,
                        (randomsource.nextFloat() - randomsource.nextFloat()) * 0.2F + 1.0F,
                        true
                    );
                TrialSpawner.addDetectPlayerParticles(this.level, pos, randomsource, data, ParticleTypes.TRIAL_SPAWNER_DETECTED_PLAYER_OMINOUS);
                break;
            case 3020:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE,
                        SoundSource.BLOCKS,
                        data == 0 ? 0.3F : 1.0F,
                        (randomsource.nextFloat() - randomsource.nextFloat()) * 0.2F + 1.0F,
                        true
                    );
                TrialSpawner.addDetectPlayerParticles(this.level, pos, randomsource, 0, ParticleTypes.TRIAL_SPAWNER_DETECTED_PLAYER_OMINOUS);
                TrialSpawner.addBecomeOminousParticles(this.level, pos, randomsource);
                break;
            case 3021:
                this.level
                    .playLocalSound(
                        pos,
                        SoundEvents.TRIAL_SPAWNER_SPAWN_ITEM,
                        SoundSource.BLOCKS,
                        1.0F,
                        (randomsource.nextFloat() - randomsource.nextFloat()) * 0.2F + 1.0F,
                        true
                    );
                TrialSpawner.addSpawnParticles(this.level, pos, randomsource, TrialSpawner.FlameParticle.decode(data).particleType);
        }
    }

    public void destroyBlockProgress(int breakerId, BlockPos pos, int progress) {
        if (progress >= 0 && progress < 10) {
            BlockDestructionProgress blockdestructionprogress1 = this.destroyingBlocks.get(breakerId);
            if (blockdestructionprogress1 != null) {
                this.removeProgress(blockdestructionprogress1);
            }

            if (blockdestructionprogress1 == null
                || blockdestructionprogress1.getPos().getX() != pos.getX()
                || blockdestructionprogress1.getPos().getY() != pos.getY()
                || blockdestructionprogress1.getPos().getZ() != pos.getZ()) {
                blockdestructionprogress1 = new BlockDestructionProgress(breakerId, pos);
                this.destroyingBlocks.put(breakerId, blockdestructionprogress1);
            }

            blockdestructionprogress1.setProgress(progress);
            blockdestructionprogress1.updateTick(this.ticks);
            this.destructionProgress
                .computeIfAbsent(blockdestructionprogress1.getPos().asLong(), p_234254_ -> Sets.newTreeSet())
                .add(blockdestructionprogress1);
        } else {
            BlockDestructionProgress blockdestructionprogress = this.destroyingBlocks.remove(breakerId);
            if (blockdestructionprogress != null) {
                this.removeProgress(blockdestructionprogress);
            }
        }
    }

    public boolean hasRenderedAllSections() {
        return this.sectionRenderDispatcher.isQueueEmpty();
    }

    public void onChunkLoaded(ChunkPos chunkPos) {
        this.sectionOcclusionGraph.onChunkLoaded(chunkPos);
    }

    public void needsUpdate() {
        this.sectionOcclusionGraph.invalidate();
        this.generateClouds = true;
    }

    public void updateGlobalBlockEntities(Collection<BlockEntity> blockEntitiesToRemove, Collection<BlockEntity> blockEntitiesToAdd) {
        synchronized (this.globalBlockEntities) {
            this.globalBlockEntities.removeAll(blockEntitiesToRemove);
            this.globalBlockEntities.addAll(blockEntitiesToAdd);
        }
    }

    public static int getLightColor(BlockAndTintGetter level, BlockPos pos) {
        return getLightColor(level, level.getBlockState(pos), pos);
    }

    public static int getLightColor(BlockAndTintGetter level, BlockState state, BlockPos pos) {
        if (state.emissiveRendering(level, pos)) {
            return 15728880;
        } else {
            int i = level.getBrightness(LightLayer.SKY, pos);
            int j = level.getBrightness(LightLayer.BLOCK, pos);
            int k = state.getLightEmission(level, pos);
            if (j < k) {
                j = k;
            }

            return i << 20 | j << 4;
        }
    }

    public boolean isSectionCompiled(BlockPos pos) {
        SectionRenderDispatcher.RenderSection sectionrenderdispatcher$rendersection = this.viewArea.getRenderSectionAt(pos);
        return sectionrenderdispatcher$rendersection != null
            && sectionrenderdispatcher$rendersection.compiled.get() != SectionRenderDispatcher.CompiledSection.UNCOMPILED;
    }

    @Nullable
    public RenderTarget entityTarget() {
        return this.entityTarget;
    }

    @Nullable
    public RenderTarget getTranslucentTarget() {
        return this.translucentTarget;
    }

    @Nullable
    public RenderTarget getItemEntityTarget() {
        return this.itemEntityTarget;
    }

    @Nullable
    public RenderTarget getParticlesTarget() {
        return this.particlesTarget;
    }

    @Nullable
    public RenderTarget getWeatherTarget() {
        return this.weatherTarget;
    }

    @Nullable
    public RenderTarget getCloudsTarget() {
        return this.cloudsTarget;
    }

    private void shootParticles(int p_direction, BlockPos pos, RandomSource random, SimpleParticleType particleType) {
        Direction direction = Direction.from3DDataValue(p_direction);
        int i = direction.getStepX();
        int j = direction.getStepY();
        int k = direction.getStepZ();
        double d0 = (double)pos.getX() + (double)i * 0.6 + 0.5;
        double d1 = (double)pos.getY() + (double)j * 0.6 + 0.5;
        double d2 = (double)pos.getZ() + (double)k * 0.6 + 0.5;

        for (int l = 0; l < 10; l++) {
            double d3 = random.nextDouble() * 0.2 + 0.01;
            double d4 = d0 + (double)i * 0.01 + (random.nextDouble() - 0.5) * (double)k * 0.5;
            double d5 = d1 + (double)j * 0.01 + (random.nextDouble() - 0.5) * (double)j * 0.5;
            double d6 = d2 + (double)k * 0.01 + (random.nextDouble() - 0.5) * (double)i * 0.5;
            double d7 = (double)i * d3 + random.nextGaussian() * 0.01;
            double d8 = (double)j * d3 + random.nextGaussian() * 0.01;
            double d9 = (double)k * d3 + random.nextGaussian() * 0.01;
            this.addParticle(particleType, d4, d5, d6, d7, d8, d9);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class TransparencyShaderException extends RuntimeException {
        public TransparencyShaderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

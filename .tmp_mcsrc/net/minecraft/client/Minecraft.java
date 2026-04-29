package net.minecraft.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.BanDetails;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.minecraft.UserApiService.UserFlag;
import com.mojang.authlib.minecraft.UserApiService.UserProperties;
import com.mojang.authlib.yggdrasil.ProfileActionType;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.ServicesKeyType;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DisplayData;
import com.mojang.blaze3d.platform.GlDebug;
import com.mojang.blaze3d.platform.GlUtil;
import com.mojang.blaze3d.platform.IconSet;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.WindowEventHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.TimerQuery;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.gui.RealmsDataFetcher;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.net.Proxy;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.FileUtil;
import net.minecraft.Optionull;
import net.minecraft.ReportType;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.SystemReport;
import net.minecraft.Util;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.GuiSpriteManager;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.components.toasts.TutorialToast;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.BanNoticeScreens;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.OutOfMemoryScreen;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.main.SilentInitException;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.profiling.ClientMetricsSamplersProvider;
import net.minecraft.client.quickplay.QuickPlay;
import net.minecraft.client.quickplay.QuickPlayLog;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.GpuWarnlistManager;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.VirtualScreen;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.client.resources.FoliageColorReloadListener;
import net.minecraft.client.resources.GrassColorReloadListener;
import net.minecraft.client.resources.MapDecorationTextureManager;
import net.minecraft.client.resources.MobEffectTextureManager;
import net.minecraft.client.resources.PaintingTextureManager;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.client.resources.SplashManager;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import net.minecraft.client.telemetry.TelemetryProperty;
import net.minecraft.client.telemetry.events.GameLoadTimesEvent;
import net.minecraft.client.tutorial.Tutorial;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.KeybindResolver;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.progress.ProcessorChunkProgressListener;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.CommonLinks;
import net.minecraft.util.FastColor;
import net.minecraft.util.FileZipper;
import net.minecraft.util.MemoryReserve;
import net.minecraft.util.ModCheck;
import net.minecraft.util.Mth;
import net.minecraft.util.SignatureValidator;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.Unit;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.profiling.ContinuousProfiler;
import net.minecraft.util.profiling.EmptyProfileResults;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.ResultField;
import net.minecraft.util.profiling.SingleTickProfiler;
import net.minecraft.util.profiling.metrics.profiling.ActiveMetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.InactiveMetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.MetricsRecorder;
import net.minecraft.util.profiling.metrics.storage.MetricsPersister;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.validation.DirectoryValidator;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.commons.io.FileUtils;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class Minecraft extends ReentrantBlockableEventLoop<Runnable> implements WindowEventHandler, net.neoforged.neoforge.client.extensions.IMinecraftExtension {
    static Minecraft instance;
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final boolean ON_OSX = Util.getPlatform() == Util.OS.OSX;
    private static final int MAX_TICKS_PER_UPDATE = 10;
    public static final ResourceLocation DEFAULT_FONT = ResourceLocation.withDefaultNamespace("default");
    public static final ResourceLocation UNIFORM_FONT = ResourceLocation.withDefaultNamespace("uniform");
    public static final ResourceLocation ALT_FONT = ResourceLocation.withDefaultNamespace("alt");
    private static final ResourceLocation REGIONAL_COMPLIANCIES = ResourceLocation.withDefaultNamespace("regional_compliancies.json");
    private static final CompletableFuture<Unit> RESOURCE_RELOAD_INITIAL_TASK = CompletableFuture.completedFuture(Unit.INSTANCE);
    private static final Component SOCIAL_INTERACTIONS_NOT_AVAILABLE = Component.translatable("multiplayer.socialInteractions.not_available");
    public static final String UPDATE_DRIVERS_ADVICE = "Please make sure you have up-to-date drivers (see aka.ms/mcdriver for instructions).";
    private final long canary = Double.doubleToLongBits(Math.PI);
    private final Path resourcePackDirectory;
    private final CompletableFuture<ProfileResult> profileFuture;
    private final TextureManager textureManager;
    private final DataFixer fixerUpper;
    private final VirtualScreen virtualScreen;
    private final Window window;
    private final DeltaTracker.Timer timer = new DeltaTracker.Timer(20.0F, 0L, this::getTickTargetMillis);
    private final RenderBuffers renderBuffers;
    public final LevelRenderer levelRenderer;
    private final EntityRenderDispatcher entityRenderDispatcher;
    private final ItemRenderer itemRenderer;
    public final ParticleEngine particleEngine;
    private final User user;
    public final Font font;
    public final Font fontFilterFishy;
    public final GameRenderer gameRenderer;
    public final DebugRenderer debugRenderer;
    private final AtomicReference<StoringChunkProgressListener> progressListener = new AtomicReference<>();
    public final Gui gui;
    public final Options options;
    private final HotbarManager hotbarManager;
    public final MouseHandler mouseHandler;
    public final KeyboardHandler keyboardHandler;
    private InputType lastInputType = InputType.NONE;
    public final File gameDirectory;
    private final String launchedVersion;
    private final String versionType;
    private final Proxy proxy;
    private final LevelStorageSource levelSource;
    private final boolean demo;
    private final boolean allowsMultiplayer;
    private final boolean allowsChat;
    private final ReloadableResourceManager resourceManager;
    private final VanillaPackResources vanillaPackResources;
    private final DownloadedPackSource downloadedPackSource;
    private final PackRepository resourcePackRepository;
    private final LanguageManager languageManager;
    private final BlockColors blockColors;
    private final ItemColors itemColors;
    private final RenderTarget mainRenderTarget;
    private final SoundManager soundManager;
    private final MusicManager musicManager;
    private final FontManager fontManager;
    private final SplashManager splashManager;
    private final GpuWarnlistManager gpuWarnlistManager;
    private final PeriodicNotificationManager regionalCompliancies = new PeriodicNotificationManager(REGIONAL_COMPLIANCIES, Minecraft::countryEqualsISO3);
    private final YggdrasilAuthenticationService authenticationService;
    private final MinecraftSessionService minecraftSessionService;
    private final UserApiService userApiService;
    private final CompletableFuture<UserProperties> userPropertiesFuture;
    private final SkinManager skinManager;
    private final ModelManager modelManager;
    /**
     * The BlockRenderDispatcher instance that will be used based off gamesettings
     */
    private final BlockRenderDispatcher blockRenderer;
    private final PaintingTextureManager paintingTextures;
    private final MobEffectTextureManager mobEffectTextures;
    private final MapDecorationTextureManager mapDecorationTextures;
    private final GuiSpriteManager guiSprites;
    private final ToastComponent toast;
    private final Tutorial tutorial;
    private final PlayerSocialManager playerSocialManager;
    private final EntityModelSet entityModels;
    private final BlockEntityRenderDispatcher blockEntityRenderDispatcher;
    private final ClientTelemetryManager telemetryManager;
    private final ProfileKeyPairManager profileKeyPairManager;
    private final RealmsDataFetcher realmsDataFetcher;
    private final QuickPlayLog quickPlayLog;
    @Nullable
    public MultiPlayerGameMode gameMode;
    @Nullable
    public ClientLevel level;
    @Nullable
    public LocalPlayer player;
    @Nullable
    private IntegratedServer singleplayerServer;
    @Nullable
    private Connection pendingConnection;
    private boolean isLocalServer;
    @Nullable
    public Entity cameraEntity;
    @Nullable
    public Entity crosshairPickEntity;
    @Nullable
    public HitResult hitResult;
    private int rightClickDelay;
    protected int missTime;
    private volatile boolean pause;
    /**
     * Time in nanoseconds of when the class is loaded
     */
    private long lastNanoTime = Util.getNanos();
    private long lastTime;
    private int frames;
    public boolean noRender;
    @Nullable
    public Screen screen;
    @Nullable
    private Overlay overlay;
    private boolean clientLevelTeardownInProgress;
    private Thread gameThread;
    private volatile boolean running;
    @Nullable
    private Supplier<CrashReport> delayedCrash;
    private static int fps;
    public String fpsString = "";
    private long frameTimeNs;
    public boolean wireframe;
    public boolean sectionPath;
    public boolean sectionVisibility;
    public boolean smartCull = true;
    private boolean windowActive;
    private final Queue<Runnable> progressTasks = Queues.newConcurrentLinkedQueue();
    @Nullable
    private CompletableFuture<Void> pendingReload;
    @Nullable
    private TutorialToast socialInteractionsToast;
    private ProfilerFiller profiler = InactiveProfiler.INSTANCE;
    private int fpsPieRenderTicks;
    private final ContinuousProfiler fpsPieProfiler = new ContinuousProfiler(Util.timeSource, () -> this.fpsPieRenderTicks);
    @Nullable
    private ProfileResults fpsPieResults;
    private MetricsRecorder metricsRecorder = InactiveMetricsRecorder.INSTANCE;
    private final ResourceLoadStateTracker reloadStateTracker = new ResourceLoadStateTracker();
    private long savedCpuDuration;
    private double gpuUtilization;
    @Nullable
    private TimerQuery.FrameProfile currentFrameProfile;
    private final GameNarrator narrator;
    private final ChatListener chatListener;
    private ReportingContext reportingContext;
    private final CommandHistory commandHistory;
    private final DirectoryValidator directoryValidator;
    private boolean gameLoadFinished;
    private final long clientStartTimeMs;
    private long clientTickCount;
    private String debugPath = "root";

    public Minecraft(GameConfig gameConfig) {
        super("Client");
        instance = this;
        this.clientStartTimeMs = System.currentTimeMillis();
        this.gameDirectory = gameConfig.location.gameDirectory;
        File file1 = gameConfig.location.assetDirectory;
        this.resourcePackDirectory = gameConfig.location.resourcePackDirectory.toPath();
        this.launchedVersion = gameConfig.game.launchVersion;
        this.versionType = gameConfig.game.versionType;
        Path path = this.gameDirectory.toPath();
        this.directoryValidator = LevelStorageSource.parseValidator(path.resolve("allowed_symlinks.txt"));
        ClientPackSource clientpacksource = new ClientPackSource(gameConfig.location.getExternalAssetSource(), this.directoryValidator);
        this.downloadedPackSource = new DownloadedPackSource(this, path.resolve("downloads"), gameConfig.user);
        RepositorySource repositorysource = new FolderRepositorySource(
            this.resourcePackDirectory, PackType.CLIENT_RESOURCES, PackSource.DEFAULT, this.directoryValidator
        );
        this.resourcePackRepository = new PackRepository(clientpacksource, this.downloadedPackSource.createRepositorySource(), repositorysource);
        this.vanillaPackResources = clientpacksource.getVanillaPack();
        this.proxy = gameConfig.user.proxy;
        this.authenticationService = new YggdrasilAuthenticationService(this.proxy);
        this.minecraftSessionService = this.authenticationService.createMinecraftSessionService();
        this.user = gameConfig.user.user;
        this.profileFuture = CompletableFuture.supplyAsync(
            () -> this.minecraftSessionService.fetchProfile(this.user.getProfileId(), true), Util.nonCriticalIoPool()
        );
        this.userApiService = this.createUserApiService(this.authenticationService, gameConfig);
        this.userPropertiesFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return this.userApiService.fetchProperties();
            } catch (AuthenticationException authenticationexception) {
                LOGGER.error("Failed to fetch user properties", (Throwable)authenticationexception);
                return UserApiService.OFFLINE_PROPERTIES;
            }
        }, Util.nonCriticalIoPool());
        LOGGER.info("Setting user: {}", this.user.getName());
        this.demo = gameConfig.game.demo;
        this.allowsMultiplayer = !gameConfig.game.disableMultiplayer;
        this.allowsChat = !gameConfig.game.disableChat;
        this.singleplayerServer = null;
        KeybindResolver.setKeyResolver(KeyMapping::createNameSupplier);
        this.fixerUpper = DataFixers.getDataFixer();
        this.toast = new ToastComponent(this);
        this.gameThread = Thread.currentThread();
        this.options = new Options(this, this.gameDirectory);
        RenderSystem.setShaderGlintAlpha(this.options.glintStrength().get());
        this.running = true;
        this.tutorial = new Tutorial(this, this.options);
        this.hotbarManager = new HotbarManager(path, this.fixerUpper);
        LOGGER.info("Backend library: {}", RenderSystem.getBackendDescription());
        DisplayData displaydata;
        if (this.options.overrideHeight > 0 && this.options.overrideWidth > 0) {
            displaydata = new DisplayData(
                this.options.overrideWidth,
                this.options.overrideHeight,
                gameConfig.display.fullscreenWidth,
                gameConfig.display.fullscreenHeight,
                gameConfig.display.isFullscreen
            );
        } else {
            displaydata = gameConfig.display;
        }

        Util.timeSource = RenderSystem.initBackendSystem();
        this.virtualScreen = new VirtualScreen(this);
        this.window = this.virtualScreen.newWindow(displaydata, this.options.fullscreenVideoModeString, this.createTitle());
        this.setWindowActive(true);
        GameLoadTimesEvent.INSTANCE.endStep(TelemetryProperty.LOAD_TIME_PRE_WINDOW_MS);

        try {
            this.window.setIcon(this.vanillaPackResources, SharedConstants.getCurrentVersion().isStable() ? IconSet.RELEASE : IconSet.SNAPSHOT);
        } catch (IOException ioexception) {
            LOGGER.error("Couldn't set icon", (Throwable)ioexception);
        }

        this.window.setFramerateLimit(this.options.framerateLimit().get());
        // FORGE: Move mouse and keyboard handler setup further below
        this.mouseHandler = new MouseHandler(this);
        this.keyboardHandler = new KeyboardHandler(this);
        RenderSystem.initRenderer(this.options.glDebugVerbosity, false);
        this.mainRenderTarget = new MainTarget(this.window.getWidth(), this.window.getHeight());
        this.mainRenderTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        this.mainRenderTarget.clear(ON_OSX);
        this.resourceManager = new ReloadableResourceManager(PackType.CLIENT_RESOURCES);
        net.neoforged.neoforge.client.loading.ClientModLoader.begin(this, this.resourcePackRepository, this.resourceManager);
        this.resourcePackRepository.reload();
        this.options.loadSelectedResourcePacks(this.resourcePackRepository);
        this.languageManager = new LanguageManager(this.options.languageCode, p_344151_ -> {
            if (this.player != null) {
                this.player.connection.updateSearchTrees();
            }
        });
        this.resourceManager.registerReloadListener(this.languageManager);
        this.textureManager = new TextureManager(this.resourceManager);
        this.resourceManager.registerReloadListener(this.textureManager);
        this.skinManager = new SkinManager(this.textureManager, file1.toPath().resolve("skins"), this.minecraftSessionService, this);
        this.levelSource = new LevelStorageSource(path.resolve("saves"), path.resolve("backups"), this.directoryValidator, this.fixerUpper);
        this.commandHistory = new CommandHistory(path);
        this.soundManager = new SoundManager(this.options);
        this.resourceManager.registerReloadListener(this.soundManager);
        this.splashManager = new SplashManager(this.user);
        this.resourceManager.registerReloadListener(this.splashManager);
        this.musicManager = new MusicManager(this);
        this.fontManager = new FontManager(this.textureManager);
        this.font = this.fontManager.createFont();
        this.fontFilterFishy = this.fontManager.createFontFilterFishy();
        this.resourceManager.registerReloadListener(this.fontManager);
        this.updateFontOptions();
        this.resourceManager.registerReloadListener(new GrassColorReloadListener());
        this.resourceManager.registerReloadListener(new FoliageColorReloadListener());
        this.window.setErrorSection("Startup");
        RenderSystem.setupDefaultState(0, 0, this.window.getWidth(), this.window.getHeight());
        this.window.setErrorSection("Post startup");
        this.blockColors = BlockColors.createDefault();
        this.itemColors = ItemColors.createDefault(this.blockColors);
        this.modelManager = new ModelManager(this.textureManager, this.blockColors, this.options.mipmapLevels().get());
        this.resourceManager.registerReloadListener(this.modelManager);
        this.entityModels = new EntityModelSet();
        this.resourceManager.registerReloadListener(this.entityModels);
        this.blockEntityRenderDispatcher = new BlockEntityRenderDispatcher(
            this.font, this.entityModels, this::getBlockRenderer, this::getItemRenderer, this::getEntityRenderDispatcher
        );
        this.resourceManager.registerReloadListener(this.blockEntityRenderDispatcher);
        BlockEntityWithoutLevelRenderer blockentitywithoutlevelrenderer = new BlockEntityWithoutLevelRenderer(
            this.blockEntityRenderDispatcher, this.entityModels
        );
        this.resourceManager.registerReloadListener(blockentitywithoutlevelrenderer);
        this.itemRenderer = new ItemRenderer(this, this.textureManager, this.modelManager, this.itemColors, blockentitywithoutlevelrenderer);
        this.resourceManager.registerReloadListener(this.itemRenderer);

        try {
            int i = Runtime.getRuntime().availableProcessors();
            Tesselator.init();
            this.renderBuffers = new RenderBuffers(i);
        } catch (OutOfMemoryError outofmemoryerror) {
            TinyFileDialogs.tinyfd_messageBox(
                "Minecraft",
                "Oh no! The game was unable to allocate memory off-heap while trying to start. You may try to free some memory by closing other applications on your computer, check that your system meets the minimum requirements, and try again. If the problem persists, please visit: "
                    + CommonLinks.GENERAL_HELP,
                "ok",
                "error",
                true
            );
            throw new SilentInitException("Unable to allocate render buffers", outofmemoryerror);
        }

        this.playerSocialManager = new PlayerSocialManager(this, this.userApiService);
        this.blockRenderer = new BlockRenderDispatcher(this.modelManager.getBlockModelShaper(), blockentitywithoutlevelrenderer, this.blockColors);
        this.resourceManager.registerReloadListener(this.blockRenderer);
        this.entityRenderDispatcher = new EntityRenderDispatcher(
            this, this.textureManager, this.itemRenderer, this.blockRenderer, this.font, this.options, this.entityModels
        );
        this.resourceManager.registerReloadListener(this.entityRenderDispatcher);
        this.particleEngine = new ParticleEngine(this.level, this.textureManager);
        net.neoforged.neoforge.client.ClientHooks.onRegisterParticleProviders(this.particleEngine);
        this.resourceManager.registerReloadListener(this.particleEngine);
        this.paintingTextures = new PaintingTextureManager(this.textureManager);
        this.resourceManager.registerReloadListener(this.paintingTextures);
        this.mobEffectTextures = new MobEffectTextureManager(this.textureManager);
        this.resourceManager.registerReloadListener(this.mobEffectTextures);
        this.mapDecorationTextures = new MapDecorationTextureManager(this.textureManager);
        this.resourceManager.registerReloadListener(this.mapDecorationTextures);
        this.guiSprites = new GuiSpriteManager(this.textureManager);
        this.resourceManager.registerReloadListener(this.guiSprites);
        this.gameRenderer = new GameRenderer(this, this.entityRenderDispatcher.getItemInHandRenderer(), this.resourceManager, this.renderBuffers);
        this.resourceManager.registerReloadListener(this.gameRenderer.createReloadListener());
        this.levelRenderer = new LevelRenderer(this, this.entityRenderDispatcher, this.blockEntityRenderDispatcher, this.renderBuffers);
        net.neoforged.fml.ModLoader.postEvent(new net.neoforged.neoforge.client.event.RenderLevelStageEvent.RegisterStageEvent());
        this.resourceManager.registerReloadListener(this.levelRenderer);
        this.gpuWarnlistManager = new GpuWarnlistManager();
        this.resourceManager.registerReloadListener(this.gpuWarnlistManager);
        this.resourceManager.registerReloadListener(this.regionalCompliancies);
        // FORGE: Moved keyboard and mouse handler setup below ingame gui creation to prevent NPEs in them.
        this.mouseHandler.setup(this.window.getWindow());
        this.keyboardHandler.setup(this.window.getWindow());
        this.gui = new Gui(this);
        this.debugRenderer = new DebugRenderer(this);
        RealmsClient realmsclient = RealmsClient.create(this);
        this.realmsDataFetcher = new RealmsDataFetcher(realmsclient);
        RenderSystem.setErrorCallback(this::onFullscreenError);
        if (this.mainRenderTarget.width != this.window.getWidth() || this.mainRenderTarget.height != this.window.getHeight()) {
            StringBuilder stringbuilder = new StringBuilder(
                "Recovering from unsupported resolution ("
                    + this.window.getWidth()
                    + "x"
                    + this.window.getHeight()
                    + ").\nPlease make sure you have up-to-date drivers (see aka.ms/mcdriver for instructions)."
            );
            if (GlDebug.isDebugEnabled()) {
                stringbuilder.append("\n\nReported GL debug messages:\n").append(String.join("\n", GlDebug.getLastOpenGlDebugMessages()));
            }

            this.window.setWindowed(this.mainRenderTarget.width, this.mainRenderTarget.height);
            TinyFileDialogs.tinyfd_messageBox("Minecraft", stringbuilder.toString(), "ok", "error", false);
        } else if (this.options.fullscreen().get() && !this.window.isFullscreen()) {
            this.window.toggleFullScreen();
            this.options.fullscreen().set(this.window.isFullscreen());
        }

        net.neoforged.neoforge.client.ClientHooks.initClientHooks(this, this.resourceManager);
        this.window.updateVsync(this.options.enableVsync().get());
        this.window.updateRawMouseInput(this.options.rawMouseInput().get());
        this.window.setDefaultErrorCallback();
        this.resizeDisplay();
        this.gameRenderer.preloadUiShader(this.vanillaPackResources.asProvider());
        this.telemetryManager = new ClientTelemetryManager(this, this.userApiService, this.user);
        this.profileKeyPairManager = ProfileKeyPairManager.create(this.userApiService, this.user, path);
        this.narrator = new GameNarrator(this);
        this.narrator.checkStatus(this.options.narrator().get() != NarratorStatus.OFF);
        this.chatListener = new ChatListener(this);
        this.chatListener.setMessageDelay(this.options.chatDelay().get());
        this.reportingContext = ReportingContext.create(ReportEnvironment.local(), this.userApiService);
        LoadingOverlay.registerTextures(this);
        this.setScreen(new GenericMessageScreen(Component.translatable("gui.loadingMinecraft")));
        List<PackResources> list = this.resourcePackRepository.openAllSelected();
        this.reloadStateTracker.startReload(ResourceLoadStateTracker.ReloadReason.INITIAL, list);
        ReloadInstance reloadinstance = this.resourceManager.createReload(Util.backgroundExecutor(), this, RESOURCE_RELOAD_INITIAL_TASK, list);
        GameLoadTimesEvent.INSTANCE.beginStep(TelemetryProperty.LOAD_TIME_LOADING_OVERLAY_MS);
        Minecraft.GameLoadCookie minecraft$gameloadcookie = new Minecraft.GameLoadCookie(realmsclient, gameConfig.quickPlay);
        this.setOverlay(
            net.neoforged.fml.loading.ImmediateWindowHandler.<LoadingOverlay>loadingOverlay(
                () -> this, () -> reloadinstance, p_299779_ -> Util.ifElse(p_299779_, p_299772_ -> this.rollbackResourcePacks(p_299772_, minecraft$gameloadcookie), () -> {
                        if (SharedConstants.IS_RUNNING_IN_IDE && false) {
                            this.selfTest();
                        }

                        this.reloadStateTracker.finishReload();
                        this.onResourceLoadFinished(minecraft$gameloadcookie);


                    }), false
            ).get()
        );
        this.quickPlayLog = QuickPlayLog.of(gameConfig.quickPlay.path());
    }

    private void onResourceLoadFinished(@Nullable Minecraft.GameLoadCookie gameLoadCookie) {
        if (!this.gameLoadFinished) {
            this.gameLoadFinished = true;
            this.onGameLoadFinished(gameLoadCookie);
        }
    }

    private void onGameLoadFinished(@Nullable Minecraft.GameLoadCookie gameLoadCookie) {
        Runnable runnable = this.buildInitialScreens(gameLoadCookie);
        GameLoadTimesEvent.INSTANCE.endStep(TelemetryProperty.LOAD_TIME_LOADING_OVERLAY_MS);
        GameLoadTimesEvent.INSTANCE.endStep(TelemetryProperty.LOAD_TIME_TOTAL_TIME_MS);
        GameLoadTimesEvent.INSTANCE.send(this.telemetryManager.getOutsideSessionSender());
        runnable.run();
    }

    public boolean isGameLoadFinished() {
        return this.gameLoadFinished;
    }

    private Runnable buildInitialScreens(@Nullable Minecraft.GameLoadCookie gameLoadCookie) {
        List<Function<Runnable, Screen>> list = new ArrayList<>();
        this.addInitialScreens(list);
        Runnable runnable = () -> {
            if (gameLoadCookie != null && gameLoadCookie.quickPlayData().isEnabled()) {
                QuickPlay.connect(this, gameLoadCookie.quickPlayData(), gameLoadCookie.realmsClient());
            } else {
                this.setScreen(new TitleScreen(true));
            }
        };

        for (Function<Runnable, Screen> function : Lists.reverse(list)) {
            Screen screen = function.apply(runnable);
            runnable = () -> this.setScreen(screen);
        }

        runnable = net.neoforged.neoforge.client.loading.ClientModLoader.completeModLoading(runnable);

        return runnable;
    }

    private void addInitialScreens(List<Function<Runnable, Screen>> output) {
        if (this.options.onboardAccessibility) {
            output.add(p_299781_ -> new AccessibilityOnboardingScreen(this.options, p_299781_));
        }

        BanDetails bandetails = this.multiplayerBan();
        if (bandetails != null) {
            output.add(p_299775_ -> BanNoticeScreens.create(p_351634_ -> {
                    if (p_351634_) {
                        Util.getPlatform().openUri(CommonLinks.SUSPENSION_HELP);
                    }

                    p_299775_.run();
                }, bandetails));
        }

        ProfileResult profileresult = this.profileFuture.join();
        if (profileresult != null) {
            GameProfile gameprofile = profileresult.profile();
            Set<ProfileActionType> set = profileresult.actions();
            if (set.contains(ProfileActionType.FORCED_NAME_CHANGE)) {
                output.add(p_299783_ -> BanNoticeScreens.createNameBan(gameprofile.getName(), p_299783_));
            }

            if (set.contains(ProfileActionType.USING_BANNED_SKIN)) {
                output.add(BanNoticeScreens::createSkinBan);
            }
        }
    }

    private static boolean countryEqualsISO3(Object country) {
        try {
            return Locale.getDefault().getISO3Country().equals(country);
        } catch (MissingResourceException missingresourceexception) {
            return false;
        }
    }

    public void updateTitle() {
        this.window.setTitle(this.createTitle());
    }

    private String createTitle() {
        StringBuilder stringbuilder = new StringBuilder("Minecraft");
        if (checkModStatus().shouldReportAsModified()) {
            stringbuilder.append(' ').append(net.neoforged.neoforge.forge.snapshots.ForgeSnapshotsMod.BRANDING_NAME).append('*');
        }

        stringbuilder.append(" ");
        stringbuilder.append(SharedConstants.getCurrentVersion().getName());
        ClientPacketListener clientpacketlistener = this.getConnection();
        if (clientpacketlistener != null && clientpacketlistener.getConnection().isConnected()) {
            stringbuilder.append(" - ");
            ServerData serverdata = this.getCurrentServer();
            if (this.singleplayerServer != null && !this.singleplayerServer.isPublished()) {
                stringbuilder.append(I18n.get("title.singleplayer"));
            } else if (serverdata != null && serverdata.isRealm()) {
                stringbuilder.append(I18n.get("title.multiplayer.realms"));
            } else if (this.singleplayerServer == null && (serverdata == null || !serverdata.isLan())) {
                stringbuilder.append(I18n.get("title.multiplayer.other"));
            } else {
                stringbuilder.append(I18n.get("title.multiplayer.lan"));
            }
        }

        return stringbuilder.toString();
    }

    private UserApiService createUserApiService(YggdrasilAuthenticationService authenticationService, GameConfig gameConfig) {
        return gameConfig.user.user.getType() != User.Type.MSA ? UserApiService.OFFLINE : authenticationService.createUserApiService(gameConfig.user.user.getAccessToken());
    }

    public static ModCheck checkModStatus() {
        return ModCheck.identify("vanilla", ClientBrandRetriever::getClientModName, "Client", Minecraft.class);
    }

    private void rollbackResourcePacks(Throwable throwable, @Nullable Minecraft.GameLoadCookie gameLoadCookie) {
        if (this.resourcePackRepository.getSelectedPacks().stream().anyMatch(e -> !e.isRequired())) { //Forge: This caused infinite loop if any resource packs are forced. Such as mod resources. So check if we can disable any.
            this.clearResourcePacksOnError(throwable, null, gameLoadCookie);
        } else {
            Util.throwAsRuntime(throwable);
        }
    }

    public void clearResourcePacksOnError(Throwable throwable, @Nullable Component errorMessage, @Nullable Minecraft.GameLoadCookie gameLoadCookie) {
        LOGGER.info("Caught error loading resourcepacks, removing all selected resourcepacks", throwable);
        this.reloadStateTracker.startRecovery(throwable);
        this.downloadedPackSource.onRecovery();
        this.resourcePackRepository.setSelected(Collections.emptyList());
        this.options.resourcePacks.clear();
        this.options.incompatibleResourcePacks.clear();
        this.options.save();
        this.reloadResourcePacks(true, gameLoadCookie).thenRun(() -> this.addResourcePackLoadFailToast(errorMessage));
    }

    private void abortResourcePackRecovery() {
        this.setOverlay(null);
        if (this.level != null) {
            this.level.disconnect();
            this.disconnect();
        }

        this.setScreen(new TitleScreen());
        this.addResourcePackLoadFailToast(null);
    }

    private void addResourcePackLoadFailToast(@Nullable Component message) {
        ToastComponent toastcomponent = this.getToasts();
        SystemToast.addOrUpdate(toastcomponent, SystemToast.SystemToastId.PACK_LOAD_FAILURE, Component.translatable("resourcePack.load_fail"), message);
    }

    public void run() {
        this.gameThread = Thread.currentThread();
        if (Runtime.getRuntime().availableProcessors() > 4) {
            this.gameThread.setPriority(10);
        }

        try {
            boolean flag = false;

            while (this.running) {
                this.handleDelayedCrash();

                try {
                    SingleTickProfiler singletickprofiler = SingleTickProfiler.createTickProfiler("Renderer");
                    boolean flag1 = this.getDebugOverlay().showProfilerChart();
                    this.profiler = this.constructProfiler(flag1, singletickprofiler);
                    this.profiler.startTick();
                    this.metricsRecorder.startTick();
                    this.runTick(!flag);
                    this.metricsRecorder.endTick();
                    this.profiler.endTick();
                    this.finishProfilers(flag1, singletickprofiler);
                } catch (OutOfMemoryError outofmemoryerror) {
                    if (flag) {
                        throw outofmemoryerror;
                    }

                    this.emergencySave();
                    this.setScreen(new OutOfMemoryScreen());
                    System.gc();
                    LOGGER.error(LogUtils.FATAL_MARKER, "Out of memory", (Throwable)outofmemoryerror);
                    flag = true;
                }
            }
        } catch (ReportedException reportedexception) {
            LOGGER.error(LogUtils.FATAL_MARKER, "Reported exception thrown!", (Throwable)reportedexception);
            this.emergencySaveAndCrash(reportedexception.getReport());
        } catch (Throwable throwable) {
            LOGGER.error(LogUtils.FATAL_MARKER, "Unreported exception thrown!", throwable);
            this.emergencySaveAndCrash(new CrashReport("Unexpected error", throwable));
        }
    }

    void updateFontOptions() {
        this.fontManager.updateOptions(this.options);
    }

    private void onFullscreenError(int error, long description) {
        this.options.enableVsync().set(false);
        this.options.save();
    }

    public RenderTarget getMainRenderTarget() {
        return this.mainRenderTarget;
    }

    public String getLaunchedVersion() {
        return this.launchedVersion;
    }

    public String getVersionType() {
        return this.versionType;
    }

    public void delayCrash(CrashReport report) {
        this.delayedCrash = () -> this.fillReport(report);
    }

    public void delayCrashRaw(CrashReport report) {
        this.delayedCrash = () -> report;
    }

    private void handleDelayedCrash() {
        if (this.delayedCrash != null) {
            crash(this, this.gameDirectory, this.delayedCrash.get());
        }
    }

    public void emergencySaveAndCrash(CrashReport crashReport) {
        CrashReport crashreport = this.fillReport(crashReport);
        this.emergencySave();
        crash(this, this.gameDirectory, crashreport);
    }

    public static void crash(@Nullable Minecraft minecraft, File gameDirectory, CrashReport crashReport) {
        Path path = gameDirectory.toPath().resolve("crash-reports");
        Path path1 = path.resolve("crash-" + Util.getFilenameFormattedDateTime() + "-client.txt");
        Bootstrap.realStdoutPrintln(crashReport.getFriendlyReport(ReportType.CRASH));
        if (minecraft != null) {
            minecraft.soundManager.emergencyShutdown();
        }

        if (crashReport.getSaveFile() != null) {
            Bootstrap.realStdoutPrintln("#@!@# Game crashed! Crash report saved to: #@!@# " + crashReport.getSaveFile().toAbsolutePath());
            net.neoforged.neoforge.server.ServerLifecycleHooks.handleExit(-1);
        } else if (crashReport.saveToFile(path1, ReportType.CRASH)) {
            Bootstrap.realStdoutPrintln("#@!@# Game crashed! Crash report saved to: #@!@# " + path1.toAbsolutePath());
            net.neoforged.neoforge.server.ServerLifecycleHooks.handleExit(-1);
        } else {
            Bootstrap.realStdoutPrintln("#@?@# Game crashed! Crash report could not be saved. #@?@#");
            net.neoforged.neoforge.server.ServerLifecycleHooks.handleExit(-2);
        }
    }

    public boolean isEnforceUnicode() {
        return this.options.forceUnicodeFont().get();
    }

    public CompletableFuture<Void> reloadResourcePacks() {
        return this.reloadResourcePacks(false, null);
    }

    private CompletableFuture<Void> reloadResourcePacks(boolean error, @Nullable Minecraft.GameLoadCookie gameLoadCookie) {
        if (this.pendingReload != null) {
            return this.pendingReload;
        } else {
            CompletableFuture<Void> completablefuture = new CompletableFuture<>();
            if (!error && this.overlay instanceof LoadingOverlay) {
                this.pendingReload = completablefuture;
                return completablefuture;
            } else {
                this.resourcePackRepository.reload();
                List<PackResources> list = this.resourcePackRepository.openAllSelected();
                if (!error) {
                    this.reloadStateTracker.startReload(ResourceLoadStateTracker.ReloadReason.MANUAL, list);
                }

                this.setOverlay(
                    new LoadingOverlay(
                        this,
                        this.resourceManager.createReload(Util.backgroundExecutor(), this, RESOURCE_RELOAD_INITIAL_TASK, list),
                        p_299767_ -> Util.ifElse(p_299767_, p_314392_ -> {
                                if (error) {
                                    this.downloadedPackSource.onRecoveryFailure();
                                    this.abortResourcePackRecovery();
                                } else {
                                    this.rollbackResourcePacks(p_314392_, gameLoadCookie);
                                }
                            }, () -> {
                                this.levelRenderer.allChanged();
                                this.reloadStateTracker.finishReload();
                                this.downloadedPackSource.onReloadSuccess();
                                completablefuture.complete(null);
                                this.onResourceLoadFinished(gameLoadCookie);
                            }),
                        !error
                    )
                );
                return completablefuture;
            }
        }
    }

    private void selfTest() {
        boolean flag = false;
        BlockModelShaper blockmodelshaper = this.getBlockRenderer().getBlockModelShaper();
        BakedModel bakedmodel = blockmodelshaper.getModelManager().getMissingModel();

        for (Block block : BuiltInRegistries.BLOCK) {
            for (BlockState blockstate : block.getStateDefinition().getPossibleStates()) {
                if (blockstate.getRenderShape() == RenderShape.MODEL) {
                    BakedModel bakedmodel1 = blockmodelshaper.getBlockModel(blockstate);
                    if (bakedmodel1 == bakedmodel) {
                        LOGGER.debug("Missing model for: {}", blockstate);
                        flag = true;
                    }
                }
            }
        }

        TextureAtlasSprite textureatlassprite1 = bakedmodel.getParticleIcon();

        for (Block block1 : BuiltInRegistries.BLOCK) {
            for (BlockState blockstate1 : block1.getStateDefinition().getPossibleStates()) {
                TextureAtlasSprite textureatlassprite = blockmodelshaper.getParticleIcon(blockstate1);
                if (!blockstate1.isAir() && textureatlassprite == textureatlassprite1) {
                    LOGGER.debug("Missing particle icon for: {}", blockstate1);
                }
            }
        }

        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack itemstack = item.getDefaultInstance();
            String s = itemstack.getDescriptionId();
            String s1 = Component.translatable(s).getString();
            if (s1.toLowerCase(Locale.ROOT).equals(item.getDescriptionId())) {
                LOGGER.debug("Missing translation for: {} {} {}", itemstack, s, item);
            }
        }

        flag |= MenuScreens.selfTest();
        flag |= EntityRenderers.validateRegistrations();
        if (flag) {
            throw new IllegalStateException("Your game data is foobar, fix the errors above!");
        }
    }

    public LevelStorageSource getLevelSource() {
        return this.levelSource;
    }

    private void openChatScreen(String defaultText) {
        Minecraft.ChatStatus minecraft$chatstatus = this.getChatStatus();
        if (!minecraft$chatstatus.isChatAllowed(this.isLocalServer())) {
            if (this.gui.isShowingChatDisabledByPlayer()) {
                this.gui.setChatDisabledByPlayerShown(false);
                this.setScreen(new ConfirmLinkScreen(p_351635_ -> {
                    if (p_351635_) {
                        Util.getPlatform().openUri(CommonLinks.ACCOUNT_SETTINGS);
                    }

                    this.setScreen(null);
                }, Minecraft.ChatStatus.INFO_DISABLED_BY_PROFILE, CommonLinks.ACCOUNT_SETTINGS, true));
            } else {
                Component component = minecraft$chatstatus.getMessage();
                this.gui.setOverlayMessage(component, false);
                this.narrator.sayNow(component);
                this.gui.setChatDisabledByPlayerShown(minecraft$chatstatus == Minecraft.ChatStatus.DISABLED_BY_PROFILE);
            }
        } else {
            this.setScreen(new ChatScreen(defaultText));
        }
    }

    public void setScreen(@Nullable Screen guiScreen) {
        if (SharedConstants.IS_RUNNING_IN_IDE && Thread.currentThread() != this.gameThread) {
            LOGGER.error("setScreen called from non-game thread");
        }

        if (this.screen == null) {
            this.setLastInputType(InputType.NONE);
        }

        if (guiScreen == null && this.clientLevelTeardownInProgress) {
            throw new IllegalStateException("Trying to return to in-game GUI during disconnection");
        } else {
            if (guiScreen == null && this.level == null) {
                guiScreen = new TitleScreen();
            } else if (guiScreen == null && this.player.isDeadOrDying()) {
                if (this.player.shouldShowDeathScreen()) {
                    guiScreen = new DeathScreen(null, this.level.getLevelData().isHardcore());
                } else {
                    this.player.respawn();
                }
            }

        net.neoforged.neoforge.client.ClientHooks.clearGuiLayers(this);
        Screen old = this.screen;
        if (guiScreen != null) {
            var event = new net.neoforged.neoforge.client.event.ScreenEvent.Opening(old, guiScreen);
            if (net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event).isCanceled()) return;
            guiScreen = event.getNewScreen();
        }

        if (old != null && guiScreen != old) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ScreenEvent.Closing(old));
            old.removed();
        }

            this.screen = guiScreen;
            if (this.screen != null) {
                this.screen.added();
            }

            BufferUploader.reset();
            if (guiScreen != null) {
                this.mouseHandler.releaseMouse();
                KeyMapping.releaseAll();
                guiScreen.init(this, this.window.getGuiScaledWidth(), this.window.getGuiScaledHeight());
                this.noRender = false;
            } else {
                this.soundManager.resume();
                this.mouseHandler.grabMouse();
            }

            this.updateTitle();
        }
    }

    public void setOverlay(@Nullable Overlay loadingGui) {
        this.overlay = loadingGui;
    }

    public void destroy() {
        try {
            LOGGER.info("Stopping!");

            try {
                this.narrator.destroy();
            } catch (Throwable throwable1) {
            }

            try {
                if (this.level != null) {
                    this.level.disconnect();
                }

                this.disconnect();
            } catch (Throwable throwable) {
            }

            if (this.screen != null) {
                this.screen.removed();
            }

            this.close();
        } finally {
            Util.timeSource = System::nanoTime;
            if (this.delayedCrash == null) {
                System.exit(0);
            }
        }
    }

    @Override
    public void close() {
        if (this.currentFrameProfile != null) {
            this.currentFrameProfile.cancel();
        }

        try {
            this.telemetryManager.close();
            this.regionalCompliancies.close();
            this.modelManager.close();
            this.fontManager.close();
            this.gameRenderer.close();
            this.levelRenderer.close();
            this.soundManager.destroy();
            this.particleEngine.close();
            this.mobEffectTextures.close();
            this.paintingTextures.close();
            this.mapDecorationTextures.close();
            this.guiSprites.close();
            this.textureManager.close();
            this.resourceManager.close();
            FreeTypeUtil.destroy();
            Util.shutdownExecutors();
        } catch (Throwable throwable) {
            LOGGER.error("Shutdown failure!", throwable);
            throw throwable;
        } finally {
            this.virtualScreen.close();
            this.window.close();
        }
    }

    private void runTick(boolean renderLevel) {
        this.window.setErrorSection("Pre render");
        if (this.window.shouldClose()) {
            this.stop();
        }

        if (this.pendingReload != null && !(this.overlay instanceof LoadingOverlay)) {
            CompletableFuture<Void> completablefuture = this.pendingReload;
            this.pendingReload = null;
            this.reloadResourcePacks().thenRun(() -> completablefuture.complete(null));
        }

        Runnable runnable;
        while ((runnable = this.progressTasks.poll()) != null) {
            runnable.run();
        }

        int i = this.timer.advanceTime(Util.getMillis(), renderLevel);
        if (renderLevel) {
            this.profiler.push("scheduledExecutables");
            this.runAllTasks();
            this.profiler.pop();
            this.profiler.push("tick");

            for (int j = 0; j < Math.min(10, i); j++) {
                this.profiler.incrementCounter("clientTick");
                this.tick();
            }

            this.profiler.pop();
        }

        this.window.setErrorSection("Render");
        this.profiler.push("sound");
        this.soundManager.updateSource(this.gameRenderer.getMainCamera());
        this.profiler.pop();
        this.profiler.push("render");
        long i1 = Util.getNanos();
        boolean flag;
        if (!this.getDebugOverlay().showDebugScreen() && !this.metricsRecorder.isRecording()) {
            flag = false;
            this.gpuUtilization = 0.0;
        } else {
            flag = this.currentFrameProfile == null || this.currentFrameProfile.isDone();
            if (flag) {
                TimerQuery.getInstance().ifPresent(TimerQuery::beginProfile);
            }
        }

        RenderSystem.clear(16640, ON_OSX);
        this.mainRenderTarget.bindWrite(true);
        FogRenderer.setupNoFog();
        this.profiler.push("display");
        RenderSystem.enableCull();
        this.profiler.popPush("mouse");
        this.mouseHandler.handleAccumulatedMovement();
        this.profiler.pop();
        if (!this.noRender) {
            net.neoforged.neoforge.client.ClientHooks.fireRenderFramePre(this.timer);
            this.profiler.popPush("gameRenderer");
            this.gameRenderer.render(this.timer, renderLevel);
            this.profiler.pop();
            net.neoforged.neoforge.client.ClientHooks.fireRenderFramePost(this.timer);
        }

        if (this.fpsPieResults != null) {
            this.profiler.push("fpsPie");
            GuiGraphics guigraphics = new GuiGraphics(this, this.renderBuffers.bufferSource());
            this.renderFpsMeter(guigraphics, this.fpsPieResults);
            guigraphics.flush();
            this.profiler.pop();
        }

        this.profiler.push("blit");
        this.mainRenderTarget.unbindWrite();
        this.mainRenderTarget.blitToScreen(this.window.getWidth(), this.window.getHeight());
        this.frameTimeNs = Util.getNanos() - i1;
        if (flag) {
            TimerQuery.getInstance().ifPresent(p_231363_ -> this.currentFrameProfile = p_231363_.endProfile());
        }

        this.profiler.popPush("updateDisplay");
        this.window.updateDisplay();
        int j1 = this.getFramerateLimit();
        if (j1 < 260) {
            RenderSystem.limitDisplayFPS(j1);
        }

        this.profiler.popPush("yield");
        Thread.yield();
        this.profiler.pop();
        this.window.setErrorSection("Post render");
        this.frames++;
        boolean pause = this.hasSingleplayerServer()
            && (this.screen != null && this.screen.isPauseScreen() || this.overlay != null && this.overlay.isPauseScreen())
            && !this.singleplayerServer.isPublished();
        if (pause != this.pause && !net.neoforged.neoforge.client.ClientHooks.onClientPauseChangePre(pause)) {
            this.pause = pause;
            net.neoforged.neoforge.client.ClientHooks.onClientPauseChangePost(pause);
        }
        this.timer.updatePauseState(this.pause);
        this.timer.updateFrozenState(!this.isLevelRunningNormally());
        long k = Util.getNanos();
        long l = k - this.lastNanoTime;
        if (flag) {
            this.savedCpuDuration = l;
        }

        this.getDebugOverlay().logFrameDuration(l);
        this.lastNanoTime = k;
        this.profiler.push("fpsUpdate");
        if (this.currentFrameProfile != null && this.currentFrameProfile.isDone()) {
            this.gpuUtilization = (double)this.currentFrameProfile.get() * 100.0 / (double)this.savedCpuDuration;
        }

        while (Util.getMillis() >= this.lastTime + 1000L) {
            String s;
            if (this.gpuUtilization > 0.0) {
                s = " GPU: " + (this.gpuUtilization > 100.0 ? ChatFormatting.RED + "100%" : Math.round(this.gpuUtilization) + "%");
            } else {
                s = "";
            }

            fps = this.frames;
            this.fpsString = String.format(
                Locale.ROOT,
                "%d fps T: %s%s%s%s B: %d%s",
                fps,
                j1 == 260 ? "inf" : j1,
                this.options.enableVsync().get() ? " vsync " : " ",
                this.options.graphicsMode().get(),
                this.options.cloudStatus().get() == CloudStatus.OFF
                    ? ""
                    : (this.options.cloudStatus().get() == CloudStatus.FAST ? " fast-clouds" : " fancy-clouds"),
                this.options.biomeBlendRadius().get(),
                s
            );
            this.lastTime += 1000L;
            this.frames = 0;
        }

        this.profiler.pop();
    }

    private ProfilerFiller constructProfiler(boolean renderFpsPie, @Nullable SingleTickProfiler singleTickProfiler) {
        if (!renderFpsPie) {
            this.fpsPieProfiler.disable();
            if (!this.metricsRecorder.isRecording() && singleTickProfiler == null) {
                return InactiveProfiler.INSTANCE;
            }
        }

        ProfilerFiller profilerfiller;
        if (renderFpsPie) {
            if (!this.fpsPieProfiler.isEnabled()) {
                this.fpsPieRenderTicks = 0;
                this.fpsPieProfiler.enable();
            }

            this.fpsPieRenderTicks++;
            profilerfiller = this.fpsPieProfiler.getFiller();
        } else {
            profilerfiller = InactiveProfiler.INSTANCE;
        }

        if (this.metricsRecorder.isRecording()) {
            profilerfiller = ProfilerFiller.tee(profilerfiller, this.metricsRecorder.getProfiler());
        }

        return SingleTickProfiler.decorateFiller(profilerfiller, singleTickProfiler);
    }

    private void finishProfilers(boolean renderFpsPie, @Nullable SingleTickProfiler profiler) {
        if (profiler != null) {
            profiler.endTick();
        }

        if (renderFpsPie) {
            this.fpsPieResults = this.fpsPieProfiler.getResults();
        } else {
            this.fpsPieResults = null;
        }

        this.profiler = this.fpsPieProfiler.getFiller();
    }

    @Override
    public void resizeDisplay() {
        int i = this.window.calculateScale(this.options.guiScale().get(), this.isEnforceUnicode());
        this.window.setGuiScale((double)i);
        if (this.screen != null) {
            this.screen.resize(this, this.window.getGuiScaledWidth(), this.window.getGuiScaledHeight());
            net.neoforged.neoforge.client.ClientHooks.resizeGuiLayers(this, this.window.getGuiScaledWidth(), this.window.getGuiScaledHeight());
        }

        RenderTarget rendertarget = this.getMainRenderTarget();
        rendertarget.resize(this.window.getWidth(), this.window.getHeight(), ON_OSX);
        if (this.gameRenderer != null)
        this.gameRenderer.resize(this.window.getWidth(), this.window.getHeight());
        this.mouseHandler.setIgnoreFirstMove();
    }

    @Override
    public void cursorEntered() {
        this.mouseHandler.cursorEntered();
    }

    public int getFps() {
        return fps;
    }

    public long getFrameTimeNs() {
        return this.frameTimeNs;
    }

    private int getFramerateLimit() {
        return this.level != null || this.screen == null && this.overlay == null ? this.window.getFramerateLimit() : 60;
    }

    private void emergencySave() {
        try {
            MemoryReserve.release();
            this.levelRenderer.clear();
        } catch (Throwable throwable1) {
        }

        try {
            System.gc();
            if (this.isLocalServer && this.singleplayerServer != null) {
                this.singleplayerServer.halt(true);
            }

            this.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
        } catch (Throwable throwable) {
        }

        System.gc();
    }

    public boolean debugClientMetricsStart(Consumer<Component> logger) {
        if (this.metricsRecorder.isRecording()) {
            this.debugClientMetricsStop();
            return false;
        } else {
            Consumer<ProfileResults> consumer = p_231435_ -> {
                if (p_231435_ != EmptyProfileResults.EMPTY) {
                    int i = p_231435_.getTickDuration();
                    double d0 = (double)p_231435_.getNanoDuration() / (double)TimeUtil.NANOSECONDS_PER_SECOND;
                    this.execute(
                        () -> logger.accept(
                                Component.translatable(
                                    "commands.debug.stopped", String.format(Locale.ROOT, "%.2f", d0), i, String.format(Locale.ROOT, "%.2f", (double)i / d0)
                                )
                            )
                    );
                }
            };
            Consumer<Path> consumer1 = p_231438_ -> {
                Component component = Component.literal(p_231438_.toString())
                    .withStyle(ChatFormatting.UNDERLINE)
                    .withStyle(p_231387_ -> p_231387_.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, p_231438_.toFile().getParent())));
                this.execute(() -> logger.accept(Component.translatable("debug.profiling.stop", component)));
            };
            SystemReport systemreport = fillSystemReport(new SystemReport(), this, this.languageManager, this.launchedVersion, this.options);
            Consumer<List<Path>> consumer2 = p_231349_ -> {
                Path path = this.archiveProfilingReport(systemreport, p_231349_);
                consumer1.accept(path);
            };
            Consumer<Path> consumer3;
            if (this.singleplayerServer == null) {
                consumer3 = p_231404_ -> consumer2.accept(ImmutableList.of(p_231404_));
            } else {
                this.singleplayerServer.fillSystemReport(systemreport);
                CompletableFuture<Path> completablefuture = new CompletableFuture<>();
                CompletableFuture<Path> completablefuture1 = new CompletableFuture<>();
                CompletableFuture.allOf(completablefuture, completablefuture1)
                    .thenRunAsync(() -> consumer2.accept(ImmutableList.of(completablefuture.join(), completablefuture1.join())), Util.ioPool());
                this.singleplayerServer.startRecordingMetrics(p_231351_ -> {
                }, completablefuture1::complete);
                consumer3 = completablefuture::complete;
            }

            this.metricsRecorder = ActiveMetricsRecorder.createStarted(
                new ClientMetricsSamplersProvider(Util.timeSource, this.levelRenderer),
                Util.timeSource,
                Util.ioPool(),
                new MetricsPersister("client"),
                p_231401_ -> {
                    this.metricsRecorder = InactiveMetricsRecorder.INSTANCE;
                    consumer.accept(p_231401_);
                },
                consumer3
            );
            return true;
        }
    }

    private void debugClientMetricsStop() {
        this.metricsRecorder.end();
        if (this.singleplayerServer != null) {
            this.singleplayerServer.finishRecordingMetrics();
        }
    }

    private void debugClientMetricsCancel() {
        this.metricsRecorder.cancel();
        if (this.singleplayerServer != null) {
            this.singleplayerServer.cancelRecordingMetrics();
        }
    }

    private Path archiveProfilingReport(SystemReport report, List<Path> paths) {
        String s;
        if (this.isLocalServer()) {
            s = this.getSingleplayerServer().getWorldData().getLevelName();
        } else {
            ServerData serverdata = this.getCurrentServer();
            s = serverdata != null ? serverdata.name : "unknown";
        }

        Path path;
        try {
            String s2 = String.format(Locale.ROOT, "%s-%s-%s", Util.getFilenameFormattedDateTime(), s, SharedConstants.getCurrentVersion().getId());
            String s1 = FileUtil.findAvailableName(MetricsPersister.PROFILING_RESULTS_DIR, s2, ".zip");
            path = MetricsPersister.PROFILING_RESULTS_DIR.resolve(s1);
        } catch (IOException ioexception1) {
            throw new UncheckedIOException(ioexception1);
        }

        try (FileZipper filezipper = new FileZipper(path)) {
            filezipper.add(Paths.get("system.txt"), report.toLineSeparatedString());
            filezipper.add(Paths.get("client").resolve(this.options.getFile().getName()), this.options.dumpOptionsForReport());
            paths.forEach(filezipper::add);
        } finally {
            for (Path path1 : paths) {
                try {
                    FileUtils.forceDelete(path1.toFile());
                } catch (IOException ioexception) {
                    LOGGER.warn("Failed to delete temporary profiling result {}", path1, ioexception);
                }
            }
        }

        return path;
    }

    /**
     * Update debugProfilerName in response to number keys in debug screen
     */
    public void debugFpsMeterKeyPress(int keyCount) {
        if (this.fpsPieResults != null) {
            List<ResultField> list = this.fpsPieResults.getTimes(this.debugPath);
            if (!list.isEmpty()) {
                ResultField resultfield = list.remove(0);
                if (keyCount == 0) {
                    if (!resultfield.name.isEmpty()) {
                        int i = this.debugPath.lastIndexOf(30);
                        if (i >= 0) {
                            this.debugPath = this.debugPath.substring(0, i);
                        }
                    }
                } else {
                    keyCount--;
                    if (keyCount < list.size() && !"unspecified".equals(list.get(keyCount).name)) {
                        if (!this.debugPath.isEmpty()) {
                            this.debugPath = this.debugPath + "\u001e";
                        }

                        this.debugPath = this.debugPath + list.get(keyCount).name;
                    }
                }
            }
        }
    }

    private void renderFpsMeter(GuiGraphics guiGraphics, ProfileResults profileResults) {
        List<ResultField> list = profileResults.getTimes(this.debugPath);
        ResultField resultfield = list.removeFirst();
        RenderSystem.clear(256, ON_OSX);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f matrix4f = new Matrix4f().setOrtho(0.0F, (float)this.window.getWidth(), (float)this.window.getHeight(), 0.0F, 1000.0F, 3000.0F);
        RenderSystem.setProjectionMatrix(matrix4f, VertexSorting.ORTHOGRAPHIC_Z);
        Tesselator tesselator = Tesselator.getInstance();
        Matrix4fStack matrix4fstack = RenderSystem.getModelViewStack();
        matrix4fstack.pushMatrix();
        matrix4fstack.translation(0.0F, 0.0F, -2000.0F);
        RenderSystem.applyModelViewMatrix();
        int i = 160;
        int j = this.window.getWidth() - 160 - 10;
        int k = this.window.getHeight() - 320;
        double d0 = 0.0;

        for (ResultField resultfield1 : list) {
            int l = Mth.floor(resultfield1.percentage / 4.0) + 1;
            BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
            int i1 = FastColor.ARGB32.opaque(resultfield1.getColor());
            int j1 = FastColor.ARGB32.multiply(i1, -8355712);
            bufferbuilder.addVertex((float)j, (float)k, 0.0F).setColor(i1);

            for (int k1 = l; k1 >= 0; k1--) {
                float f = (float)((d0 + resultfield1.percentage * (double)k1 / (double)l) * (float) (Math.PI * 2) / 100.0);
                float f1 = Mth.sin(f) * 160.0F;
                float f2 = Mth.cos(f) * 160.0F * 0.5F;
                bufferbuilder.addVertex((float)j + f1, (float)k - f2, 0.0F).setColor(i1);
            }

            BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
            bufferbuilder = tesselator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

            for (int j2 = l; j2 >= 0; j2--) {
                float f3 = (float)((d0 + resultfield1.percentage * (double)j2 / (double)l) * (float) (Math.PI * 2) / 100.0);
                float f4 = Mth.sin(f3) * 160.0F;
                float f5 = Mth.cos(f3) * 160.0F * 0.5F;
                if (!(f5 > 0.0F)) {
                    bufferbuilder.addVertex((float)j + f4, (float)k - f5, 0.0F).setColor(j1);
                    bufferbuilder.addVertex((float)j + f4, (float)k - f5 + 10.0F, 0.0F).setColor(j1);
                }
            }

            MeshData meshdata = bufferbuilder.build();
            if (meshdata != null) {
                BufferUploader.drawWithShader(meshdata);
            }

            d0 += resultfield1.percentage;
        }

        DecimalFormat decimalformat = new DecimalFormat("##0.00");
        decimalformat.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.ROOT));
        String s = ProfileResults.demanglePath(resultfield.name);
        String s1 = "";
        if (!"unspecified".equals(s)) {
            s1 = s1 + "[0] ";
        }

        if (s.isEmpty()) {
            s1 = s1 + "ROOT ";
        } else {
            s1 = s1 + s + " ";
        }

        int i2 = 16777215;
        guiGraphics.drawString(this.font, s1, j - 160, k - 80 - 16, 16777215);
        s1 = decimalformat.format(resultfield.globalPercentage) + "%";
        guiGraphics.drawString(this.font, s1, j + 160 - this.font.width(s1), k - 80 - 16, 16777215);

        for (int l1 = 0; l1 < list.size(); l1++) {
            ResultField resultfield2 = list.get(l1);
            StringBuilder stringbuilder = new StringBuilder();
            if ("unspecified".equals(resultfield2.name)) {
                stringbuilder.append("[?] ");
            } else {
                stringbuilder.append("[").append(l1 + 1).append("] ");
            }

            String s2 = stringbuilder.append(resultfield2.name).toString();
            guiGraphics.drawString(this.font, s2, j - 160, k + 80 + l1 * 8 + 20, resultfield2.getColor());
            s2 = decimalformat.format(resultfield2.percentage) + "%";
            guiGraphics.drawString(this.font, s2, j + 160 - 50 - this.font.width(s2), k + 80 + l1 * 8 + 20, resultfield2.getColor());
            s2 = decimalformat.format(resultfield2.globalPercentage) + "%";
            guiGraphics.drawString(this.font, s2, j + 160 - this.font.width(s2), k + 80 + l1 * 8 + 20, resultfield2.getColor());
        }

        matrix4fstack.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    public void stop() {
        if (this.isRunning()) net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.GameShuttingDownEvent());
        this.running = false;
    }

    public boolean isRunning() {
        return this.running;
    }

    /**
     * Displays the ingame menu
     */
    public void pauseGame(boolean pauseOnly) {
        if (this.screen == null) {
            boolean flag = this.hasSingleplayerServer() && !this.singleplayerServer.isPublished();
            if (flag) {
                this.setScreen(new PauseScreen(!pauseOnly));
                this.soundManager.pause();
            } else {
                this.setScreen(new PauseScreen(true));
            }
        }
    }

    private void continueAttack(boolean leftClick) {
        if (!leftClick) {
            this.missTime = 0;
        }

        if (this.missTime <= 0 && !this.player.isUsingItem()) {
            if (leftClick && this.hitResult != null && this.hitResult.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockhitresult = (BlockHitResult)this.hitResult;
                BlockPos blockpos = blockhitresult.getBlockPos();
                if (!this.level.getBlockState(blockpos).isAir()) {
                    var inputEvent = net.neoforged.neoforge.client.ClientHooks.onClickInput(0, this.options.keyAttack, InteractionHand.MAIN_HAND);
                    if (inputEvent.isCanceled()) {
                        if (inputEvent.shouldSwingHand()) {
                            this.particleEngine.addBlockHitEffects(blockpos, blockhitresult);
                            this.player.swing(InteractionHand.MAIN_HAND);
                        }
                        return;
                    }
                    Direction direction = blockhitresult.getDirection();
                    if (this.gameMode.continueDestroyBlock(blockpos, direction) && inputEvent.shouldSwingHand()) {
                        this.particleEngine.addBlockHitEffects(blockpos, blockhitresult);
                        this.player.swing(InteractionHand.MAIN_HAND);
                    }
                }
            } else {
                this.gameMode.stopDestroyBlock();
            }
        }
    }

    private boolean startAttack() {
        if (this.missTime > 0) {
            return false;
        } else if (this.hitResult == null) {
            LOGGER.error("Null returned as 'hitResult', this shouldn't happen!");
            if (this.gameMode.hasMissTime()) {
                this.missTime = 10;
            }

            return false;
        } else if (this.player.isHandsBusy()) {
            return false;
        } else {
            ItemStack itemstack = this.player.getItemInHand(InteractionHand.MAIN_HAND);
            if (!itemstack.isItemEnabled(this.level.enabledFeatures())) {
                return false;
            } else {
                boolean flag = false;
                var inputEvent = net.neoforged.neoforge.client.ClientHooks.onClickInput(0, this.options.keyAttack, InteractionHand.MAIN_HAND);
                if (!inputEvent.isCanceled())
                switch (this.hitResult.getType()) {
                    case ENTITY:
                        this.gameMode.attack(this.player, ((EntityHitResult)this.hitResult).getEntity());
                        break;
                    case BLOCK:
                        BlockHitResult blockhitresult = (BlockHitResult)this.hitResult;
                        BlockPos blockpos = blockhitresult.getBlockPos();
                        if (!this.level.getBlockState(blockpos).isAir()) {
                            this.gameMode.startDestroyBlock(blockpos, blockhitresult.getDirection());
                            if (this.level.getBlockState(blockpos).isAir()) {
                                flag = true;
                            }
                            break;
                        }
                    case MISS:
                        if (this.gameMode.hasMissTime()) {
                            this.missTime = 10;
                        }

                        this.player.resetAttackStrengthTicker();
                        net.neoforged.neoforge.common.CommonHooks.onEmptyLeftClick(this.player);
                }

                if (inputEvent.shouldSwingHand())
                this.player.swing(InteractionHand.MAIN_HAND);
                return flag;
            }
        }
    }

    private void startUseItem() {
        if (!this.gameMode.isDestroying()) {
            this.rightClickDelay = 4;
            if (!this.player.isHandsBusy()) {
                if (this.hitResult == null) {
                    LOGGER.warn("Null returned as 'hitResult', this shouldn't happen!");
                }

                for (InteractionHand interactionhand : InteractionHand.values()) {
                    var inputEvent = net.neoforged.neoforge.client.ClientHooks.onClickInput(1, this.options.keyUse, interactionhand);
                    if (inputEvent.isCanceled()) {
                        if (inputEvent.shouldSwingHand()) this.player.swing(interactionhand);
                        return;
                    }
                    ItemStack itemstack = this.player.getItemInHand(interactionhand);
                    if (!itemstack.isItemEnabled(this.level.enabledFeatures())) {
                        return;
                    }

                    if (this.hitResult != null) {
                        switch (this.hitResult.getType()) {
                            case ENTITY:
                                EntityHitResult entityhitresult = (EntityHitResult)this.hitResult;
                                Entity entity = entityhitresult.getEntity();
                                if (!this.level.getWorldBorder().isWithinBounds(entity.blockPosition())) {
                                    return;
                                }

                                InteractionResult interactionresult = this.gameMode.interactAt(this.player, entity, entityhitresult, interactionhand);
                                if (!interactionresult.consumesAction()) {
                                    interactionresult = this.gameMode.interact(this.player, entity, interactionhand);
                                }

                                if (interactionresult.consumesAction()) {
                                    if (interactionresult.shouldSwing() && inputEvent.shouldSwingHand()) {
                                        this.player.swing(interactionhand);
                                    }

                                    return;
                                }
                                break;
                            case BLOCK:
                                BlockHitResult blockhitresult = (BlockHitResult)this.hitResult;
                                int i = itemstack.getCount();
                                InteractionResult interactionresult1 = this.gameMode.useItemOn(this.player, interactionhand, blockhitresult);
                                if (interactionresult1.consumesAction()) {
                                    if (interactionresult1.shouldSwing() && inputEvent.shouldSwingHand()) {
                                        this.player.swing(interactionhand);
                                        if (!itemstack.isEmpty() && (itemstack.getCount() != i || this.gameMode.hasInfiniteItems())) {
                                            this.gameRenderer.itemInHandRenderer.itemUsed(interactionhand);
                                        }
                                    }

                                    return;
                                }

                                if (interactionresult1 == InteractionResult.FAIL) {
                                    return;
                                }
                        }
                    }

                    if (itemstack.isEmpty() && (this.hitResult == null || this.hitResult.getType() == HitResult.Type.MISS))
                        net.neoforged.neoforge.common.CommonHooks.onEmptyClick(this.player, interactionhand);

                    if (!itemstack.isEmpty()) {
                        InteractionResult interactionresult2 = this.gameMode.useItem(this.player, interactionhand);
                        if (interactionresult2.consumesAction()) {
                            if (interactionresult2.shouldSwing()) {
                                this.player.swing(interactionhand);
                            }

                            this.gameRenderer.itemInHandRenderer.itemUsed(interactionhand);
                            return;
                        }
                    }
                }
            }
        }
    }

    public MusicManager getMusicManager() {
        return this.musicManager;
    }

    public void tick() {
        this.clientTickCount++;
        net.neoforged.neoforge.client.ClientHooks.fireClientTickPre();

        if (this.level != null && !this.pause) {
            this.level.tickRateManager().tick();
        }

        if (this.rightClickDelay > 0) {
            this.rightClickDelay--;
        }

        this.profiler.push("gui");
        this.chatListener.tick();
        this.gui.tick(this.pause);
        this.profiler.pop();
        this.gameRenderer.pick(1.0F);
        this.tutorial.onLookAt(this.level, this.hitResult);
        this.profiler.push("gameMode");
        if (!this.pause && this.level != null) {
            this.gameMode.tick();
        }

        this.profiler.popPush("textures");
        if (this.isLevelRunningNormally()) {
            this.textureManager.tick();
        }

        if (this.screen != null || this.player == null) {
            if (this.screen instanceof InBedChatScreen inbedchatscreen && !this.player.isSleeping()) {
                inbedchatscreen.onPlayerWokeUp();
            }
        } else if (this.player.isDeadOrDying() && !(this.screen instanceof DeathScreen)) {
            this.setScreen(null);
        } else if (this.player.isSleeping() && this.level != null) {
            this.setScreen(new InBedChatScreen());
        }

        if (this.screen != null) {
            this.missTime = 10000;
        }

        if (this.screen != null) {
            Screen.wrapScreenError(() -> this.screen.tick(), "Ticking screen", this.screen.getClass().getCanonicalName());
        }

        if (!this.getDebugOverlay().showDebugScreen()) {
            this.gui.clearCache();
        }

        if (this.overlay == null && this.screen == null) {
            this.profiler.popPush("Keybindings");
            this.handleKeybinds();
            if (this.missTime > 0) {
                this.missTime--;
            }
        }

        if (this.level != null) {
            this.profiler.popPush("gameRenderer");
            if (!this.pause) {
                this.gameRenderer.tick();
            }

            this.profiler.popPush("levelRenderer");
            if (!this.pause) {
                this.levelRenderer.tick();
            }

            this.profiler.popPush("level");
            if (!this.pause) {
                this.level.tickEntities();
            }
        } else if (this.gameRenderer.currentEffect() != null) {
            this.gameRenderer.shutdownEffect();
        }

        if (!this.pause) {
            this.musicManager.tick();
        }

        this.soundManager.tick(this.pause);
        if (this.level != null) {
            if (!this.pause) {
                if (!this.options.joinedFirstServer && this.isMultiplayerServer()) {
                    Component component = Component.translatable("tutorial.socialInteractions.title");
                    Component component1 = Component.translatable("tutorial.socialInteractions.description", Tutorial.key("socialInteractions"));
                    this.socialInteractionsToast = new TutorialToast(TutorialToast.Icons.SOCIAL_INTERACTIONS, component, component1, true);
                    this.tutorial.addTimedToast(this.socialInteractionsToast, 160);
                    this.options.joinedFirstServer = true;
                    this.options.save();
                }

                this.tutorial.tick();

                net.neoforged.neoforge.event.EventHooks.fireLevelTickPre(this.level, () -> true);
                try {
                    this.level.tick(() -> true);
                } catch (Throwable throwable) {
                    CrashReport crashreport = CrashReport.forThrowable(throwable, "Exception in world tick");
                    if (this.level == null) {
                        CrashReportCategory crashreportcategory = crashreport.addCategory("Affected level");
                        crashreportcategory.setDetail("Problem", "Level is null!");
                    } else {
                        this.level.fillReportDetails(crashreport);
                    }

                    throw new ReportedException(crashreport);
                }
                net.neoforged.neoforge.event.EventHooks.fireLevelTickPost(this.level, () -> true);
            }

            this.profiler.popPush("animateTick");
            if (!this.pause && this.isLevelRunningNormally()) {
                this.level.animateTick(this.player.getBlockX(), this.player.getBlockY(), this.player.getBlockZ());
            }

            this.profiler.popPush("particles");
            if (!this.pause && this.isLevelRunningNormally()) {
                this.particleEngine.tick();
            }
        } else if (this.pendingConnection != null) {
            this.profiler.popPush("pendingConnection");
            this.pendingConnection.tick();
        }

        this.profiler.popPush("keyboard");
        this.keyboardHandler.tick();
        this.profiler.pop();

        net.neoforged.neoforge.client.ClientHooks.fireClientTickPost();
    }

    private boolean isLevelRunningNormally() {
        return this.level == null || this.level.tickRateManager().runsNormally();
    }

    private boolean isMultiplayerServer() {
        return !this.isLocalServer || this.singleplayerServer != null && this.singleplayerServer.isPublished();
    }

    private void handleKeybinds() {
        while (this.options.keyTogglePerspective.consumeClick()) {
            CameraType cameratype = this.options.getCameraType();
            this.options.setCameraType(this.options.getCameraType().cycle());
            if (cameratype.isFirstPerson() != this.options.getCameraType().isFirstPerson()) {
                this.gameRenderer.checkEntityPostEffect(this.options.getCameraType().isFirstPerson() ? this.getCameraEntity() : null);
            }

            this.levelRenderer.needsUpdate();
        }

        while (this.options.keySmoothCamera.consumeClick()) {
            this.options.smoothCamera = !this.options.smoothCamera;
        }

        for (int i = 0; i < 9; i++) {
            boolean flag = this.options.keySaveHotbarActivator.isDown();
            boolean flag1 = this.options.keyLoadHotbarActivator.isDown();
            if (this.options.keyHotbarSlots[i].consumeClick()) {
                if (this.player.isSpectator()) {
                    this.gui.getSpectatorGui().onHotbarSelected(i);
                } else if (!this.player.isCreative() || this.screen != null || !flag1 && !flag) {
                    this.player.getInventory().selected = i;
                } else {
                    CreativeModeInventoryScreen.handleHotbarLoadOrSave(this, i, flag1, flag);
                }
            }
        }

        while (this.options.keySocialInteractions.consumeClick()) {
            if (!this.isMultiplayerServer()) {
                this.player.displayClientMessage(SOCIAL_INTERACTIONS_NOT_AVAILABLE, true);
                this.narrator.sayNow(SOCIAL_INTERACTIONS_NOT_AVAILABLE);
            } else {
                if (this.socialInteractionsToast != null) {
                    this.tutorial.removeTimedToast(this.socialInteractionsToast);
                    this.socialInteractionsToast = null;
                }

                this.setScreen(new SocialInteractionsScreen());
            }
        }

        while (this.options.keyInventory.consumeClick()) {
            if (this.gameMode.isServerControlledInventory()) {
                this.player.sendOpenInventory();
            } else {
                this.tutorial.onOpenInventory();
                this.setScreen(new InventoryScreen(this.player));
            }
        }

        while (this.options.keyAdvancements.consumeClick()) {
            this.setScreen(new AdvancementsScreen(this.player.connection.getAdvancements()));
        }

        while (this.options.keySwapOffhand.consumeClick()) {
            if (!this.player.isSpectator()) {
                this.getConnection()
                    .send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
            }
        }

        while (this.options.keyDrop.consumeClick()) {
            if (!this.player.isSpectator() && this.player.drop(Screen.hasControlDown())) {
                this.player.swing(InteractionHand.MAIN_HAND);
            }
        }

        while (this.options.keyChat.consumeClick()) {
            this.openChatScreen("");
        }

        if (this.screen == null && this.overlay == null && this.options.keyCommand.consumeClick()) {
            this.openChatScreen("/");
        }

        boolean flag2 = false;
        if (this.player.isUsingItem()) {
            if (!this.options.keyUse.isDown()) {
                this.gameMode.releaseUsingItem(this.player);
            }

            while (this.options.keyAttack.consumeClick()) {
            }

            while (this.options.keyUse.consumeClick()) {
            }

            while (this.options.keyPickItem.consumeClick()) {
            }
        } else {
            while (this.options.keyAttack.consumeClick()) {
                flag2 |= this.startAttack();
            }

            while (this.options.keyUse.consumeClick()) {
                this.startUseItem();
            }

            while (this.options.keyPickItem.consumeClick()) {
                this.pickBlock();
            }
        }

        if (this.options.keyUse.isDown() && this.rightClickDelay == 0 && !this.player.isUsingItem()) {
            this.startUseItem();
        }

        this.continueAttack(this.screen == null && !flag2 && this.options.keyAttack.isDown() && this.mouseHandler.isMouseGrabbed());
    }

    public ClientTelemetryManager getTelemetryManager() {
        return this.telemetryManager;
    }

    public double getGpuUtilization() {
        return this.gpuUtilization;
    }

    public ProfileKeyPairManager getProfileKeyPairManager() {
        return this.profileKeyPairManager;
    }

    public WorldOpenFlows createWorldOpenFlows() {
        return new WorldOpenFlows(this, this.levelSource);
    }

    public void doWorldLoad(LevelStorageSource.LevelStorageAccess levelStorage, PackRepository packRepository, WorldStem worldStem, boolean newWorld) {
        this.disconnect();
        this.progressListener.set(null);
        Instant instant = Instant.now();

        try {
            levelStorage.saveDataTag(worldStem.registries().compositeAccess(), worldStem.worldData());
            Services services = Services.create(this.authenticationService, this.gameDirectory);
            services.profileCache().setExecutor(this);
            SkullBlockEntity.setup(services, this);
            GameProfileCache.setUsesAuthentication(false);
            this.singleplayerServer = MinecraftServer.spin(
                p_231361_ -> new IntegratedServer(p_231361_, this, levelStorage, packRepository, worldStem, services, p_319374_ -> {
                        StoringChunkProgressListener storingchunkprogresslistener = StoringChunkProgressListener.createFromGameruleRadius(p_319374_ + 0);
                        this.progressListener.set(storingchunkprogresslistener);
                        return ProcessorChunkProgressListener.createStarted(storingchunkprogresslistener, this.progressTasks::add);
                    })
            );
            this.isLocalServer = true;
            this.updateReportEnvironment(ReportEnvironment.local());
            this.quickPlayLog.setWorldData(QuickPlayLog.Type.SINGLEPLAYER, levelStorage.getLevelId(), worldStem.worldData().getLevelName());
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Starting integrated server");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Starting integrated server");
            crashreportcategory.setDetail("Level ID", levelStorage.getLevelId());
            crashreportcategory.setDetail("Level Name", () -> worldStem.worldData().getLevelName());
            throw new ReportedException(crashreport);
        }

        while (this.progressListener.get() == null) {
            Thread.yield();
        }

        LevelLoadingScreen levelloadingscreen = new LevelLoadingScreen(this.progressListener.get());
        this.setScreen(levelloadingscreen);
        this.profiler.push("waitForServer");

        for (; !this.singleplayerServer.isReady() || this.overlay != null; this.handleDelayedCrash()) {
            levelloadingscreen.tick();
            this.runTick(false);

            try {
                Thread.sleep(16L);
            } catch (InterruptedException interruptedexception) {
            }
        }

        this.profiler.pop();
        Duration duration = Duration.between(instant, Instant.now());
        SocketAddress socketaddress = this.singleplayerServer.getConnection().startMemoryChannel();
        Connection connection = Connection.connectToLocalServer(socketaddress);
        connection.initiateServerboundPlayConnection(
            socketaddress.toString(), 0, new ClientHandshakePacketListenerImpl(connection, this, null, null, newWorld, duration, p_231442_ -> {
            }, null)
        );
        connection.send(new ServerboundHelloPacket(this.getUser().getName(), this.getUser().getProfileId()));
        this.pendingConnection = connection;
    }

    public void setLevel(ClientLevel level, ReceivingLevelScreen.Reason reason) {
        if (this.level != null) net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.LevelEvent.Unload(this.level));
        this.updateScreenAndTick(net.neoforged.neoforge.client.DimensionTransitionScreenManager.getScreenFromLevel(level, this.level).create(() -> false, reason));
        this.level = level;
        this.updateLevelInEngines(level);
        if (!this.isLocalServer) {
            Services services = Services.create(this.authenticationService, this.gameDirectory);
            services.profileCache().setExecutor(this);
            SkullBlockEntity.setup(services, this);
            GameProfileCache.setUsesAuthentication(false);
        }
    }

    public void disconnect() {
        this.disconnect(new ProgressScreen(true), false);
    }

    public void disconnect(Screen nextScreen) {
        this.disconnect(nextScreen, false);
    }

    public void disconnect(Screen nextScreen, boolean keepResourcePacks) {
        ClientPacketListener clientpacketlistener = this.getConnection();
        if (clientpacketlistener != null) {
            this.dropAllTasks();
            clientpacketlistener.close();
            if (!keepResourcePacks) {
                this.clearDownloadedResourcePacks();
            }
        }

        this.playerSocialManager.stopOnlineMode();
        if (this.metricsRecorder.isRecording()) {
            this.debugClientMetricsCancel();
        }

        IntegratedServer integratedserver = this.singleplayerServer;
        this.singleplayerServer = null;
        this.gameRenderer.resetData();
        net.neoforged.neoforge.client.ClientHooks.firePlayerLogout(this.gameMode, this.player);
        this.gameMode = null;
        this.narrator.clear();
        this.clientLevelTeardownInProgress = true;

        var shouldRevertRegistriesToFrozen = this.getConnection() != null && this.getConnection().getConnection() != null; // Neo: Track whether to revert registries after disconnect
        try {
            this.updateScreenAndTick(nextScreen);
            if (this.level != null) {
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.LevelEvent.Unload(this.level));
                if (integratedserver != null) {
                    this.profiler.push("waitForServer");

                    while (!integratedserver.isShutdown()) {
                        this.runTick(false);
                    }

                    this.profiler.pop();
                }

                this.gui.onDisconnected();
                this.isLocalServer = false;
            }

            this.level = null;
            this.updateLevelInEngines(null);
            this.player = null;
        } finally {
            this.clientLevelTeardownInProgress = false;
        }

        SkullBlockEntity.clear();
        if(shouldRevertRegistriesToFrozen) net.neoforged.neoforge.registries.RegistryManager.revertToFrozen(); // Neo: Revert registries to frozen on disconnect
    }

    public void clearDownloadedResourcePacks() {
        this.downloadedPackSource.cleanupAfterDisconnect();
        this.runAllTasks();
    }

    public void clearClientLevel(Screen nextScreen) {
        ClientPacketListener clientpacketlistener = this.getConnection();
        if (clientpacketlistener != null) {
            clientpacketlistener.clearLevel();
        }

        if (this.metricsRecorder.isRecording()) {
            this.debugClientMetricsCancel();
        }

        this.gameRenderer.resetData();
        this.gameMode = null;
        this.narrator.clear();
        this.clientLevelTeardownInProgress = true;

        try {
            this.updateScreenAndTick(nextScreen);
            this.gui.onDisconnected();
            this.level = null;
            this.updateLevelInEngines(null);
            this.player = null;
        } finally {
            this.clientLevelTeardownInProgress = false;
        }

        SkullBlockEntity.clear();
    }

    private void updateScreenAndTick(Screen screen) {
        this.profiler.push("forcedTick");
        this.soundManager.stop();
        this.cameraEntity = null;
        this.pendingConnection = null;
        this.setScreen(screen);
        this.runTick(false);
        this.profiler.pop();
    }

    public void forceSetScreen(Screen screen) {
        this.profiler.push("forcedTick");
        this.setScreen(screen);
        this.runTick(false);
        this.profiler.pop();
    }

    private void updateLevelInEngines(@Nullable ClientLevel level) {
        this.levelRenderer.setLevel(level);
        this.particleEngine.setLevel(level);
        this.blockEntityRenderDispatcher.setLevel(level);
        this.updateTitle();
    }

    private UserProperties userProperties() {
        return this.userPropertiesFuture.join();
    }

    public boolean telemetryOptInExtra() {
        return this.extraTelemetryAvailable() && this.options.telemetryOptInExtra().get();
    }

    public boolean extraTelemetryAvailable() {
        return this.allowsTelemetry() && this.userProperties().flag(UserFlag.OPTIONAL_TELEMETRY_AVAILABLE);
    }

    public boolean allowsTelemetry() {
        return SharedConstants.IS_RUNNING_IN_IDE ? false : this.userProperties().flag(UserFlag.TELEMETRY_ENABLED);
    }

    public boolean allowsMultiplayer() {
        return this.allowsMultiplayer && this.userProperties().flag(UserFlag.SERVERS_ALLOWED) && this.multiplayerBan() == null && !this.isNameBanned();
    }

    public boolean allowsRealms() {
        return this.userProperties().flag(UserFlag.REALMS_ALLOWED) && this.multiplayerBan() == null;
    }

    @Nullable
    public BanDetails multiplayerBan() {
        return this.userProperties().bannedScopes().get("MULTIPLAYER");
    }

    public boolean isNameBanned() {
        ProfileResult profileresult = this.profileFuture.getNow(null);
        return profileresult != null && profileresult.actions().contains(ProfileActionType.FORCED_NAME_CHANGE);
    }

    public boolean isBlocked(UUID playerUUID) {
        return this.getChatStatus().isChatAllowed(false)
            ? this.playerSocialManager.shouldHideMessageFrom(playerUUID)
            : (this.player == null || !playerUUID.equals(this.player.getUUID())) && !playerUUID.equals(Util.NIL_UUID);
    }

    public Minecraft.ChatStatus getChatStatus() {
        if (this.options.chatVisibility().get() == ChatVisiblity.HIDDEN) {
            return Minecraft.ChatStatus.DISABLED_BY_OPTIONS;
        } else if (!this.allowsChat) {
            return Minecraft.ChatStatus.DISABLED_BY_LAUNCHER;
        } else {
            return !this.userProperties().flag(UserFlag.CHAT_ALLOWED) ? Minecraft.ChatStatus.DISABLED_BY_PROFILE : Minecraft.ChatStatus.ENABLED;
        }
    }

    public final boolean isDemo() {
        return this.demo;
    }

    @Nullable
    public ClientPacketListener getConnection() {
        return this.player == null ? null : this.player.connection;
    }

    public static boolean renderNames() {
        return !instance.options.hideGui;
    }

    public static boolean useFancyGraphics() {
        return instance.options.graphicsMode().get().getId() >= GraphicsStatus.FANCY.getId();
    }

    public static boolean useShaderTransparency() {
        return !instance.gameRenderer.isPanoramicMode() && instance.options.graphicsMode().get().getId() >= GraphicsStatus.FABULOUS.getId();
    }

    public static boolean useAmbientOcclusion() {
        return instance.options.ambientOcclusion().get();
    }

    private void pickBlock() {
        if (this.hitResult != null && this.hitResult.getType() != HitResult.Type.MISS) {
            if (net.neoforged.neoforge.client.ClientHooks.onClickInput(2, this.options.keyPickItem, InteractionHand.MAIN_HAND).isCanceled()) return;
            boolean flag = this.player.getAbilities().instabuild;
            BlockEntity blockentity = null;
            HitResult.Type hitresult$type = this.hitResult.getType();
            ItemStack itemstack;
            if (hitresult$type == HitResult.Type.BLOCK) {
                BlockPos blockpos = ((BlockHitResult)this.hitResult).getBlockPos();
                BlockState blockstate = this.level.getBlockState(blockpos);
                if (blockstate.isAir()) {
                    return;
                }

                Block block = blockstate.getBlock();
                itemstack = blockstate.getCloneItemStack(this.hitResult, this.level, blockpos, this.player);
                if (itemstack.isEmpty()) {
                    return;
                }

                if (flag && Screen.hasControlDown() && blockstate.hasBlockEntity()) {
                    blockentity = this.level.getBlockEntity(blockpos);
                }
            } else {
                if (hitresult$type != HitResult.Type.ENTITY || !flag) {
                    return;
                }

                Entity entity = ((EntityHitResult)this.hitResult).getEntity();
                itemstack = entity.getPickedResult(this.hitResult);
                if (itemstack == null) {
                    return;
                }
            }

            if (itemstack.isEmpty()) {
                String s = "";
                if (hitresult$type == HitResult.Type.BLOCK) {
                    s = BuiltInRegistries.BLOCK.getKey(this.level.getBlockState(((BlockHitResult)this.hitResult).getBlockPos()).getBlock()).toString();
                } else if (hitresult$type == HitResult.Type.ENTITY) {
                    s = BuiltInRegistries.ENTITY_TYPE.getKey(((EntityHitResult)this.hitResult).getEntity().getType()).toString();
                }

                LOGGER.warn("Picking on: [{}] {} gave null item", hitresult$type, s);
            } else {
                Inventory inventory = this.player.getInventory();
                if (blockentity != null) {
                    this.addCustomNbtData(itemstack, blockentity, this.level.registryAccess());
                }

                int i = inventory.findSlotMatchingItem(itemstack);
                if (flag) {
                    inventory.setPickedItem(itemstack);
                    this.gameMode.handleCreativeModeItemAdd(this.player.getItemInHand(InteractionHand.MAIN_HAND), 36 + inventory.selected);
                } else if (i != -1) {
                    if (Inventory.isHotbarSlot(i)) {
                        inventory.selected = i;
                    } else {
                        this.gameMode.handlePickItem(i);
                    }
                }
            }
        }
    }

    private void addCustomNbtData(ItemStack stack, BlockEntity blockEntity, RegistryAccess registryAccess) {
        CompoundTag compoundtag = blockEntity.saveCustomAndMetadata(registryAccess);
        blockEntity.removeComponentsFromTag(compoundtag);
        BlockItem.setBlockEntityData(stack, blockEntity.getType(), compoundtag);
        stack.applyComponents(blockEntity.collectComponents());
    }

    /**
     * Adds core server Info (GL version, Texture pack, isModded, type), and the worldInfo to the crash report.
     */
    public CrashReport fillReport(CrashReport theCrash) {
        SystemReport systemreport = theCrash.getSystemReport();
        fillSystemReport(systemreport, this, this.languageManager, this.launchedVersion, this.options);
        this.fillUptime(theCrash.addCategory("Uptime"));
        if (this.level != null) {
            this.level.fillReportDetails(theCrash);
        }

        if (this.singleplayerServer != null) {
            this.singleplayerServer.fillSystemReport(systemreport);
        }

        this.reloadStateTracker.fillCrashReport(theCrash);
        return theCrash;
    }

    public static void fillReport(
        @Nullable Minecraft minecraft, @Nullable LanguageManager languageManager, String launchVersion, @Nullable Options options, CrashReport report
    ) {
        SystemReport systemreport = report.getSystemReport();
        fillSystemReport(systemreport, minecraft, languageManager, launchVersion, options);
    }

    private static String formatSeconds(double seconds) {
        return String.format(Locale.ROOT, "%.3fs", seconds);
    }

    private void fillUptime(CrashReportCategory category) {
        category.setDetail("JVM uptime", () -> formatSeconds((double)ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0));
        category.setDetail("Wall uptime", () -> formatSeconds((double)(System.currentTimeMillis() - this.clientStartTimeMs) / 1000.0));
        category.setDetail("High-res time", () -> formatSeconds((double)Util.getMillis() / 1000.0));
        category.setDetail("Client ticks", () -> String.format(Locale.ROOT, "%d ticks / %.3fs", this.clientTickCount, (double)this.clientTickCount / 20.0));
    }

    private static SystemReport fillSystemReport(
        SystemReport report, @Nullable Minecraft minecraft, @Nullable LanguageManager languageManager, String launchVersion, @Nullable Options options
    ) {
        report.setDetail("Launched Version", () -> launchVersion);
        String s = getLauncherBrand();
        if (s != null) {
            report.setDetail("Launcher name", s);
        }

        report.setDetail("Backend library", RenderSystem::getBackendDescription);
        report.setDetail("Backend API", RenderSystem::getApiDescription);
        report.setDetail("Window size", () -> minecraft != null ? minecraft.window.getWidth() + "x" + minecraft.window.getHeight() : "<not initialized>");
        report.setDetail("GFLW Platform", Window::getPlatform);
        report.setDetail("GL Caps", RenderSystem::getCapsString);
        report.setDetail("GL debug messages", () -> GlDebug.isDebugEnabled() ? String.join("\n", GlDebug.getLastOpenGlDebugMessages()) : "<disabled>");
        report.setDetail("Is Modded", () -> checkModStatus().fullDescription());
        report.setDetail("Universe", () -> minecraft != null ? Long.toHexString(minecraft.canary) : "404");
        report.setDetail("Type", "Client (map_client.txt)");
        if (options != null) {
            if (minecraft != null) {
                String s1 = minecraft.getGpuWarnlistManager().getAllWarnings();
                if (s1 != null) {
                    report.setDetail("GPU Warnings", s1);
                }
            }

            report.setDetail("Graphics mode", options.graphicsMode().get().toString());
            report.setDetail("Render Distance", options.getEffectiveRenderDistance() + "/" + options.renderDistance().get() + " chunks");
        }

        if (minecraft != null) {
            report.setDetail("Resource Packs", () -> PackRepository.displayPackList(minecraft.getResourcePackRepository().getSelectedPacks()));
        }

        if (languageManager != null) {
            report.setDetail("Current Language", () -> languageManager.getSelected());
        }

        report.setDetail("Locale", String.valueOf(Locale.getDefault()));
        report.setDetail("System encoding", () -> System.getProperty("sun.jnu.encoding", "<not set>"));
        report.setDetail("File encoding", () -> System.getProperty("file.encoding", "<not set>"));
        report.setDetail("CPU", GlUtil::getCpuInfo);
        return report;
    }

    public static Minecraft getInstance() {
        return instance;
    }

    public CompletableFuture<Void> delayTextureReload() {
        return this.<CompletableFuture<Void>>submit((Supplier<CompletableFuture<Void>>)this::reloadResourcePacks).thenCompose(p_231391_ -> (CompletionStage<Void>)p_231391_);
    }

    public void updateReportEnvironment(ReportEnvironment reportEnvironment) {
        if (!this.reportingContext.matches(reportEnvironment)) {
            this.reportingContext = ReportingContext.create(reportEnvironment, this.userApiService);
        }
    }

    @Nullable
    public ServerData getCurrentServer() {
        return Optionull.map(this.getConnection(), ClientPacketListener::getServerData);
    }

    public boolean isLocalServer() {
        return this.isLocalServer;
    }

    public boolean hasSingleplayerServer() {
        return this.isLocalServer && this.singleplayerServer != null;
    }

    @Nullable
    public IntegratedServer getSingleplayerServer() {
        return this.singleplayerServer;
    }

    public boolean isSingleplayer() {
        IntegratedServer integratedserver = this.getSingleplayerServer();
        return integratedserver != null && !integratedserver.isPublished();
    }

    public boolean isLocalPlayer(UUID uuid) {
        return uuid.equals(this.getUser().getProfileId());
    }

    public User getUser() {
        return this.user;
    }

    public GameProfile getGameProfile() {
        ProfileResult profileresult = this.profileFuture.join();
        return profileresult != null ? profileresult.profile() : new GameProfile(this.user.getProfileId(), this.user.getName());
    }

    public Proxy getProxy() {
        return this.proxy;
    }

    public TextureManager getTextureManager() {
        return this.textureManager;
    }

    public ResourceManager getResourceManager() {
        return this.resourceManager;
    }

    public PackRepository getResourcePackRepository() {
        return this.resourcePackRepository;
    }

    public VanillaPackResources getVanillaPackResources() {
        return this.vanillaPackResources;
    }

    public DownloadedPackSource getDownloadedPackSource() {
        return this.downloadedPackSource;
    }

    public Path getResourcePackDirectory() {
        return this.resourcePackDirectory;
    }

    public LanguageManager getLanguageManager() {
        return this.languageManager;
    }

    public Function<ResourceLocation, TextureAtlasSprite> getTextureAtlas(ResourceLocation location) {
        return this.modelManager.getAtlas(location)::getSprite;
    }

    public boolean isPaused() {
        return this.pause;
    }

    public GpuWarnlistManager getGpuWarnlistManager() {
        return this.gpuWarnlistManager;
    }

    public SoundManager getSoundManager() {
        return this.soundManager;
    }

    public Music getSituationalMusic() {
        Music music = Optionull.map(this.screen, Screen::getBackgroundMusic);
        if (music != null) {
            return music;
        } else if (this.player != null) {
            if (this.player.level().dimension() == Level.END) {
                return this.gui.getBossOverlay().shouldPlayMusic() ? Musics.END_BOSS : Musics.END;
            } else {
                Holder<Biome> holder = this.player.level().getBiome(this.player.blockPosition());
                if (!this.musicManager.isPlayingMusic(Musics.UNDER_WATER) && (!this.player.isUnderWater() || !holder.is(BiomeTags.PLAYS_UNDERWATER_MUSIC))) {
                    return this.player.level().dimension() != Level.NETHER && this.player.getAbilities().instabuild && this.player.getAbilities().mayfly
                        ? Musics.CREATIVE
                        : holder.value().getBackgroundMusic().orElse(Musics.GAME);
                } else {
                    return Musics.UNDER_WATER;
                }
            }
        } else {
            return Musics.MENU;
        }
    }

    public MinecraftSessionService getMinecraftSessionService() {
        return this.minecraftSessionService;
    }

    public SkinManager getSkinManager() {
        return this.skinManager;
    }

    @Nullable
    public Entity getCameraEntity() {
        return this.cameraEntity;
    }

    public void setCameraEntity(Entity viewingEntity) {
        this.cameraEntity = viewingEntity;
        this.gameRenderer.checkEntityPostEffect(viewingEntity);
    }

    public boolean shouldEntityAppearGlowing(Entity entity) {
        return entity.isCurrentlyGlowing()
            || this.player != null && this.player.isSpectator() && this.options.keySpectatorOutlines.isDown() && entity.getType() == EntityType.PLAYER;
    }

    @Override
    protected Thread getRunningThread() {
        return this.gameThread;
    }

    @Override
    protected Runnable wrapRunnable(Runnable runnable) {
        return runnable;
    }

    @Override
    protected boolean shouldRun(Runnable runnable) {
        return true;
    }

    public BlockRenderDispatcher getBlockRenderer() {
        return this.blockRenderer;
    }

    public EntityRenderDispatcher getEntityRenderDispatcher() {
        return this.entityRenderDispatcher;
    }

    public BlockEntityRenderDispatcher getBlockEntityRenderDispatcher() {
        return this.blockEntityRenderDispatcher;
    }

    public ItemRenderer getItemRenderer() {
        return this.itemRenderer;
    }

    public DataFixer getFixerUpper() {
        return this.fixerUpper;
    }

    public DeltaTracker getTimer() {
        return this.timer;
    }

    public BlockColors getBlockColors() {
        return this.blockColors;
    }

    public boolean showOnlyReducedInfo() {
        return this.player != null && this.player.isReducedDebugInfo() || this.options.reducedDebugInfo().get();
    }

    public ToastComponent getToasts() {
        return this.toast;
    }

    public Tutorial getTutorial() {
        return this.tutorial;
    }

    public boolean isWindowActive() {
        return this.windowActive;
    }

    public HotbarManager getHotbarManager() {
        return this.hotbarManager;
    }

    public ModelManager getModelManager() {
        return this.modelManager;
    }

    public PaintingTextureManager getPaintingTextures() {
        return this.paintingTextures;
    }

    public MobEffectTextureManager getMobEffectTextures() {
        return this.mobEffectTextures;
    }

    public MapDecorationTextureManager getMapDecorationTextures() {
        return this.mapDecorationTextures;
    }

    public GuiSpriteManager getGuiSprites() {
        return this.guiSprites;
    }

    @Override
    public void setWindowActive(boolean focused) {
        this.windowActive = focused;
    }

    public Component grabPanoramixScreenshot(File gameDirectory, int width, int height) {
        int i = this.window.getWidth();
        int j = this.window.getHeight();
        RenderTarget rendertarget = new TextureTarget(width, height, true, ON_OSX);
        float f = this.player.getXRot();
        float f1 = this.player.getYRot();
        float f2 = this.player.xRotO;
        float f3 = this.player.yRotO;
        this.gameRenderer.setRenderBlockOutline(false);

        MutableComponent mutablecomponent;
        try {
            this.gameRenderer.setPanoramicMode(true);
            this.levelRenderer.graphicsChanged();
            this.window.setWidth(width);
            this.window.setHeight(height);

            for (int k = 0; k < 6; k++) {
                switch (k) {
                    case 0:
                        this.player.setYRot(f1);
                        this.player.setXRot(0.0F);
                        break;
                    case 1:
                        this.player.setYRot((f1 + 90.0F) % 360.0F);
                        this.player.setXRot(0.0F);
                        break;
                    case 2:
                        this.player.setYRot((f1 + 180.0F) % 360.0F);
                        this.player.setXRot(0.0F);
                        break;
                    case 3:
                        this.player.setYRot((f1 - 90.0F) % 360.0F);
                        this.player.setXRot(0.0F);
                        break;
                    case 4:
                        this.player.setYRot(f1);
                        this.player.setXRot(-90.0F);
                        break;
                    case 5:
                    default:
                        this.player.setYRot(f1);
                        this.player.setXRot(90.0F);
                }

                this.player.yRotO = this.player.getYRot();
                this.player.xRotO = this.player.getXRot();
                rendertarget.bindWrite(true);
                this.gameRenderer.renderLevel(DeltaTracker.ONE);

                try {
                    Thread.sleep(10L);
                } catch (InterruptedException interruptedexception) {
                }

                Screenshot.grab(gameDirectory, "panorama_" + k + ".png", rendertarget, p_231415_ -> {
                });
            }

            Component component = Component.literal(gameDirectory.getName())
                .withStyle(ChatFormatting.UNDERLINE)
                .withStyle(p_231426_ -> p_231426_.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, gameDirectory.getAbsolutePath())));
            return Component.translatable("screenshot.success", component);
        } catch (Exception exception) {
            LOGGER.error("Couldn't save image", (Throwable)exception);
            mutablecomponent = Component.translatable("screenshot.failure", exception.getMessage());
        } finally {
            this.player.setXRot(f);
            this.player.setYRot(f1);
            this.player.xRotO = f2;
            this.player.yRotO = f3;
            this.gameRenderer.setRenderBlockOutline(true);
            this.window.setWidth(i);
            this.window.setHeight(j);
            rendertarget.destroyBuffers();
            this.gameRenderer.setPanoramicMode(false);
            this.levelRenderer.graphicsChanged();
            this.getMainRenderTarget().bindWrite(true);
        }

        return mutablecomponent;
    }

    private Component grabHugeScreenshot(File gameDirectory, int columnWidth, int rowHeight, int width, int height) {
        try {
            ByteBuffer bytebuffer = GlUtil.allocateMemory(columnWidth * rowHeight * 3);
            Screenshot screenshot = new Screenshot(gameDirectory, width, height, rowHeight);
            float f = (float)width / (float)columnWidth;
            float f1 = (float)height / (float)rowHeight;
            float f2 = f > f1 ? f : f1;

            for (int i = (height - 1) / rowHeight * rowHeight; i >= 0; i -= rowHeight) {
                for (int j = 0; j < width; j += columnWidth) {
                    RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
                    float f3 = (float)(width - columnWidth) / 2.0F * 2.0F - (float)(j * 2);
                    float f4 = (float)(height - rowHeight) / 2.0F * 2.0F - (float)(i * 2);
                    f3 /= (float)columnWidth;
                    f4 /= (float)rowHeight;
                    this.gameRenderer.renderZoomed(f2, f3, f4);
                    bytebuffer.clear();
                    RenderSystem.pixelStore(3333, 1);
                    RenderSystem.pixelStore(3317, 1);
                    RenderSystem.readPixels(0, 0, columnWidth, rowHeight, 32992, 5121, bytebuffer);
                    screenshot.addRegion(bytebuffer, j, i, columnWidth, rowHeight);
                }

                screenshot.saveRow();
            }

            File file1 = screenshot.close();
            GlUtil.freeMemory(bytebuffer);
            Component component = Component.literal(file1.getName())
                .withStyle(ChatFormatting.UNDERLINE)
                .withStyle(p_231379_ -> p_231379_.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file1.getAbsolutePath())));
            return Component.translatable("screenshot.success", component);
        } catch (Exception exception) {
            LOGGER.warn("Couldn't save screenshot", (Throwable)exception);
            return Component.translatable("screenshot.failure", exception.getMessage());
        }
    }

    public ProfilerFiller getProfiler() {
        return this.profiler;
    }

    @Nullable
    public StoringChunkProgressListener getProgressListener() {
        return this.progressListener.get();
    }

    public SplashManager getSplashManager() {
        return this.splashManager;
    }

    @Nullable
    public Overlay getOverlay() {
        return this.overlay;
    }

    public PlayerSocialManager getPlayerSocialManager() {
        return this.playerSocialManager;
    }

    public Window getWindow() {
        return this.window;
    }

    public DebugScreenOverlay getDebugOverlay() {
        return this.gui.getDebugOverlay();
    }

    public RenderBuffers renderBuffers() {
        return this.renderBuffers;
    }

    public void updateMaxMipLevel(int mipMapLevel) {
        this.modelManager.updateMaxMipLevel(mipMapLevel);
    }

    public ItemColors getItemColors() {
        return this.itemColors;
    }

    public EntityModelSet getEntityModels() {
        return this.entityModels;
    }

    public boolean isTextFilteringEnabled() {
        return this.userProperties().flag(UserFlag.PROFANITY_FILTER_ENABLED);
    }

    public void prepareForMultiplayer() {
        this.playerSocialManager.startOnlineMode();
        this.getProfileKeyPairManager().prepareKeyPair();
    }

    @Nullable
    public SignatureValidator getProfileKeySignatureValidator() {
        return SignatureValidator.from(this.authenticationService.getServicesKeySet(), ServicesKeyType.PROFILE_KEY);
    }

    public boolean canValidateProfileKeys() {
        return !this.authenticationService.getServicesKeySet().keys(ServicesKeyType.PROFILE_KEY).isEmpty();
    }

    public InputType getLastInputType() {
        return this.lastInputType;
    }

    public void setLastInputType(InputType lastInputType) {
        this.lastInputType = lastInputType;
    }

    public GameNarrator getNarrator() {
        return this.narrator;
    }

    public ChatListener getChatListener() {
        return this.chatListener;
    }

    public ReportingContext getReportingContext() {
        return this.reportingContext;
    }

    public RealmsDataFetcher realmsDataFetcher() {
        return this.realmsDataFetcher;
    }

    public QuickPlayLog quickPlayLog() {
        return this.quickPlayLog;
    }

    public CommandHistory commandHistory() {
        return this.commandHistory;
    }

    public DirectoryValidator directoryValidator() {
        return this.directoryValidator;
    }

    private float getTickTargetMillis(float defaultValue) {
        if (this.level != null) {
            TickRateManager tickratemanager = this.level.tickRateManager();
            if (tickratemanager.runsNormally()) {
                return Math.max(defaultValue, tickratemanager.millisecondsPerTick());
            }
        }

        return defaultValue;
    }

    @Nullable
    public static String getLauncherBrand() {
        return System.getProperty("minecraft.launcher.brand");
    }

    @OnlyIn(Dist.CLIENT)
    public static enum ChatStatus {
        ENABLED(CommonComponents.EMPTY) {
            @Override
            public boolean isChatAllowed(boolean p_168045_) {
                return true;
            }
        },
        DISABLED_BY_OPTIONS(Component.translatable("chat.disabled.options").withStyle(ChatFormatting.RED)) {
            @Override
            public boolean isChatAllowed(boolean p_168051_) {
                return false;
            }
        },
        DISABLED_BY_LAUNCHER(Component.translatable("chat.disabled.launcher").withStyle(ChatFormatting.RED)) {
            @Override
            public boolean isChatAllowed(boolean p_168057_) {
                return p_168057_;
            }
        },
        DISABLED_BY_PROFILE(
            Component.translatable("chat.disabled.profile", Component.keybind(Minecraft.instance.options.keyChat.getName())).withStyle(ChatFormatting.RED)
        ) {
            @Override
            public boolean isChatAllowed(boolean p_168063_) {
                return p_168063_;
            }
        };

        static final Component INFO_DISABLED_BY_PROFILE = Component.translatable("chat.disabled.profile.moreInfo");
        private final Component message;

        ChatStatus(Component message) {
            this.message = message;
        }

        public Component getMessage() {
            return this.message;
        }

        public abstract boolean isChatAllowed(boolean isLocalServer);
    }

    @OnlyIn(Dist.CLIENT)
    static record GameLoadCookie(RealmsClient realmsClient, GameConfig.QuickPlayData quickPlayData) {
    }
}

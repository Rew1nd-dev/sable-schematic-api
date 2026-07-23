package dev.rew1nd.sableschematicapi.survival.camera.client;

import dev.rew1nd.sableschematicapi.survival.camera.CameraState;
import dev.rew1nd.sableschematicapi.tool.SableSchematicApiItems;
import dev.rew1nd.sableschematicapi.survival.camera.client.ui.CameraUiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Main-hand camera viewfinder input, FOV transition, and frame overlay. */
public final class CameraClientEvents {
    private static final int HOLD_TICKS = 5;
    private static int heldUseTicks;
    private static boolean viewfinder;
    private static int targetFov = CameraState.DEFAULT_FOV;
    private static float displayedFov = CameraState.DEFAULT_FOV;
    private static List<CameraViewfinderCandidates.Candidate> candidates = List.of();
    private static Set<UUID> capturedBodyIds = Set.of();
    private static UUID capturedId;
    private static int previewUntilTick;

    private CameraClientEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(CameraClientEvents::onInteraction);
        NeoForge.EVENT_BUS.addListener(CameraClientEvents::onMouseScroll);
        NeoForge.EVENT_BUS.addListener(CameraClientEvents::onTick);
        NeoForge.EVENT_BUS.addListener(CameraClientEvents::onComputeFov);
        NeoForge.EVENT_BUS.addListener(CameraClientEvents::onRenderGui);
        NeoForge.EVENT_BUS.addListener(CameraClientEvents::onRenderGuiLayer);
        NeoForge.EVENT_BUS.addListener(CameraClientEvents::onRenderHand);
        CameraBodyOutlineRenderer.registerEvents();
    }

    private static void onInteraction(final InputEvent.InteractionKeyMappingTriggered event) {
        final Minecraft minecraft = Minecraft.getInstance();
        final Player player = minecraft.player;
        if (player == null || minecraft.screen != null || !holdingCamera(player)) {
            return;
        }
        if (event.isUseItem()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (!event.isAttack()) {
            return;
        }
        if (viewfinder) {
            event.setCanceled(true);
            event.setSwingHand(false);
            CameraClientSession.requestCapture(targetFov);
            reset(false);
        } else if (player.getAbilities().instabuild && player.hasPermissions(2)) {
            event.setCanceled(true);
            event.setSwingHand(false);
            CameraClientSession.requestPlace(CameraState.read(player.getMainHandItem()));
        }
    }

    private static void onMouseScroll(final InputEvent.MouseScrollingEvent event) {
        if (!viewfinder) {
            return;
        }
        final int delta = event.getScrollDeltaY() > 0.0D ? -5 : event.getScrollDeltaY() < 0.0D ? 5 : 0;
        if (delta == 0) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        final int maxFov = Math.max(CameraState.MIN_FOV, minecraft.options.fov().get());
        targetFov = Math.clamp(targetFov + delta, CameraState.MIN_FOV, maxFov);
        event.setCanceled(true);
    }

    private static void onTick(final ClientTickEvent.Post event) {
        final Minecraft minecraft = Minecraft.getInstance();
        final Player player = minecraft.player;
        if (player == null || minecraft.screen != null || !holdingCamera(player)) {
            reset(true);
            CameraBodyOutlineRenderer.update(Set.of(), Set.of());
            return;
        }
        if (minecraft.options.keyUse.isDown()) {
            if (++heldUseTicks >= HOLD_TICKS && !viewfinder) {
                viewfinder = true;
                targetFov = Math.min(CameraState.read(player.getMainHandItem()).targetFov(), minecraft.options.fov().get());
            }
        } else if (heldUseTicks > 0 || viewfinder) {
            reset(true);
        }
        while (CameraKeyMappings.OPEN_CAMERA.consumeClick()) {
            CameraUiRenderer.open();
        }
        candidates = viewfinder ? CameraViewfinderCandidates.find(player, targetFov) : List.of();
        if (minecraft.level == null || minecraft.level.getGameTime() >= previewUntilTick) {
            previewUntilTick = 0;
        }
        CameraBodyOutlineRenderer.update(
                candidates.stream().map(CameraViewfinderCandidates.Candidate::id).collect(java.util.stream.Collectors.toSet()),
                previewUntilTick > 0 ? capturedBodyIds : Set.of()
        );
    }

    private static void onComputeFov(final ViewportEvent.ComputeFov event) {
        final Minecraft minecraft = Minecraft.getInstance();
        final Player player = minecraft.player;
        final float wanted = viewfinder ? targetFov : minecraft.options.fov().get();
        displayedFov += (wanted - displayedFov) * 0.2F;
        if (player != null && holdingCamera(player) && (viewfinder || Math.abs(displayedFov - wanted) > 0.05F)) {
            event.setFOV(displayedFov);
        }
    }

    private static void onRenderGui(final RenderGuiEvent.Post event) {
        if (!viewfinder) {
            return;
        }
        final GuiGraphics graphics = event.getGuiGraphics();
        final int width = graphics.guiWidth();
        final int height = graphics.guiHeight();
        final int frameWidth = Math.min((int) (width * 0.70F), (int) (height * (16.0F / 9.0F)));
        final int frameHeight = frameWidth * 9 / 16;
        final int left = (width - frameWidth) / 2;
        final int top = (height - frameHeight) / 2;
        final int color = 0xFFE7B64D;
        graphics.fill(left, top, left + frameWidth, top + 1, color);
        graphics.fill(left, top + frameHeight - 1, left + frameWidth, top + frameHeight, color);
        graphics.fill(left, top, left + 1, top + frameHeight, color);
        graphics.fill(left + frameWidth - 1, top, left + frameWidth, top + frameHeight, color);
    }

    private static void onRenderGuiLayer(final RenderGuiLayerEvent.Pre event) {
        if (!viewfinder) {
            return;
        }
        if (event.getName().equals(VanillaGuiLayers.HOTBAR) || event.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
            event.setCanceled(true);
        }
    }

    private static void onRenderHand(final RenderHandEvent event) {
        if (viewfinder) {
            event.setCanceled(true);
        }
    }

    static void recordCapture(final UUID captureId, final List<UUID> bodyIds) {
        if (captureId == null || bodyIds == null || bodyIds.isEmpty()) {
            return;
        }
        capturedId = captureId;
        capturedBodyIds = Set.copyOf(bodyIds);
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            previewUntilTick = (int) minecraft.level.getGameTime() + 100;
        }
    }

    public static void showCachedPreview(final UUID captureId) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (captureId != null && captureId.equals(capturedId) && minecraft.level != null) {
            previewUntilTick = (int) minecraft.level.getGameTime() + 100;
        }
    }

    private static boolean holdingCamera(final Player player) {
        return player.getMainHandItem().is(SableSchematicApiItems.CAMERA.get());
    }

    private static void reset(final boolean syncFov) {
        if (syncFov && viewfinder) {
            CameraClientSession.requestFov(targetFov);
        }
        heldUseTicks = 0;
        viewfinder = false;
        candidates = List.of();
    }
}

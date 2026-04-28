package dev.rew1nd.sableschematicapi.tool.client;

import dev.rew1nd.sableschematicapi.network.SableSchematicApiPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BlueprintToolClientSession {
    private static final String KEY_PREFIX = "sable_schematic_api.blueprint_tool.";

    private static Vec3 start;
    private static Vec3 end;
    private static BlueprintToolLocalFiles.Entry selectedBlueprint;
    private static List<BlueprintToolSubLevelEntry> subLevels = List.of();
    private static UUID selectedSubLevel;
    private static UUID openedSubLevelDetail;
    private static ResourceLocation currentModeId = BlueprintToolModes.BLUEPRINT.id();
    private static Component status = tr("status.ready");
    private static String detail = "";
    private static int localBlueprintRevision;
    private static int subLevelRevision;

    private BlueprintToolClientSession() {
    }

    public static void advanceSelection(final Player player) {
        clearLoadSelection();
        if (start == null) {
            start = player.position();
            end = null;
            setStatusKey("start_set");
            notify(player, status, ChatFormatting.AQUA);
            return;
        }

        if (end == null) {
            end = player.position();
            setStatusKey("end_set");
            notify(player, status, ChatFormatting.GREEN);
            return;
        }

        clearSelection(player);
    }

    public static BlueprintToolMode currentMode() {
        return BlueprintToolModes.byId(currentModeId);
    }

    public static void setMode(final BlueprintToolMode mode) {
        if (mode == null) {
            return;
        }
        currentModeId = mode.id();
        if (BlueprintToolModes.SUBLEVELS.id().equals(mode.id())) {
            requestSubLevelRefresh();
        }
    }

    public static Component modeLabel() {
        return currentMode().label();
    }

    public static BlueprintToolInputResult handleInput(final Player player,
                                                       final InteractionHand hand,
                                                       final BlueprintToolInputIntent intent) {
        return currentMode().handleInput(new BlueprintToolInputContext(player, hand, intent));
    }

    public static void clearSelection(final Player player) {
        start = null;
        end = null;
        clearLoadSelection();
        setStatusKey("cleared");
        notify(player, status, ChatFormatting.YELLOW);
    }

    public static boolean hasRange() {
        return start != null && end != null;
    }

    public static Component selectionLabel() {
        if (start == null) {
            return tr("selection.none");
        }
        if (end == null) {
            return tr("selection.start");
        }
        return tr("selection.ready");
    }

    public static void requestSave(final String name) {
        final Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (!hasRange()) {
            setStatusKey("set_start_end_first");
            notify(player, status, ChatFormatting.RED);
            return;
        }
        if (name == null || name.isBlank()) {
            setStatusKey("name_required");
            notify(player, status, ChatFormatting.RED);
            return;
        }

        setStatusKey("saving");
        SableSchematicApiPackets.sendSaveRequest(name.trim(), start, end);
    }

    public static boolean requestLoadAtLookTarget(final Player player) {
        if (selectedBlueprint == null) {
            setStatusKey("no_file_selected");
            notify(player, status, ChatFormatting.YELLOW);
            return false;
        }

        try {
            final byte[] data = BlueprintToolLocalFiles.read(selectedBlueprint);
            final Vec3 target = lookTarget(player);
            setStatusKey("placing", selectedBlueprint.name());
            SableSchematicApiPackets.sendLoadRequest(selectedBlueprint.name(), target, data);
            return true;
        } catch (final IOException e) {
            setStatus(tr("status.read_failed"), e.getMessage());
            notify(player, status, ChatFormatting.RED);
            return true;
        }
    }

    public static void requestDeleteLookedSubLevel(final Player player) {
        setStatusKey("delete_requested");
        notify(player, status, ChatFormatting.YELLOW);
        SableSchematicApiPackets.sendDeleteRequest();
    }

    public static void requestSubLevelRefresh() {
        setStatusKey("sublevels_refreshing");
        SableSchematicApiPackets.sendSubLevelRefreshRequest();
    }

    public static void requestTeleportPlayerToSubLevel(final BlueprintToolSubLevelEntry entry) {
        if (entry == null) {
            return;
        }
        setStatusKey("sublevel_tp_requested");
        SableSchematicApiPackets.sendSubLevelAction(BlueprintToolServerSubLevelAction.TP_PLAYER, entry.uuid(), "");
    }

    public static void requestBringSubLevel(final BlueprintToolSubLevelEntry entry) {
        if (entry == null) {
            return;
        }
        setStatusKey("sublevel_bring_requested");
        SableSchematicApiPackets.sendSubLevelAction(BlueprintToolServerSubLevelAction.BRING, entry.uuid(), "");
    }

    public static void requestToggleStatic(final BlueprintToolSubLevelEntry entry) {
        if (entry == null) {
            return;
        }
        setStatusKey("sublevel_static_requested");
        SableSchematicApiPackets.sendSubLevelAction(BlueprintToolServerSubLevelAction.TOGGLE_STATIC, entry.uuid(), "");
    }

    public static void requestRenameSubLevel(final BlueprintToolSubLevelEntry entry, final String name) {
        if (entry == null) {
            return;
        }
        if (name == null || name.isBlank()) {
            setStatusKey("name_required");
            final Player player = Minecraft.getInstance().player;
            if (player != null) {
                notify(player, status, ChatFormatting.RED);
            }
            return;
        }
        setStatusKey("sublevel_rename_requested");
        SableSchematicApiPackets.sendSubLevelAction(BlueprintToolServerSubLevelAction.RENAME, entry.uuid(), name.trim());
    }

    public static List<BlueprintToolLocalFiles.Entry> localBlueprints() {
        try {
            return BlueprintToolLocalFiles.list();
        } catch (final IOException e) {
            setStatus(tr("status.list_failed"), e.getMessage());
            return List.of();
        }
    }

    public static int localBlueprintRevision() {
        return localBlueprintRevision;
    }

    public static List<BlueprintToolSubLevelEntry> subLevels() {
        return subLevels;
    }

    public static int subLevelRevision() {
        return subLevelRevision;
    }

    public static void selectSubLevel(final BlueprintToolSubLevelEntry entry) {
        selectedSubLevel = entry == null ? null : entry.uuid();
        setStatusKey("sublevel_selected", entry == null ? "" : entry.displayName());
        subLevelRevision++;
    }

    public static UUID selectedSubLevel() {
        return selectedSubLevel;
    }

    public static void openSubLevelDetail(final BlueprintToolSubLevelEntry entry) {
        openedSubLevelDetail = entry == null ? null : entry.uuid();
        if (entry != null) {
            selectSubLevel(entry);
        }
    }

    public static void closeSubLevelDetail() {
        openedSubLevelDetail = null;
    }

    public static BlueprintToolSubLevelEntry openedSubLevelDetail() {
        return findSubLevel(openedSubLevelDetail);
    }

    public static BlueprintToolSubLevelEntry findSubLevel(final UUID uuid) {
        if (uuid == null) {
            return null;
        }

        for (final BlueprintToolSubLevelEntry entry : subLevels) {
            if (uuid.equals(entry.uuid())) {
                return entry;
            }
        }
        return null;
    }

    public static void selectBlueprint(final BlueprintToolLocalFiles.Entry entry) {
        selectedBlueprint = entry;
        setStatusKey("selected", entry.name());
    }

    public static BlueprintToolLocalFiles.Entry selectedBlueprint() {
        return selectedBlueprint;
    }

    public static Component status() {
        return status;
    }

    public static Component hudText() {
        final Component load = selectedBlueprint == null ? tr("load.none") : tr("load.named", selectedBlueprint.name());
        return tr("hud", modeLabel(), selectionLabel(), load, status);
    }

    public static void setStatusKey(final String key, final Object... args) {
        setStatus(tr("status." + key, args));
    }

    public static void notifyStatus(final Player player, final ChatFormatting color) {
        notify(player, status, color);
    }

    public static void handleSaveResult(final String name, final boolean success, final String message, final byte[] data) {
        final Player player = Minecraft.getInstance().player;
        if (!success) {
            if ("op_required".equals(message)) {
                setStatusKey("op_required");
            } else {
                setStatus(tr("status.save_failed"), message);
            }
            if (player != null) {
                notify(player, status, ChatFormatting.RED);
            }
            return;
        }

        try {
            BlueprintToolLocalFiles.save(name, data);
            setStatus(tr("status.saved", name), message);
            if (player != null) {
                notify(player, status, ChatFormatting.GREEN);
            }
            clearSelectionSilently();
            localBlueprintRevision++;
        } catch (final IOException e) {
            setStatus(tr("status.write_failed"), e.getMessage());
            if (player != null) {
                notify(player, status, ChatFormatting.RED);
            }
        }
    }

    public static void handleSubLevelList(final CompoundTag data) {
        final ListTag list = data.getList("sublevels", Tag.TAG_COMPOUND);
        final List<BlueprintToolSubLevelEntry> entries = new ArrayList<>(list.size());
        for (final Tag tag : list) {
            if (tag instanceof final CompoundTag compound && compound.hasUUID("uuid")) {
                entries.add(BlueprintToolSubLevelEntry.fromTag(compound));
            }
        }

        subLevels = List.copyOf(entries);
        if (selectedSubLevel != null && findSubLevel(selectedSubLevel) == null) {
            selectedSubLevel = null;
        }
        if (openedSubLevelDetail != null && findSubLevel(openedSubLevelDetail) == null) {
            openedSubLevelDetail = null;
        }

        subLevelRevision++;
        setStatusKey("sublevels_loaded", entries.size());
    }

    private static void clearSelectionSilently() {
        start = null;
        end = null;
    }

    private static void clearLoadSelection() {
        selectedBlueprint = null;
    }

    private static Vec3 lookTarget(final Player player) {
        final HitResult hit = Minecraft.getInstance().hitResult;
        if (hit != null && hit.getType() != HitResult.Type.MISS) {
            return hit.getLocation();
        }
        return player.pick(64.0, 0.0F, false).getLocation();
    }

    private static void setStatus(final Component value) {
        status = value == null ? tr("status.ready") : value;
        detail = "";
    }

    private static void setStatus(final Component value, final String detailMessage) {
        status = value == null ? tr("status.ready") : value;
        detail = detailMessage == null ? "" : detailMessage;
    }

    private static void notify(final Player player, final Component message, final ChatFormatting color) {
        player.displayClientMessage(message.copy().withStyle(color), true);
    }

    private static Component tr(final String key, final Object... args) {
        return Component.translatable(KEY_PREFIX + key, args);
    }

    public static String detail() {
        return detail;
    }
}

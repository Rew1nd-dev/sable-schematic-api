package dev.rew1nd.sableschematicapi.survival.physicsmanager.client;

import dev.rew1nd.sableschematicapi.network.PhysicsManagerActionPayload;
import dev.rew1nd.sableschematicapi.network.PhysicsManagerSnapshotPayload;
import dev.rew1nd.sableschematicapi.survival.camera.client.CameraBodyOutlineRenderer;
import dev.rew1nd.sableschematicapi.survival.physicsmanager.PhysicsManagerServerActions;
import dev.rew1nd.sableschematicapi.survival.physicsmanager.PhysicsManagerAlgorithms;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PhysicsManagerClientSession {
    private static final Map<UUID, PhysicsManagerGroup> GROUPS = new LinkedHashMap<>();
    private static final Set<UUID> EXPANDED = new LinkedHashSet<>();
    private static final Map<UUID, String> NAME_DRAFTS = new LinkedHashMap<>();
    private static PhysicsManagerSortMode sortMode = PhysicsManagerSortMode.DISTANCE;
    private static PhysicsManagerRemovalProgress removal = PhysicsManagerRemovalProgress.IDLE;
    private static Component status = Component.translatable("gui.sable_schematic_api.physics_manager.ready");
    private static long snapshotRevision;
    private static long listRevision;
    private static long viewRevision;
    private static float scroll;
    private static UUID detailsBody;
    private static boolean detailsOpen;
    private static long highlightUntil;
    private static boolean registered;

    private PhysicsManagerClientSession() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        NeoForge.EVENT_BUS.addListener(PhysicsManagerClientSession::onClientTick);
        NeoForge.EVENT_BUS.addListener(PhysicsManagerClientSession::onLoggingOut);
    }

    public static void handleSnapshot(final PhysicsManagerSnapshotPayload payload) {
        final CompoundTag root = payload.data();
        snapshotRevision = root.getLong("revision");
        if (!root.getBoolean("authorized")) {
            status = Component.literal(root.getString("status")).withStyle(ChatFormatting.RED);
            closeScreen();
            viewRevision++;
            return;
        }

        final Map<UUID, List<PhysicsManagerBodyEntry>> members = new LinkedHashMap<>();
        final ListTag list = root.getList("bodies", Tag.TAG_COMPOUND);
        for (final Tag raw : list) {
            if (!(raw instanceof final CompoundTag tag)
                    || !tag.hasUUID("uuid")
                    || !tag.hasUUID("group_id")) {
                continue;
            }
            final PhysicsManagerBodyEntry entry = PhysicsManagerBodyEntry.read(tag);
            members.computeIfAbsent(entry.groupId(), ignored -> new ArrayList<>()).add(entry);
        }
        GROUPS.clear();
        for (final Map.Entry<UUID, List<PhysicsManagerBodyEntry>> entry : members.entrySet()) {
            final List<PhysicsManagerBodyEntry> bodies = entry.getValue();
            if (!bodies.isEmpty()) {
                GROUPS.put(
                        entry.getKey(),
                        new PhysicsManagerGroup(entry.getKey(), bodies.getFirst().signature(), bodies)
                );
            }
        }
        EXPANDED.retainAll(GROUPS.keySet());
        NAME_DRAFTS.keySet().removeIf(id -> findBody(id).isEmpty());
        if (detailsBody != null && findBody(detailsBody).isEmpty()) {
            detailsBody = null;
            detailsOpen = false;
        }
        removal = PhysicsManagerRemovalProgress.read(root);
        final String message = root.getString("status");
        if (!message.isBlank()) {
            status = Component.literal(message);
        } else if (removal.active()) {
            status = Component.translatable(
                    "gui.sable_schematic_api.physics_manager.deleting",
                    removal.completed(),
                    removal.total()
            );
        } else {
            status = Component.translatable(
                    "gui.sable_schematic_api.physics_manager.loaded",
                    list.size()
            );
        }
        viewRevision++;
        listRevision++;
        if (root.getBoolean("close_ui")) {
            closeScreen();
        }
    }

    public static void requestRefresh() {
        send(PhysicsManagerServerActions.REFRESH, new CompoundTag());
        status = Component.translatable("gui.sable_schematic_api.physics_manager.refreshing");
    }

    public static void requestTeleportPlayer(final PhysicsManagerBodyEntry body,
                                             final PhysicsManagerGroup group,
                                             final boolean wholeGroup) {
        final CompoundTag data = groupData(group, body.uuid());
        data.putBoolean("group", wholeGroup);
        send(PhysicsManagerServerActions.TELEPORT_PLAYER, data);
    }

    public static void requestBringGroup(final PhysicsManagerGroup group,
                                         final PhysicsManagerBodyEntry anchor) {
        send(PhysicsManagerServerActions.BRING_GROUP, groupData(group, anchor.uuid()));
    }

    public static void requestSetStatic(final PhysicsManagerGroup group,
                                        final PhysicsManagerBodyEntry anchor) {
        final CompoundTag data = groupData(group, anchor.uuid());
        data.putBoolean("static", group.staticState().nextTarget());
        send(PhysicsManagerServerActions.SET_GROUP_STATIC, data);
    }

    public static void requestRename(final PhysicsManagerBodyEntry body, final String name) {
        final CompoundTag data = new CompoundTag();
        data.putUUID("uuid", body.uuid());
        data.putString("name", name == null ? "" : name);
        send(PhysicsManagerServerActions.RENAME, data);
    }

    public static void requestDeleteBody(final PhysicsManagerBodyEntry body) {
        final CompoundTag data = new CompoundTag();
        data.putUUID("uuid", body.uuid());
        send(PhysicsManagerServerActions.DELETE_BODY, data);
    }

    public static void requestDeleteGroup(final PhysicsManagerGroup group) {
        send(
                PhysicsManagerServerActions.DELETE_GROUP,
                groupData(group, group.members().getFirst().uuid())
        );
    }

    public static boolean highlightBody(final PhysicsManagerBodyEntry body) {
        return highlight(List.of(body));
    }

    public static boolean highlightGroup(final PhysicsManagerGroup group) {
        return highlight(group.members());
    }

    private static boolean highlight(final List<PhysicsManagerBodyEntry> bodies) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }
        final String currentDimension = minecraft.level.dimension().location().toString();
        final Set<UUID> ids = new LinkedHashSet<>();
        for (final PhysicsManagerBodyEntry body : bodies) {
            if (body.loaded() && currentDimension.equals(body.dimension())) {
                ids.add(body.uuid());
            }
        }
        final int visible = CameraBodyOutlineRenderer.updateManager(ids);
        if (visible <= 0) {
            CameraBodyOutlineRenderer.updateManager(Set.of());
            status = Component.translatable("gui.sable_schematic_api.physics_manager.highlight_none");
            viewRevision++;
            return false;
        }
        highlightUntil = minecraft.level.getGameTime() + 100L;
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.translatable(
                            "gui.sable_schematic_api.physics_manager.highlighted",
                            visible,
                            bodies.size()
                    ).withStyle(ChatFormatting.GREEN),
                    true
            );
        }
        closeScreen();
        return true;
    }

    private static CompoundTag groupData(final PhysicsManagerGroup group, final UUID anchor) {
        final CompoundTag data = new CompoundTag();
        data.putUUID("uuid", anchor);
        data.putString("signature", group.signature());
        return data;
    }

    private static void send(final net.minecraft.resources.ResourceLocation action,
                             final CompoundTag data) {
        PacketDistributor.sendToServer(new PhysicsManagerActionPayload(action, data));
    }

    public static List<PhysicsManagerGroup> sortedGroups() {
        final List<PhysicsManagerGroup> result = new ArrayList<>(GROUPS.values());
        if (sortMode == PhysicsManagerSortMode.NAME) {
            result.sort(PhysicsManagerAlgorithms.nameComparator(
                    PhysicsManagerGroup::representativeName,
                    group -> group.groupId().toString()
            ));
        } else {
            result.sort(PhysicsManagerAlgorithms.distanceComparator(
                    PhysicsManagerGroup::distance,
                    group -> group.groupId().toString()
            ));
        }
        return List.copyOf(result);
    }

    public static Optional<PhysicsManagerGroup> findGroup(final UUID id) {
        return Optional.ofNullable(GROUPS.get(id));
    }

    public static Optional<PhysicsManagerGroup> groupForBody(final UUID bodyId) {
        return GROUPS.values().stream()
                .filter(group -> group.members().stream().anyMatch(body -> body.uuid().equals(bodyId)))
                .findFirst();
    }

    public static Optional<PhysicsManagerBodyEntry> findBody(final UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return GROUPS.values().stream()
                .flatMap(group -> group.members().stream())
                .filter(body -> id.equals(body.uuid()))
                .findFirst();
    }

    public static void toggleExpanded(final UUID groupId) {
        if (!EXPANDED.add(groupId)) {
            EXPANDED.remove(groupId);
        }
        viewRevision++;
        listRevision++;
    }

    public static boolean isExpanded(final UUID groupId) {
        return EXPANDED.contains(groupId);
    }

    public static PhysicsManagerSortMode sortMode() {
        return sortMode;
    }

    public static void setSortMode(final PhysicsManagerSortMode value) {
        final PhysicsManagerSortMode safe = value == null ? PhysicsManagerSortMode.DISTANCE : value;
        if (safe != sortMode) {
            sortMode = safe;
            viewRevision++;
            listRevision++;
        }
    }

    public static void openDetails(final PhysicsManagerBodyEntry body) {
        detailsBody = body.uuid();
        detailsOpen = true;
        NAME_DRAFTS.putIfAbsent(body.uuid(), body.name() == null ? "" : body.name());
        viewRevision++;
    }

    public static void closeDetails() {
        if (detailsOpen) {
            detailsOpen = false;
            viewRevision++;
        }
    }

    public static UUID detailsBody() {
        return detailsBody;
    }

    public static boolean detailsOpen() {
        return detailsOpen;
    }

    public static String nameDraft(final PhysicsManagerBodyEntry body) {
        return NAME_DRAFTS.getOrDefault(body.uuid(), body.name() == null ? "" : body.name());
    }

    public static void setNameDraft(final UUID bodyId, final String value) {
        if (bodyId != null) {
            NAME_DRAFTS.put(bodyId, value == null ? "" : value);
        }
    }

    public static PhysicsManagerRemovalProgress removal() {
        return removal;
    }

    public static boolean isDeleting(final UUID bodyId) {
        return removal.active() && bodyId != null && removal.targets().contains(bodyId);
    }

    public static Component status() {
        return status;
    }

    public static long snapshotRevision() {
        return snapshotRevision;
    }

    public static long viewRevision() {
        return viewRevision;
    }

    public static long listRevision() {
        return listRevision;
    }

    public static float scroll() {
        return scroll;
    }

    public static void setScroll(final float value) {
        scroll = Math.clamp(value, 0.0F, 1.0F);
    }

    private static void onClientTick(final ClientTickEvent.Post event) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (highlightUntil > 0L
                && (minecraft.level == null || minecraft.level.getGameTime() >= highlightUntil)) {
            highlightUntil = 0L;
            CameraBodyOutlineRenderer.updateManager(Set.of());
        }
    }

    public static void onLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    private static void reset() {
        GROUPS.clear();
        EXPANDED.clear();
        NAME_DRAFTS.clear();
        sortMode = PhysicsManagerSortMode.DISTANCE;
        removal = PhysicsManagerRemovalProgress.IDLE;
        status = Component.translatable("gui.sable_schematic_api.physics_manager.ready");
        snapshotRevision = 0L;
        viewRevision++;
        listRevision++;
        scroll = 0.0F;
        detailsBody = null;
        detailsOpen = false;
        highlightUntil = 0L;
        CameraBodyOutlineRenderer.updateManager(Set.of());
    }

    private static void closeScreen() {
        Minecraft.getInstance().setScreen(null);
    }
}

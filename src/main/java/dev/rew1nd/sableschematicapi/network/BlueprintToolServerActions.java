package dev.rew1nd.sableschematicapi.network;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.blueprint.tool.BlueprintToolFile;
import dev.rew1nd.sableschematicapi.blueprint.tool.BlueprintToolResult;
import dev.rew1nd.sableschematicapi.blueprint.tool.BlueprintToolService;
import dev.rew1nd.sableschematicapi.sublevel.LoadedSubLevelTeleportService;
import dev.rew1nd.sableschematicapi.sublevel.PendingSubLevelLoadTeleportService;
import dev.rew1nd.sableschematicapi.sublevel.RuntimeSubLevelStaticService;
import dev.rew1nd.sableschematicapi.sublevel.SubLevelDirectoryService;
import dev.rew1nd.sableschematicapi.sublevel.SubLevelGroupRecord;
import dev.rew1nd.sableschematicapi.sublevel.SubLevelGroupService;
import dev.rew1nd.sableschematicapi.sublevel.SubLevelManagementService;
import dev.rew1nd.sableschematicapi.sublevel.SubLevelOperationResult;
import dev.rew1nd.sableschematicapi.sublevel.SubLevelRecord;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class BlueprintToolServerActions {
    public static final ResourceLocation SAVE_SELECTION = SableSchematicApi.id("blueprint_tool/save_selection");
    public static final ResourceLocation LOAD_BLUEPRINT = SableSchematicApi.id("blueprint_tool/load_blueprint");
    public static final ResourceLocation DELETE_LOOKED_SUBLEVEL = SableSchematicApi.id("blueprint_tool/delete_looked_sublevel");
    public static final ResourceLocation SUBLEVEL_REFRESH = SableSchematicApi.id("blueprint_tool/sublevel_refresh");
    public static final ResourceLocation SUBLEVEL_TP_PLAYER = SableSchematicApi.id("blueprint_tool/sublevel_tp_player");
    public static final ResourceLocation SUBLEVEL_BRING = SableSchematicApi.id("blueprint_tool/sublevel_bring");
    public static final ResourceLocation SUBLEVEL_TOGGLE_STATIC = SableSchematicApi.id("blueprint_tool/sublevel_toggle_static");
    public static final ResourceLocation SUBLEVEL_RENAME = SableSchematicApi.id("blueprint_tool/sublevel_rename");

    private static final int MAX_NAME_LENGTH = 128;
    private static final double MAX_DELETE_DISTANCE = 96.0;
    private static final Map<ResourceLocation, BlueprintToolServerAction> ACTIONS = new LinkedHashMap<>();

    private BlueprintToolServerActions() {
    }

    public static void registerDefaults() {
        if (!ACTIONS.isEmpty()) {
            return;
        }

        register(SAVE_SELECTION, BlueprintToolServerActions::handleSaveSelection);
        register(LOAD_BLUEPRINT, BlueprintToolServerActions::handleLoadBlueprint);
        register(DELETE_LOOKED_SUBLEVEL, BlueprintToolServerActions::handleDeleteLookedSubLevel);
        register(SUBLEVEL_REFRESH, BlueprintToolServerActions::handleSubLevelRefresh);
        register(SUBLEVEL_TP_PLAYER, BlueprintToolServerActions::handleTeleportPlayerToSubLevel);
        register(SUBLEVEL_BRING, BlueprintToolServerActions::handleBringSubLevel);
        register(SUBLEVEL_TOGGLE_STATIC, BlueprintToolServerActions::handleToggleSubLevelStatic);
        register(SUBLEVEL_RENAME, BlueprintToolServerActions::handleRenameSubLevel);
    }

    public static void register(final ResourceLocation id, final BlueprintToolServerAction action) {
        ACTIONS.put(id, action);
    }

    public static void handle(final ResourceLocation id, final ServerPlayer player, final CompoundTag data) {
        final BlueprintToolServerAction action = ACTIONS.get(id);
        if (action == null) {
            SableSchematicApiPackets.notify(player, SableSchematicApiPackets.tr("status.unknown_action"), ChatFormatting.RED);
            return;
        }

        action.handle(player, data == null ? new CompoundTag() : data);
    }

    private static void handleSaveSelection(final ServerPlayer player, final CompoundTag data) {
        final String name = readName(data);
        if (!SableSchematicApiPackets.canUseBlueprintTool(player)) {
            SableSchematicApiPackets.sendSaveResult(player, name, false, "op_required", new byte[0]);
            return;
        }

        if (!hasVec3(data, "start") || !hasVec3(data, "end")) {
            SableSchematicApiPackets.sendSaveResult(player, name, false, "invalid_upload", new byte[0]);
            return;
        }

        final BlueprintToolFile file = BlueprintToolService.saveSelectionToBytes(
                player.serverLevel(),
                readVec3(data, "start"),
                readVec3(data, "end"),
                name
        );
        SableSchematicApiPackets.sendSaveResult(player, name, file.success(), file.result().message(), file.data());
    }

    private static void handleLoadBlueprint(final ServerPlayer player, final CompoundTag data) {
        final String name = readName(data);
        if (!SableSchematicApiPackets.canUseBlueprintTool(player)) {
            SableSchematicApiPackets.notify(player, SableSchematicApiPackets.tr("status.op_required"), ChatFormatting.RED);
            return;
        }

        final byte[] blueprintData = data.contains("data", Tag.TAG_BYTE_ARRAY) ? data.getByteArray("data") : new byte[0];
        if (blueprintData.length == 0 || blueprintData.length > SableSchematicApiPackets.MAX_BLUEPRINT_BYTES) {
            SableSchematicApiPackets.notify(player, SableSchematicApiPackets.tr("status.invalid_upload"), ChatFormatting.RED);
            return;
        }

        if (!hasVec3(data, "origin")) {
            SableSchematicApiPackets.notify(player, SableSchematicApiPackets.tr("status.invalid_upload"), ChatFormatting.RED);
            return;
        }

        final Vec3 origin = readVec3(data, "origin");
        if (Sable.HELPER.distanceSquaredWithSubLevels(player.serverLevel(), player.position(), origin) > SableSchematicApiPackets.MAX_LOAD_DISTANCE_SQUARED) {
            SableSchematicApiPackets.notify(player, SableSchematicApiPackets.tr("status.too_far"), ChatFormatting.RED);
            return;
        }

        final BlueprintToolResult result = BlueprintToolService.loadBytes(player.serverLevel(), origin, blueprintData, name);
        SableSchematicApiPackets.notify(player,
                result.success() ? SableSchematicApiPackets.tr("status.placed", name) : SableSchematicApiPackets.tr("status.place_failed"),
                result.success() ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static void handleDeleteLookedSubLevel(final ServerPlayer player, final CompoundTag data) {
        if (!SableSchematicApiPackets.canUseBlueprintTool(player)) {
            SableSchematicApiPackets.notify(player, SableSchematicApiPackets.tr("status.op_required"), ChatFormatting.RED);
            return;
        }

        final ServerLevel level = player.serverLevel();
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            SableSchematicApiPackets.notify(player, SableSchematicApiPackets.tr("status.no_sublevel_container"), ChatFormatting.RED);
            return;
        }

        final ServerSubLevel target = lookedSubLevel(player);
        if (target == null || target.isRemoved()) {
            SableSchematicApiPackets.notify(player, SableSchematicApiPackets.tr("status.delete_no_target"), ChatFormatting.YELLOW);
            return;
        }

        final String description = target.getName() == null ? target.getUniqueId().toString() : target.getName();
        try {
            container.removeSubLevel(target, SubLevelRemovalReason.REMOVED);
            SableSchematicApiPackets.notify(player, SableSchematicApiPackets.tr("status.deleted", description), ChatFormatting.GREEN);
        } catch (final RuntimeException e) {
            SableSchematicApi.LOGGER.warn("Failed deleting looked Sable sub-level {}", description, e);
            SableSchematicApiPackets.notify(player, SableSchematicApiPackets.tr("status.delete_failed"), ChatFormatting.RED);
        }
    }

    private static void handleSubLevelRefresh(final ServerPlayer player, final CompoundTag data) {
        if (!SableSchematicApiPackets.canUseBlueprintTool(player)) {
            SableSchematicApiPackets.notify(player, SableSchematicApiPackets.tr("status.op_required"), ChatFormatting.RED);
            return;
        }

        sendSubLevelList(player);
    }

    private static void handleTeleportPlayerToSubLevel(final ServerPlayer player, final CompoundTag data) {
        final Optional<SubLevelRecord> target = readSubLevelTarget(player, data);
        if (target.isEmpty()) {
            return;
        }

        sendResult(player, LoadedSubLevelTeleportService.teleportPlayerTo(player, target.get()));
        sendSubLevelList(player);
    }

    private static void handleBringSubLevel(final ServerPlayer player, final CompoundTag data) {
        final Optional<SubLevelRecord> target = readSubLevelTarget(player, data);
        if (target.isEmpty()) {
            return;
        }

        sendResult(player, PendingSubLevelLoadTeleportService.requestTeleportSubLevelToPlayer(player, target.get()));
        sendSubLevelList(player);
    }

    private static void handleToggleSubLevelStatic(final ServerPlayer player, final CompoundTag data) {
        final Optional<SubLevelRecord> target = readSubLevelTarget(player, data);
        if (target.isEmpty()) {
            return;
        }

        final ServerLevel level = player.getServer().getLevel(target.get().dimension());
        if (level == null) {
            sendResult(player, SubLevelOperationResult.failure("Target dimension is not loaded: " + target.get().dimension().location()));
            return;
        }

        sendResult(player, RuntimeSubLevelStaticService.toggleStatic(level, target.get().uuid()));
        sendSubLevelList(player);
    }

    private static void handleRenameSubLevel(final ServerPlayer player, final CompoundTag data) {
        if (!SableSchematicApiPackets.canUseBlueprintTool(player)) {
            SableSchematicApiPackets.notify(player, SableSchematicApiPackets.tr("status.op_required"), ChatFormatting.RED);
            return;
        }
        if (!data.hasUUID("uuid")) {
            SableSchematicApiPackets.notify(player, SableSchematicApiPackets.tr("status.invalid_upload"), ChatFormatting.RED);
            return;
        }

        final SubLevelOperationResult result = SubLevelManagementService.rename(player.getServer(), data.getUUID("uuid"), readName(data));
        sendResult(player, result);
        sendSubLevelList(player);
    }

    private static Optional<SubLevelRecord> readSubLevelTarget(final ServerPlayer player, final CompoundTag data) {
        if (!SableSchematicApiPackets.canUseBlueprintTool(player)) {
            SableSchematicApiPackets.notify(player, SableSchematicApiPackets.tr("status.op_required"), ChatFormatting.RED);
            return Optional.empty();
        }
        if (!data.hasUUID("uuid")) {
            SableSchematicApiPackets.notify(player, SableSchematicApiPackets.tr("status.invalid_upload"), ChatFormatting.RED);
            return Optional.empty();
        }

        final UUID uuid = data.getUUID("uuid");
        final Optional<SubLevelRecord> target = SubLevelDirectoryService.find(player.getServer(), uuid);
        if (target.isEmpty()) {
            SableSchematicApiPackets.notify(player, SableSchematicApiPackets.tr("status.sublevel_missing"), ChatFormatting.RED);
        }
        return target;
    }

    private static void sendResult(final ServerPlayer player, final SubLevelOperationResult result) {
        SableSchematicApiPackets.notify(
                player,
                result.asComponent(),
                result.success() ? ChatFormatting.GREEN : ChatFormatting.RED
        );
    }

    private static void sendSubLevelList(final ServerPlayer player) {
        final CompoundTag data = new CompoundTag();
        final ListTag list = new ListTag();
        for (final SubLevelGroupRecord group : SubLevelGroupService.listAll(player.getServer())) {
            for (final SubLevelRecord record : group.members()) {
                list.add(writeSubLevelEntry(player, record, group));
            }
        }
        data.put("sublevels", list);
        SableSchematicApiPackets.sendSubLevelList(player, data);
    }

    private static CompoundTag writeSubLevelEntry(final ServerPlayer player,
                                                  final SubLevelRecord record,
                                                  final SubLevelGroupRecord group) {
        final CompoundTag tag = new CompoundTag();
        final Vector3dc pos = record.pose().position();
        tag.putString("dimension", record.dimension().location().toString());
        tag.putUUID("uuid", record.uuid());
        tag.putUUID("group_id", group.groupId());
        tag.putInt("group_size", group.members().size());
        tag.putString("name", record.name() == null ? "" : record.name());
        tag.putString("load_state", record.loadState().name());
        tag.putBoolean("static", RuntimeSubLevelStaticService.isStatic(record.dimension(), record.uuid()));
        tag.putDouble("x", pos.x());
        tag.putDouble("y", pos.y());
        tag.putDouble("z", pos.z());
        tag.putDouble("distance", distanceToPlayer(player, record, pos));
        return tag;
    }

    private static double distanceToPlayer(final ServerPlayer player,
                                           final SubLevelRecord record,
                                           final Vector3dc pos) {
        if (!player.serverLevel().dimension().equals(record.dimension())) {
            return -1.0;
        }

        return Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels(
                player.serverLevel(),
                player.position(),
                pos.x(),
                pos.y(),
                pos.z()
        ));
    }

    private static ServerSubLevel lookedSubLevel(final ServerPlayer player) {
        final HitResult hit = player.pick(MAX_DELETE_DISTANCE, 1.0F, true);
        if (!(hit instanceof final BlockHitResult blockHit) || blockHit.getType() == HitResult.Type.MISS) {
            return null;
        }

        SubLevel target = Sable.HELPER.getContaining(player.serverLevel(), blockHit.getBlockPos());
        if (!(target instanceof ServerSubLevel)) {
            target = Sable.HELPER.getContaining(player.serverLevel(), blockHit.getLocation());
        }

        return target instanceof final ServerSubLevel serverSubLevel ? serverSubLevel : null;
    }

    private static String readName(final CompoundTag data) {
        final String name = data.getString("name");
        if (name.length() <= MAX_NAME_LENGTH) {
            return name;
        }
        return name.substring(0, MAX_NAME_LENGTH);
    }

    private static boolean hasVec3(final CompoundTag data, final String key) {
        if (!data.contains(key, Tag.TAG_COMPOUND)) {
            return false;
        }

        final CompoundTag tag = data.getCompound(key);
        return tag.contains("x", Tag.TAG_DOUBLE) && tag.contains("y", Tag.TAG_DOUBLE) && tag.contains("z", Tag.TAG_DOUBLE);
    }

    static CompoundTag writeVec3(final Vec3 vec) {
        final CompoundTag tag = new CompoundTag();
        tag.putDouble("x", vec.x);
        tag.putDouble("y", vec.y);
        tag.putDouble("z", vec.z);
        return tag;
    }

    private static Vec3 readVec3(final CompoundTag data, final String key) {
        final CompoundTag tag = data.getCompound(key);
        return new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
    }
}

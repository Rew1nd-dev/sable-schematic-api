package dev.rew1nd.sableschematicapi.network;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.blueprint.tool.BlueprintToolFile;
import dev.rew1nd.sableschematicapi.blueprint.tool.BlueprintToolResult;
import dev.rew1nd.sableschematicapi.blueprint.tool.BlueprintToolService;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

public final class BlueprintToolServerActions {
    public static final ResourceLocation SAVE_SELECTION = SableSchematicApi.id("blueprint_tool/save_selection");
    public static final ResourceLocation LOAD_BLUEPRINT = SableSchematicApi.id("blueprint_tool/load_blueprint");
    public static final ResourceLocation DELETE_LOOKED_SUBLEVEL = SableSchematicApi.id("blueprint_tool/delete_looked_sublevel");

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

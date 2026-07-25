package dev.rew1nd.sableschematicapi.survival.physicsmanager;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.network.PhysicsManagerSnapshotPayload;
import dev.rew1nd.sableschematicapi.sublevel.LoadedSubLevelTeleportService;
import dev.rew1nd.sableschematicapi.sublevel.PendingSubLevelDirectoryRemovalService;
import dev.rew1nd.sableschematicapi.sublevel.PendingSubLevelLoadTeleportService;
import dev.rew1nd.sableschematicapi.sublevel.RuntimeSubLevelStaticService;
import dev.rew1nd.sableschematicapi.sublevel.SubLevelDirectoryService;
import dev.rew1nd.sableschematicapi.sublevel.SubLevelGroupRecord;
import dev.rew1nd.sableschematicapi.sublevel.SubLevelGroupService;
import dev.rew1nd.sableschematicapi.sublevel.SubLevelManagementService;
import dev.rew1nd.sableschematicapi.sublevel.SubLevelOperationResult;
import dev.rew1nd.sableschematicapi.sublevel.SubLevelRecord;
import dev.rew1nd.sableschematicapi.sublevel.SubLevelStorageFlushService;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3dc;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class PhysicsManagerServerActions {
    public static final ResourceLocation REFRESH = SableSchematicApi.id("physics_manager/refresh");
    public static final ResourceLocation TELEPORT_PLAYER = SableSchematicApi.id("physics_manager/teleport_player");
    public static final ResourceLocation BRING_GROUP = SableSchematicApi.id("physics_manager/bring_group");
    public static final ResourceLocation SET_GROUP_STATIC = SableSchematicApi.id("physics_manager/set_group_static");
    public static final ResourceLocation RENAME = SableSchematicApi.id("physics_manager/rename");
    public static final ResourceLocation DELETE_BODY = SableSchematicApi.id("physics_manager/delete_body");
    public static final ResourceLocation DELETE_GROUP = SableSchematicApi.id("physics_manager/delete_group");

    private static final int MAX_NAME_LENGTH = 128;
    private static final AtomicLong SNAPSHOT_REVISION = new AtomicLong();
    private static boolean registered;

    private PhysicsManagerServerActions() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        PendingSubLevelDirectoryRemovalService.setProgressListener((server, playerId) -> {
            final ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                final String message = PendingSubLevelDirectoryRemovalService.status().message();
                if (canUse(player)) {
                    sendSnapshot(player, message, false);
                } else if (!message.isBlank()) {
                    player.displayClientMessage(Component.literal(message), false);
                }
            }
        });
        PendingSubLevelLoadTeleportService.setDetachedCompletionListener((server, playerId) -> {
            final ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                if (canUse(player)) {
                    sendSnapshot(player, "Bring task finished.", false);
                } else {
                    player.displayClientMessage(Component.literal("Bring task finished."), false);
                }
            }
        });
    }

    public static void handle(final ResourceLocation action,
                              final ServerPlayer player,
                              final CompoundTag data) {
        if (!canUse(player)) {
            sendUnauthorized(player);
            return;
        }
        final CompoundTag safeData = data == null ? new CompoundTag() : data;
        if (REFRESH.equals(action)) {
            final SubLevelOperationResult flush = SubLevelStorageFlushService.flush(player.getServer());
            sendSnapshot(player, flush.success() ? "" : flush.message(), false);
        } else if (TELEPORT_PLAYER.equals(action)) {
            handleTeleportPlayer(player, safeData);
        } else if (BRING_GROUP.equals(action)) {
            handleBringGroup(player, safeData);
        } else if (SET_GROUP_STATIC.equals(action)) {
            handleSetGroupStatic(player, safeData);
        } else if (RENAME.equals(action)) {
            handleRename(player, safeData);
        } else if (DELETE_BODY.equals(action)) {
            handleDeleteBody(player, safeData);
        } else if (DELETE_GROUP.equals(action)) {
            handleDeleteGroup(player, safeData);
        } else {
            sendSnapshot(player, "Unknown physics manager action.", false);
        }
    }

    public static boolean canUse(final ServerPlayer player) {
        return player != null
                && PhysicsManagerItem.canUse(player)
                && PhysicsManagerItem.is(player.getMainHandItem());
    }

    public static void sendSnapshot(final ServerPlayer player) {
        sendSnapshot(player, "", false);
    }

    private static void handleTeleportPlayer(final ServerPlayer player, final CompoundTag data) {
        final Optional<SubLevelRecord> selected = readTarget(player, data);
        if (selected.isEmpty()) {
            return;
        }

        SubLevelRecord target = selected.get();
        if (data.getBoolean("group")) {
            final Optional<SubLevelGroupRecord> group = validateGroup(player, target.uuid(), data);
            if (group.isEmpty()) {
                return;
            }
            target = PhysicsManagerAlgorithms.largest(
                    group.get().members(),
                    PhysicsManagerServerActions::volume,
                    record -> record.uuid().toString()
            );
        }

        final SubLevelOperationResult result =
                PendingSubLevelLoadTeleportService.requestTeleportPlayerToBody(player, target);
        notify(player, result);
        sendSnapshot(player, result.message(), result.success());
    }

    private static void handleBringGroup(final ServerPlayer player, final CompoundTag data) {
        final Optional<SubLevelGroupRecord> group = validateGroupFromData(player, data);
        if (group.isEmpty()) {
            return;
        }
        final UUID anchorId = data.getUUID("uuid");
        final SubLevelOperationResult result =
                PendingSubLevelLoadTeleportService.requestTeleportGroupToPlayerSnapshot(
                        player,
                        group.get(),
                        anchorId
                );
        notify(player, result);
        sendSnapshot(player, result.message(), false);
    }

    private static void handleSetGroupStatic(final ServerPlayer player, final CompoundTag data) {
        final Optional<SubLevelGroupRecord> group = validateGroupFromData(player, data);
        if (group.isEmpty()) {
            return;
        }
        final boolean desired = data.getBoolean("static");
        int changed = 0;
        int failed = 0;
        for (final SubLevelRecord record : group.get().members()) {
            final SubLevelOperationResult result = RuntimeSubLevelStaticService.setStatic(
                    player.getServer(),
                    record.dimension(),
                    record.uuid(),
                    desired
            );
            if (result.success()) {
                changed += result.affectedSubLevels();
            } else {
                failed++;
            }
        }
        final String message = failed == 0
                ? "Set " + group.get().members().size() + " bodies to "
                        + (desired ? "static." : "dynamic.")
                : "Updated " + changed + " bodies; " + failed + " failed.";
        sendSnapshot(player, message, false);
    }

    private static void handleRename(final ServerPlayer player, final CompoundTag data) {
        final Optional<SubLevelRecord> target = readTarget(player, data);
        if (target.isEmpty()) {
            return;
        }
        final String name = data.getString("name").trim();
        if (name.isEmpty()) {
            sendSnapshot(player, "Name cannot be empty.", false);
            return;
        }
        final String safeName = name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
        final SubLevelOperationResult result =
                SubLevelManagementService.rename(player.getServer(), target.get().uuid(), safeName);
        sendSnapshot(player, result.message(), false);
    }

    private static void handleDeleteBody(final ServerPlayer player, final CompoundTag data) {
        final Optional<SubLevelRecord> target = readTarget(player, data);
        if (target.isEmpty()) {
            return;
        }
        final SubLevelOperationResult result = PendingSubLevelDirectoryRemovalService.requestTargetedRemoval(
                player.getServer(),
                player.getUUID(),
                List.of(target.get())
        );
        sendSnapshot(player, result.message(), false);
    }

    private static void handleDeleteGroup(final ServerPlayer player, final CompoundTag data) {
        final Optional<SubLevelGroupRecord> group = validateGroupFromData(player, data);
        if (group.isEmpty()) {
            return;
        }
        final SubLevelOperationResult result = PendingSubLevelDirectoryRemovalService.requestTargetedRemoval(
                player.getServer(),
                player.getUUID(),
                group.get().members()
        );
        sendSnapshot(player, result.message(), false);
    }

    private static Optional<SubLevelRecord> readTarget(final ServerPlayer player,
                                                       final CompoundTag data) {
        if (!data.hasUUID("uuid")) {
            sendSnapshot(player, "No body was selected.", false);
            return Optional.empty();
        }
        final Optional<SubLevelRecord> target =
                SubLevelDirectoryService.find(player.getServer(), data.getUUID("uuid"));
        if (target.isEmpty()) {
            sendSnapshot(player, "The selected body no longer exists.", false);
        }
        return target;
    }

    private static Optional<SubLevelGroupRecord> validateGroupFromData(final ServerPlayer player,
                                                                       final CompoundTag data) {
        if (!data.hasUUID("uuid")) {
            sendSnapshot(player, "No group anchor was selected.", false);
            return Optional.empty();
        }
        return validateGroup(player, data.getUUID("uuid"), data);
    }

    private static Optional<SubLevelGroupRecord> validateGroup(final ServerPlayer player,
                                                               final UUID memberId,
                                                               final CompoundTag data) {
        final Optional<SubLevelGroupRecord> group =
                SubLevelGroupService.findGroup(player.getServer(), memberId);
        if (group.isEmpty()) {
            sendSnapshot(player, "The selected group no longer exists.", false);
            return Optional.empty();
        }
        final String expected = data.getString("signature");
        final String current = PhysicsManagerGroupSignature.compute(group.get());
        if (expected.isEmpty() || !current.equals(expected)) {
            sendSnapshot(player, "The group changed; review it and confirm the action again.", false);
            return Optional.empty();
        }
        return group;
    }

    private static void sendUnauthorized(final ServerPlayer player) {
        final CompoundTag root = new CompoundTag();
        root.putBoolean("authorized", false);
        root.putLong("revision", SNAPSHOT_REVISION.incrementAndGet());
        root.putString("status", "Creative mode, permission level 2, and the manager in the main hand are required.");
        PacketDistributor.sendToPlayer(player, new PhysicsManagerSnapshotPayload(root));
        player.displayClientMessage(
                Component.translatable("message.sable_schematic_api.physics_manager.permission")
                        .withStyle(ChatFormatting.RED),
                true
        );
    }

    private static void sendSnapshot(final ServerPlayer player,
                                     final String status,
                                     final boolean closeUi) {
        if (!canUse(player)) {
            sendUnauthorized(player);
            return;
        }
        final CompoundTag root = new CompoundTag();
        root.putBoolean("authorized", true);
        root.putBoolean("close_ui", closeUi);
        root.putLong("revision", SNAPSHOT_REVISION.incrementAndGet());
        root.putString("status", status == null ? "" : status);

        final ListTag bodies = new ListTag();
        for (final SubLevelGroupRecord group : SubLevelGroupService.listAll(player.getServer())) {
            final String signature = PhysicsManagerGroupSignature.compute(group);
            for (final SubLevelRecord record : group.members()) {
                bodies.add(writeBody(player, record, group, signature));
            }
        }
        root.put("bodies", bodies);
        writeRemovalStatus(root, PendingSubLevelDirectoryRemovalService.status());
        PacketDistributor.sendToPlayer(player, new PhysicsManagerSnapshotPayload(root));
    }

    private static CompoundTag writeBody(final ServerPlayer player,
                                         final SubLevelRecord record,
                                         final SubLevelGroupRecord group,
                                         final String signature) {
        final CompoundTag tag = new CompoundTag();
        final Vector3dc position = record.pose().position();
        final BoundingBox3dc bounds = record.bounds();
        tag.putUUID("uuid", record.uuid());
        tag.putUUID("group_id", group.groupId());
        tag.putString("signature", signature);
        tag.putString("dimension", record.dimension().location().toString());
        tag.putString("name", record.name() == null ? "" : record.name());
        tag.putString("load_state", record.loadState().name());
        tag.putBoolean(
                "static",
                RuntimeSubLevelStaticService.isStatic(
                        player.getServer(),
                        record.dimension(),
                        record.uuid()
                )
        );
        tag.putDouble("x", position.x());
        tag.putDouble("y", position.y());
        tag.putDouble("z", position.z());
        tag.putDouble("min_x", bounds.minX());
        tag.putDouble("min_y", bounds.minY());
        tag.putDouble("min_z", bounds.minZ());
        tag.putDouble("max_x", bounds.maxX());
        tag.putDouble("max_y", bounds.maxY());
        tag.putDouble("max_z", bounds.maxZ());
        tag.putDouble("distance", distanceToPlayer(player, record, position));
        return tag;
    }

    private static void writeRemovalStatus(
            final CompoundTag root,
            final PendingSubLevelDirectoryRemovalService.RemovalStatus status) {
        final CompoundTag removal = new CompoundTag();
        removal.putBoolean("active", status.active());
        removal.putInt("total", status.total());
        removal.putInt("completed", status.completed());
        removal.putInt("removed", status.removed());
        removal.putInt("missing", status.missing());
        removal.putInt("load_failures", status.loadFailures());
        removal.putInt("failures", status.failures());
        removal.putInt("unavailable_dimensions", status.unavailableDimensions());
        removal.putString("message", status.message());
        if (status.currentId() != null) {
            removal.putUUID("current", status.currentId());
        }
        final ListTag targets = new ListTag();
        status.targetIds().stream()
                .sorted(Comparator.comparing(UUID::toString))
                .forEach(uuid -> {
                    final CompoundTag entry = new CompoundTag();
                    entry.putUUID("uuid", uuid);
                    targets.add(entry);
                });
        removal.put("targets", targets);
        root.put("removal", removal);
    }

    private static double distanceToPlayer(final ServerPlayer player,
                                           final SubLevelRecord record,
                                           final Vector3dc position) {
        if (!player.serverLevel().dimension().equals(record.dimension())) {
            return -1.0D;
        }
        return Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels(
                player.serverLevel(),
                player.position(),
                position.x(),
                position.y(),
                position.z()
        ));
    }

    private static double volume(final SubLevelRecord record) {
        final BoundingBox3dc bounds = record.bounds();
        return PhysicsManagerAlgorithms.volume(
                bounds.minX(),
                bounds.minY(),
                bounds.minZ(),
                bounds.maxX(),
                bounds.maxY(),
                bounds.maxZ()
        );
    }

    private static void notify(final ServerPlayer player, final SubLevelOperationResult result) {
        player.displayClientMessage(
                result.asComponent().copy().withStyle(
                        result.success() ? ChatFormatting.GREEN : ChatFormatting.RED
                ),
                true
        );
    }
}

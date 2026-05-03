package dev.rew1nd.sableschematicapi.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles;
import dev.rew1nd.sableschematicapi.blueprint.tool.BlueprintToolResult;
import dev.rew1nd.sableschematicapi.blueprint.tool.BlueprintToolService;
import dev.rew1nd.sableschematicapi.survival.BlueprintDataItem;
import dev.rew1nd.sableschematicapi.survival.BlueprintPayloads;
import dev.rew1nd.sableschematicapi.survival.BlueprintServerFiles;
import dev.rew1nd.sableschematicapi.sublevel.LoadedSubLevelTeleportService;
import dev.rew1nd.sableschematicapi.sublevel.PendingSubLevelLoadTeleportService;
import dev.rew1nd.sableschematicapi.sublevel.SubLevelDirectoryService;
import dev.rew1nd.sableschematicapi.sublevel.SubLevelOperationResult;
import dev.rew1nd.sableschematicapi.sublevel.SubLevelRecord;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.joml.Vector3dc;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SableBlueprintCommands {
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_BLUEPRINTS = (ctx, builder) -> {
        final MinecraftServer server = ctx.getSource().getServer();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return BlueprintToolService.listBlueprints(server);
            } catch (final IOException e) {
                return java.util.List.<String>of();
            }
        }, Util.backgroundExecutor()).thenCompose(names -> {
            final String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (final String name : names) {
                if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                    builder.suggest(name);
                }
            }
            return builder.buildFuture();
        });
    };
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_SUB_LEVELS = (ctx, builder) -> {
        final String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (final SubLevelRecord record : SubLevelDirectoryService.listAll(ctx.getSource().getServer())) {
            final String uuid = record.uuid().toString();
            if (uuid.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(uuid);
            }
        }
        return builder.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_ROOT_BLUEPRINTS = (ctx, builder) -> {
        final MinecraftServer server = ctx.getSource().getServer();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return SableBlueprintRootFiles.list(server);
            } catch (final IOException e) {
                return java.util.Set.<String>of();
            }
        }, Util.backgroundExecutor()).thenCompose(names -> {
            final String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (final String name : names) {
                if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                    builder.suggest(name);
                }
            }
            return builder.buildFuture();
        });
    };

    private SableBlueprintCommands() {
    }

    public static void register(final RegisterCommandsEvent event) {
        event.getDispatcher().register(root("sablebp"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root(final String literal) {
        return Commands.literal(literal)
                .requires(commandSourceStack -> commandSourceStack.hasPermission(2))
                .then(Commands.literal("save")
                        .then(Commands.argument("pos", Vec3Argument.vec3(false))
                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.0))
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(SableBlueprintCommands::saveSchematic)))))
                .then(Commands.literal("load")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests(SUGGEST_BLUEPRINTS)
                                .executes(SableBlueprintCommands::loadSchematic)))
                .then(Commands.literal("survival_item")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests(SUGGEST_ROOT_BLUEPRINTS)
                                .executes(SableBlueprintCommands::giveSurvivalBlueprint)))
                .then(Commands.literal("sublevels")
                        .then(Commands.literal("list")
                                .executes(SableBlueprintCommands::listSubLevels))
                        .then(Commands.literal("tp_player")
                                .then(Commands.argument("uuid", UuidArgument.uuid())
                                        .suggests(SUGGEST_SUB_LEVELS)
                                        .executes(SableBlueprintCommands::teleportPlayerToSubLevel)))
                        .then(Commands.literal("bring")
                                .then(Commands.argument("uuid", UuidArgument.uuid())
                                        .suggests(SUGGEST_SUB_LEVELS)
                                        .executes(SableBlueprintCommands::bringSubLevelToPlayer))));
    }

    private static int saveSchematic(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final CommandSourceStack source = ctx.getSource();
        final ServerLevel level = source.getLevel();
        final Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
        final double radius = DoubleArgumentType.getDouble(ctx, "radius");
        final String name = StringArgumentType.getString(ctx, "name");

        final BlueprintToolResult result = BlueprintToolService.save(source.getServer(), level, pos, radius, name);
        sendResult(source, result);
        return result.affectedSubLevels();
    }

    private static int loadSchematic(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack source = ctx.getSource();
        final ServerLevel level = source.getLevel();
        final String name = StringArgumentType.getString(ctx, "name");
        final Vec3 origin = source.getPosition();

        final BlueprintToolResult result = BlueprintToolService.load(source.getServer(), level, origin, name);
        sendResult(source, result);
        return result.affectedSubLevels();
    }

    private static int giveSurvivalBlueprint(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final CommandSourceStack source = ctx.getSource();
        final ServerPlayer player = source.getPlayerOrException();
        final String name = StringArgumentType.getString(ctx, "name");

        try {
            final SableBlueprint blueprint = SableBlueprintRootFiles.load(source.getServer(), name);
            final byte[] data = BlueprintPayloads.writeCompressed(blueprint);
            final byte[] hash = BlueprintPayloads.sha256(data);
            final var summary = dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintSummary.of(blueprint);
            BlueprintServerFiles.save(player.serverLevel(), player.getName().getString(), name, data);
            final ItemStack stack = BlueprintDataItem.createFromServerFile(
                    player.getName().getString(), name, hash, summary);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            source.sendSuccess(() -> Component.literal("Created survival blueprint item from Sable-Schematics: " + name), true);
            return 1;
        } catch (final IOException | RuntimeException e) {
            source.sendFailure(Component.literal("Failed to create survival blueprint item: " + e.getMessage()));
            return 0;
        }
    }

    private static void sendResult(final CommandSourceStack source, final BlueprintToolResult result) {
        if (result.success()) {
            source.sendSuccess(result::asComponent, true);
        } else {
            source.sendFailure(result.asComponent());
        }
    }

    private static int listSubLevels(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack source = ctx.getSource();
        final List<SubLevelRecord> records = SubLevelDirectoryService.listAll(source.getServer());

        if (records.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No Sable sub-levels found."), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Found " + records.size() + " Sable sub-level(s):"), false);
        for (final SubLevelRecord record : records) {
            source.sendSuccess(() -> describeSubLevel(record), false);
        }
        return records.size();
    }

    private static int teleportPlayerToSubLevel(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final CommandSourceStack source = ctx.getSource();
        final ServerPlayer player = source.getPlayerOrException();
        final UUID uuid = UuidArgument.getUuid(ctx, "uuid");
        final Optional<SubLevelRecord> target = findSubLevel(source, uuid);
        if (target.isEmpty()) {
            return 0;
        }

        final SubLevelOperationResult result = LoadedSubLevelTeleportService.teleportPlayerTo(player, target.get());
        sendResult(source, result);
        return result.affectedSubLevels();
    }

    private static int bringSubLevelToPlayer(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final CommandSourceStack source = ctx.getSource();
        final ServerPlayer player = source.getPlayerOrException();
        final UUID uuid = UuidArgument.getUuid(ctx, "uuid");
        final Optional<SubLevelRecord> target = findSubLevel(source, uuid);
        if (target.isEmpty()) {
            return 0;
        }

        final SubLevelOperationResult result = PendingSubLevelLoadTeleportService.requestTeleportSubLevelToPlayer(player, target.get());
        sendResult(source, result);
        return result.affectedSubLevels();
    }

    private static Optional<SubLevelRecord> findSubLevel(final CommandSourceStack source, final UUID uuid) {
        final Optional<SubLevelRecord> target = SubLevelDirectoryService.find(source.getServer(), uuid);
        if (target.isEmpty()) {
            source.sendFailure(Component.literal("No Sable sub-level found with UUID " + uuid + "."));
        }
        return target;
    }

    private static Component describeSubLevel(final SubLevelRecord record) {
        final Vector3dc pos = record.pose().position();
        final String state = record.loadState().name().toLowerCase(Locale.ROOT);
        return Component.literal("[%s] %s %s (%s) @ %.2f %.2f %.2f".formatted(
                record.dimension().location(),
                state,
                record.displayName(),
                record.uuid(),
                pos.x(),
                pos.y(),
                pos.z()
        ));
    }

    private static void sendResult(final CommandSourceStack source, final SubLevelOperationResult result) {
        if (result.success()) {
            source.sendSuccess(result::asComponent, true);
        } else {
            source.sendFailure(result.asComponent());
        }
    }
}

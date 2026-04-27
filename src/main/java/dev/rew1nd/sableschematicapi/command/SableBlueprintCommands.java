package dev.rew1nd.sableschematicapi.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.rew1nd.sableschematicapi.blueprint.tool.BlueprintToolResult;
import dev.rew1nd.sableschematicapi.blueprint.tool.BlueprintToolService;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.util.Locale;
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
                                .executes(SableBlueprintCommands::loadSchematic)));
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

    private static void sendResult(final CommandSourceStack source, final BlueprintToolResult result) {
        if (result.success()) {
            source.sendSuccess(result::asComponent, true);
        } else {
            source.sendFailure(result.asComponent());
        }
    }
}

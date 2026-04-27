package dev.rew1nd.sableschematicapi.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprintExporter;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprintFiles;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprintPlacer;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class SableBlueprintCommands {
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_BLUEPRINTS = (ctx, builder) -> {
        final MinecraftServer server = ctx.getSource().getServer();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return SableBlueprintFiles.list(server);
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
        event.getDispatcher().register(root("sable_schematic_api"));
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

        final SableBlueprint blueprint = SableBlueprintExporter.export(level, pos, radius);
        if (blueprint.isEmpty()) {
            source.sendFailure(Component.literal("No sub-levels found in blueprint radius."));
            return 0;
        }

        try {
            SableBlueprintFiles.save(source.getServer(), name, blueprint);
            final Path path = SableBlueprintFiles.path(source.getServer(), name);
            source.sendSuccess(() -> Component.literal("Saved Sable blueprint '%s' with %s sub-level(s), %s block(s), %s block entity tag(s), %s entity tag(s) to %s"
                    .formatted(name, blueprint.subLevels().size(), blueprint.blockCount(), blueprint.blockEntityCount(), blueprint.entityCount(), path)), true);
            return blueprint.subLevels().size();
        } catch (final IOException e) {
            source.sendFailure(Component.literal("Failed to save Sable blueprint: " + e.getMessage()));
            return 0;
        }
    }

    private static int loadSchematic(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack source = ctx.getSource();
        final ServerLevel level = source.getLevel();
        final String name = StringArgumentType.getString(ctx, "name");
        final Vec3 origin = source.getPosition();

        try {
            final SableBlueprint blueprint = SableBlueprintFiles.load(source.getServer(), name);
            final SableBlueprintPlacer.Result result = SableBlueprintPlacer.place(level, blueprint, origin);
            source.sendSuccess(() -> Component.literal("Loaded Sable blueprint '%s' with %s sub-level(s), %s block(s), %s block entity tag(s), %s entity tag(s)."
                    .formatted(name, result.placedSubLevels(), blueprint.blockCount(), blueprint.blockEntityCount(), blueprint.entityCount())), true);
            return result.placedSubLevels();
        } catch (final IOException | IllegalArgumentException | IllegalStateException e) {
            source.sendFailure(Component.literal("Failed to load Sable blueprint: " + e.getMessage()));
            return 0;
        }
    }
}

package dev.rew1nd.sableschematicapi.client.frontier;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector3d;

import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

public final class SableFrontierClientCommands {
    private static final float DEFAULT_SECONDS = 3.0f;
    private static final float DEFAULT_GRADIENT_WIDTH = 3.0f;

    private SableFrontierClientCommands() {
    }

    public static void register(final RegisterClientCommandsEvent event) {
        event.getDispatcher().register(root());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal("sablebp_frontier")
                .then(Commands.literal("reveal")
                        .executes(ctx -> reveal(ctx.getSource(), DEFAULT_SECONDS, "y"))
                        .then(Commands.argument("seconds", FloatArgumentType.floatArg(0.1f, 60.0f))
                                .executes(ctx -> reveal(ctx.getSource(), FloatArgumentType.getFloat(ctx, "seconds"), "y"))
                                .then(Commands.argument("axis", StringArgumentType.word())
                                        .executes(ctx -> reveal(ctx.getSource(),
                                                FloatArgumentType.getFloat(ctx, "seconds"),
                                                StringArgumentType.getString(ctx, "axis"))))))
                .then(Commands.literal("clear")
                        .executes(ctx -> clear(ctx.getSource())));
    }

    private static int reveal(final CommandSourceStack source, final float seconds, final String axisName) {
        final Optional<Vector3fc> normal = parseAxis(axisName);
        if (normal.isEmpty()) {
            source.sendFailure(Component.literal("Unknown axis: " + axisName + " (use x, y, z, -x, -y, or -z)"));
            return 0;
        }

        final Optional<ClientSubLevel> target = closestSubLevel();
        if (target.isEmpty()) {
            source.sendFailure(Component.literal("No client sub-level is currently loaded."));
            return 0;
        }

        final Vec3 eyePosition = Minecraft.getInstance().player.getEyePosition();
        FrontierLaserStore.start(target.get(), new Vector3d(eyePosition.x, eyePosition.y, eyePosition.z));
        SableFrontierAnimations.reveal(target.get(), normal.get(), seconds, DEFAULT_GRADIENT_WIDTH);
        source.sendSuccess(() -> Component.literal("Started frontier reveal on nearest sub-level."), false);
        return 1;
    }

    private static int clear(final CommandSourceStack source) {
        SableFrontierAnimations.clearAll();
        FrontierLaserStore.clearAll();
        source.sendSuccess(() -> Component.literal("Cleared all frontier effects."), false);
        return 1;
    }

    private static Optional<ClientSubLevel> closestSubLevel() {
        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            return Optional.empty();
        }

        final ClientSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return Optional.empty();
        }

        final double playerX = minecraft.player.getX();
        final double playerY = minecraft.player.getY();
        final double playerZ = minecraft.player.getZ();
        return container.getAllSubLevels().stream()
                .min(Comparator.comparingDouble(subLevel -> distanceSquared(subLevel, playerX, playerY, playerZ)));
    }

    private static double distanceSquared(final ClientSubLevel subLevel, final double x, final double y, final double z) {
        final Pose3dc pose = subLevel.renderPose();
        return pose.position().distanceSquared(x, y, z);
    }

    private static Optional<Vector3fc> parseAxis(final String axisName) {
        return switch (axisName.toLowerCase(Locale.ROOT)) {
            case "x", "+x" -> Optional.of(new Vector3f(1.0f, 0.0f, 0.0f));
            case "-x" -> Optional.of(new Vector3f(-1.0f, 0.0f, 0.0f));
            case "y", "+y" -> Optional.of(new Vector3f(0.0f, 1.0f, 0.0f));
            case "-y" -> Optional.of(new Vector3f(0.0f, -1.0f, 0.0f));
            case "z", "+z" -> Optional.of(new Vector3f(0.0f, 0.0f, 1.0f));
            case "-z" -> Optional.of(new Vector3f(0.0f, 0.0f, -1.0f));
            default -> Optional.empty();
        };
    }
}

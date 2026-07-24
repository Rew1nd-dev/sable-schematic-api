package dev.rew1nd.sableschematicapi.survival.projector;

import dev.rew1nd.sableschematicapi.survival.SableSchematicApiBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public record ProjectorScreen(ProjectorAttachment locator,
                              BlockPos locatorPos,
                              Direction locatorAxis,
                              int roll,
                              Direction hitFace,
                              int blocks) {
    public static ProjectorScreen discover(final ServerLevel level,
                                           final BlockPos hitPos,
                                           final Direction hitFace) throws ProjectorAdmissionException {
        if (!isScreen(level.getBlockState(hitPos))) {
            throw new ProjectorAdmissionException("sable_schematic_api.projector.status.no_screen");
        }
        final Set<BlockPos> members = new HashSet<>();
        final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(hitPos);
        BlockPos locator = null;
        while (!queue.isEmpty()) {
            final BlockPos pos = queue.removeFirst();
            if (!members.add(pos)) {
                continue;
            }
            if (members.size() > ProjectorConfig.MAX_SCREEN_BLOCKS.get()) {
                throw new ProjectorAdmissionException("sable_schematic_api.projector.status.screen_too_large");
            }
            final BlockState state = level.getBlockState(pos);
            if (state.is(SableSchematicApiBlocks.PROJECTOR_SCREEN_LOCATOR.get())) {
                if (locator != null) {
                    throw new ProjectorAdmissionException("sable_schematic_api.projector.status.multiple_locators");
                }
                locator = pos;
            }
            for (final Direction direction : Direction.values()) {
                if (direction.getAxis() == hitFace.getAxis()) {
                    continue;
                }
                final BlockPos next = pos.relative(direction);
                if (!members.contains(next) && isScreen(level.getBlockState(next))) {
                    queue.add(next);
                }
            }
        }
        if (locator == null) {
            throw new ProjectorAdmissionException("sable_schematic_api.projector.status.no_locator");
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (final BlockPos pos : members) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        final int sx = maxX - minX + 1;
        final int sy = maxY - minY + 1;
        final int sz = maxZ - minZ + 1;
        final int normalSize = switch (hitFace.getAxis()) {
            case X -> sx;
            case Y -> sy;
            case Z -> sz;
        };
        if (normalSize != 1 || (long) sx * sy * sz != members.size()) {
            throw new ProjectorAdmissionException("sable_schematic_api.projector.status.screen_not_rectangle");
        }
        for (final BlockPos pos : members) {
            if (isScreen(level.getBlockState(pos.relative(hitFace)))
                    || isScreen(level.getBlockState(pos.relative(hitFace.getOpposite())))) {
                throw new ProjectorAdmissionException("sable_schematic_api.projector.status.screen_not_rectangle");
            }
        }
        final BlockState locatorState = level.getBlockState(locator);
        return new ProjectorScreen(
                ProjectorAttachment.capture(level, locator),
                locator,
                locatorState.getValue(ProjectorScreenLocatorBlock.NORMAL),
                locatorState.getValue(ProjectorScreenLocatorBlock.ROLL),
                hitFace,
                members.size()
        );
    }

    public Frame frame(final ServerLevel level) {
        Vector3d z = this.locator.worldDirection(level, direction(this.locatorAxis));
        Vector3d y = Math.abs(z.y) < 0.999D ? new Vector3d(0, 1, 0) : new Vector3d(0, 0, -1);
        Vector3d x = new Vector3d(y).cross(z).normalize();
        y = new Vector3d(z).cross(x).normalize();
        for (int i = 0; i < this.roll; i++) {
            final Vector3d oldX = x;
            x = new Vector3d(y);
            y = new Vector3d(oldX).negate();
        }
        final Vector3d center = this.locator.worldPosition(level);
        final Vector3d localHitNormal = direction(this.hitFace);
        final Vector3d normal = this.locator.worldDirection(level, localHitNormal);
        final Vector3d surface = this.locator.worldPoint(level, new Vector3d(
                this.locatorPos.getX() + 0.5D + localHitNormal.x * 0.5D,
                this.locatorPos.getY() + 0.5D + localHitNormal.y * 0.5D,
                this.locatorPos.getZ() + 0.5D + localHitNormal.z * 0.5D
        ));
        return new Frame(center, surface, x, y, z, normal);
    }

    private static boolean isScreen(final BlockState state) {
        return state.is(SableSchematicApiBlocks.PROJECTOR_SCREEN.get())
                || state.is(SableSchematicApiBlocks.PROJECTOR_SCREEN_LOCATOR.get());
    }

    private static Vector3d direction(final Direction direction) {
        return new Vector3d(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    public record Frame(Vector3d center,
                        Vector3d surface,
                        Vector3d x,
                        Vector3d y,
                        Vector3d z,
                        Vector3d normal) {
        public Frame {
            center = new Vector3d(center);
            surface = new Vector3d(surface);
            x = new Vector3d(x);
            y = new Vector3d(y);
            z = new Vector3d(z);
            normal = new Vector3d(normal);
        }
    }
}

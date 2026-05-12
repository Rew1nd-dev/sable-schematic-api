package dev.rew1nd.sableschematicapi.compat.aeronautics;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import dev.eriksonn.aeronautics.content.blocks.hot_air.BlockEntityLiftingGasProvider;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.Balloon;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.ServerBalloon;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.graph.BalloonBuilder;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.graph.BalloonLayerGraph;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.map.BalloonMap;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.map.SavedBalloon;
import dev.eriksonn.aeronautics.content.blocks.hot_air.hot_air_burner.HotAirBurnerBlockEntity;
import dev.eriksonn.aeronautics.content.blocks.hot_air.lifting_gas.LiftingGasHolder;
import dev.eriksonn.aeronautics.content.blocks.hot_air.steam_vent.SteamVentBlockEntity;
import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockRef;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlaceSession;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlacedBlock;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintSaveSession;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintEvent;
import dev.rew1nd.sableschematicapi.api.blueprint.SubLevelSaveFrame;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import dev.rew1nd.sableschematicapi.compat.BlueprintRefTags;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.util.LevelAccelerator;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class AeronauticsHotAirBalloonBlueprintEvent implements SableBlueprintEvent {
    private static final ResourceLocation ID = SableSchematicApi.id("aeronautics/hot_air_balloons");
    private static final String BALLOONS = "balloons";
    private static final String CONTROLLER_REF = "controller_ref";
    private static final String LOCAL_BOUNDS = "local_bounds";
    private static final String GAS_DATA = "gas_data";

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void onSaveAfterBlocks(final BlueprintSaveSession session, final CompoundTag data) {
        final BalloonMap map = BalloonMap.MAP.get(session.level());
        final ListTag balloons = new ListTag();
        final Set<BlockPos> savedControllers = new HashSet<>();

        for (final Balloon balloon : map.getBalloons()) {
            if (balloon instanceof final ServerBalloon serverBalloon) {
                saveBalloon(session, BalloonMap.saveBalloon(serverBalloon), savedControllers).ifPresent(balloons::add);
            }
        }

        for (final SavedBalloon unloaded : map.getUnloadedBalloons()) {
            saveBalloon(session, unloaded, savedControllers).ifPresent(balloons::add);
        }

        if (!balloons.isEmpty()) {
            data.put(BALLOONS, balloons);
        }
    }

    @Override
    public void onPlaceAfterBlocks(final BlueprintPlaceSession session, final CompoundTag data) {
        final ListTag balloons = data.getList(BALLOONS, Tag.TAG_COMPOUND).copy();
        if (balloons.isEmpty()) {
            return;
        }

        session.level().getServer().execute(() -> restoreBalloons(session, balloons));
    }

    private static Optional<CompoundTag> saveBalloon(final BlueprintSaveSession session,
                                                     final SavedBalloon balloon,
                                                     final Set<BlockPos> savedControllers) {
        final BlockPos controllerPos = balloon.controllerPos().immutable();
        if (!savedControllers.add(controllerPos)) {
            return Optional.empty();
        }

        final Optional<BlueprintBlockRef> controllerRef = session.blockRef(controllerPos);
        if (controllerRef.isEmpty()) {
            return Optional.empty();
        }

        final @Nullable SubLevelSaveFrame frame = frame(session, controllerRef.get().subLevelId());
        if (frame == null) {
            return Optional.empty();
        }

        final @Nullable ListTag gasData = encodeGasData(balloon.gasData());
        if (gasData == null) {
            return Optional.empty();
        }

        final CompoundTag tag = new CompoundTag();
        tag.put(CONTROLLER_REF, BlueprintRefTags.write(controllerRef.get()));
        tag.put(LOCAL_BOUNDS, SableBlueprint.writeBoundingBox(toLocalBounds(balloon.bounds(), frame.blocksOrigin())));
        tag.put(GAS_DATA, gasData);
        return Optional.of(tag);
    }

    private static void restoreBalloons(final BlueprintPlaceSession session, final ListTag balloons) {
        for (int i = 0; i < balloons.size(); i++) {
            try {
                restoreBalloon(session, balloons.getCompound(i));
            } catch (final RuntimeException e) {
                SableSchematicApi.LOGGER.warn("Failed to restore Create Aeronautics hot-air balloon from blueprint", e);
            }
        }
    }

    private static void restoreBalloon(final BlueprintPlaceSession session, final CompoundTag tag) {
        final Optional<BlueprintBlockRef> controllerRef = BlueprintRefTags.read(tag, CONTROLLER_REF);
        if (controllerRef.isEmpty()) {
            return;
        }

        final @Nullable BlockPos controllerPos = session.mapBlock(controllerRef.get());
        if (controllerPos == null) {
            return;
        }

        final List<LiftingGasHolder> gasData = decodeGasData(tag);
        final BoundingBox3i bounds = toPlacedBounds(session, controllerRef.get().subLevelId(), controllerPos, tag);
        final ServerLevel level = session.level();
        final BalloonMap map = BalloonMap.MAP.get(level);
        final Balloon existing = map.getBalloon(controllerPos);
        final @Nullable ServerBalloon balloon;
        final boolean newlyCreated;

        if (existing instanceof final ServerBalloon existingServerBalloon) {
            balloon = existingServerBalloon;
            newlyCreated = false;
        } else if (existing == null) {
            balloon = createBalloon(level, controllerPos);
            newlyCreated = true;
        } else {
            return;
        }

        if (balloon == null) {
            return;
        }

        balloon.loadFrom(new SavedBalloon(bounds, controllerPos, gasData));
        attachPlacedProviders(session, controllerRef.get().subLevelId(), balloon);
        balloon.tick();

        if (newlyCreated) {
            map.addBalloon(balloon);
        }
        map.markDirty();
    }

    private static @Nullable ServerBalloon createBalloon(final ServerLevel level, final BlockPos controllerPos) {
        final BalloonLayerGraph graph = BalloonBuilder.buildBalloon(level, controllerPos, null);
        if (graph == null) {
            return null;
        }

        return new ServerBalloon(level, new LevelAccelerator(level), controllerPos, graph, new ObjectArrayList<>());
    }

    private static void attachPlacedProviders(final BlueprintPlaceSession session,
                                              final int blueprintSubLevelId,
                                              final ServerBalloon balloon) {
        for (final BlueprintPlacedBlock block : session.placedBlocks().blocksInSubLevel(blueprintSubLevelId)) {
            BlockEntity blockEntity = block.blockEntity();
            if (blockEntity == null) {
                blockEntity = session.level().getBlockEntity(block.storagePos());
            }

            if (blockEntity instanceof final BlockEntityLiftingGasProvider provider) {
                refreshProviderState(blockEntity);
                attachProvider(provider, balloon);
                blockEntity.setChanged();
            }
        }
    }

    private static void refreshProviderState(final BlockEntity blockEntity) {
        if (blockEntity instanceof final HotAirBurnerBlockEntity burner) {
            burner.updateSignal();
        } else if (blockEntity instanceof final SteamVentBlockEntity vent) {
            vent.getAndCacheTank();
            if (!vent.updateRawSignal()) {
                vent.signalSync();
            }
        }
    }

    private static void attachProvider(final BlockEntityLiftingGasProvider provider, final ServerBalloon balloon) {
        if (provider.getBalloon() != null) {
            return;
        }

        provider.doRaycast();
        final @Nullable BlockPos castPosition = provider.getCastPosition();
        if (castPosition == null || !balloon.getGraph().hasBlockAt(castPosition)) {
            return;
        }

        balloon.addHeater(provider);
        provider.setBalloon(balloon);
    }

    private static BoundingBox3i toLocalBounds(final BoundingBox3ic bounds, final BlockPos origin) {
        return new BoundingBox3i(
                bounds.minX() - origin.getX(),
                bounds.minY() - origin.getY(),
                bounds.minZ() - origin.getZ(),
                bounds.maxX() - origin.getX(),
                bounds.maxY() - origin.getY(),
                bounds.maxZ() - origin.getZ()
        );
    }

    private static BoundingBox3i toPlacedBounds(final BlueprintPlaceSession session,
                                                final int blueprintSubLevelId,
                                                final BlockPos controllerPos,
                                                final CompoundTag tag) {
        final @Nullable BlockPos origin = session.placedBlockOrigin(blueprintSubLevelId);
        if (origin == null || !tag.contains(LOCAL_BOUNDS, Tag.TAG_COMPOUND)) {
            return new BoundingBox3i(
                    controllerPos.getX(),
                    controllerPos.getY(),
                    controllerPos.getZ(),
                    controllerPos.getX(),
                    controllerPos.getY(),
                    controllerPos.getZ()
            );
        }

        final BoundingBox3i localBounds = SableBlueprint.readBoundingBox(tag.getCompound(LOCAL_BOUNDS));
        return new BoundingBox3i(
                localBounds.minX() + origin.getX(),
                localBounds.minY() + origin.getY(),
                localBounds.minZ() + origin.getZ(),
                localBounds.maxX() + origin.getX(),
                localBounds.maxY() + origin.getY(),
                localBounds.maxZ() + origin.getZ()
        );
    }

    private static @Nullable ListTag encodeGasData(final List<LiftingGasHolder> gasData) {
        final DataResult<Tag> encoded = LiftingGasHolder.CODEC.listOf().encodeStart(NbtOps.INSTANCE, gasData);
        final @Nullable Tag tag = encoded.result().orElse(null);
        return tag instanceof final ListTag list ? list.copy() : null;
    }

    private static List<LiftingGasHolder> decodeGasData(final CompoundTag tag) {
        final ListTag gasData = tag.getList(GAS_DATA, Tag.TAG_COMPOUND);
        final DataResult<Pair<List<LiftingGasHolder>, Tag>> decoded = LiftingGasHolder.CODEC.listOf().decode(NbtOps.INSTANCE, gasData);
        return decoded.result()
                .map(Pair::getFirst)
                .orElseGet(List::of);
    }

    private static @Nullable SubLevelSaveFrame frame(final BlueprintSaveSession session, final int blueprintSubLevelId) {
        for (final SubLevelSaveFrame frame : session.frames()) {
            if (frame.blueprintId() == blueprintSubLevelId) {
                return frame;
            }
        }

        return null;
    }
}

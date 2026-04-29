package dev.rew1nd.sableschematicapi.compat.drivebywire;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockRef;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlaceSession;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintSaveSession;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintSavedBlock;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintEvent;
import dev.rew1nd.sableschematicapi.compat.BlueprintRefTags;
import edn.stratodonut.drivebywire.network.WireNetworkFullSyncPacket;
import edn.stratodonut.drivebywire.wire.WireNetworkManager;
import edn.stratodonut.drivebywire.wire.graph.WireNetworkNode.WireNetworkSink;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class DriveByWireBlueprintEvent implements SableBlueprintEvent {
    private static final ResourceLocation ID = SableSchematicApi.id("drivebywire/wires");
    private static final String CONNECTIONS = "connections";
    private static final String SOURCE_REF = "source_ref";
    private static final String SINK_REF = "sink_ref";
    private static final String DIRECTION = "direction";
    private static final String CHANNEL = "channel";
    private static final String SKIPPED_CONNECTIONS = "skipped_connections";

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void onSaveAfterBlocks(final BlueprintSaveSession session, final CompoundTag data) {
        final Set<BlueprintBlockRef> savedRefs = new HashSet<>();
        for (final BlueprintSavedBlock block : session.savedBlocks().blocks()) {
            savedRefs.add(block.ref());
        }

        final ListTag connections = new ListTag();
        int skipped = 0;
        final WireNetworkManager manager = WireNetworkManager.get(session.level());
        for (final Map.Entry<Long, Map<String, Set<WireNetworkSink>>> sourceEntry : manager.getNetwork().entrySet()) {
            final BlockPos sourcePos = BlockPos.of(sourceEntry.getKey());
            final Optional<BlueprintBlockRef> sourceRef = session.blockRef(sourcePos);

            for (final Map.Entry<String, Set<WireNetworkSink>> channelEntry : sourceEntry.getValue().entrySet()) {
                for (final WireNetworkSink sink : channelEntry.getValue()) {
                    final BlockPos sinkPos = BlockPos.of(sink.position());
                    final Optional<BlueprintBlockRef> sinkRef = session.blockRef(sinkPos);
                    if (sourceRef.isEmpty() && sinkRef.isEmpty()) {
                        continue;
                    }
                    if (sourceRef.isEmpty()
                            || sinkRef.isEmpty()
                            || !savedRefs.contains(sourceRef.get())
                            || !savedRefs.contains(sinkRef.get())) {
                        skipped++;
                        continue;
                    }

                    final CompoundTag connection = new CompoundTag();
                    connection.put(SOURCE_REF, BlueprintRefTags.write(sourceRef.get()));
                    connection.put(SINK_REF, BlueprintRefTags.write(sinkRef.get()));
                    connection.putByte(DIRECTION, (byte) sink.direction());
                    connection.putString(CHANNEL, channelEntry.getKey());
                    connections.add(connection);
                }
            }
        }

        if (!connections.isEmpty()) {
            data.put(CONNECTIONS, connections);
        }
        if (skipped > 0) {
            data.putInt(SKIPPED_CONNECTIONS, skipped);
            SableSchematicApi.LOGGER.warn(
                    "Drive By Wire blueprint save skipped {} cable links whose endpoints were not fully included in the blueprint.",
                    skipped
            );
        }
    }

    @Override
    public void onPlaceAfterBlockEntities(final BlueprintPlaceSession session, final CompoundTag data) {
        final ListTag connections = data.getList(CONNECTIONS, Tag.TAG_COMPOUND);
        int restored = 0;
        int existing = 0;
        int skipped = 0;
        int failed = 0;

        for (int i = 0; i < connections.size(); i++) {
            final CompoundTag connection = connections.getCompound(i);
            final @Nullable BlockPos sourcePos = BlueprintRefTags.read(connection, SOURCE_REF)
                    .map(session::mapBlock)
                    .orElse(null);
            final @Nullable BlockPos sinkPos = BlueprintRefTags.read(connection, SINK_REF)
                    .map(session::mapBlock)
                    .orElse(null);
            if (sourcePos == null
                    || sinkPos == null
                    || !connection.contains(DIRECTION, Tag.TAG_BYTE)
                    || !connection.contains(CHANNEL, Tag.TAG_STRING)) {
                skipped++;
                continue;
            }

            final Direction direction = Direction.from3DDataValue(connection.getByte(DIRECTION));
            final String channel = connection.getString(CHANNEL);
            if (WireNetworkManager.hasConnection(session.level(), sourcePos, sinkPos, direction, channel)) {
                existing++;
                continue;
            }

            final WireNetworkManager.ConnectionResult result = WireNetworkManager.createConnection(
                    session.level(),
                    sourcePos,
                    sinkPos,
                    direction,
                    channel
            );
            if (result.isSuccess()) {
                restored++;
            } else {
                failed++;
                SableSchematicApi.LOGGER.warn(
                        "Drive By Wire blueprint restore failed for {} -> {} face {} channel {}: {}",
                        sourcePos,
                        sinkPos,
                        direction,
                        channel,
                        result.getDescription()
                );
            }
        }

        if (restored > 0 || existing > 0) {
            syncPlayers(session);
        }

        if (restored > 0 || existing > 0 || skipped > 0 || failed > 0) {
            SableSchematicApi.LOGGER.info(
                    "Drive By Wire blueprint restore: {} restored, {} existing, {} skipped, {} failed.",
                    restored,
                    existing,
                    skipped,
                    failed
            );
        }
    }

    private static void syncPlayers(final BlueprintPlaceSession session) {
        for (final ServerPlayer player : session.level().players()) {
            WireNetworkFullSyncPacket.sendTo(player);
        }
    }
}

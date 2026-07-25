package dev.rew1nd.sableschematicapi.survival.honeywelder;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.api.honeywelder.HoneyWelderCancelReason;
import dev.rew1nd.sableschematicapi.api.honeywelder.HoneyWelderConsumer;
import dev.rew1nd.sableschematicapi.api.honeywelder.HoneyWelderConsumers;
import dev.rew1nd.sableschematicapi.api.honeywelder.HoneyWelderStartResult;
import dev.rew1nd.sableschematicapi.network.HoneyWelderSessionPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Runtime-only, server-authoritative one-session-per-player manager. */
public final class HoneyWelderServerSessions {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private HoneyWelderServerSessions() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(HoneyWelderServerSessions::tick);
        NeoForge.EVENT_BUS.addListener(HoneyWelderServerSessions::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(HoneyWelderServerSessions::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(HoneyWelderServerSessions::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(HoneyWelderServerSessions::onServerStopped);
    }

    public static void selectConsumer(final ServerPlayer player, final ResourceLocation consumerId) {
        if (player == null || SESSIONS.containsKey(player.getUUID()) || !holdingWelder(player)) {
            return;
        }
        if (consumerId != null && HoneyWelderConsumers.find(consumerId).isEmpty()) {
            return;
        }
        final ItemStack stack = player.getMainHandItem();
        HoneyWelderState.write(stack, HoneyWelderState.read(stack).withSelectedConsumer(consumerId));
        player.getInventory().setChanged();
    }

    public static void start(final ServerPlayer player, final ResourceLocation action) {
        if (player == null || action == null || !holdingWelder(player)) {
            return;
        }
        if (SESSIONS.containsKey(player.getUUID())) {
            send(player, HoneyWelderSessionPayload.Event.REJECTED, SESSIONS.get(player.getUUID()),
                    HoneyWelderCancelReason.USER, "session_active");
            return;
        }

        final HoneyWelderState state = HoneyWelderState.read(player.getMainHandItem());
        final ResourceLocation consumerId = state.selectedConsumer().orElse(null);
        final HoneyWelderConsumer consumer = HoneyWelderConsumers.find(consumerId).orElse(null);
        if (consumer == null) {
            send(player, HoneyWelderSessionPayload.Event.REJECTED,
                    new Session(consumerId, action, player.getInventory().selected),
                    HoneyWelderCancelReason.CONSUMER_MISSING, "consumer_missing");
            return;
        }

        final boolean creative = player.getAbilities().instabuild;
        if (!creative && consumer.requiresFuel(action) && state.fuel() <= 0) {
            send(player, HoneyWelderSessionPayload.Event.REJECTED,
                    new Session(consumerId, action, player.getInventory().selected),
                    HoneyWelderCancelReason.USER, "fuel_empty");
            return;
        }

        final HoneyWelderStartResult result;
        try {
            result = consumer.start(player, action, state.fuel(), creative);
        } catch (final RuntimeException exception) {
            SableSchematicApi.LOGGER.error("Honey welder consumer {} failed to start", consumerId, exception);
            send(player, HoneyWelderSessionPayload.Event.REJECTED,
                    new Session(consumerId, action, player.getInventory().selected),
                    HoneyWelderCancelReason.USER, "consumer_error");
            return;
        }
        if (result == null || !result.accepted()) {
            send(player, HoneyWelderSessionPayload.Event.REJECTED,
                    new Session(consumerId, action, player.getInventory().selected),
                    HoneyWelderCancelReason.USER, result == null ? "consumer_rejected" : result.message());
            return;
        }

        final Session session = new Session(consumerId, action, player.getInventory().selected);
        SESSIONS.put(player.getUUID(), session);
        send(player, HoneyWelderSessionPayload.Event.STARTED, session, HoneyWelderCancelReason.USER, result.message());
    }

    public static boolean isActive(final ServerPlayer player, final ResourceLocation consumer) {
        if (player == null || consumer == null) {
            return false;
        }
        final Session session = SESSIONS.get(player.getUUID());
        return session != null && consumer.equals(session.consumer) && validHeldItem(player, session);
    }

    public static boolean isActive(final ServerPlayer player,
                                   final ResourceLocation consumer,
                                   final ResourceLocation action) {
        if (!isActive(player, consumer) || action == null) {
            return false;
        }
        final Session session = SESSIONS.get(player.getUUID());
        return session != null && action.equals(session.action);
    }

    public static boolean finish(final ServerPlayer player,
                                 final ResourceLocation consumer,
                                 final int fuelUsed) {
        if (!isActive(player, consumer)) {
            if (player != null) {
                cancel(player, HoneyWelderCancelReason.ITEM_CHANGED);
            }
            return false;
        }
        final Session session = SESSIONS.remove(player.getUUID());
        final ItemStack stack = player.getMainHandItem();
        final HoneyWelderState state = HoneyWelderState.read(stack);
        if (!player.getAbilities().instabuild) {
            HoneyWelderState.write(stack, state.withFuel(Math.max(0, state.fuel() - Math.max(0, fuelUsed))));
            player.getInventory().setChanged();
        }
        send(player, HoneyWelderSessionPayload.Event.FINISHED, session, HoneyWelderCancelReason.USER, "");
        return true;
    }

    public static void cancel(final ServerPlayer player, final HoneyWelderCancelReason reason) {
        if (player == null) {
            return;
        }
        final Session session = SESSIONS.remove(player.getUUID());
        if (session == null) {
            return;
        }
        HoneyWelderConsumers.find(session.consumer).ifPresent(consumer -> {
            try {
                consumer.cancel(player, reason == null ? HoneyWelderCancelReason.USER : reason);
            } catch (final RuntimeException exception) {
                SableSchematicApi.LOGGER.error("Honey welder consumer {} failed to cancel", session.consumer, exception);
            }
        });
        send(player, HoneyWelderSessionPayload.Event.CANCELLED, session,
                reason == null ? HoneyWelderCancelReason.USER : reason, "");
    }

    private static void tick(final ServerTickEvent.Post event) {
        for (final UUID playerId : new ArrayList<>(SESSIONS.keySet())) {
            final ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerId);
            if (player == null) {
                SESSIONS.remove(playerId);
                continue;
            }
            final Session session = SESSIONS.get(playerId);
            if (session != null && !validHeldItem(player, session)) {
                cancel(player, HoneyWelderCancelReason.ITEM_CHANGED);
            }
        }
    }

    private static boolean holdingWelder(final ServerPlayer player) {
        return player != null && HoneyWelderItem.is(player.getMainHandItem());
    }

    private static boolean validHeldItem(final ServerPlayer player, final Session session) {
        return holdingWelder(player) && player.getInventory().selected == session.hotbarSlot;
    }

    private static void send(final ServerPlayer player,
                             final HoneyWelderSessionPayload.Event event,
                             final Session session,
                             final HoneyWelderCancelReason reason,
                             final String message) {
        if (player == null || session == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new HoneyWelderSessionPayload(
                event,
                session.consumer,
                session.action,
                reason,
                HoneyWelderState.read(player.getMainHandItem()).fuel(),
                message
        ));
    }

    private static void onPlayerLoggedOut(final PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof final ServerPlayer player) {
            cancel(player, HoneyWelderCancelReason.LOGOUT);
        }
    }

    private static void onPlayerChangedDimension(final PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof final ServerPlayer player) {
            cancel(player, HoneyWelderCancelReason.DIMENSION_CHANGED);
        }
    }

    private static void onLivingDeath(final LivingDeathEvent event) {
        if (event.getEntity() instanceof final ServerPlayer player) {
            cancel(player, HoneyWelderCancelReason.DEATH);
        }
    }

    private static void onServerStopped(final ServerStoppedEvent event) {
        for (final UUID playerId : new ArrayList<>(SESSIONS.keySet())) {
            final ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
                cancel(player, HoneyWelderCancelReason.SERVER_STOPPED);
            }
        }
        SESSIONS.clear();
    }

    private record Session(ResourceLocation consumer, ResourceLocation action, int hotbarSlot) {
        private Session {
            consumer = consumer == null ? dev.rew1nd.sableschematicapi.network.HoneyWelderControlPayload.NONE : consumer;
            action = action == null ? dev.rew1nd.sableschematicapi.network.HoneyWelderControlPayload.NONE : action;
        }
    }
}

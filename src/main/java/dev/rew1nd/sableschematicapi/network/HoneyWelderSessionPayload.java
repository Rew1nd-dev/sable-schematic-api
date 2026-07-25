package dev.rew1nd.sableschematicapi.network;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.api.honeywelder.HoneyWelderCancelReason;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HoneyWelderSessionPayload(Event event,
                                        ResourceLocation consumer,
                                        ResourceLocation action,
                                        HoneyWelderCancelReason reason,
                                        int fuel,
                                        String message) implements CustomPacketPayload {
    public static final Type<HoneyWelderSessionPayload> TYPE =
            new Type<>(SableSchematicApi.id("honey_welder_session"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HoneyWelderSessionPayload> STREAM_CODEC =
            StreamCodec.ofMember(HoneyWelderSessionPayload::write, HoneyWelderSessionPayload::read);

    public HoneyWelderSessionPayload {
        event = event == null ? Event.REJECTED : event;
        consumer = consumer == null ? HoneyWelderControlPayload.NONE : consumer;
        action = action == null ? HoneyWelderControlPayload.NONE : action;
        reason = reason == null ? HoneyWelderCancelReason.USER : reason;
        fuel = Math.max(0, fuel);
        message = message == null ? "" : message;
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeEnum(this.event);
        buffer.writeResourceLocation(this.consumer);
        buffer.writeResourceLocation(this.action);
        buffer.writeEnum(this.reason);
        buffer.writeVarInt(this.fuel);
        buffer.writeUtf(this.message, 256);
    }

    private static HoneyWelderSessionPayload read(final RegistryFriendlyByteBuf buffer) {
        return new HoneyWelderSessionPayload(
                buffer.readEnum(Event.class),
                buffer.readResourceLocation(),
                buffer.readResourceLocation(),
                buffer.readEnum(HoneyWelderCancelReason.class),
                buffer.readVarInt(),
                buffer.readUtf(256)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Event {
        STARTED,
        REJECTED,
        CANCELLED,
        FINISHED
    }
}

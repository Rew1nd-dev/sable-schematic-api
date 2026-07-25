package dev.rew1nd.sableschematicapi.network;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HoneyWelderControlPayload(Command command,
                                        ResourceLocation value) implements CustomPacketPayload {
    public static final Type<HoneyWelderControlPayload> TYPE =
            new Type<>(SableSchematicApi.id("honey_welder_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HoneyWelderControlPayload> STREAM_CODEC =
            StreamCodec.ofMember(HoneyWelderControlPayload::write, HoneyWelderControlPayload::read);
    public static final ResourceLocation NONE = SableSchematicApi.id("none");

    public HoneyWelderControlPayload {
        command = command == null ? Command.CANCEL : command;
        value = value == null ? NONE : value;
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeEnum(this.command);
        buffer.writeResourceLocation(this.value);
    }

    private static HoneyWelderControlPayload read(final RegistryFriendlyByteBuf buffer) {
        return new HoneyWelderControlPayload(
                buffer.readEnum(Command.class),
                buffer.readResourceLocation()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Command {
        SELECT_CONSUMER,
        START,
        CANCEL
    }
}

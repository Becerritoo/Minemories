package dev.becerritoo.minemories.client.net;

import dev.becerritoo.minemories.common.Protocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ControlPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<ControlPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("minemories", "control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ControlPayload> CODEC = CustomPacketPayload.codec(
            (payload, buffer) -> buffer.writeBytes(payload.data),
            buffer -> {
                int length = buffer.readableBytes();
                if (length < 1 || length > Protocol.MAX_PACKET_BYTES) {
                    throw new IllegalArgumentException("Invalid Minemories payload length: " + length);
                }
                byte[] bytes = new byte[length];
                buffer.readBytes(bytes);
                return new ControlPayload(bytes);
            }
    );

    public ControlPayload {
        data = data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}

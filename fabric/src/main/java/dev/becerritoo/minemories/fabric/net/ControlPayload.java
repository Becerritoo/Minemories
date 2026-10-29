package dev.becerritoo.minemories.fabric.net;

import dev.becerritoo.minemories.common.Protocol;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ControlPayload(byte[] data) implements CustomPayload {
    public static final Id<ControlPayload> ID = new Id<>(Identifier.of("minemories", "control"));
    public static final PacketCodec<RegistryByteBuf, ControlPayload> CODEC = CustomPayload.codecOf(
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
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

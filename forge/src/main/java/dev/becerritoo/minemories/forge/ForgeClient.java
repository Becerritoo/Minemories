package dev.becerritoo.minemories.forge;

import dev.becerritoo.minemories.client.CameraAimController;
import dev.becerritoo.minemories.client.MinemoriesClient;
import dev.becerritoo.minemories.client.net.ControlPayload;
import dev.becerritoo.minemories.client.net.NetworkBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;

final class ForgeClient {
    private static final Channel<CustomPacketPayload> CHANNEL = ChannelBuilder
            .named(Identifier.fromNamespaceAndPath("minemories", "transport"))
            // Paper advertises the payload as a vanilla plugin channel. Forge must
            // accept a missing transport channel in both directions or it rejects
            // the otherwise valid vanilla/Paper connection during negotiation.
            .optional()
            .payloadChannel()
            .play()
            .flow((PacketFlow) null)
            .addMain(ControlPayload.ID, ControlPayload.CODEC, (payload, context) -> {
                if (context.isClientSide()) {
                    NetworkBridge.receive(payload.data());
                }
            })
            .build();

    private ForgeClient() {
    }

    static void initialize() {
        MinemoriesClient.initialize(new ForgeClientPlatform(CHANNEL));
        TickEvent.ClientTickEvent.Pre.BUS.addListener(event ->
                CameraAimController.tick(Minecraft.getInstance()));
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> NetworkBridge.endClientTick());
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> NetworkBridge.disconnected());
    }
}

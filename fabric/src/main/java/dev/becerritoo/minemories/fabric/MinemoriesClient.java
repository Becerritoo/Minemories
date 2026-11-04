package dev.becerritoo.minemories.fabric;

import dev.becerritoo.minemories.client.CameraAimController;
import dev.becerritoo.minemories.client.net.ControlPayload;
import dev.becerritoo.minemories.client.net.NetworkBridge;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class MinemoriesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        dev.becerritoo.minemories.client.MinemoriesClient.initialize(new FabricClientPlatform());
        PayloadTypeRegistry.serverboundPlay().register(ControlPayload.ID, ControlPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ControlPayload.ID, ControlPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(ControlPayload.ID,
                (payload, context) -> NetworkBridge.receive(payload.data()));
        ClientTickEvents.START_CLIENT_TICK.register(CameraAimController::tick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> NetworkBridge.endClientTick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> NetworkBridge.disconnected());
    }
}

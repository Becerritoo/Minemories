package dev.becerritoo.minemories.fabric;

import dev.becerritoo.minemories.fabric.net.ControlPayload;
import dev.becerritoo.minemories.fabric.net.NetworkBridge;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class MinemoriesClient implements ClientModInitializer {
    public static final String MOD_ID = "minemories";
    private static ClientConfig config;

    @Override
    public void onInitializeClient() {
        config = ClientConfig.load();
        PayloadTypeRegistry.playC2S().register(ControlPayload.ID, ControlPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ControlPayload.ID, ControlPayload.CODEC);
        NetworkBridge.initialize();
        ClientTickEvents.START_CLIENT_TICK.register(CameraAimController::tick);
    }

    public static ClientConfig config() {
        return config;
    }
}

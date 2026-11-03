package dev.becerritoo.minemories.fabric;

import dev.becerritoo.minemories.client.ClientPlatform;
import dev.becerritoo.minemories.client.net.ControlPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

final class FabricClientPlatform implements ClientPlatform {
    @Override
    public Path configDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean serverAvailable() {
        return ClientPlayNetworking.canSend(ControlPayload.ID);
    }

    @Override
    public void sendToServer(byte[] payload) {
        ClientPlayNetworking.send(new ControlPayload(payload));
    }
}

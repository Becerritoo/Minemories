package dev.becerritoo.minemories.neoforge;

import dev.becerritoo.minemories.client.ClientPlatform;
import dev.becerritoo.minemories.client.net.ControlPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.nio.file.Path;

final class NeoForgeClientPlatform implements ClientPlatform {
    @Override
    public Path configDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean serverAvailable() {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && NetworkRegistry.hasChannel(connection, ControlPayload.ID.id());
    }

    @Override
    public void sendToServer(byte[] payload) {
        ClientPacketDistributor.sendToServer(new ControlPayload(payload));
    }
}

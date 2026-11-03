package dev.becerritoo.minemories.forge;

import dev.becerritoo.minemories.client.ClientPlatform;
import dev.becerritoo.minemories.client.net.ControlPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.Channel;

import java.nio.file.Path;

final class ForgeClientPlatform implements ClientPlatform {
    private final Channel<CustomPacketPayload> channel;

    ForgeClientPlatform(Channel<CustomPacketPayload> channel) {
        this.channel = channel;
    }

    @Override
    public Path configDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean serverAvailable() {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && channel.isRemotePresent(connection.getConnection());
    }

    @Override
    public void sendToServer(byte[] payload) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            throw new IllegalStateException("Not connected to a server");
        }
        channel.send(new ControlPayload(payload), connection.getConnection());
    }
}

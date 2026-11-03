package dev.becerritoo.minemories.neoforge;

import dev.becerritoo.minemories.client.CameraAimController;
import dev.becerritoo.minemories.client.MinemoriesClient;
import dev.becerritoo.minemories.client.net.ControlPayload;
import dev.becerritoo.minemories.client.net.NetworkBridge;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(value = MinemoriesClient.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeMinemories {
    public NeoForgeMinemories(IEventBus modBus) {
        MinemoriesClient.initialize(new NeoForgeClientPlatform());
        modBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(this::onClientTickPre);
        NeoForge.EVENT_BUS.addListener(this::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(this::onLoggingOut);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playBidirectional(
                ControlPayload.ID,
                ControlPayload.CODEC,
                (payload, context) -> {
                    // Minemories is client-only; a server-side receipt is intentionally ignored.
                },
                (payload, context) -> NetworkBridge.receive(payload.data())
        );
    }

    private void onClientTickPre(ClientTickEvent.Pre event) {
        CameraAimController.tick(Minecraft.getInstance());
    }

    private void onClientTickPost(ClientTickEvent.Post event) {
        NetworkBridge.endClientTick();
    }

    private void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        NetworkBridge.disconnected();
    }
}

package dev.becerritoo.minemories.client;

import java.util.Objects;

/** Shared client bootstrap used by Fabric, Forge and NeoForge. */
public final class MinemoriesClient {
    public static final String MOD_ID = "minemories";
    private static ClientPlatform platform;
    private static ClientConfig config;

    private MinemoriesClient() {
    }

    public static synchronized void initialize(ClientPlatform clientPlatform) {
        if (platform != null) {
            return;
        }
        platform = Objects.requireNonNull(clientPlatform, "clientPlatform");
        config = ClientConfig.load();
    }

    public static ClientPlatform platform() {
        return Objects.requireNonNull(platform, "Minemories client has not been initialized");
    }

    public static ClientConfig config() {
        return Objects.requireNonNull(config, "Minemories client has not been initialized");
    }
}

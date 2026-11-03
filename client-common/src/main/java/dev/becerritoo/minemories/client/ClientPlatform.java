package dev.becerritoo.minemories.client;

import java.nio.file.Path;

/** Loader-specific services required by the shared Minemories client. */
public interface ClientPlatform {
    Path configDirectory();

    boolean serverAvailable();

    void sendToServer(byte[] payload);
}

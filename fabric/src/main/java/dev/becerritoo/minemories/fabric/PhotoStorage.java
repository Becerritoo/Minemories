package dev.becerritoo.minemories.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

final class PhotoStorage {
    private static final DateTimeFormatter NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss")
            .withZone(ZoneId.systemDefault());

    private PhotoStorage() {
    }

    static CompletableFuture<Path> save(PhotoData photo) {
        return CompletableFuture.supplyAsync(() -> {
            Path directory = MinecraftClient.getInstance().runDirectory.toPath().resolve("screenshots").resolve("minemories");
            try {
                Files.createDirectories(directory);
                String base = NAME.format(Instant.ofEpochMilli(photo.capturedAtEpochMillis()));
                Path target = unique(directory, base);
                Files.write(target, photo.pngBytes());
                return target;
            } catch (IOException exception) {
                throw new IllegalStateException(exception.getMessage(), exception);
            }
        }, Util.getIoWorkerExecutor());
    }

    private static Path unique(Path directory, String base) {
        Path candidate = directory.resolve(base + ".png");
        int suffix = 2;
        while (Files.exists(candidate)) {
            candidate = directory.resolve(base + '_' + suffix++ + ".png");
        }
        return candidate;
    }
}

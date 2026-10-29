package dev.becerritoo.minemories.paper;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class ServerCaptureService implements AutoCloseable {
    private final MinemoriesPlugin plugin;
    private final Settings settings;
    private final CameraService cameras;
    private final UploadRateLimiter rateLimiter;
    private final DiscordDelivery discord;
    private final ArrayDeque<PendingCapture> queue = new ArrayDeque<>();
    private final Set<UUID> scheduledPlayers = new HashSet<>();
    private boolean active;
    private boolean closed;

    ServerCaptureService(MinemoriesPlugin plugin, Settings settings, CameraService cameras,
                         UploadRateLimiter rateLimiter, DiscordDelivery discord) {
        this.plugin = plugin;
        this.settings = settings;
        this.cameras = cameras;
        this.rateLimiter = rateLimiter;
        this.discord = discord;
    }

    void request(Player player, CameraStore.CameraRecord camera) {
        if (closed || !settings.serverRendererEnabled()) {
            player.sendMessage("§eEl renderizador fotográfico no está disponible.");
            return;
        }
        String denied = policy(player);
        if (denied != null) {
            player.sendMessage("§c" + denied);
            return;
        }
        if (scheduledPlayers.contains(player.getUniqueId())) {
            player.sendMessage("§eTu cámara ya tiene una fotografía en revelado.");
            return;
        }
        int waiting = queue.size() + (active ? 1 : 0);
        if (waiting >= settings.serverRendererQueue()) {
            player.sendMessage("§eEl cuarto oscuro está ocupado; intenta de nuevo en unos segundos.");
            return;
        }
        scheduledPlayers.add(player.getUniqueId());
        queue.addLast(new PendingCapture(player.getUniqueId(), camera.id(), camera.revision()));
        if (active) {
            player.sendMessage("§eFotografía añadida a la cola de revelado (posición " + queue.size() + ").");
        }
        drain();
    }

    private void drain() {
        if (closed || active) {
            return;
        }
        PendingCapture pending = queue.pollFirst();
        if (pending == null) {
            return;
        }
        Player player = Bukkit.getPlayer(pending.playerId());
        if (player == null || !player.isOnline()) {
            scheduledPlayers.remove(pending.playerId());
            drain();
            return;
        }
        String denied = policy(player);
        if (denied != null) {
            scheduledPlayers.remove(pending.playerId());
            player.sendMessage("§c" + denied);
            drain();
            return;
        }
        UploadRateLimiter.Result permission = rateLimiter.acquire(player.getUniqueId(),
                player.hasPermission("minemories.bypass.cooldown"), settings.cooldownSeconds(),
                settings.globalUploadsPerMinute());
        if (!permission.allowed()) {
            scheduledPlayers.remove(pending.playerId());
            player.sendMessage("§eNo se tomó la fotografía: " + permission.reason() + ".");
            drain();
            return;
        }
        var reservation = cameras.reserveDirect(player, pending.cameraId(), pending.cameraRevision());
        if (reservation.isEmpty()) {
            scheduledPlayers.remove(pending.playerId());
            player.sendMessage("§eNo se tomó la fotografía porque la cámara cambió mientras esperaba.");
            drain();
            return;
        }

        active = true;
        CameraService.CaptureReservation shot = reservation.get();
        Location location = player.getLocation();
        UUID worldId = player.getWorld().getUID();
        String playerName = player.getName();
        String discordId = plugin.discordId(player);
        boolean bypassCooldown = player.hasPermission("minemories.bypass.cooldown");
        boolean publish = settings.serverRendererPublish() && discord != null;
        TicketService.LocationSnapshot locationSnapshot = new TicketService.LocationSnapshot(
                location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        ServerSceneRenderer.Scene scene;
        try {
            scene = ServerSceneRenderer.capture(player, settings);
        } catch (RuntimeException exception) {
            finishFailure(pending, "No se pudo preparar la escena: " + exception.getMessage());
            return;
        }
        try {
            player.getWorld().playSound(location, Sound.BLOCK_DISPENSER_LAUNCH, 0.9f, 1.7f);
            player.getWorld().spawnParticle(Particle.FLASH, player.getEyeLocation().add(
                    player.getLocation().getDirection().multiply(0.45)), 1, org.bukkit.Color.WHITE);
        } catch (RuntimeException visualFailure) {
            plugin.getLogger().warning("No se pudo mostrar el efecto de cámara: "
                    + safeMessage(visualFailure));
        }
        player.sendMessage("§eRevelando fotografía…");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            RenderResult result;
            try {
                long started = System.nanoTime();
                ServerSceneRenderer.Rendered rendered = ServerSceneRenderer.render(scene);
                byte[] png = encodePng(ServerSceneRenderer.upscale(
                        rendered.image(), settings.serverRendererUpscale()));
                ImageSanitizer.SanitizedImage image = ImageSanitizer.sanitize(png, "image/png", settings);
                DiscordDelivery.Result delivery = publish
                        ? discord.send(settings, new TicketService.PhotoTicket(
                                pending.playerId(), playerName,
                                System.currentTimeMillis() + settings.ticketLifetimeSeconds() * 1000L,
                                locationSnapshot, discordId,
                                bypassCooldown, true, shot),
                        image, "", false)
                        : new DiscordDelivery.Result(true, "");
                long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
                result = new RenderResult(true, rendered.mapPixels(), image.bytes().length,
                        elapsedMillis, publish, delivery);
            } catch (IOException | RuntimeException exception) {
                result = new RenderResult(false, null, 0, 0, publish,
                        new DiscordDelivery.Result(false, safeMessage(exception)));
            }
            RenderResult completed = result;
            Bukkit.getScheduler().runTask(plugin, () -> complete(
                    pending, shot, worldId, location, playerName, completed));
        });
    }

    private void complete(PendingCapture pending, CameraService.CaptureReservation shot, UUID worldId,
                          Location location, String playerName, RenderResult result) {
        try {
            if (closed) {
                return;
            }
            Player player = Bukkit.getPlayer(pending.playerId());
            if (!result.rendered()) {
                if (player != null) {
                    player.sendMessage("§cNo se pudo revelar la fotografía: " + result.delivery().reason());
                }
                plugin.getLogger().warning("Falló el render fotográfico de " + playerName + ": "
                        + result.delivery().reason());
                return;
            }
            if (settings.serverRendererGiveMap()) {
                World world = Bukkit.getWorld(worldId);
                if (world != null) {
                    ItemStack print = cameras.createPrint(world, shot.photoId(),
                            result.mapPixels(), playerName, "");
                    if (player != null && player.isOnline()) {
                        Map<Integer, ItemStack> remaining = player.getInventory().addItem(print);
                        remaining.values().forEach(item -> world.dropItemNaturally(player.getLocation(), item));
                    } else if (world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                        world.dropItemNaturally(location, print);
                    }
                }
            }
            String message;
            if (result.published() && result.delivery().success()) {
                message = "§aInstax impresa y publicada en Discord.";
            } else if (result.published()) {
                message = "§eInstax impresa, pero Discord respondió: "
                        + result.delivery().reason();
            } else {
                message = "§aInstax impresa.";
            }
            if (player != null) {
                player.sendMessage(message);
            }
            plugin.getLogger().info("Render fotográfico de " + playerName + ": "
                    + result.elapsedMillis() + " ms, " + humanBytes(result.imageBytes())
                    + (result.published() ? ", Discord=" + result.delivery().success() : "") + ".");
        } finally {
            active = false;
            scheduledPlayers.remove(pending.playerId());
            drain();
        }
    }

    private void finishFailure(PendingCapture pending, String reason) {
        Player player = Bukkit.getPlayer(pending.playerId());
        if (player != null) {
            player.sendMessage("§c" + reason);
        }
        active = false;
        scheduledPlayers.remove(pending.playerId());
        drain();
    }

    private String policy(Player player) {
        if (!player.hasPermission("minemories.use")) {
            return "No tienes permiso para tomar fotografías.";
        }
        if (plugin.isBanned(player.getUniqueId())) {
            return "No tienes permitido publicar fotografías.";
        }
        if (settings.requireAuthMe() && !plugin.isAuthenticated(player)) {
            return "Inicia sesión antes de tomar fotografías.";
        }
        if (settings.serverRendererPublish() && discord != null
                && settings.requireDiscordLink() && plugin.discordId(player) == null) {
            return "Debes vincular tu cuenta mediante DiscordSRV antes de publicar.";
        }
        return null;
    }

    private static byte[] encodePng(java.awt.image.BufferedImage image) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                throw new IOException("no hay codificador PNG disponible");
            }
            return output.toByteArray();
        }
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? "error interno de renderizado" : message;
    }

    private static String humanBytes(int bytes) {
        return Math.max(1, Math.round(bytes / 1024.0f)) + "K";
    }

    @Override
    public void close() {
        closed = true;
        queue.clear();
        scheduledPlayers.clear();
    }

    private record PendingCapture(UUID playerId, UUID cameraId, long cameraRevision) {
    }

    private record RenderResult(boolean rendered, byte[] mapPixels, int imageBytes, long elapsedMillis,
                                boolean published, DiscordDelivery.Result delivery) {
        RenderResult {
            mapPixels = mapPixels == null ? null : mapPixels.clone();
        }

        @Override
        public byte[] mapPixels() {
            return mapPixels == null ? null : mapPixels.clone();
        }
    }
}

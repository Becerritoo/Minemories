package dev.becerritoo.minemories.paper;

import dev.becerritoo.minemories.common.Protocol;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MinemoriesPlugin extends JavaPlugin implements PluginMessageListener, Listener {
    private final Set<UUID> onlinePlayers = ConcurrentHashMap.newKeySet();
    private final TicketService tickets = new TicketService();
    private final UploadRateLimiter rateLimiter = new UploadRateLimiter();
    private final DiscordWebhook webhook = new DiscordWebhook();
    private Settings settings;
    private MinecraftUploadService uploadService;
    private DiscordSrvBridge discordSrv;
    private DiscordDelivery discordDelivery;
    private AuthMeBridge authMe;
    private BanStore bans;
    private CameraService cameras;
    private ServerCaptureService serverCapture;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        bans = new BanStore(getDataFolder(), getLogger());
        getServer().getMessenger().registerIncomingPluginChannel(this, Protocol.CHANNEL, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, Protocol.CHANNEL);
        getServer().getPluginManager().registerEvents(this, this);
        onlinePlayers.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList());

        PluginCommand command = getCommand("minemories");
        if (command != null) {
            var handler = new MinemoriesCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }
        reloadServices();
        startCameraService();
    }

    @Override
    public void onDisable() {
        stopUploadService();
        stopCameraService();
        tickets.clear();
        onlinePlayers.clear();
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    void reloadAll() {
        stopUploadService();
        stopCameraService();
        tickets.clear();
        reloadConfig();
        reloadServices();
        startCameraService();
    }

    private void reloadServices() {
        settings = Settings.load(getConfig());
        discordSrv = new DiscordSrvBridge(getServer().getPluginManager(), getLogger(),
                settings.discordSrvChannelId());
        authMe = new AuthMeBridge(getServer().getPluginManager(), getLogger());
        String error = settings.validationError();
        if (error != null) {
            getLogger().warning("Minemories no está listo: " + error + ". Revisa config.yml.");
            return;
        }
        if (discordSrv.detected()) {
            if (!discordSrv.deliveryAvailable()) {
                getLogger().warning("Minemories no está listo: " + discordSrv.deliveryError()
                        + ". Revisa discordsrv.channel-id y los permisos del bot.");
                getLogger().warning("Las Instax aún podrán imprimirse, pero no publicarse en Discord.");
            } else {
                discordDelivery = discordSrv;
            }
        } else if (settings.webhookAvailable()) {
            discordDelivery = webhook;
        } else {
            getLogger().warning("No hay destino Discord; las cámaras sólo imprimirán las Instax.");
        }
        if (settings.requireDiscordLink() && !discordSrv.available()) {
            getLogger().warning("Se exige vinculación con DiscordSRV, pero DiscordSRV no está disponible; no se emitirán tickets.");
        }
        uploadService = new MinecraftUploadService(this, settings, tickets, rateLimiter, discordDelivery);
        getLogger().info("Transporte de fotografías activo mediante la conexión de Minecraft; destino: "
                + (discordDelivery == null ? "solo impresiones Instax" : discordDelivery.description()) + ".");
    }

    private void startCameraService() {
        if (!settings.cameraEnabled()) {
            return;
        }
        try {
            cameras = new CameraService(this);
            getServer().getPluginManager().registerEvents(cameras, this);
            if (settings.serverRendererEnabled()) {
                serverCapture = new ServerCaptureService(this, settings, cameras, rateLimiter, discordDelivery);
            }
            getLogger().info("Cámaras físicas activas: " + settings.cameraDurability()
                    + " disparos base, rollos y protección contra duplicados; captura="
                    + settings.cameraCaptureMode() + ".");
        } catch (RuntimeException exception) {
            cameras = null;
            getLogger().severe("No se pudo iniciar el sistema de cámaras: " + exception.getMessage());
        }
    }

    private void stopCameraService() {
        if (serverCapture != null) {
            serverCapture.close();
            serverCapture = null;
        }
        if (cameras != null) {
            HandlerList.unregisterAll(cameras);
            cameras.close();
            cameras = null;
        }
    }

    private void stopUploadService() {
        if (uploadService != null) {
            uploadService.close();
            uploadService = null;
        }
        discordDelivery = null;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] payload) {
        if (!Protocol.CHANNEL.equals(channel) || payload.length > Protocol.MAX_PACKET_BYTES) {
            return;
        }
        int opcode;
        try {
            opcode = Protocol.opcode(payload);
        } catch (Protocol.ProtocolException ignored) {
            return;
        }
        if (opcode == Protocol.REQUEST_TICKET) {
            Protocol.TicketRequest request;
            try {
                request = Protocol.decodeRequest(payload);
            } catch (Protocol.ProtocolException ignored) {
                return;
            }
            Protocol.TicketResponse response = issueTicket(player, request);
            player.sendPluginMessage(this, Protocol.CHANNEL, Protocol.encodeResponse(response));
        } else if (opcode == Protocol.CAMERA_SHUTTER) {
            try {
                Protocol.decodeCameraShutter(payload);
            } catch (Protocol.ProtocolException ignored) {
                return;
            }
            if (cameras != null) {
                cameras.fireShutter(player);
            }
        } else if (uploadService != null) {
            uploadService.handle(player, payload);
        }
    }

    private Protocol.TicketResponse issueTicket(Player player, Protocol.TicketRequest request) {
        long requestId = request.requestId();
        if (uploadService == null) {
            return Protocol.TicketResponse.denied(requestId, "Minemories todavía no está configurado en este servidor.");
        }
        if (!player.hasPermission("minemories.use")) {
            return Protocol.TicketResponse.denied(requestId, "No tienes permiso para publicar fotografías.");
        }
        if (bans.contains(player.getUniqueId())) {
            return Protocol.TicketResponse.denied(requestId, "No tienes permitido publicar fotografías.");
        }
        if (settings.requireAuthMe() && !authMe.isAuthenticated(player)) {
            return Protocol.TicketResponse.denied(requestId, "Inicia sesión en el servidor antes de publicar.");
        }
        String discordId = discordSrv.discordId(player.getUniqueId());
        if (request.publishToDiscord() && settings.requireDiscordLink() && discordId == null) {
            String reason = discordSrv.available()
                    ? "Debes vincular tu cuenta mediante DiscordSRV antes de publicar."
                    : "La vinculación con Discord no está disponible en este momento.";
            return Protocol.TicketResponse.denied(requestId, reason);
        }
        if (request.publishToDiscord() && discordDelivery == null) {
            return Protocol.TicketResponse.denied(requestId, "No hay un destino de Discord disponible.");
        }
        CameraService.CaptureReservation reservation = null;
        if (request.cameraCapture()) {
            if (cameras == null) {
                return Protocol.TicketResponse.denied(requestId, "El sistema de cámaras no está disponible.");
            }
            var reserved = cameras.reserve(player, request.captureToken());
            if (reserved.isEmpty()) {
                return Protocol.TicketResponse.denied(requestId,
                        "La activación de la cámara venció o el objeto ya no es válido.");
            }
            reservation = reserved.get();
        } else if (settings.requireCameraForServer()) {
            return Protocol.TicketResponse.denied(requestId,
                    "Usa una cámara Minemories con un rollo cargado para fotografiar.");
        }
        TicketService.IssuedTicket issued = tickets.issue(player, settings.ticketLifetimeSeconds(), discordId,
                request.publishToDiscord(), reservation);
        return new Protocol.TicketResponse(
                requestId,
                true,
                "Autorizado",
                issued.token(),
                issued.ticket().expiresAtEpochMillis(),
                settings.maxFileBytes(),
                settings.targetFileBytes(),
                settings.maxWidth(),
                settings.maxHeight(),
                settings.jpegQuality(),
                settings.cooldownSeconds(),
                settings.maxCaptionCharacters(),
                settings.allowCoordinates(),
                settings.serverName()
        );
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        onlinePlayers.add(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        onlinePlayers.remove(playerId);
        tickets.invalidate(playerId);
        if (uploadService != null) {
            uploadService.invalidate(playerId);
        }
    }

    boolean isPlayerOnline(UUID playerId) {
        return onlinePlayers.contains(playerId);
    }

    boolean isBanned(UUID playerId) {
        return bans.contains(playerId);
    }

    void messagePlayer(UUID playerId, String message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage(message);
        }
    }

    Settings settings() {
        return settings;
    }

    BanStore bans() {
        return bans;
    }

    boolean uploadReady() {
        return uploadService != null;
    }

    int activeTransfers() {
        return uploadService == null ? 0 : uploadService.activeTransfers();
    }

    boolean discordSrvAvailable() {
        return discordSrv != null && discordSrv.available();
    }

    String discordDeliveryDescription() {
        return discordDelivery == null ? "no disponible" : discordDelivery.description();
    }

    CameraService cameras() {
        return cameras;
    }

    void requestServerCapture(Player player, CameraStore.CameraRecord camera) {
        if (serverCapture == null) {
            player.sendMessage("§eEl renderizador fotográfico del servidor no está disponible.");
            return;
        }
        serverCapture.request(player, camera);
    }

    boolean isAuthenticated(Player player) {
        return authMe == null || authMe.isAuthenticated(player);
    }

    String discordId(Player player) {
        return discordSrv == null ? null : discordSrv.discordId(player.getUniqueId());
    }
}

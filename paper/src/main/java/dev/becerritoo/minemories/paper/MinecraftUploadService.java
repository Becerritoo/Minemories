package dev.becerritoo.minemories.paper;

import dev.becerritoo.minemories.common.Protocol;
import dev.becerritoo.minemories.common.MapPaletteCodec;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Semaphore;

final class MinecraftUploadService implements AutoCloseable {
    private final MinemoriesPlugin plugin;
    private final Settings settings;
    private final TicketService tickets;
    private final UploadRateLimiter rateLimiter;
    private final DiscordDelivery discord;
    private final Semaphore activeSlots;
    private final Map<UUID, IncomingUpload> incoming = new HashMap<>();
    private volatile boolean closed;

    MinecraftUploadService(MinemoriesPlugin plugin, Settings settings, TicketService tickets,
                           UploadRateLimiter rateLimiter, DiscordDelivery discord) {
        this.plugin = plugin;
        this.settings = settings;
        this.tickets = tickets;
        this.rateLimiter = rateLimiter;
        this.discord = discord;
        this.activeSlots = new Semaphore(settings.maxConcurrentUploads());
    }

    void handle(Player player, byte[] payload) {
        if (closed) {
            return;
        }
        int opcode;
        try {
            opcode = Protocol.opcode(payload);
        } catch (Protocol.ProtocolException ignored) {
            return;
        }
        switch (opcode) {
            case Protocol.UPLOAD_BEGIN -> begin(player, payload);
            case Protocol.UPLOAD_CHUNK -> chunk(player, payload);
            default -> {
            }
        }
    }

    private void begin(Player player, byte[] payload) {
        Protocol.UploadBegin begin;
        try {
            begin = Protocol.decodeUploadBegin(payload);
        } catch (Protocol.ProtocolException exception) {
            return;
        }
        if (incoming.containsKey(player.getUniqueId())) {
            sendResult(player, begin.requestId(), false, "Ya tienes una fotografía en transferencia.");
            return;
        }

        Optional<TicketService.PhotoTicket> consumed = tickets.consume(begin.token());
        if (consumed.isEmpty() || !consumed.get().playerId().equals(player.getUniqueId())) {
            sendResult(player, begin.requestId(), false, "La autorización venció o no es válida.");
            return;
        }
        TicketService.PhotoTicket ticket = consumed.get();
        if (!plugin.isPlayerOnline(ticket.playerId()) || plugin.isBanned(ticket.playerId())) {
            sendResult(player, begin.requestId(), false, "Ya no tienes autorización para publicar.");
            return;
        }
        byte[] instaxPixels = begin.instaxPixels();
        int muralBytes = begin.muralBytes();
        boolean cameraCapture = ticket.reservation() != null;
        if ((!cameraCapture && instaxPixels.length != 0)
                || (!cameraCapture && muralBytes != 0)
                || (cameraCapture && settings.serverRendererGiveMap()
                && (!MapPaletteCodec.valid(instaxPixels)
                || muralBytes != MapPaletteCodec.MURAL_PIXEL_COUNT))) {
            sendResult(player, begin.requestId(), false, "Los datos de la Instax no son válidos.");
            return;
        }

        long transferBytes = (long) begin.totalBytes() + muralBytes;
        int expectedChunks = (int) ((transferBytes + Protocol.CHUNK_BYTES - 1L)
                / Protocol.CHUNK_BYTES);
        if (begin.totalBytes() < 1 || begin.totalBytes() > settings.maxFileBytes()
                || transferBytes > settings.maxFileBytes() + (long) MapPaletteCodec.MURAL_PIXEL_COUNT
                || begin.chunkCount() < 1 || begin.chunkCount() != expectedChunks) {
            sendResult(player, begin.requestId(), false, "El tamaño declarado de la fotografía no es válido.");
            return;
        }
        String caption = begin.caption() == null ? "" : begin.caption().strip();
        if (caption.codePointCount(0, caption.length()) > settings.maxCaptionCharacters()) {
            sendResult(player, begin.requestId(), false, "El pie de foto es demasiado largo.");
            return;
        }
        if (!activeSlots.tryAcquire()) {
            sendResult(player, begin.requestId(), false, "El servidor está procesando demasiadas fotografías.");
            return;
        }
        UploadRateLimiter.Result permission = rateLimiter.acquire(ticket.playerId(), ticket.bypassCooldown(),
                settings.cooldownSeconds(), settings.globalUploadsPerMinute());
        if (!permission.allowed()) {
            activeSlots.release();
            sendResult(player, begin.requestId(), false, permission.reason());
            return;
        }

        IncomingUpload upload = new IncomingUpload(begin.requestId(), ticket, caption,
                begin.includeCoordinates() && settings.allowCoordinates(), begin.totalBytes(), begin.chunkCount(),
                instaxPixels, muralBytes);
        incoming.put(player.getUniqueId(), upload);
        long timeoutTicks = Math.max(100L, settings.ticketLifetimeSeconds() * 20L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> timeout(player.getUniqueId(), upload), timeoutTicks);
    }

    private void chunk(Player player, byte[] payload) {
        IncomingUpload upload = incoming.get(player.getUniqueId());
        if (upload == null) {
            return;
        }
        Protocol.UploadChunk chunk;
        try {
            chunk = Protocol.decodeUploadChunk(payload);
        } catch (Protocol.ProtocolException exception) {
            abort(player, upload, "Se recibió un fragmento de imagen inválido.");
            return;
        }
        if (chunk.requestId() != upload.requestId || !upload.append(chunk.index(), chunk.data())) {
            abort(player, upload, "Los fragmentos de la fotografía llegaron fuera de orden.");
            return;
        }
        if (upload.complete()) {
            incoming.remove(player.getUniqueId(), upload);
            process(upload);
        }
    }

    private void process(IncomingUpload upload) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean success = false;
            String message;
            byte[] printPixels = null;
            try {
                ImageSanitizer.SanitizedImage image = ImageSanitizer.sanitize(
                        upload.imageBytes(), "image/jpeg", settings);
                if (closed) {
                    return;
                }
                boolean stored = false;
                if (upload.ticket.reservation() != null && plugin.cameras() != null) {
                    stored = true;
                    if (settings.serverRendererGiveMap() && MapPaletteCodec.valid(upload.instaxPixels)) {
                        printPixels = upload.instaxPixels.clone();
                        if (MapPaletteCodec.validMural(upload.muralPixels())) {
                            plugin.cameras().saveMural(upload.ticket.reservation().photoId(),
                                    upload.muralPixels());
                        }
                    }
                }
                DiscordDelivery.Result result;
                if (upload.ticket.publishToDiscord()) {
                    result = discord == null
                            ? new DiscordDelivery.Result(false, "No hay destino de Discord disponible.")
                            : discord.send(settings, upload.ticket, image, upload.caption, upload.includeCoordinates);
                } else {
                    result = new DiscordDelivery.Result(true, "");
                }
                success = result.success();
                if (success && upload.ticket.publishToDiscord()) {
                    plugin.getLogger().info("Fotografía de " + upload.ticket.playerName()
                            + " publicada: recibidos " + humanBytes(upload.imageBytes)
                            + ", enviados " + humanBytes(image.bytes().length)
                            + " mediante " + discord.description() + ".");
                    message = stored ? "Instax impresa y publicada en Discord."
                            : "Recuerdo publicado en Discord.";
                } else if (success) {
                    message = stored ? "Instax impresa." : "Fotografía procesada.";
                } else {
                    message = stored ? "Instax impresa, pero Discord respondió: " + result.reason()
                            : result.reason();
                }
            } catch (IOException exception) {
                message = exception.getMessage();
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("No se pudo procesar una fotografía: " + exception.getMessage());
                message = "No se pudo procesar la fotografía.";
            } finally {
                activeSlots.release();
            }
            if (!closed && plugin.isEnabled()) {
                boolean finalSuccess = success;
                String finalMessage = message == null || message.isBlank()
                        ? "No se pudo publicar la fotografía." : message;
                byte[] finalPrintPixels = printPixels;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (finalPrintPixels != null && plugin.cameras() != null) {
                        try {
                            deliverPrint(upload.ticket, finalPrintPixels);
                        } catch (RuntimeException exception) {
                            plugin.getLogger().warning("La fotografía se procesó, pero no se pudo crear la Instax: "
                                    + exception.getMessage());
                        }
                    }
                    sendResult(upload.ticket.playerId(), upload.requestId, finalSuccess, finalMessage);
                });
            }
        });
    }

    private void deliverPrint(TicketService.PhotoTicket ticket, byte[] pixels) {
        Player player = Bukkit.getPlayer(ticket.playerId());
        org.bukkit.World world = Bukkit.getWorld(ticket.location().world());
        if (world == null) {
            plugin.getLogger().warning("No se pudo crear la Instax: el mundo ya no está disponible.");
            return;
        }
        ItemStack print = plugin.cameras().createPrint(world, ticket.reservation().photoId(),
                pixels, ticket.playerName());
        if (player != null && player.isOnline()) {
            Map<Integer, ItemStack> remaining = player.getInventory().addItem(print);
            remaining.values().forEach(item -> world.dropItemNaturally(player.getLocation(), item));
        } else {
            int chunkX = ticket.location().x() >> 4;
            int chunkZ = ticket.location().z() >> 4;
            if (world.isChunkLoaded(chunkX, chunkZ)) {
                org.bukkit.Location location = new org.bukkit.Location(world, ticket.location().x() + 0.5,
                        ticket.location().y() + 0.5, ticket.location().z() + 0.5);
                world.dropItemNaturally(location, print);
            }
        }
    }

    private void timeout(UUID playerId, IncomingUpload expected) {
        if (incoming.remove(playerId, expected)) {
            activeSlots.release();
            sendResult(playerId, expected.requestId, false, "La transferencia de la fotografía expiró.");
        }
    }

    private void abort(Player player, IncomingUpload upload, String reason) {
        if (incoming.remove(player.getUniqueId(), upload)) {
            activeSlots.release();
        }
        sendResult(player, upload.requestId, false, reason);
    }

    void invalidate(UUID playerId) {
        IncomingUpload removed = incoming.remove(playerId);
        if (removed != null) {
            activeSlots.release();
        }
    }

    int activeTransfers() {
        return settings.maxConcurrentUploads() - activeSlots.availablePermits();
    }

    private void sendResult(UUID playerId, long requestId, boolean success, String message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            sendResult(player, requestId, success, message);
        }
    }

    private void sendResult(Player player, long requestId, boolean success, String message) {
        player.sendPluginMessage(plugin, Protocol.CHANNEL,
                Protocol.encodeUploadResult(new Protocol.UploadResult(requestId, success, message)));
    }

    private static String humanBytes(int bytes) {
        if (bytes >= 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1fM", bytes / (1024.0 * 1024.0));
        }
        return Math.max(1, Math.round(bytes / 1024.0f)) + "K";
    }

    @Override
    public void close() {
        closed = true;
        int abandoned = incoming.size();
        incoming.clear();
        if (abandoned > 0) {
            activeSlots.release(abandoned);
        }
    }

    private static final class IncomingUpload {
        private final long requestId;
        private final TicketService.PhotoTicket ticket;
        private final String caption;
        private final boolean includeCoordinates;
        private final byte[] bytes;
        private final int chunkCount;
        private final byte[] instaxPixels;
        private final int imageBytes;
        private final int muralBytes;
        private int nextChunk;
        private int receivedBytes;

        private IncomingUpload(long requestId, TicketService.PhotoTicket ticket, String caption,
                               boolean includeCoordinates, int imageBytes, int chunkCount, byte[] instaxPixels,
                               int muralBytes) {
            this.requestId = requestId;
            this.ticket = ticket;
            this.caption = caption;
            this.includeCoordinates = includeCoordinates;
            this.bytes = new byte[imageBytes + muralBytes];
            this.chunkCount = chunkCount;
            this.instaxPixels = instaxPixels.clone();
            this.imageBytes = imageBytes;
            this.muralBytes = muralBytes;
        }

        private boolean append(int index, byte[] chunk) {
            int remaining = bytes.length - receivedBytes;
            int expectedLength = Math.min(Protocol.CHUNK_BYTES, remaining);
            if (index != nextChunk || index >= chunkCount || chunk.length != expectedLength) {
                return false;
            }
            System.arraycopy(chunk, 0, bytes, receivedBytes, chunk.length);
            receivedBytes += chunk.length;
            nextChunk++;
            return true;
        }

        private boolean complete() {
            return nextChunk == chunkCount && receivedBytes == bytes.length;
        }

        private byte[] imageBytes() {
            return java.util.Arrays.copyOf(bytes, imageBytes);
        }

        private byte[] muralPixels() {
            return muralBytes == 0 ? new byte[0]
                    : java.util.Arrays.copyOfRange(bytes, imageBytes, imageBytes + muralBytes);
        }
    }
}

package dev.becerritoo.minemories.client.net;

import dev.becerritoo.minemories.common.Protocol;
import dev.becerritoo.minemories.client.ClientConfig;
import dev.becerritoo.minemories.client.CameraAimController;
import dev.becerritoo.minemories.client.CaptureManager;
import dev.becerritoo.minemories.client.ImageEncoder;
import dev.becerritoo.minemories.client.InstaxSettings;
import dev.becerritoo.minemories.client.MinemoriesClient;
import dev.becerritoo.minemories.client.PersonalWebhookClient;
import dev.becerritoo.minemories.client.PhotoData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class NetworkBridge {
    private static final int PERSONAL_MAX_BYTES = 500 * 1024;
    private static final long MAX_QUEUED_SOURCE_BYTES = 64L * 1024L * 1024L;
    private static final long CHUNK_FALLBACK_INTERVAL_MILLIS = 100L;
    private static final System.Logger LOGGER = System.getLogger("Minemories");
    private static final AtomicLong REQUEST_IDS = new AtomicLong(System.nanoTime());
    private static final Map<Long, QueuedUpload> PENDING = new ConcurrentHashMap<>();
    private static final Object QUEUE_LOCK = new Object();
    private static final ArrayDeque<QueuedUpload> QUEUE = new ArrayDeque<>();
    private static QueuedUpload activeUpload;
    private static OutgoingTransfer outgoingTransfer;
    private static long retainedSourceBytes;
    private static long nextUploadAtMillis;
    private static boolean drainScheduled;

    private NetworkBridge() {
    }

    public static boolean serverAvailable() {
        try {
            return Minecraft.getInstance().getConnection() != null
                    && MinemoriesClient.platform().serverAvailable();
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "No se pudo solicitar el disparo de cámara al servidor.", exception);
            return false;
        }
    }

    public static boolean hasAvailableDestination() {
        ClientConfig config = MinemoriesClient.config();
        boolean server = serverAvailable();
        return config.usesServer(server)
                || (config.usesPersonal(server) && !config.enabledPersonalWebhooks().isEmpty());
    }

    public static boolean requestCameraShutter() {
        if (!serverAvailable()) {
            return false;
        }
        try {
            MinemoriesClient.platform().sendToServer(Protocol.encodeCameraShutter());
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static int requestUpload(PhotoData photo, String caption, boolean coordinates, UploadCallback callback) {
        return requestUpload(photo, caption, coordinates, null, false, callback);
    }

    public static int requestUpload(PhotoData photo, String caption, boolean coordinates,
                                    byte[] exactInstaxPixels, UploadCallback callback) {
        return requestUpload(photo, caption, coordinates, exactInstaxPixels, false, callback);
    }

    public static int requestCameraStore(PhotoData photo, String caption, boolean coordinates,
                                         UploadCallback callback) {
        return requestUpload(photo, caption, coordinates, null, true, callback);
    }

    public static int requestCameraStore(PhotoData photo, String caption, boolean coordinates,
                                         byte[] exactInstaxPixels, UploadCallback callback) {
        return requestUpload(photo, caption, coordinates, exactInstaxPixels, true, callback);
    }

    private static int requestUpload(PhotoData photo, String caption, boolean coordinates,
                                     byte[] exactInstaxPixels, boolean storeOnly, UploadCallback callback) {
        ClientConfig config = MinemoriesClient.config();
        boolean available = serverAvailable();
        boolean publishToServer = !storeOnly && config.usesServer(available);
        boolean sendServer = publishToServer || (photo.cameraCapture() && available);
        List<ClientConfig.PersonalWebhook> personal = !storeOnly && config.usesPersonal(available)
                ? config.enabledPersonalWebhooks() : List.of();
        if (!sendServer && personal.isEmpty()) {
            callback.complete(false, "No hay ningún destino de Discord configurado.");
            return -1;
        }

        ImageEncoder.Limits personalLimits = new ImageEncoder.Limits(
                PERSONAL_MAX_BYTES,
                config.personalTargetFileBytes,
                config.maxUploadWidth,
                config.maxUploadHeight,
                config.jpegQuality
        );
        QueuedUpload upload = new QueuedUpload(photo, caption, coordinates, callback,
                sendServer, publishToServer, personal, personalLimits, config.personalCooldownSeconds,
                personalContext(caption, coordinates), config.instaxSettings(), exactInstaxPixels);
        int position;
        synchronized (QUEUE_LOCK) {
            int queuedPhotos = QUEUE.size() + (activeUpload == null ? 0 : 1);
            long newTotalBytes = retainedSourceBytes + photo.pngBytes().length;
            if (queuedPhotos >= config.maxQueuedPhotos || newTotalBytes > MAX_QUEUED_SOURCE_BYTES) {
                callback.complete(false, "La cola de fotografías está llena.");
                return -1;
            }
            QUEUE.addLast(upload);
            retainedSourceBytes = newTotalBytes;
            position = QUEUE.size() + (activeUpload == null ? 0 : 1);
            scheduleDrainLocked(0L);
        }
        callback.progress(position == 1
                ? "Preparando la publicación…"
                : "Fotografía añadida a la cola (posición " + position + ").");
        return position;
    }

    private static void drainQueue() {
        QueuedUpload upload;
        synchronized (QUEUE_LOCK) {
            drainScheduled = false;
            if (activeUpload != null || QUEUE.isEmpty()) {
                return;
            }
            long remaining = nextUploadAtMillis - System.currentTimeMillis();
            if (remaining > 0L) {
                QUEUE.peekFirst().callback.progress(
                        "Esperando " + ((remaining + 999L) / 1000L) + " s para publicar…");
                scheduleDrainLocked(remaining);
                return;
            }
            upload = QUEUE.removeFirst();
            activeUpload = upload;
        }
        if (upload.sendServer) {
            requestTicket(upload);
        } else {
            prepareAndDispatch(upload, null);
        }
    }

    private static void requestTicket(QueuedUpload upload) {
        if (!serverAvailable()) {
            destinationComplete(upload, "servidor", false,
                    "El servidor no tiene disponible Minemories Paper.", 0);
            if (!upload.personalDestinations.isEmpty()) {
                prepareAndDispatch(upload, null);
            }
            return;
        }
        long requestId = REQUEST_IDS.incrementAndGet();
        PENDING.put(requestId, upload);
        try {
            byte[] request = upload.photo.cameraCapture()
                    ? Protocol.encodeCameraRequest(requestId, upload.photo.cameraCaptureToken(), upload.publishToServer)
                    : Protocol.encodeRequest(requestId);
            MinemoriesClient.platform().sendToServer(request);
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "No se pudo solicitar autorización para publicar la fotografía.", exception);
            PENDING.remove(requestId);
            destinationComplete(upload, "servidor", false,
                    "No se pudo contactar al servidor.", 0);
            if (!upload.personalDestinations.isEmpty()) {
                prepareAndDispatch(upload, null);
            }
            return;
        }
        CompletableFuture.delayedExecutor(12, TimeUnit.SECONDS).execute(() -> {
            QueuedUpload expired = PENDING.remove(requestId);
            if (expired != null) {
                Minecraft.getInstance().execute(() -> {
                    destinationComplete(expired, "servidor", false,
                            "El servidor no respondió.", 0);
                    if (!expired.personalDestinations.isEmpty()) {
                        prepareAndDispatch(expired, null);
                    }
                });
            }
        });
    }

    public static void receive(byte[] payload) {
        byte[] data = payload.clone();
        Minecraft.getInstance().execute(() -> handleResponse(data));
    }

    public static void endClientTick() {
        sendChunks();
    }

    public static void disconnected() {
        CameraAimController.reset();
        clearQueue("Te desconectaste del servidor.");
    }

    private static void handleResponse(byte[] data) {
        int opcode;
        try {
            opcode = Protocol.opcode(data);
        } catch (Protocol.ProtocolException ignored) {
            return;
        }
        if (opcode == Protocol.TICKET_RESPONSE) {
            handleTicketResponse(data);
        } else if (opcode == Protocol.UPLOAD_RESULT) {
            handleUploadResult(data);
        } else if (opcode == Protocol.CAMERA_TRIGGER) {
            handleCameraTrigger(data);
        } else if (opcode == Protocol.ALBUM_BEGIN || opcode == Protocol.ALBUM_CHUNK) {
            AlbumReceiver.receive(data);
        }
    }

    private static void handleCameraTrigger(byte[] data) {
        try {
            Protocol.CameraTrigger trigger = Protocol.decodeCameraTrigger(data);
            Minecraft client = Minecraft.getInstance();
            if (!CaptureManager.start(client, trigger.captureToken()) && client.player != null) {
                client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§eCierra la pantalla actual para usar la cámara."));
            }
        } catch (Protocol.ProtocolException ignored) {
        }
    }

    private static void handleTicketResponse(byte[] data) {
        Protocol.TicketResponse response;
        try {
            response = Protocol.decodeResponse(data);
        } catch (Protocol.ProtocolException ignored) {
            return;
        }
        QueuedUpload pending = PENDING.remove(response.requestId());
        if (pending == null || !isActive(pending)) {
            return;
        }
        if (!response.accepted() || response.expiresAtEpochMillis() <= System.currentTimeMillis()) {
            String reason = response.accepted() ? "La autorización del servidor venció." : response.message();
            destinationComplete(pending, "servidor", false, reason, 0);
            if (!pending.personalDestinations.isEmpty()) {
                prepareAndDispatch(pending, null);
            }
            return;
        }
        pending.callback.progress("Preparando la imagen…");
        prepareAndDispatch(pending, response);
    }

    private static void prepareAndDispatch(QueuedUpload upload, Protocol.TicketResponse ticket) {
        if (!isActive(upload) || upload.preparationStarted) {
            return;
        }
        upload.preparationStarted = true;
        ImageEncoder.Limits limits = upload.personalLimits;
        if (ticket != null) {
            ImageEncoder.Limits serverLimits = new ImageEncoder.Limits(
                    ticket.maxBytes(), ticket.targetBytes(), ticket.maxWidth(), ticket.maxHeight(), ticket.jpegQuality());
            limits = upload.personalDestinations.isEmpty() ? serverLimits : limits.combine(serverLimits);
        }
        ImageEncoder.Limits finalLimits = limits;
        CompletableFuture.supplyAsync(() -> ImageEncoder.prepare(
                        upload.photo, finalLimits, upload.instaxSettings, upload.exactInstaxPixels))
                .whenComplete((image, error) -> Minecraft.getInstance().execute(() -> {
                    if (!isActive(upload)) {
                        return;
                    }
                    if (error != null) {
                        LOGGER.log(System.Logger.Level.WARNING,
                                "No se pudo preparar la fotografía para publicación.", error);
                        completeRemainingAsFailure(upload, readableError(error));
                        return;
                    }
                    if (ticket != null) {
                        startTransfer(ticket, upload, image);
                    }
                    dispatchPersonal(upload, image);
                }));
    }

    private static void startTransfer(Protocol.TicketResponse ticket, QueuedUpload upload,
                                      ImageEncoder.EncodedImage image) {
        String caption = limitCodePoints(upload.caption, ticket.maxCaptionCharacters());
        byte[] imageBytes = image.bytes();
        byte[] instaxPixels = image.instaxPixels();
        byte[] muralPixels = image.muralPixels();
        byte[] transferBytes = java.util.Arrays.copyOf(imageBytes, imageBytes.length + muralPixels.length);
        System.arraycopy(muralPixels, 0, transferBytes, imageBytes.length, muralPixels.length);
        int chunkCount = (transferBytes.length + Protocol.CHUNK_BYTES - 1) / Protocol.CHUNK_BYTES;
        var begin = new Protocol.UploadBegin(ticket.requestId(), ticket.token(), imageBytes.length,
                chunkCount, caption, upload.coordinates && ticket.coordinatesAllowed(),
                instaxPixels, muralPixels.length);
        try {
            synchronized (QUEUE_LOCK) {
                if (activeUpload != upload) {
                    return;
                }
                outgoingTransfer = new OutgoingTransfer(ticket, upload, transferBytes, chunkCount);
            }
            MinemoriesClient.platform().sendToServer(Protocol.encodeUploadBegin(begin));
            scheduleChunkFallback(outgoingTransfer);
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "No se pudo iniciar la transferencia de la fotografía.", exception);
            synchronized (QUEUE_LOCK) {
                if (outgoingTransfer != null && outgoingTransfer.upload == upload) {
                    outgoingTransfer = null;
                }
            }
            destinationComplete(upload, "servidor", false,
                    "No se pudo iniciar la transferencia al servidor.", 0);
            return;
        }
        upload.callback.progress("Publicando la fotografía…");
        CompletableFuture.delayedExecutor(45, TimeUnit.SECONDS).execute(() ->
                Minecraft.getInstance().execute(() -> timeoutTransfer(ticket.requestId(), upload)));
    }

    private static void scheduleChunkFallback(OutgoingTransfer expected) {
        if (expected == null) {
            return;
        }
        CompletableFuture.delayedExecutor(CHUNK_FALLBACK_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
                .execute(() -> Minecraft.getInstance().execute(() -> {
                    synchronized (QUEUE_LOCK) {
                        if (outgoingTransfer != expected || expected.nextChunk >= expected.chunkCount) {
                            return;
                        }
                    }
                    // Los loaders normalmente bombean cuatro fragmentos por tick. Este
                    // respaldo mantiene viva la transferencia si un loader deja de
                    // entregar temporalmente su evento de fin de tick.
                    sendChunks();
                    synchronized (QUEUE_LOCK) {
                        if (outgoingTransfer != expected || expected.nextChunk >= expected.chunkCount) {
                            return;
                        }
                    }
                    scheduleChunkFallback(expected);
                }));
    }

    private static void dispatchPersonal(QueuedUpload upload, ImageEncoder.EncodedImage image) {
        for (ClientConfig.PersonalWebhook destination : upload.personalDestinations) {
            PersonalWebhookClient.send(destination, image, upload.personalContext)
                    .whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
                        if (error != null) {
                            destinationComplete(upload, destination.name, false,
                                    "No fue posible conectar con Discord.", 0);
                        } else {
                            destinationComplete(upload, destination.name, result.success(), result.reason(), 0);
                        }
                    }));
        }
    }

    private static void sendChunks() {
        for (int sentThisTick = 0; sentThisTick < 4; sentThisTick++) {
            OutgoingTransfer transfer;
            int index;
            byte[] chunk;
            synchronized (QUEUE_LOCK) {
                transfer = outgoingTransfer;
                if (transfer == null || transfer.nextChunk >= transfer.chunkCount) {
                    return;
                }
                index = transfer.nextChunk;
                int offset = index * Protocol.CHUNK_BYTES;
                int end = Math.min(transfer.bytes.length, offset + Protocol.CHUNK_BYTES);
                chunk = java.util.Arrays.copyOfRange(transfer.bytes, offset, end);
                transfer.nextChunk++;
            }
            try {
                MinemoriesClient.platform().sendToServer(
                        Protocol.encodeUploadChunk(transfer.ticket.requestId(), index, chunk));
            } catch (RuntimeException exception) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Se interrumpió el envío de fragmentos de la fotografía.", exception);
                synchronized (QUEUE_LOCK) {
                    if (outgoingTransfer == transfer) {
                        outgoingTransfer = null;
                    }
                }
                destinationComplete(transfer.upload, "servidor", false,
                        "Se interrumpió la transferencia al servidor.", 0);
                return;
            }
        }
    }

    private static void handleUploadResult(byte[] data) {
        Protocol.UploadResult result;
        try {
            result = Protocol.decodeUploadResult(data);
        } catch (Protocol.ProtocolException ignored) {
            return;
        }
        OutgoingTransfer transfer;
        synchronized (QUEUE_LOCK) {
            transfer = outgoingTransfer;
            if (transfer == null || transfer.ticket.requestId() != result.requestId()) {
                return;
            }
            outgoingTransfer = null;
        }
        destinationComplete(transfer.upload, "servidor", result.success(), result.message(),
                transfer.ticket.cooldownSeconds());
    }

    private static void timeoutTransfer(long requestId, QueuedUpload upload) {
        synchronized (QUEUE_LOCK) {
            if (outgoingTransfer == null || outgoingTransfer.ticket.requestId() != requestId) {
                return;
            }
            outgoingTransfer = null;
        }
        destinationComplete(upload, "servidor", false,
                "El servidor no confirmó la publicación.", 0);
    }

    private static void completeRemainingAsFailure(QueuedUpload upload, String reason) {
        while (isActive(upload) && upload.completedDestinations < upload.expectedDestinations) {
            destinationComplete(upload, "Discord", false, reason, 0);
        }
    }

    private static void destinationComplete(QueuedUpload upload, String destination, boolean success,
                                            String reason, int cooldownSeconds) {
        if (!isActive(upload) || upload.completedDestinations >= upload.expectedDestinations) {
            return;
        }
        upload.completedDestinations++;
        if (success) {
            upload.successfulDestinations++;
        } else if (upload.firstFailure == null) {
            String readable = reason == null || reason.isBlank() ? "no se pudo publicar" : reason;
            upload.firstFailure = destination + ": " + readable;
        }
        upload.cooldownSeconds = Math.max(upload.cooldownSeconds, cooldownSeconds);
        if (upload.completedDestinations == upload.expectedDestinations) {
            finish(upload);
        }
    }

    private static void finish(QueuedUpload upload) {
        boolean completed;
        synchronized (QUEUE_LOCK) {
            completed = activeUpload == upload;
            if (!completed) {
                return;
            }
            activeUpload = null;
            if (outgoingTransfer != null && outgoingTransfer.upload == upload) {
                outgoingTransfer = null;
            }
            retainedSourceBytes = Math.max(0L, retainedSourceBytes - upload.photo.pngBytes().length);
            int cooldown = Math.max(upload.cooldownSeconds,
                    upload.personalDestinations.isEmpty() ? 0 : upload.personalCooldownSeconds);
            if (cooldown > 0) {
                nextUploadAtMillis = Math.max(nextUploadAtMillis,
                        System.currentTimeMillis() + cooldown * 1000L);
            }
            scheduleDrainLocked(0L);
        }

        boolean success = upload.successfulDestinations == upload.expectedDestinations;
        String message;
        if (success) {
            message = upload.expectedDestinations == 1
                    ? (upload.sendServer && !upload.publishToServer
                    ? "Instax impresa." : "Recuerdo publicado en Discord.")
                    : "Fotografía enviada a " + upload.expectedDestinations + " destinos.";
        } else if (upload.successfulDestinations > 0) {
            message = "Fotografía enviada a " + upload.successfulDestinations + " de "
                    + upload.expectedDestinations + " destinos.";
        } else {
            message = "No se pudo publicar la fotografía"
                    + (upload.firstFailure == null ? "." : ": " + upload.firstFailure);
        }
        upload.callback.complete(success, message);
    }

    private static boolean isActive(QueuedUpload upload) {
        synchronized (QUEUE_LOCK) {
            return activeUpload == upload;
        }
    }

    private static void scheduleDrainLocked(long delayMillis) {
        if (drainScheduled) {
            return;
        }
        drainScheduled = true;
        CompletableFuture.delayedExecutor(Math.max(0L, delayMillis), TimeUnit.MILLISECONDS)
                .execute(() -> Minecraft.getInstance().execute(NetworkBridge::drainQueue));
    }

    private static void clearQueue(String reason) {
        List<QueuedUpload> cancelled = new ArrayList<>();
        synchronized (QUEUE_LOCK) {
            if (activeUpload != null) {
                cancelled.add(activeUpload);
            }
            cancelled.addAll(QUEUE);
            QUEUE.clear();
            activeUpload = null;
            outgoingTransfer = null;
            retainedSourceBytes = 0L;
            nextUploadAtMillis = 0L;
            drainScheduled = false;
            PENDING.clear();
        }
        cancelled.forEach(upload -> upload.callback.complete(false, reason));
    }

    private static String limitCodePoints(String value, int maximum) {
        if (value == null || maximum <= 0) {
            return "";
        }
        String stripped = value.strip();
        int count = stripped.codePointCount(0, stripped.length());
        return count <= maximum ? stripped : stripped.substring(0, stripped.offsetByCodePoints(0, maximum));
    }

    private static String readableError(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null
                && (current.getMessage() == null || current instanceof java.util.concurrent.CompletionException)) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "No se pudo preparar la fotografía." : current.getMessage();
    }

    private static PersonalWebhookClient.UploadContext personalContext(String caption, boolean coordinates) {
        Minecraft client = Minecraft.getInstance();
        String playerName = client.player == null ? "Jugador" : client.player.getName().getString();
        String serverName = "Minecraft";
        if (client.getCurrentServer() != null) {
            serverName = client.getCurrentServer().name;
        } else if (client.isLocalServer()) {
            serverName = "Mundo local";
        }
        String world = client.level == null ? "desconocido"
                : client.level.dimension().identifier().toString();
        BlockPos position = client.player == null ? BlockPos.ZERO : client.player.blockPosition();
        return new PersonalWebhookClient.UploadContext(playerName, serverName, caption, coordinates,
                world, position.getX(), position.getY(), position.getZ());
    }

    private static final class QueuedUpload {
        private final PhotoData photo;
        private final String caption;
        private final boolean coordinates;
        private final UploadCallback callback;
        private final boolean sendServer;
        private final boolean publishToServer;
        private final List<ClientConfig.PersonalWebhook> personalDestinations;
        private final ImageEncoder.Limits personalLimits;
        private final int personalCooldownSeconds;
        private final PersonalWebhookClient.UploadContext personalContext;
        private final InstaxSettings instaxSettings;
        private final byte[] exactInstaxPixels;
        private final int expectedDestinations;
        private boolean preparationStarted;
        private int completedDestinations;
        private int successfulDestinations;
        private int cooldownSeconds;
        private String firstFailure;

        private QueuedUpload(PhotoData photo, String caption, boolean coordinates, UploadCallback callback,
                             boolean sendServer, boolean publishToServer,
                             List<ClientConfig.PersonalWebhook> personalDestinations,
                             ImageEncoder.Limits personalLimits, int personalCooldownSeconds,
                             PersonalWebhookClient.UploadContext personalContext,
                             InstaxSettings instaxSettings, byte[] exactInstaxPixels) {
            this.photo = photo;
            this.caption = caption;
            this.coordinates = coordinates;
            this.callback = callback;
            this.sendServer = sendServer;
            this.publishToServer = publishToServer;
            this.personalDestinations = List.copyOf(personalDestinations);
            this.personalLimits = personalLimits;
            this.personalCooldownSeconds = personalCooldownSeconds;
            this.personalContext = personalContext;
            this.instaxSettings = instaxSettings;
            this.exactInstaxPixels = exactInstaxPixels == null ? null : exactInstaxPixels.clone();
            this.expectedDestinations = (sendServer ? 1 : 0) + personalDestinations.size();
        }
    }

    private static final class OutgoingTransfer {
        private final Protocol.TicketResponse ticket;
        private final QueuedUpload upload;
        private final byte[] bytes;
        private final int chunkCount;
        private int nextChunk;

        private OutgoingTransfer(Protocol.TicketResponse ticket, QueuedUpload upload,
                                 byte[] bytes, int chunkCount) {
            this.ticket = ticket;
            this.upload = upload;
            this.bytes = bytes;
            this.chunkCount = chunkCount;
        }
    }

    public interface UploadCallback {
        void progress(String message);

        void complete(boolean success, String message);
    }
}

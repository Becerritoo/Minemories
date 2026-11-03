package dev.becerritoo.minemories.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.becerritoo.minemories.client.net.NetworkBridge;
import java.nio.file.Path;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class PreviewScreen extends Screen {
    private static final Identifier PREVIEW_ID = Identifier.fromNamespaceAndPath("minemories", "preview");
    private static final Identifier INSTAX_PREVIEW_ID = Identifier.fromNamespaceAndPath("minemories", "preview_instax");
    private static final int MAX_PANEL_WIDTH = 420;
    private static final int MAX_PANEL_HEIGHT = 300;
    private static final int PANEL_MARGIN = 8;
    private static final int PANEL_PADDING = 12;
    private static final int CONTROLS_HEIGHT = 118;
    private final PhotoData photo;
    private final NativeImage image;
    private EditBox caption;
    private Checkbox coordinates;
    private Checkbox saveLocal;
    private Checkbox cleanHud;
    private Button sendButton;
    private Button previewModeButton;
    private NativeImage instaxImage;
    private byte[] renderedInstaxPixels;
    private boolean instaxTextureRegistered;
    private boolean showInstax;
    private boolean instaxGenerating;
    private boolean disposed;
    private long instaxGeneration;
    private InstaxSettings renderedInstaxSettings;
    private Component status = Component.literal("Revisa la fotografía antes de publicarla.");
    private boolean textureRegistered;
    private boolean saved;
    private boolean uploading;
    private boolean preserveForChild;
    private String captionDraft = "";
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int controlsTop;

    public PreviewScreen(PhotoData photo, NativeImage image) {
        super(Component.literal("Minemories"));
        this.photo = photo;
        this.image = image;
    }

    @Override
    public void added() {
        preserveForChild = false;
        disposed = false;
        if (!textureRegistered) {
            minecraft.getTextureManager().register(PREVIEW_ID,
                    new DynamicTexture(() -> "Minemories preview", image));
            textureRegistered = true;
        }
        if (showInstax && !MinemoriesClient.config().instaxSettings().equals(renderedInstaxSettings)) {
            requestInstaxPreview();
        }
    }

    @Override
    protected void init() {
        panelWidth = Math.min(MAX_PANEL_WIDTH, Math.max(1, width - PANEL_MARGIN * 2));
        panelHeight = Math.min(MAX_PANEL_HEIGHT, Math.max(1, height - PANEL_MARGIN * 2));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        controlsTop = panelTop + panelHeight - CONTROLS_HEIGHT;

        int contentLeft = panelLeft + PANEL_PADDING;
        int contentWidth = panelWidth - PANEL_PADDING * 2;
        caption = new EditBox(font, contentLeft, controlsTop + 17,
                contentWidth, 20, Component.literal("Pie de foto"));
        caption.setMaxLength(1000);
        caption.setHint(Component.literal("Agrega un pie de foto opcional…"));
        caption.setValue(captionDraft);
        addRenderableWidget(caption);

        addRenderableWidget(Button.builder(Component.literal("⚙"), button -> openSettings())
                .bounds(panelLeft + panelWidth - 25, panelTop + 4, 20, 20).build());

        if (photo.cameraCapture()) {
            previewModeButton = addRenderableWidget(Button.builder(previewModeText(), button -> {
                showInstax = !showInstax;
                button.setMessage(previewModeText());
                if (showInstax && (!instaxTextureRegistered
                        || !MinemoriesClient.config().instaxSettings().equals(renderedInstaxSettings))) {
                    requestInstaxPreview();
                }
            }).bounds(panelLeft + 5, panelTop + 4, 112, 20).build());

            addRenderableWidget(Button.builder(Component.literal("Editar Instax"), button -> openInstaxEditor())
                    .bounds(panelLeft + panelWidth - 125, panelTop + 4, 96, 20).build());
        }

        ClientConfig config = MinemoriesClient.config();
        coordinates = addRenderableWidget(Checkbox.builder(Component.literal("Enviar coordenadas"), font)
                .pos(contentLeft, controlsTop + 43).selected(config.includeCoordinates)
                .onValueChange((widget, checked) -> config.includeCoordinates = checked).build());
        saveLocal = addRenderableWidget(Checkbox.builder(Component.literal("Guardar copia local"), font)
                .pos(panelLeft + panelWidth / 2, controlsTop + 43).selected(config.saveLocalCopy)
                .onValueChange((widget, checked) -> config.saveLocalCopy = checked).build());
        cleanHud = addRenderableWidget(Checkbox.builder(Component.literal("HUD limpio en la próxima foto"), font)
                .pos(contentLeft, controlsTop + 63).selected(config.cleanHud)
                .onValueChange((widget, checked) -> config.cleanHud = checked).build());

        int buttonY = controlsTop + 83;
        int gap = 4;
        int available = contentWidth - gap * 2;
        int sendWidth = Math.max(1, available * 45 / 100);
        int saveWidth = Math.max(1, available * 30 / 100);
        int cancelWidth = Math.max(1, available - sendWidth - saveWidth);
        sendButton = addRenderableWidget(Button.builder(Component.literal("Enviar a Discord"), button -> publish())
                .bounds(contentLeft, buttonY, sendWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal(photo.cameraCapture() ? "Imprimir Instax" : "Solo guardar"),
                        button -> saveOnly())
                .bounds(contentLeft + sendWidth + gap, buttonY, saveWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancelar"), button -> onClose())
                .bounds(contentLeft + sendWidth + gap + saveWidth + gap, buttonY, cancelWidth, 20).build());
        setInitialFocus(caption);
    }

    private void publish() {
        if (uploading) {
            return;
        }
        if (!NetworkBridge.hasAvailableDestination()) {
            status = Component.literal("§eConfigura un destino de Discord para continuar.");
            openSettings();
            return;
        }
        uploading = true;
        sendButton.active = false;
        savePreferences();
        status = Component.literal("§ePreparando la publicación…");
        if (saveLocal.selected() && !saved) {
            saveInBackground(false);
        }
        int queuePosition = NetworkBridge.requestUpload(
                photo, caption.getValue(), coordinates.selected(), exactInstaxPixels(),
                new NetworkBridge.UploadCallback() {
            @Override
            public void progress(String message) {
                minecraft.execute(() -> status = Component.literal("§e" + message));
            }

            @Override
            public void complete(boolean success, String message) {
                minecraft.execute(() -> {
                    uploading = false;
                    sendButton.active = true;
                    status = Component.literal((success ? "§a" : "§c") + message);
                    if (minecraft.player != null && minecraft.screen != PreviewScreen.this) {
                        minecraft.player.displayClientMessage(Component.literal((success ? "§a" : "§c") + message), false);
                    }
                });
            }
        });
        if (queuePosition > 0) {
            if (minecraft.player != null) {
                String queued = queuePosition == 1
                        ? "§aFotografía en cola."
                        : "§eFotografía en cola (posición " + queuePosition + ").";
                minecraft.player.displayClientMessage(Component.literal(queued), false);
            }
            onClose();
        }
    }

    private void saveOnly() {
        savePreferences();
        if (!photo.cameraCapture()) {
            saveInBackground(true);
            return;
        }
        if (uploading) {
            return;
        }
        uploading = true;
        sendButton.active = false;
        if (saveLocal.selected() && !saved) {
            saveInBackground(false);
        }
        int queuePosition = NetworkBridge.requestCameraStore(
                photo, caption.getValue(), coordinates.selected(), exactInstaxPixels(),
                new NetworkBridge.UploadCallback() {
                    @Override
                    public void progress(String message) {
                        minecraft.execute(() -> status = Component.literal("§e" + message));
                    }

                    @Override
                    public void complete(boolean success, String message) {
                        minecraft.execute(() -> {
                            uploading = false;
                            status = Component.literal((success ? "§a" : "§c") + message);
                            if (minecraft.player != null) {
                                minecraft.player.displayClientMessage(Component.literal((success ? "§a" : "§c") + message), false);
                            }
                        });
                    }
                });
        if (queuePosition > 0) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal("§aInstax enviada para impresión."), false);
            }
            onClose();
        } else {
            uploading = false;
            sendButton.active = true;
        }
    }

    private void saveInBackground(boolean closeAfter) {
        if (saved) {
            if (closeAfter) {
                onClose();
            }
            return;
        }
        status = Component.literal("§eGuardando copia local…");
        PhotoStorage.save(photo).whenComplete((path, error) -> minecraft.execute(() -> {
            if (error != null) {
                status = Component.literal("§cNo se pudo guardar: " + error.getMessage());
                return;
            }
            saved = true;
            status = Component.literal("§aGuardada en " + relative(path));
            if (closeAfter) {
                onClose();
            }
        }));
    }

    private void savePreferences() {
        captionDraft = caption == null ? captionDraft : caption.getValue();
        ClientConfig config = MinemoriesClient.config();
        if (coordinates != null) {
            config.includeCoordinates = coordinates.selected();
        }
        if (saveLocal != null) {
            config.saveLocalCopy = saveLocal.selected();
        }
        if (cleanHud != null) {
            config.cleanHud = cleanHud.selected();
        }
        config.save();
    }

    private void openSettings() {
        if (uploading) {
            return;
        }
        savePreferences();
        preserveForChild = true;
        minecraft.setScreen(new DestinationSettingsScreen(this));
    }

    private void openInstaxEditor() {
        if (uploading || !photo.cameraCapture()) {
            return;
        }
        showInstax = true;
        savePreferences();
        preserveForChild = true;
        minecraft.setScreen(new InstaxSettingsScreen(this, photo));
    }

    PhotoData photo() {
        return photo;
    }

    private Component previewModeText() {
        return Component.literal(showInstax ? "Vista: Instax" : "Vista: Original");
    }

    private void requestInstaxPreview() {
        long generation = ++instaxGeneration;
        InstaxSettings settings = MinemoriesClient.config().instaxSettings();
        instaxGenerating = true;
        java.util.concurrent.CompletableFuture.supplyAsync(
                        () -> InstaxPreviewGenerator.generate(photo, settings))
                .whenComplete((pixels, error) -> minecraft.execute(() -> {
                    if (disposed || generation != instaxGeneration || minecraft.screen != this) {
                        return;
                    }
                    instaxGenerating = false;
                    if (error != null) {
                        status = Component.literal("§cNo se pudo generar la vista Instax.");
                        return;
                    }
                    destroyInstaxTexture();
                    instaxImage = InstaxPreviewGenerator.nativeImage(pixels);
                    renderedInstaxPixels = pixels.clone();
                    minecraft.getTextureManager().register(INSTAX_PREVIEW_ID,
                            new DynamicTexture(() -> "Minemories Instax preview", instaxImage));
                    instaxTextureRegistered = true;
                    renderedInstaxSettings = settings;
                }));
    }

    private byte[] exactInstaxPixels() {
        return renderedInstaxPixels != null
                && MinemoriesClient.config().instaxSettings().equals(renderedInstaxSettings)
                ? renderedInstaxPixels.clone() : null;
    }

    private String relative(Path path) {
        try {
            return minecraft.gameDirectory.toPath().relativize(path).toString();
        } catch (IllegalArgumentException ignored) {
            return path.toString();
        }
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(panelLeft - 3, panelTop - 3,
                panelLeft + panelWidth + 3, panelTop + panelHeight + 3, 0x66000000);
        context.fill(panelLeft, panelTop,
                panelLeft + panelWidth, panelTop + panelHeight, 0xee101014);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
        renderBackground(context, mouseX, mouseY, deltaTicks);
        context.drawCenteredString(font, title, width / 2, panelTop + 8, 0xffffff);
        int availableWidth = Math.max(1, panelWidth - PANEL_PADDING * 2);
        int availableHeight = Math.max(1, controlsTop - panelTop - 32);
        if (showInstax) {
            int scale = Math.max(1, Math.min(availableWidth, availableHeight) / 128);
            int drawSize = Math.min(Math.min(availableWidth, availableHeight), 128 * scale);
            int x = (width - drawSize) / 2;
            int y = panelTop + 24 + (availableHeight - drawSize) / 2;
            context.fill(x - 2, y - 2, x + drawSize + 2, y + drawSize + 2, 0xff4c473c);
            if (instaxTextureRegistered) {
                context.blit(RenderPipelines.GUI_TEXTURED, INSTAX_PREVIEW_ID, x, y, 0, 0,
                        drawSize, drawSize, 128, 128, 128, 128);
            } else {
                context.fill(x, y, x + drawSize, y + drawSize, 0xff18181c);
                context.drawCenteredString(font,
                        Component.literal(instaxGenerating ? "Preparando Instax…" : "Vista no disponible"),
                        width / 2, y + drawSize / 2 - 4, 0xdddddd);
            }
        } else {
            double scale = Math.min(availableWidth / (double) photo.width(),
                    availableHeight / (double) photo.height());
            int drawWidth = Math.max(1, (int) Math.round(photo.width() * scale));
            int drawHeight = Math.max(1, (int) Math.round(photo.height() * scale));
            int x = (width - drawWidth) / 2;
            int y = panelTop + 24 + (availableHeight - drawHeight) / 2;
            context.blit(RenderPipelines.GUI_TEXTURED, PREVIEW_ID, x, y, 0, 0,
                    drawWidth, drawHeight, photo.width(), photo.height(), photo.width(), photo.height());
        }
        context.fill(panelLeft, controlsTop - 4, panelLeft + panelWidth, panelTop + panelHeight, 0xf01b1b22);
        context.drawString(font, Component.literal("Pie de foto"),
                panelLeft + PANEL_PADDING, controlsTop + 5, 0xdddddd);
        String visibleStatus = font.plainSubstrByWidth(status.getString(), panelWidth - PANEL_PADDING * 2);
        context.drawString(font, Component.literal(visibleStatus),
                panelLeft + PANEL_PADDING, panelTop + panelHeight - 12, 0xffffff);
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !uploading;
    }

    @Override
    public void removed() {
        savePreferences();
        if (preserveForChild) {
            return;
        }
        disposed = true;
        instaxGeneration++;
        destroyInstaxTexture();
        if (textureRegistered) {
            minecraft.getTextureManager().release(PREVIEW_ID);
            textureRegistered = false;
        } else {
            image.close();
        }
    }

    private void destroyInstaxTexture() {
        if (instaxTextureRegistered) {
            minecraft.getTextureManager().release(INSTAX_PREVIEW_ID);
            instaxTextureRegistered = false;
            instaxImage = null;
        } else if (instaxImage != null) {
            instaxImage.close();
            instaxImage = null;
        }
    }
}

package dev.becerritoo.minemories.fabric;

import dev.becerritoo.minemories.fabric.net.NetworkBridge;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.nio.file.Path;

public final class PreviewScreen extends Screen {
    private static final Identifier PREVIEW_ID = Identifier.of("minemories", "preview");
    private static final Identifier INSTAX_PREVIEW_ID = Identifier.of("minemories", "preview_instax");
    private static final int MAX_PANEL_WIDTH = 420;
    private static final int MAX_PANEL_HEIGHT = 300;
    private static final int PANEL_MARGIN = 8;
    private static final int PANEL_PADDING = 12;
    private static final int CONTROLS_HEIGHT = 118;
    private final PhotoData photo;
    private final NativeImage image;
    private TextFieldWidget caption;
    private CheckboxWidget coordinates;
    private CheckboxWidget saveLocal;
    private CheckboxWidget cleanHud;
    private ButtonWidget sendButton;
    private ButtonWidget previewModeButton;
    private NativeImage instaxImage;
    private byte[] renderedInstaxPixels;
    private boolean instaxTextureRegistered;
    private boolean showInstax;
    private boolean instaxGenerating;
    private boolean disposed;
    private long instaxGeneration;
    private InstaxSettings renderedInstaxSettings;
    private Text status = Text.literal("Revisa la fotografía antes de publicarla.");
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
        super(Text.literal("Minemories"));
        this.photo = photo;
        this.image = image;
    }

    @Override
    public void onDisplayed() {
        preserveForChild = false;
        disposed = false;
        if (!textureRegistered) {
            client.getTextureManager().registerTexture(PREVIEW_ID,
                    new NativeImageBackedTexture(() -> "Minemories preview", image));
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
        caption = new TextFieldWidget(textRenderer, contentLeft, controlsTop + 17,
                contentWidth, 20, Text.literal("Pie de foto"));
        caption.setMaxLength(1000);
        caption.setPlaceholder(Text.literal("Agrega un pie de foto opcional…"));
        caption.setText(captionDraft);
        addDrawableChild(caption);

        addDrawableChild(ButtonWidget.builder(Text.literal("⚙"), button -> openSettings())
                .dimensions(panelLeft + panelWidth - 25, panelTop + 4, 20, 20).build());

        if (photo.cameraCapture()) {
            previewModeButton = addDrawableChild(ButtonWidget.builder(previewModeText(), button -> {
                showInstax = !showInstax;
                button.setMessage(previewModeText());
                if (showInstax && (!instaxTextureRegistered
                        || !MinemoriesClient.config().instaxSettings().equals(renderedInstaxSettings))) {
                    requestInstaxPreview();
                }
            }).dimensions(panelLeft + 5, panelTop + 4, 112, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Editar Instax"), button -> openInstaxEditor())
                    .dimensions(panelLeft + panelWidth - 125, panelTop + 4, 96, 20).build());
        }

        ClientConfig config = MinemoriesClient.config();
        coordinates = addDrawableChild(CheckboxWidget.builder(Text.literal("Enviar coordenadas"), textRenderer)
                .pos(contentLeft, controlsTop + 43).checked(config.includeCoordinates)
                .callback((widget, checked) -> config.includeCoordinates = checked).build());
        saveLocal = addDrawableChild(CheckboxWidget.builder(Text.literal("Guardar copia local"), textRenderer)
                .pos(panelLeft + panelWidth / 2, controlsTop + 43).checked(config.saveLocalCopy)
                .callback((widget, checked) -> config.saveLocalCopy = checked).build());
        cleanHud = addDrawableChild(CheckboxWidget.builder(Text.literal("HUD limpio en la próxima foto"), textRenderer)
                .pos(contentLeft, controlsTop + 63).checked(config.cleanHud)
                .callback((widget, checked) -> config.cleanHud = checked).build());

        int buttonY = controlsTop + 83;
        int gap = 4;
        int available = contentWidth - gap * 2;
        int sendWidth = Math.max(1, available * 45 / 100);
        int saveWidth = Math.max(1, available * 30 / 100);
        int cancelWidth = Math.max(1, available - sendWidth - saveWidth);
        sendButton = addDrawableChild(ButtonWidget.builder(Text.literal("Enviar a Discord"), button -> publish())
                .dimensions(contentLeft, buttonY, sendWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(photo.cameraCapture() ? "Imprimir Instax" : "Solo guardar"),
                        button -> saveOnly())
                .dimensions(contentLeft + sendWidth + gap, buttonY, saveWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancelar"), button -> close())
                .dimensions(contentLeft + sendWidth + gap + saveWidth + gap, buttonY, cancelWidth, 20).build());
        setInitialFocus(caption);
    }

    private void publish() {
        if (uploading) {
            return;
        }
        if (!NetworkBridge.hasAvailableDestination()) {
            status = Text.literal("§eConfigura un destino de Discord para continuar.");
            openSettings();
            return;
        }
        uploading = true;
        sendButton.active = false;
        savePreferences();
        status = Text.literal("§ePreparando la publicación…");
        if (saveLocal.isChecked() && !saved) {
            saveInBackground(false);
        }
        int queuePosition = NetworkBridge.requestUpload(
                photo, caption.getText(), coordinates.isChecked(), exactInstaxPixels(),
                new NetworkBridge.UploadCallback() {
            @Override
            public void progress(String message) {
                client.execute(() -> status = Text.literal("§e" + message));
            }

            @Override
            public void complete(boolean success, String message) {
                client.execute(() -> {
                    uploading = false;
                    sendButton.active = true;
                    status = Text.literal((success ? "§a" : "§c") + message);
                    if (client.player != null && client.currentScreen != PreviewScreen.this) {
                        client.player.sendMessage(Text.literal((success ? "§a" : "§c") + message), false);
                    }
                });
            }
        });
        if (queuePosition > 0) {
            if (client.player != null) {
                String queued = queuePosition == 1
                        ? "§aFotografía en cola."
                        : "§eFotografía en cola (posición " + queuePosition + ").";
                client.player.sendMessage(Text.literal(queued), false);
            }
            close();
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
        if (saveLocal.isChecked() && !saved) {
            saveInBackground(false);
        }
        int queuePosition = NetworkBridge.requestCameraStore(
                photo, caption.getText(), coordinates.isChecked(), exactInstaxPixels(),
                new NetworkBridge.UploadCallback() {
                    @Override
                    public void progress(String message) {
                        client.execute(() -> status = Text.literal("§e" + message));
                    }

                    @Override
                    public void complete(boolean success, String message) {
                        client.execute(() -> {
                            uploading = false;
                            status = Text.literal((success ? "§a" : "§c") + message);
                            if (client.player != null) {
                                client.player.sendMessage(Text.literal((success ? "§a" : "§c") + message), false);
                            }
                        });
                    }
                });
        if (queuePosition > 0) {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§aInstax enviada para impresión."), false);
            }
            close();
        } else {
            uploading = false;
            sendButton.active = true;
        }
    }

    private void saveInBackground(boolean closeAfter) {
        if (saved) {
            if (closeAfter) {
                close();
            }
            return;
        }
        status = Text.literal("§eGuardando copia local…");
        PhotoStorage.save(photo).whenComplete((path, error) -> client.execute(() -> {
            if (error != null) {
                status = Text.literal("§cNo se pudo guardar: " + error.getMessage());
                return;
            }
            saved = true;
            status = Text.literal("§aGuardada en " + relative(path));
            if (closeAfter) {
                close();
            }
        }));
    }

    private void savePreferences() {
        captionDraft = caption == null ? captionDraft : caption.getText();
        ClientConfig config = MinemoriesClient.config();
        if (coordinates != null) {
            config.includeCoordinates = coordinates.isChecked();
        }
        if (saveLocal != null) {
            config.saveLocalCopy = saveLocal.isChecked();
        }
        if (cleanHud != null) {
            config.cleanHud = cleanHud.isChecked();
        }
        config.save();
    }

    private void openSettings() {
        if (uploading) {
            return;
        }
        savePreferences();
        preserveForChild = true;
        client.setScreen(new DestinationSettingsScreen(this));
    }

    private void openInstaxEditor() {
        if (uploading || !photo.cameraCapture()) {
            return;
        }
        showInstax = true;
        savePreferences();
        preserveForChild = true;
        client.setScreen(new InstaxSettingsScreen(this, photo));
    }

    PhotoData photo() {
        return photo;
    }

    private Text previewModeText() {
        return Text.literal(showInstax ? "Vista: Instax" : "Vista: Original");
    }

    private void requestInstaxPreview() {
        long generation = ++instaxGeneration;
        InstaxSettings settings = MinemoriesClient.config().instaxSettings();
        instaxGenerating = true;
        java.util.concurrent.CompletableFuture.supplyAsync(
                        () -> InstaxPreviewGenerator.generate(photo, settings))
                .whenComplete((pixels, error) -> client.execute(() -> {
                    if (disposed || generation != instaxGeneration || client.currentScreen != this) {
                        return;
                    }
                    instaxGenerating = false;
                    if (error != null) {
                        status = Text.literal("§cNo se pudo generar la vista Instax.");
                        return;
                    }
                    destroyInstaxTexture();
                    instaxImage = InstaxPreviewGenerator.nativeImage(pixels);
                    renderedInstaxPixels = pixels.clone();
                    client.getTextureManager().registerTexture(INSTAX_PREVIEW_ID,
                            new NativeImageBackedTexture(() -> "Minemories Instax preview", instaxImage));
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
            return client.runDirectory.toPath().relativize(path).toString();
        } catch (IllegalArgumentException ignored) {
            return path.toString();
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(panelLeft - 3, panelTop - 3,
                panelLeft + panelWidth + 3, panelTop + panelHeight + 3, 0x66000000);
        context.fill(panelLeft, panelTop,
                panelLeft + panelWidth, panelTop + panelHeight, 0xee101014);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        renderBackground(context, mouseX, mouseY, deltaTicks);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, panelTop + 8, 0xffffff);
        int availableWidth = Math.max(1, panelWidth - PANEL_PADDING * 2);
        int availableHeight = Math.max(1, controlsTop - panelTop - 32);
        if (showInstax) {
            int scale = Math.max(1, Math.min(availableWidth, availableHeight) / 128);
            int drawSize = Math.min(Math.min(availableWidth, availableHeight), 128 * scale);
            int x = (width - drawSize) / 2;
            int y = panelTop + 24 + (availableHeight - drawSize) / 2;
            context.fill(x - 2, y - 2, x + drawSize + 2, y + drawSize + 2, 0xff4c473c);
            if (instaxTextureRegistered) {
                context.drawTexture(RenderPipelines.GUI_TEXTURED, INSTAX_PREVIEW_ID, x, y, 0, 0,
                        drawSize, drawSize, 128, 128, 128, 128);
            } else {
                context.fill(x, y, x + drawSize, y + drawSize, 0xff18181c);
                context.drawCenteredTextWithShadow(textRenderer,
                        Text.literal(instaxGenerating ? "Preparando Instax…" : "Vista no disponible"),
                        width / 2, y + drawSize / 2 - 4, 0xdddddd);
            }
        } else {
            double scale = Math.min(availableWidth / (double) photo.width(),
                    availableHeight / (double) photo.height());
            int drawWidth = Math.max(1, (int) Math.round(photo.width() * scale));
            int drawHeight = Math.max(1, (int) Math.round(photo.height() * scale));
            int x = (width - drawWidth) / 2;
            int y = panelTop + 24 + (availableHeight - drawHeight) / 2;
            context.drawTexture(RenderPipelines.GUI_TEXTURED, PREVIEW_ID, x, y, 0, 0,
                    drawWidth, drawHeight, photo.width(), photo.height(), photo.width(), photo.height());
        }
        context.fill(panelLeft, controlsTop - 4, panelLeft + panelWidth, panelTop + panelHeight, 0xf01b1b22);
        context.drawTextWithShadow(textRenderer, Text.literal("Pie de foto"),
                panelLeft + PANEL_PADDING, controlsTop + 5, 0xdddddd);
        String visibleStatus = textRenderer.trimToWidth(status.getString(), panelWidth - PANEL_PADDING * 2);
        context.drawTextWithShadow(textRenderer, Text.literal(visibleStatus),
                panelLeft + PANEL_PADDING, panelTop + panelHeight - 12, 0xffffff);
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public boolean shouldPause() {
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
            client.getTextureManager().destroyTexture(PREVIEW_ID);
            textureRegistered = false;
        } else {
            image.close();
        }
    }

    private void destroyInstaxTexture() {
        if (instaxTextureRegistered) {
            client.getTextureManager().destroyTexture(INSTAX_PREVIEW_ID);
            instaxTextureRegistered = false;
            instaxImage = null;
        } else if (instaxImage != null) {
            instaxImage.close();
            instaxImage = null;
        }
    }
}

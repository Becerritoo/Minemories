package dev.becerritoo.minemories.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class InstaxSettingsScreen extends Screen {
    private static final Identifier PREVIEW_ID = Identifier.fromNamespaceAndPath("minemories", "instax_settings_preview");
    private final Screen parent;
    private final PhotoData photo;
    private Button presetButton;
    private Button filterButton;
    private Button ditheringButton;
    private NativeImage previewImage;
    private boolean textureRegistered;
    private volatile long previewGeneration;
    private Component previewStatus = Component.literal("Preparando vista Instax…");
    private int contentLeft;
    private int contentWidth;
    private int previewArea;
    private int controlsLeft;
    private int controlsWidth;

    public InstaxSettingsScreen(Screen parent, PhotoData photo) {
        super(Component.literal("Edición Instax"));
        this.parent = parent;
        this.photo = photo;
    }

    @Override
    protected void init() {
        ClientConfig config = MinemoriesClient.config();
        contentWidth = Math.min(660, Math.max(300, width - 24));
        contentLeft = (width - contentWidth) / 2;
        previewArea = Math.max(132, Math.min(276, contentWidth / 2 - 10));
        controlsLeft = contentLeft + previewArea + 18;
        controlsWidth = Math.max(150, contentLeft + contentWidth - controlsLeft);
        int top = 42;

        presetButton = addRenderableWidget(Button.builder(presetText(config), button -> {
            applyPreset(nextPreset(config.instaxPreset()));
            rebuildWidgets();
        }).bounds(controlsLeft, top, controlsWidth, 20).build());

        filterButton = addRenderableWidget(Button.builder(filterText(config), button -> {
            config.instaxFilter = config.instaxFilter.next();
            changed();
        }).bounds(controlsLeft, top + 26, controlsWidth, 20).build());

        addRenderableWidget(new ValueSlider(controlsLeft, top + 52, controlsWidth,
                "Enfoque", 0.0, 1.0, config.instaxSharpness,
                value -> Math.round(value * 100.0) + "%", value -> {
                    config.instaxSharpness = value;
                    changed();
                }));
        addRenderableWidget(new ValueSlider(controlsLeft, top + 78, controlsWidth,
                "Contraste", 0.80, 1.20, config.instaxContrast,
                InstaxSettingsScreen::decimal, value -> {
                    config.instaxContrast = value;
                    changed();
                }));
        addRenderableWidget(new ValueSlider(controlsLeft, top + 104, controlsWidth,
                "Saturación", 0.80, 1.20, config.instaxSaturation,
                InstaxSettingsScreen::decimal, value -> {
                    config.instaxSaturation = value;
                    changed();
                }));

        ditheringButton = addRenderableWidget(Button.builder(ditheringText(config), button -> {
            config.instaxDithering = !config.instaxDithering;
            changed();
        }).bounds(controlsLeft, top + 130, controlsWidth, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Restaurar Nítido"), button -> {
            applyPreset(InstaxSettings.Preset.SHARP);
            rebuildWidgets();
        }).bounds(controlsLeft, top + 162, Math.max(70, controlsWidth / 2 - 2), 20).build());
        addRenderableWidget(Button.builder(Component.literal("Listo"), button -> onClose())
                .bounds(controlsLeft + controlsWidth / 2 + 2, top + 162,
                        Math.max(70, controlsWidth - controlsWidth / 2 - 2), 20).build());
        requestPreview();
    }

    private void applyPreset(InstaxSettings.Preset preset) {
        ClientConfig config = MinemoriesClient.config();
        switch (preset) {
            case SOFT -> config.applyInstax(InstaxSettings.SOFT);
            case BALANCED -> config.applyInstax(InstaxSettings.BALANCED);
            case SHARP, CUSTOM -> config.applyInstax(InstaxSettings.SHARP);
        }
        requestPreview();
    }

    private static InstaxSettings.Preset nextPreset(InstaxSettings.Preset current) {
        return switch (current) {
            case SOFT -> InstaxSettings.Preset.BALANCED;
            case BALANCED -> InstaxSettings.Preset.SHARP;
            case SHARP, CUSTOM -> InstaxSettings.Preset.SOFT;
        };
    }

    private void changed() {
        ClientConfig config = MinemoriesClient.config();
        if (presetButton != null) {
            presetButton.setMessage(presetText(config));
        }
        if (filterButton != null) {
            filterButton.setMessage(filterText(config));
        }
        if (ditheringButton != null) {
            ditheringButton.setMessage(ditheringText(config));
        }
        requestPreview();
    }

    private void requestPreview() {
        long generation = ++previewGeneration;
        InstaxSettings settings = MinemoriesClient.config().instaxSettings();
        previewStatus = Component.literal("§eActualizando vista Instax…");
        CompletableFuture.delayedExecutor(120, TimeUnit.MILLISECONDS).execute(() -> {
            if (generation != previewGeneration) {
                return;
            }
            try {
                byte[] pixels = InstaxPreviewGenerator.generate(photo, settings);
                minecraft.execute(() -> installPreview(generation, pixels));
            } catch (RuntimeException exception) {
                minecraft.execute(() -> {
                    if (generation == previewGeneration) {
                        previewStatus = Component.literal("§cNo se pudo generar la vista Instax.");
                    }
                });
            }
        });
    }

    private void installPreview(long generation, byte[] pixels) {
        if (generation != previewGeneration || minecraft.screen != this) {
            return;
        }
        destroyTexture();
        previewImage = InstaxPreviewGenerator.nativeImage(pixels);
        minecraft.getTextureManager().register(PREVIEW_ID,
                new DynamicTexture(() -> "Minemories Instax settings", previewImage));
        textureRegistered = true;
        previewStatus = Component.literal("§aVista exacta del mapa 128×128");
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(0, 0, width, height, 0xd0101014);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
        renderBackground(context, mouseX, mouseY, deltaTicks);
        context.drawCenteredString(font, title, width / 2, 14, 0xffffff);
        context.drawCenteredString(font, Component.literal("Resultado del mapa"),
                contentLeft + previewArea / 2, 32, 0xdddddd);
        int available = Math.min(previewArea, Math.max(128, height - 88));
        int scale = Math.max(1, available / 128);
        int drawSize = 128 * scale;
        int drawX = contentLeft + (previewArea - drawSize) / 2;
        int drawY = 46;
        context.fill(drawX - 2, drawY - 2, drawX + drawSize + 2, drawY + drawSize + 2, 0xff4c473c);
        if (textureRegistered) {
            context.blit(RenderPipelines.GUI_TEXTURED, PREVIEW_ID, drawX, drawY, 0, 0,
                    drawSize, drawSize, 128, 128, 128, 128);
        } else {
            context.fill(drawX, drawY, drawX + drawSize, drawY + drawSize, 0xff18181c);
        }
        context.drawCenteredString(font, previewStatus,
                contentLeft + previewArea / 2, Math.min(height - 36, drawY + drawSize + 8), 0xffffff);
        context.drawCenteredString(font,
                Component.literal("Los ajustes sólo afectan la Instax; Discord no cambia."),
                width / 2, height - 14, 0xaaaaaa);
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public void onClose() {
        MinemoriesClient.config().save();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        previewGeneration++;
        destroyTexture();
    }

    private void destroyTexture() {
        if (textureRegistered) {
            minecraft.getTextureManager().release(PREVIEW_ID);
            textureRegistered = false;
            previewImage = null;
        } else if (previewImage != null) {
            previewImage.close();
            previewImage = null;
        }
    }

    private static Component presetText(ClientConfig config) {
        return Component.literal("Preset: " + config.instaxPreset().label());
    }

    private static Component filterText(ClientConfig config) {
        return Component.literal("Filtro: " + config.instaxFilter.label());
    }

    private static Component ditheringText(ClientConfig config) {
        return Component.literal("Tramado: " + (config.instaxDithering ? "Activado" : "Desactivado"));
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static final class ValueSlider extends AbstractSliderButton {
        private final String label;
        private final double minimum;
        private final double maximum;
        private final java.util.function.DoubleFunction<String> formatter;
        private final DoubleConsumer consumer;

        private ValueSlider(int x, int y, int width, String label,
                            double minimum, double maximum, double initial,
                            java.util.function.DoubleFunction<String> formatter,
                            DoubleConsumer consumer) {
            super(x, y, width, 20, Component.empty(), (initial - minimum) / (maximum - minimum));
            this.label = label;
            this.minimum = minimum;
            this.maximum = maximum;
            this.formatter = formatter;
            this.consumer = consumer;
            updateMessage();
        }

        private double actualValue() {
            return minimum + value * (maximum - minimum);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label + ": " + formatter.apply(actualValue())));
        }

        @Override
        protected void applyValue() {
            consumer.accept(actualValue());
        }
    }
}

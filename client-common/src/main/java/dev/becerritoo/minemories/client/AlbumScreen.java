package dev.becerritoo.minemories.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.becerritoo.minemories.common.AlbumArchive;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class AlbumScreen extends Screen {
    private static final Identifier PHOTO_ID = Identifier.fromNamespaceAndPath("minemories", "album_photo");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final int MAX_WIDTH = 430;
    private static final int MAX_HEIGHT = 320;
    private final List<Entry> photos;
    private int index;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private NativeImage image;
    private boolean textureRegistered;
    private Component error;
    private Button previous;
    private Button next;

    public AlbumScreen(AlbumArchive.Album album) {
        super(Component.literal("Álbum Minemories"));
        List<Entry> entries = new ArrayList<>();
        for (int rollIndex = 0; rollIndex < album.rolls().size(); rollIndex++) {
            AlbumArchive.Roll roll = album.rolls().get(rollIndex);
            for (AlbumArchive.Photo photo : roll.photos()) {
                entries.add(new Entry(rollIndex + 1, roll, photo));
            }
        }
        this.photos = List.copyOf(entries);
    }

    @Override
    protected void init() {
        panelWidth = Math.min(MAX_WIDTH, Math.max(1, width - 16));
        panelHeight = Math.min(MAX_HEIGHT, Math.max(1, height - 16));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        int y = panelTop + panelHeight - 26;
        previous = addRenderableWidget(Button.builder(Component.literal("‹ Anterior"), button -> move(-1))
                .bounds(panelLeft + 12, y, 90, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal("Siguiente ›"), button -> move(1))
                .bounds(panelLeft + panelWidth - 102, y, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cerrar"), button -> onClose())
                .bounds(width / 2 - 38, y, 76, 20).build());
        updateButtons();
        loadCurrent();
    }

    private void move(int offset) {
        int target = index + offset;
        if (target < 0 || target >= photos.size()) {
            return;
        }
        index = target;
        loadCurrent();
        updateButtons();
    }

    private void updateButtons() {
        if (previous != null) {
            previous.active = index > 0;
        }
        if (next != null) {
            next.active = index + 1 < photos.size();
        }
    }

    private void loadCurrent() {
        destroyTexture();
        error = null;
        if (photos.isEmpty()) {
            error = Component.literal("El álbum no contiene fotografías.");
            return;
        }
        try {
            image = NativeImage.read(photos.get(index).photo().jpeg());
            minecraft.getTextureManager().register(PHOTO_ID,
                    new DynamicTexture(() -> "Minemories album", image));
            textureRegistered = true;
        } catch (IOException exception) {
            error = Component.literal("No se pudo mostrar esta fotografía.");
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(panelLeft - 3, panelTop - 3,
                panelLeft + panelWidth + 3, panelTop + panelHeight + 3, 0x66000000);
        context.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xee101014);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        context.centeredText(font, title, width / 2, panelTop + 8, 0xffffff);
        if (textureRegistered && image != null) {
            int availableWidth = panelWidth - 24;
            int availableHeight = panelHeight - 92;
            double scale = Math.min(availableWidth / (double) image.getWidth(),
                    availableHeight / (double) image.getHeight());
            int drawWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
            int drawHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
            int x = (width - drawWidth) / 2;
            int y = panelTop + 24 + (availableHeight - drawHeight) / 2;
            context.blit(RenderPipelines.GUI_TEXTURED, PHOTO_ID, x, y, 0, 0,
                    drawWidth, drawHeight, image.getWidth(), image.getHeight(), image.getWidth(), image.getHeight());
        } else if (error != null) {
            context.centeredText(font, error, width / 2, panelTop + 80, 0xff7777);
        }
        if (!photos.isEmpty()) {
            Entry entry = photos.get(index);
            AlbumArchive.Photo photo = entry.photo();
            String caption = photo.caption().isBlank() ? "Sin pie de foto" : photo.caption();
            context.centeredText(font,
                    Component.literal(font.plainSubstrByWidth(caption, panelWidth - 24)),
                    width / 2, panelTop + panelHeight - 58, 0xffffff);
            String detail = "Rollo " + entry.rollNumber() + " · foto " + photo.slot() + "/"
                    + entry.roll().capacity() + " · " + photo.playerName() + " · "
                    + DATE.format(Instant.ofEpochMilli(photo.createdAtEpochMillis()));
            context.centeredText(font,
                    Component.literal(font.plainSubstrByWidth(detail, panelWidth - 24)),
                    width / 2, panelTop + panelHeight - 44, 0xaaaaaa);
            context.centeredText(font,
                    Component.literal((index + 1) + " de " + photos.size()),
                    width / 2, panelTop + 8, 0xdddddd);
        }
        super.extractRenderState(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        destroyTexture();
    }

    private void destroyTexture() {
        if (textureRegistered) {
            minecraft.getTextureManager().release(PHOTO_ID);
            textureRegistered = false;
            image = null;
        } else if (image != null) {
            image.close();
            image = null;
        }
    }

    private record Entry(int rollNumber, AlbumArchive.Roll roll, AlbumArchive.Photo photo) {
    }
}

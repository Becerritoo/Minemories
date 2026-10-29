package dev.becerritoo.minemories.fabric;

import dev.becerritoo.minemories.common.AlbumArchive;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class AlbumScreen extends Screen {
    private static final Identifier PHOTO_ID = Identifier.of("minemories", "album_photo");
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
    private Text error;
    private ButtonWidget previous;
    private ButtonWidget next;

    public AlbumScreen(AlbumArchive.Album album) {
        super(Text.literal("Álbum Minemories"));
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
        previous = addDrawableChild(ButtonWidget.builder(Text.literal("‹ Anterior"), button -> move(-1))
                .dimensions(panelLeft + 12, y, 90, 20).build());
        next = addDrawableChild(ButtonWidget.builder(Text.literal("Siguiente ›"), button -> move(1))
                .dimensions(panelLeft + panelWidth - 102, y, 90, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cerrar"), button -> close())
                .dimensions(width / 2 - 38, y, 76, 20).build());
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
            error = Text.literal("El álbum no contiene fotografías.");
            return;
        }
        try {
            image = NativeImage.read(photos.get(index).photo().jpeg());
            client.getTextureManager().registerTexture(PHOTO_ID,
                    new NativeImageBackedTexture(() -> "Minemories album", image));
            textureRegistered = true;
        } catch (IOException exception) {
            error = Text.literal("No se pudo mostrar esta fotografía.");
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(panelLeft - 3, panelTop - 3,
                panelLeft + panelWidth + 3, panelTop + panelHeight + 3, 0x66000000);
        context.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xee101014);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        renderBackground(context, mouseX, mouseY, deltaTicks);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, panelTop + 8, 0xffffff);
        if (textureRegistered && image != null) {
            int availableWidth = panelWidth - 24;
            int availableHeight = panelHeight - 92;
            double scale = Math.min(availableWidth / (double) image.getWidth(),
                    availableHeight / (double) image.getHeight());
            int drawWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
            int drawHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
            int x = (width - drawWidth) / 2;
            int y = panelTop + 24 + (availableHeight - drawHeight) / 2;
            context.drawTexture(RenderPipelines.GUI_TEXTURED, PHOTO_ID, x, y, 0, 0,
                    drawWidth, drawHeight, image.getWidth(), image.getHeight(), image.getWidth(), image.getHeight());
        } else if (error != null) {
            context.drawCenteredTextWithShadow(textRenderer, error, width / 2, panelTop + 80, 0xff7777);
        }
        if (!photos.isEmpty()) {
            Entry entry = photos.get(index);
            AlbumArchive.Photo photo = entry.photo();
            String caption = photo.caption().isBlank() ? "Sin pie de foto" : photo.caption();
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(textRenderer.trimToWidth(caption, panelWidth - 24)),
                    width / 2, panelTop + panelHeight - 58, 0xffffff);
            String detail = "Rollo " + entry.rollNumber() + " · foto " + photo.slot() + "/"
                    + entry.roll().capacity() + " · " + photo.playerName() + " · "
                    + DATE.format(Instant.ofEpochMilli(photo.createdAtEpochMillis()));
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(textRenderer.trimToWidth(detail, panelWidth - 24)),
                    width / 2, panelTop + panelHeight - 44, 0xaaaaaa);
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal((index + 1) + " de " + photos.size()),
                    width / 2, panelTop + 8, 0xdddddd);
        }
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        destroyTexture();
    }

    private void destroyTexture() {
        if (textureRegistered) {
            client.getTextureManager().destroyTexture(PHOTO_ID);
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

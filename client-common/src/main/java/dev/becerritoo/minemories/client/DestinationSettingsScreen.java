package dev.becerritoo.minemories.client;

import dev.becerritoo.minemories.client.net.NetworkBridge;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class DestinationSettingsScreen extends Screen {
    private static final int MAX_WEBHOOKS = 10;
    private static final int[] TARGET_SIZES_KIB = {100, 150, 200, 250, 300, 400, 500};
    private static final int[] COOLDOWNS = {0, 3, 5, 10, 15, 30};

    private final Screen parent;
    private final List<VisibleWebhook> visibleWebhooks = new ArrayList<>();
    private Button modeButton;
    private Button targetButton;
    private Button cooldownButton;
    private int page;
    private int pageSize;
    private int contentLeft;
    private int contentWidth;

    public DestinationSettingsScreen(Screen parent) {
        super(Component.literal("Destinos de Discord"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ClientConfig config = MinemoriesClient.config();
        contentWidth = Math.min(500, Math.max(260, width - 24));
        contentLeft = (width - contentWidth) / 2;

        modeButton = addRenderableWidget(Button.builder(modeText(config), button -> {
            config.destinationMode = config.destinationMode.next();
            config.save();
            button.setMessage(modeText(config));
        }).bounds(contentLeft + 150, 48, contentWidth - 150, 20).build());

        targetButton = addRenderableWidget(Button.builder(targetText(config), button -> {
            config.personalTargetFileBytes = nextValue(
                    config.personalTargetFileBytes / 1024, TARGET_SIZES_KIB) * 1024;
            config.save();
            button.setMessage(targetText(config));
        }).bounds(contentLeft + 150, 74, contentWidth - 150, 20).build());

        cooldownButton = addRenderableWidget(Button.builder(cooldownText(config), button -> {
            config.personalCooldownSeconds = nextValue(config.personalCooldownSeconds, COOLDOWNS);
            config.save();
            button.setMessage(cooldownText(config));
        }).bounds(contentLeft + 150, 100, contentWidth - 150, 20).build());

        Button instax = addRenderableWidget(Button.builder(Component.literal("Configurar y previsualizar"),
                        button -> {
                            if (parent instanceof PreviewScreen preview) {
                                minecraft.setScreen(new InstaxSettingsScreen(this, preview.photo()));
                            }
                        })
                .bounds(contentLeft + 150, 126, contentWidth - 150, 20).build());
        instax.active = parent instanceof PreviewScreen;

        int listTop = 174;
        int footerTop = height - 28;
        pageSize = Math.max(1, Math.min(5, (footerTop - listTop) / 24));
        int pageCount = pageCount(config.personalWebhooks.size());
        page = Math.max(0, Math.min(page, pageCount - 1));
        visibleWebhooks.clear();
        int first = page * pageSize;
        int last = Math.min(config.personalWebhooks.size(), first + pageSize);
        for (int index = first; index < last; index++) {
            ClientConfig.PersonalWebhook webhook = config.personalWebhooks.get(index);
            int rowY = listTop + (index - first) * 24;
            visibleWebhooks.add(new VisibleWebhook(webhook, rowY));
            addRenderableWidget(Button.builder(enabledText(webhook), button -> {
                webhook.enabled = !webhook.enabled;
                config.save();
                button.setMessage(enabledText(webhook));
            }).bounds(contentLeft + contentWidth - 132, rowY, 72, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Editar"), button ->
                    minecraft.setScreen(new WebhookEditorScreen(this, webhook)))
                    .bounds(contentLeft + contentWidth - 56, rowY, 56, 20).build());
        }

        Button previous = addRenderableWidget(Button.builder(Component.literal("‹"), button -> {
            page--;
            rebuildWidgets();
        }).bounds(contentLeft, footerTop, 24, 20).build());
        previous.active = page > 0;

        Button next = addRenderableWidget(Button.builder(Component.literal("›"), button -> {
            page++;
            rebuildWidgets();
        }).bounds(contentLeft + 28, footerTop, 24, 20).build());
        next.active = page + 1 < pageCount;

        Button add = addRenderableWidget(Button.builder(Component.literal("Añadir webhook"), button ->
                minecraft.setScreen(new WebhookEditorScreen(this, null)))
                .bounds(contentLeft + 60, footerTop, 130, 20).build());
        add.active = config.personalWebhooks.size() < MAX_WEBHOOKS;

        addRenderableWidget(Button.builder(Component.literal("Listo"), button -> onClose())
                .bounds(contentLeft + contentWidth - 80, footerTop, 80, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(0, 0, width, height, 0xd0101014);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
        renderBackground(context, mouseX, mouseY, deltaTicks);
        context.drawCenteredString(font, title, width / 2, 14, 0xffffff);
        String server = NetworkBridge.serverAvailable()
                ? "§aServidor Minemories detectado"
                : "§7Este servidor no ofrece Minemories";
        context.drawCenteredString(font, Component.literal(server), width / 2, 30, 0xffffff);
        context.drawString(font, Component.literal("Modo de envío"), contentLeft, 54, 0xdddddd);
        context.drawString(font, Component.literal("Objetivo personal"), contentLeft, 80, 0xdddddd);
        context.drawString(font, Component.literal("Espera personal"), contentLeft, 106, 0xdddddd);
        context.drawString(font, Component.literal("Edición Instax"), contentLeft, 132, 0xdddddd);
        context.drawString(font, Component.literal("Webhooks personales"), contentLeft, 160, 0xffffff);

        for (VisibleWebhook visible : visibleWebhooks) {
            String name = font.plainSubstrByWidth(visible.webhook.name, Math.max(20, contentWidth - 142));
            context.drawString(font, Component.literal(name), contentLeft + 4,
                    visible.y + 6, visible.webhook.enabled ? 0xffffff : 0x888888);
        }
        if (MinemoriesClient.config().personalWebhooks.isEmpty()) {
            context.drawString(font, Component.literal("§7No has agregado destinos personales."),
                    contentLeft + 4, 180, 0xaaaaaa);
        }
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

    void refresh() {
        rebuildWidgets();
    }

    private int pageCount(int count) {
        return Math.max(1, (count + pageSize - 1) / pageSize);
    }

    private static int nextValue(int current, int[] values) {
        for (int value : values) {
            if (value > current) {
                return value;
            }
        }
        return values[0];
    }

    private static Component modeText(ClientConfig config) {
        return Component.literal(config.destinationMode.label());
    }

    private static Component targetText(ClientConfig config) {
        return Component.literal((config.personalTargetFileBytes / 1024) + " KiB");
    }

    private static Component cooldownText(ClientConfig config) {
        return Component.literal(config.personalCooldownSeconds == 0
                ? "Sin espera" : config.personalCooldownSeconds + " s");
    }

    private static Component enabledText(ClientConfig.PersonalWebhook webhook) {
        return Component.literal(webhook.enabled ? "Activo" : "Pausado");
    }

    private record VisibleWebhook(ClientConfig.PersonalWebhook webhook, int y) {
    }
}

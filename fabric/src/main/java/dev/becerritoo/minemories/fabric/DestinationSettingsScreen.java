package dev.becerritoo.minemories.fabric;

import dev.becerritoo.minemories.fabric.net.NetworkBridge;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class DestinationSettingsScreen extends Screen {
    private static final int MAX_WEBHOOKS = 10;
    private static final int[] TARGET_SIZES_KIB = {100, 150, 200, 250, 300, 400, 500};
    private static final int[] COOLDOWNS = {0, 3, 5, 10, 15, 30};

    private final Screen parent;
    private final List<VisibleWebhook> visibleWebhooks = new ArrayList<>();
    private ButtonWidget modeButton;
    private ButtonWidget targetButton;
    private ButtonWidget cooldownButton;
    private int page;
    private int pageSize;
    private int contentLeft;
    private int contentWidth;

    public DestinationSettingsScreen(Screen parent) {
        super(Text.literal("Destinos de Discord"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ClientConfig config = MinemoriesClient.config();
        contentWidth = Math.min(500, Math.max(260, width - 24));
        contentLeft = (width - contentWidth) / 2;

        modeButton = addDrawableChild(ButtonWidget.builder(modeText(config), button -> {
            config.destinationMode = config.destinationMode.next();
            config.save();
            button.setMessage(modeText(config));
        }).dimensions(contentLeft + 150, 48, contentWidth - 150, 20).build());

        targetButton = addDrawableChild(ButtonWidget.builder(targetText(config), button -> {
            config.personalTargetFileBytes = nextValue(
                    config.personalTargetFileBytes / 1024, TARGET_SIZES_KIB) * 1024;
            config.save();
            button.setMessage(targetText(config));
        }).dimensions(contentLeft + 150, 74, contentWidth - 150, 20).build());

        cooldownButton = addDrawableChild(ButtonWidget.builder(cooldownText(config), button -> {
            config.personalCooldownSeconds = nextValue(config.personalCooldownSeconds, COOLDOWNS);
            config.save();
            button.setMessage(cooldownText(config));
        }).dimensions(contentLeft + 150, 100, contentWidth - 150, 20).build());

        ButtonWidget instax = addDrawableChild(ButtonWidget.builder(Text.literal("Configurar y previsualizar"),
                        button -> {
                            if (parent instanceof PreviewScreen preview) {
                                client.setScreen(new InstaxSettingsScreen(this, preview.photo()));
                            }
                        })
                .dimensions(contentLeft + 150, 126, contentWidth - 150, 20).build());
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
            addDrawableChild(ButtonWidget.builder(enabledText(webhook), button -> {
                webhook.enabled = !webhook.enabled;
                config.save();
                button.setMessage(enabledText(webhook));
            }).dimensions(contentLeft + contentWidth - 132, rowY, 72, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Editar"), button ->
                    client.setScreen(new WebhookEditorScreen(this, webhook)))
                    .dimensions(contentLeft + contentWidth - 56, rowY, 56, 20).build());
        }

        ButtonWidget previous = addDrawableChild(ButtonWidget.builder(Text.literal("‹"), button -> {
            page--;
            clearAndInit();
        }).dimensions(contentLeft, footerTop, 24, 20).build());
        previous.active = page > 0;

        ButtonWidget next = addDrawableChild(ButtonWidget.builder(Text.literal("›"), button -> {
            page++;
            clearAndInit();
        }).dimensions(contentLeft + 28, footerTop, 24, 20).build());
        next.active = page + 1 < pageCount;

        ButtonWidget add = addDrawableChild(ButtonWidget.builder(Text.literal("Añadir webhook"), button ->
                client.setScreen(new WebhookEditorScreen(this, null)))
                .dimensions(contentLeft + 60, footerTop, 130, 20).build());
        add.active = config.personalWebhooks.size() < MAX_WEBHOOKS;

        addDrawableChild(ButtonWidget.builder(Text.literal("Listo"), button -> close())
                .dimensions(contentLeft + contentWidth - 80, footerTop, 80, 20).build());
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(0, 0, width, height, 0xd0101014);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        renderBackground(context, mouseX, mouseY, deltaTicks);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xffffff);
        String server = NetworkBridge.serverAvailable()
                ? "§aServidor Minemories detectado"
                : "§7Este servidor no ofrece Minemories";
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(server), width / 2, 30, 0xffffff);
        context.drawTextWithShadow(textRenderer, Text.literal("Modo de envío"), contentLeft, 54, 0xdddddd);
        context.drawTextWithShadow(textRenderer, Text.literal("Objetivo personal"), contentLeft, 80, 0xdddddd);
        context.drawTextWithShadow(textRenderer, Text.literal("Espera personal"), contentLeft, 106, 0xdddddd);
        context.drawTextWithShadow(textRenderer, Text.literal("Edición Instax"), contentLeft, 132, 0xdddddd);
        context.drawTextWithShadow(textRenderer, Text.literal("Webhooks personales"), contentLeft, 160, 0xffffff);

        for (VisibleWebhook visible : visibleWebhooks) {
            String name = textRenderer.trimToWidth(visible.webhook.name, Math.max(20, contentWidth - 142));
            context.drawTextWithShadow(textRenderer, Text.literal(name), contentLeft + 4,
                    visible.y + 6, visible.webhook.enabled ? 0xffffff : 0x888888);
        }
        if (MinemoriesClient.config().personalWebhooks.isEmpty()) {
            context.drawTextWithShadow(textRenderer, Text.literal("§7No has agregado destinos personales."),
                    contentLeft + 4, 180, 0xaaaaaa);
        }
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public void close() {
        MinemoriesClient.config().save();
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    void refresh() {
        clearAndInit();
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

    private static Text modeText(ClientConfig config) {
        return Text.literal(config.destinationMode.label());
    }

    private static Text targetText(ClientConfig config) {
        return Text.literal((config.personalTargetFileBytes / 1024) + " KiB");
    }

    private static Text cooldownText(ClientConfig config) {
        return Text.literal(config.personalCooldownSeconds == 0
                ? "Sin espera" : config.personalCooldownSeconds + " s");
    }

    private static Text enabledText(ClientConfig.PersonalWebhook webhook) {
        return Text.literal(webhook.enabled ? "Activo" : "Pausado");
    }

    private record VisibleWebhook(ClientConfig.PersonalWebhook webhook, int y) {
    }
}

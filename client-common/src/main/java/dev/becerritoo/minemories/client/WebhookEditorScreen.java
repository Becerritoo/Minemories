package dev.becerritoo.minemories.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class WebhookEditorScreen extends Screen {
    private final DestinationSettingsScreen parent;
    private final ClientConfig.PersonalWebhook existing;
    private EditBox nameField;
    private EditBox urlField;
    private Checkbox enabled;
    private Button deleteButton;
    private Component status = Component.literal("La URL se guarda únicamente en tu configuración local.");
    private boolean confirmDelete;
    private int contentLeft;
    private int contentWidth;

    public WebhookEditorScreen(DestinationSettingsScreen parent, ClientConfig.PersonalWebhook existing) {
        super(Component.literal(existing == null ? "Añadir webhook" : "Editar webhook"));
        this.parent = parent;
        this.existing = existing;
    }

    @Override
    protected void init() {
        contentWidth = Math.min(500, Math.max(260, width - 24));
        contentLeft = (width - contentWidth) / 2;
        String oldName = nameField == null ? (existing == null ? "Mi Discord" : existing.name) : nameField.getValue();
        String oldUrl = urlField == null ? (existing == null ? "" : existing.webhookUrl) : urlField.getValue();
        boolean oldEnabled = enabled == null ? existing == null || existing.enabled : enabled.selected();

        nameField = new EditBox(font, contentLeft, 54, contentWidth, 20,
                Component.literal("Nombre"));
        nameField.setMaxLength(48);
        nameField.setValue(oldName);
        addRenderableWidget(nameField);

        urlField = new EditBox(font, contentLeft, 94, contentWidth, 20,
                Component.literal("Webhook de Discord"));
        urlField.setMaxLength(512);
        urlField.setValue(oldUrl);
        urlField.setHint(Component.literal("https://discord.com/api/webhooks/…"));
        addRenderableWidget(urlField);

        enabled = addRenderableWidget(Checkbox.builder(Component.literal("Destino activo"), font)
                .pos(contentLeft, 124).selected(oldEnabled).build());

        int buttonY = Math.min(170, height - 28);
        int gap = 4;
        int normalWidth = existing == null ? (contentWidth - gap * 2) / 3 : (contentWidth - gap * 3) / 4;
        addRenderableWidget(Button.builder(Component.literal("Guardar"), button -> save())
                .bounds(contentLeft, buttonY, normalWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Probar"), button -> test())
                .bounds(contentLeft + normalWidth + gap, buttonY, normalWidth, 20).build());

        int cancelX;
        if (existing != null) {
            deleteButton = addRenderableWidget(Button.builder(deleteText(), button -> delete())
                    .bounds(contentLeft + (normalWidth + gap) * 2, buttonY, normalWidth, 20).build());
            cancelX = contentLeft + (normalWidth + gap) * 3;
        } else {
            cancelX = contentLeft + (normalWidth + gap) * 2;
        }
        int cancelWidth = contentLeft + contentWidth - cancelX;
        addRenderableWidget(Button.builder(Component.literal("Cancelar"), button -> onClose())
                .bounds(cancelX, buttonY, cancelWidth, 20).build());
        setInitialFocus(nameField);
    }

    private void save() {
        String name = nameField.getValue().strip();
        String url = urlField.getValue().strip();
        if (name.isBlank()) {
            status = Component.literal("§cEscribe un nombre para este destino.");
            return;
        }
        try {
            PersonalWebhookClient.parseWebhook(url);
        } catch (IllegalArgumentException exception) {
            status = Component.literal("§c" + exception.getMessage());
            return;
        }

        ClientConfig config = MinemoriesClient.config();
        if (existing == null) {
            if (config.personalWebhooks.size() >= 10) {
                status = Component.literal("§cPuedes guardar un máximo de 10 webhooks.");
                return;
            }
            config.personalWebhooks.add(new ClientConfig.PersonalWebhook(
                    null, name, url, enabled.selected()));
        } else {
            existing.name = name;
            existing.webhookUrl = url;
            existing.enabled = enabled.selected();
        }
        config.save();
        minecraft.gui.setScreen(parent);
    }

    private void test() {
        try {
            PersonalWebhookClient.parseWebhook(urlField.getValue());
        } catch (IllegalArgumentException exception) {
            status = Component.literal("§c" + exception.getMessage());
            return;
        }
        status = Component.literal("§eComprobando con Discord…");
        PersonalWebhookClient.test(urlField.getValue()).whenComplete((result, error) -> minecraft.execute(() -> {
            if (error != null) {
                status = Component.literal("§cNo fue posible conectar con Discord.");
            } else {
                status = Component.literal((result.success() ? "§a" : "§c") + result.reason());
            }
        }));
    }

    private void delete() {
        if (!confirmDelete) {
            confirmDelete = true;
            deleteButton.setMessage(deleteText());
            status = Component.literal("§ePulsa Confirmar para eliminar este destino.");
            return;
        }
        MinemoriesClient.config().personalWebhooks.remove(existing);
        MinemoriesClient.config().save();
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(0, 0, width, height, 0xd0101014);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        context.centeredText(font, title, width / 2, 16, 0xffffff);
        context.text(font, Component.literal("Nombre"), contentLeft, 42, 0xdddddd);
        context.text(font, Component.literal("URL del webhook"), contentLeft, 82, 0xdddddd);
        String visibleStatus = font.plainSubstrByWidth(status.getString(), contentWidth);
        context.text(font, Component.literal(visibleStatus), contentLeft, 148, 0xffffff);
        super.extractRenderState(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Component deleteText() {
        return Component.literal(confirmDelete ? "Confirmar" : "Eliminar");
    }
}

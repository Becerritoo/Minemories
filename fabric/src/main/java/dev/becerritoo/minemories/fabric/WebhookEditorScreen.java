package dev.becerritoo.minemories.fabric;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class WebhookEditorScreen extends Screen {
    private final DestinationSettingsScreen parent;
    private final ClientConfig.PersonalWebhook existing;
    private TextFieldWidget nameField;
    private TextFieldWidget urlField;
    private CheckboxWidget enabled;
    private ButtonWidget deleteButton;
    private Text status = Text.literal("La URL se guarda únicamente en tu configuración local.");
    private boolean confirmDelete;
    private int contentLeft;
    private int contentWidth;

    public WebhookEditorScreen(DestinationSettingsScreen parent, ClientConfig.PersonalWebhook existing) {
        super(Text.literal(existing == null ? "Añadir webhook" : "Editar webhook"));
        this.parent = parent;
        this.existing = existing;
    }

    @Override
    protected void init() {
        contentWidth = Math.min(500, Math.max(260, width - 24));
        contentLeft = (width - contentWidth) / 2;
        String oldName = nameField == null ? (existing == null ? "Mi Discord" : existing.name) : nameField.getText();
        String oldUrl = urlField == null ? (existing == null ? "" : existing.webhookUrl) : urlField.getText();
        boolean oldEnabled = enabled == null ? existing == null || existing.enabled : enabled.isChecked();

        nameField = new TextFieldWidget(textRenderer, contentLeft, 54, contentWidth, 20,
                Text.literal("Nombre"));
        nameField.setMaxLength(48);
        nameField.setText(oldName);
        addDrawableChild(nameField);

        urlField = new TextFieldWidget(textRenderer, contentLeft, 94, contentWidth, 20,
                Text.literal("Webhook de Discord"));
        urlField.setMaxLength(512);
        urlField.setText(oldUrl);
        urlField.setPlaceholder(Text.literal("https://discord.com/api/webhooks/…"));
        addDrawableChild(urlField);

        enabled = addDrawableChild(CheckboxWidget.builder(Text.literal("Destino activo"), textRenderer)
                .pos(contentLeft, 124).checked(oldEnabled).build());

        int buttonY = Math.min(170, height - 28);
        int gap = 4;
        int normalWidth = existing == null ? (contentWidth - gap * 2) / 3 : (contentWidth - gap * 3) / 4;
        addDrawableChild(ButtonWidget.builder(Text.literal("Guardar"), button -> save())
                .dimensions(contentLeft, buttonY, normalWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Probar"), button -> test())
                .dimensions(contentLeft + normalWidth + gap, buttonY, normalWidth, 20).build());

        int cancelX;
        if (existing != null) {
            deleteButton = addDrawableChild(ButtonWidget.builder(deleteText(), button -> delete())
                    .dimensions(contentLeft + (normalWidth + gap) * 2, buttonY, normalWidth, 20).build());
            cancelX = contentLeft + (normalWidth + gap) * 3;
        } else {
            cancelX = contentLeft + (normalWidth + gap) * 2;
        }
        int cancelWidth = contentLeft + contentWidth - cancelX;
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancelar"), button -> close())
                .dimensions(cancelX, buttonY, cancelWidth, 20).build());
        setInitialFocus(nameField);
    }

    private void save() {
        String name = nameField.getText().strip();
        String url = urlField.getText().strip();
        if (name.isBlank()) {
            status = Text.literal("§cEscribe un nombre para este destino.");
            return;
        }
        try {
            PersonalWebhookClient.parseWebhook(url);
        } catch (IllegalArgumentException exception) {
            status = Text.literal("§c" + exception.getMessage());
            return;
        }

        ClientConfig config = MinemoriesClient.config();
        if (existing == null) {
            if (config.personalWebhooks.size() >= 10) {
                status = Text.literal("§cPuedes guardar un máximo de 10 webhooks.");
                return;
            }
            config.personalWebhooks.add(new ClientConfig.PersonalWebhook(
                    null, name, url, enabled.isChecked()));
        } else {
            existing.name = name;
            existing.webhookUrl = url;
            existing.enabled = enabled.isChecked();
        }
        config.save();
        client.setScreen(parent);
    }

    private void test() {
        try {
            PersonalWebhookClient.parseWebhook(urlField.getText());
        } catch (IllegalArgumentException exception) {
            status = Text.literal("§c" + exception.getMessage());
            return;
        }
        status = Text.literal("§eComprobando con Discord…");
        PersonalWebhookClient.test(urlField.getText()).whenComplete((result, error) -> client.execute(() -> {
            if (error != null) {
                status = Text.literal("§cNo fue posible conectar con Discord.");
            } else {
                status = Text.literal((result.success() ? "§a" : "§c") + result.reason());
            }
        }));
    }

    private void delete() {
        if (!confirmDelete) {
            confirmDelete = true;
            deleteButton.setMessage(deleteText());
            status = Text.literal("§ePulsa Confirmar para eliminar este destino.");
            return;
        }
        MinemoriesClient.config().personalWebhooks.remove(existing);
        MinemoriesClient.config().save();
        client.setScreen(parent);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(0, 0, width, height, 0xd0101014);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        renderBackground(context, mouseX, mouseY, deltaTicks);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, 0xffffff);
        context.drawTextWithShadow(textRenderer, Text.literal("Nombre"), contentLeft, 42, 0xdddddd);
        context.drawTextWithShadow(textRenderer, Text.literal("URL del webhook"), contentLeft, 82, 0xdddddd);
        String visibleStatus = textRenderer.trimToWidth(status.getString(), contentWidth);
        context.drawTextWithShadow(textRenderer, Text.literal(visibleStatus), contentLeft, 148, 0xffffff);
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private Text deleteText() {
        return Text.literal(confirmDelete ? "Confirmar" : "Eliminar");
    }
}

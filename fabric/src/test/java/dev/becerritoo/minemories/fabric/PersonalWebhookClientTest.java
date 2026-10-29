package dev.becerritoo.minemories.fabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PersonalWebhookClientTest {
    @Test
    void acceptsOfficialDiscordWebhookUrls() {
        assertDoesNotThrow(() -> PersonalWebhookClient.parseWebhook(
                "https://discord.com/api/webhooks/123456/token-value"));
        assertDoesNotThrow(() -> PersonalWebhookClient.parseWebhook(
                "https://canary.discord.com/api/v10/webhooks/123456/token_value"));
    }

    @Test
    void rejectsNonDiscordOrMalformedUrls() {
        assertThrows(IllegalArgumentException.class, () -> PersonalWebhookClient.parseWebhook(
                "http://discord.com/api/webhooks/123/token"));
        assertThrows(IllegalArgumentException.class, () -> PersonalWebhookClient.parseWebhook(
                "https://discord.com.example.org/api/webhooks/123/token"));
        assertThrows(IllegalArgumentException.class, () -> PersonalWebhookClient.parseWebhook(
                "https://discord.com:8443/api/webhooks/123/token"));
        assertThrows(IllegalArgumentException.class, () -> PersonalWebhookClient.parseWebhook(
                "https://discord.com/channels/123/456"));
        assertThrows(IllegalArgumentException.class, () -> PersonalWebhookClient.parseWebhook(""));
    }
}

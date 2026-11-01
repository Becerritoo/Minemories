package dev.becerritoo.minemories.paper;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsTest {
    @Test
    void maximumIsFixedAndOldConfigurationCannotRaiseIt() {
        var config = baseConfig();
        config.set("limits.max-file-bytes", "500M");

        Settings settings = Settings.load(config);

        assertEquals(500 * 1024, settings.maxFileBytes());
        assertNull(settings.validationError());
    }

    @Test
    void acceptsHumanReadableTargetSizes() {
        assertTargetSize("200K", 200 * 1024);
        assertTargetSize("0.25 MiB", 256 * 1024);
        assertTargetSize(204_800, 204_800);
    }

    @Test
    void rejectsUnsafeOrMalformedTargetSizes() {
        Settings huge = settingsWithTargetSize("1M");
        assertTrue(huge.validationError().contains("entre 64K y 500K"));

        Settings malformed = settingsWithTargetSize("mucho");
        assertTrue(malformed.validationError().contains("limits.target-file-bytes"));
    }

    @Test
    void defaultsToTwoHundredKilobytes() {
        Settings settings = Settings.load(baseConfig());
        assertEquals(200 * 1024, settings.targetFileBytes());
        assertEquals(10, settings.cooldownSeconds());
        assertEquals(900, settings.cameraCaptureSessionSeconds());
        assertEquals("", settings.discordSrvChannelId());
    }

    @Test
    void boundsTheCameraEditingSession() {
        var shortSession = baseConfig();
        shortSession.set("camera.capture-session-seconds", 1);
        assertEquals(60, Settings.load(shortSession).cameraCaptureSessionSeconds());

        var longSession = baseConfig();
        longSession.set("camera.capture-session-seconds", 20_000);
        assertEquals(7_200, Settings.load(longSession).cameraCaptureSessionSeconds());
    }

    @Test
    void acceptsAChannelIdOrTheMainChannelDefault() {
        var main = baseConfig();
        main.set("discordsrv.channel-id", "");
        assertNull(Settings.load(main).validationError());

        var explicit = baseConfig();
        explicit.set("discordsrv.channel-id", "1012375982646247424");
        assertNull(Settings.load(explicit).validationError());

        var invalid = baseConfig();
        invalid.set("discordsrv.channel-id", "general");
        assertTrue(Settings.load(invalid).validationError().contains("discordsrv.channel-id"));
    }

    private static void assertTargetSize(Object configured, int expectedBytes) {
        Settings settings = settingsWithTargetSize(configured);
        assertEquals(expectedBytes, settings.targetFileBytes());
        assertNull(settings.validationError());
    }

    private static Settings settingsWithTargetSize(Object configured) {
        var config = baseConfig();
        config.set("limits.target-file-bytes", configured);
        return Settings.load(config);
    }

    private static YamlConfiguration baseConfig() {
        var config = new YamlConfiguration();
        config.set("discord.webhook-url", "https://discord.example.com/webhook");
        return config;
    }
}

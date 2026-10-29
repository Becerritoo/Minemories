package dev.becerritoo.minemories.paper;

import org.bukkit.configuration.file.YamlConfiguration;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

final class ItemSecurity {
    private static final String ALGORITHM = "HmacSHA256";
    private final Map<String, byte[]> keys;
    private final String currentKeyId;

    private ItemSecurity(Map<String, byte[]> keys, String currentKeyId) {
        this.keys = Map.copyOf(keys);
        this.currentKeyId = currentKeyId;
    }

    static ItemSecurity load(File dataFolder, Logger logger) {
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("No se pudo crear la carpeta de Minemories");
        }
        File file = new File(dataFolder, "security.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String current = yaml.getString("current-key", "k1");
        Map<String, byte[]> loaded = new HashMap<>();
        var section = yaml.getConfigurationSection("keys");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                try {
                    byte[] decoded = Base64.getDecoder().decode(section.getString(id, ""));
                    if (decoded.length >= 32) {
                        loaded.put(id, decoded);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (!loaded.containsKey(current)) {
            byte[] generated = new byte[32];
            new SecureRandom().nextBytes(generated);
            loaded.put(current, generated);
            yaml.set("current-key", current);
            yaml.set("keys." + current, Base64.getEncoder().encodeToString(generated));
            try {
                yaml.save(file);
                try {
                    Files.setPosixFilePermissions(file.toPath(), EnumSet.of(
                            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
                } catch (UnsupportedOperationException ignored) {
                }
                logger.info("Se creó la clave de firma para cámaras y rollos.");
            } catch (IOException exception) {
                throw new IllegalStateException("No se pudo guardar security.yml", exception);
            }
        }
        return new ItemSecurity(loaded, current);
    }

    String currentKeyId() {
        return currentKeyId;
    }

    String sign(String canonical) {
        return sign(currentKeyId, canonical);
    }

    boolean verify(String keyId, String canonical, String encodedSignature) {
        if (keyId == null || encodedSignature == null || !keys.containsKey(keyId)) {
            return false;
        }
        try {
            byte[] provided = Base64.getUrlDecoder().decode(encodedSignature);
            byte[] expected = Base64.getUrlDecoder().decode(sign(keyId, canonical));
            return MessageDigest.isEqual(provided, expected);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String sign(String keyId, String canonical) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(keys.get(keyId), ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo firmar el objeto", exception);
        }
    }
}

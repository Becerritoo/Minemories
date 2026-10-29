package dev.becerritoo.minemories.paper;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

final class BanStore {
    private final File file;
    private final Logger logger;
    private final Set<UUID> banned = new HashSet<>();

    BanStore(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "bans.yml");
        this.logger = logger;
        load();
    }

    synchronized boolean contains(UUID playerId) {
        return banned.contains(playerId);
    }

    synchronized boolean add(UUID playerId) {
        boolean changed = banned.add(playerId);
        if (changed) {
            save();
        }
        return changed;
    }

    synchronized boolean remove(UUID playerId) {
        boolean changed = banned.remove(playerId);
        if (changed) {
            save();
        }
        return changed;
    }

    synchronized int size() {
        return banned.size();
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String value : yaml.getStringList("banned-uuids")) {
            try {
                banned.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
                logger.warning("UUID inválido ignorado en bans.yml: " + value);
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("banned-uuids", banned.stream().map(UUID::toString).sorted().toList());
        try {
            yaml.save(file);
        } catch (IOException exception) {
            logger.severe("No se pudo guardar bans.yml: " + exception.getMessage());
        }
    }
}

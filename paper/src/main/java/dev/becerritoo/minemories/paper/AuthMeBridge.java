package dev.becerritoo.minemories.paper;

import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Method;
import java.util.logging.Logger;

final class AuthMeBridge {
    private final boolean available;
    private final Object api;
    private final Method isAuthenticated;

    AuthMeBridge(PluginManager plugins, Logger logger) {
        Object instance = null;
        Method check = null;
        boolean loaded = plugins.isPluginEnabled("AuthMe");
        if (loaded) {
            try {
                Class<?> apiClass = Class.forName("fr.xephi.authme.api.v3.AuthMeApi");
                instance = apiClass.getMethod("getInstance").invoke(null);
                check = apiClass.getMethod("isAuthenticated", Player.class);
            } catch (ReflectiveOperationException exception) {
                loaded = false;
                logger.warning("AuthMe está instalado, pero su API no es compatible: "
                        + exception.getClass().getSimpleName());
            }
        }
        this.available = loaded;
        this.api = instance;
        this.isAuthenticated = check;
    }

    boolean isAuthenticated(Player player) {
        if (!available) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(isAuthenticated.invoke(api, player));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}

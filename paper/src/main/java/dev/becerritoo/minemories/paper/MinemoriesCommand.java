package dev.becerritoo.minemories.paper;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class MinemoriesCommand implements CommandExecutor, TabCompleter {
    private final MinemoriesPlugin plugin;

    MinemoriesCommand(MinemoriesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("minemories.admin")) {
            sender.sendMessage("§cNo tienes permiso.");
            return true;
        }
        String subcommand = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "status" -> status(sender);
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage("§aMinemories recargado. Transporte Minecraft: "
                        + (plugin.uploadReady() ? "activo" : "inactivo"));
            }
            case "ban", "unban" -> changeBan(sender, subcommand.equals("ban"), args);
            case "give" -> give(sender, args);
            default -> sender.sendMessage("§eUso: /minemories <status|reload|ban|unban|give>");
        }
        return true;
    }

    private void status(CommandSender sender) {
        sender.sendMessage("§6Minemories §7v" + plugin.getPluginMeta().getVersion());
        sender.sendMessage("§7Transporte Minecraft: " + (plugin.uploadReady() ? "§aactivo" : "§cinactivo")
                + "§7 · transferencias activas: §f" + plugin.activeTransfers());
        Settings settings = plugin.settings();
        sender.sendMessage("§7Imagen: §f" + settings.maxWidth() + "×" + settings.maxHeight()
                + "§7 · objetivo: §f" + humanBytes(settings.targetFileBytes())
                + "§7 · máximo: §f" + humanBytes(settings.maxFileBytes()));
        sender.sendMessage("§7DiscordSRV: " + (plugin.discordSrvAvailable() ? "§adisponible" : "§eno disponible")
                + "§7 · vinculación obligatoria: §f" + settings.requireDiscordLink());
        sender.sendMessage("§7Destino Discord: §f" + plugin.discordDeliveryDescription());
        sender.sendMessage("§7Captura de cámara: §f" + settings.cameraCaptureMode()
                + "§7 · render del servidor: §f" + settings.serverRendererEnabled());
        sender.sendMessage("§7Usuarios bloqueados: §f" + plugin.bans().size());
        if (plugin.cameras() != null) {
            CameraStore.Stats stats = plugin.cameras().stats();
            sender.sendMessage("§7Cámaras físicas: §aactivas§7 · cámaras: §f" + stats.cameras()
                    + "§7 · rollos: §f" + stats.rolls() + "§7 · fotos: §f" + stats.photos());
        } else {
            sender.sendMessage("§7Cámaras físicas: §cinactivas");
        }
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3 || plugin.cameras() == null) {
            sender.sendMessage("§eUso: /minemories give <jugador> <camera|rollo> [cantidad]");
            return;
        }
        var player = Bukkit.getPlayerExact(args[1]);
        if (player == null) {
            sender.sendMessage("§cEse jugador no está conectado.");
            return;
        }
        boolean camera;
        if (args[2].equalsIgnoreCase("camera") || args[2].equalsIgnoreCase("camara")) {
            camera = true;
        } else if (args[2].equalsIgnoreCase("roll") || args[2].equalsIgnoreCase("rollo")) {
            camera = false;
        } else {
            sender.sendMessage("§cEl tipo debe ser camera o rollo.");
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException exception) {
                sender.sendMessage("§cLa cantidad no es válida.");
                return;
            }
        }
        plugin.cameras().give(player, camera, amount);
        sender.sendMessage("§aEntregados " + amount + (camera ? " cámara(s)" : " rollo(s)")
                + " a " + player.getName() + '.');
    }

    private void changeBan(CommandSender sender, boolean ban, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eUso: /minemories " + (ban ? "ban" : "unban") + " <jugador|UUID>");
            return;
        }
        UUID playerId = resolveUuid(args[1]);
        if (playerId == null) {
            sender.sendMessage("§cNo se encontró ese jugador o UUID.");
            return;
        }
        boolean changed = ban ? plugin.bans().add(playerId) : plugin.bans().remove(playerId);
        sender.sendMessage((changed ? "§a" : "§e") + (ban ? "Bloqueo" : "Desbloqueo")
                + (changed ? " aplicado a " : " sin cambios para ") + playerId + '.');
    }

    private static UUID resolveUuid(String input) {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ignored) {
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(input);
            return cached == null ? null : cached.getUniqueId();
        }
    }

    private static String humanBytes(int bytes) {
        if (bytes % (1024 * 1024) == 0) {
            return (bytes / (1024 * 1024)) + "M";
        }
        if (bytes % 1024 == 0) {
            return (bytes / 1024) + "K";
        }
        return bytes + " B";
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Arrays.asList("status", "reload", "ban", "unban", "give").stream()
                    .filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("ban") || args[0].equalsIgnoreCase("unban"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(player -> player.getName())
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(player -> player.getName())
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return List.of("camera", "rollo").stream().filter(value -> value.startsWith(prefix)).toList();
        }
        return List.of();
    }
}

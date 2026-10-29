package dev.becerritoo.minemories.paper;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class TicketService {
    private final SecureRandom random = new SecureRandom();
    private final Map<String, PhotoTicket> tickets = new ConcurrentHashMap<>();
    private final Map<UUID, String> tokenByPlayer = new ConcurrentHashMap<>();
    private final Clock clock;

    TicketService() {
        this(Clock.systemUTC());
    }

    TicketService(Clock clock) {
        this.clock = clock;
    }

    IssuedTicket issue(Player player, int lifetimeSeconds, String discordId, boolean publishToDiscord,
                       CameraService.CaptureReservation reservation) {
        purgeExpired();
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        long expiresAt = clock.millis() + lifetimeSeconds * 1000L;
        Location location = player.getLocation();
        var snapshot = new LocationSnapshot(location.getWorld().getName(), location.getBlockX(),
                location.getBlockY(), location.getBlockZ());
        var ticket = new PhotoTicket(player.getUniqueId(), player.getName(), expiresAt, snapshot, discordId,
                player.hasPermission("minemories.bypass.cooldown"), publishToDiscord, reservation);
        String previous = tokenByPlayer.put(player.getUniqueId(), token);
        if (previous != null) {
            tickets.remove(previous);
        }
        tickets.put(token, ticket);
        return new IssuedTicket(token, ticket);
    }

    Optional<PhotoTicket> consume(String token) {
        if (token == null || token.length() < 32 || token.length() > 128) {
            return Optional.empty();
        }
        PhotoTicket ticket = tickets.remove(token);
        if (ticket == null || ticket.expiresAtEpochMillis() < clock.millis()) {
            return Optional.empty();
        }
        tokenByPlayer.remove(ticket.playerId(), token);
        return Optional.of(ticket);
    }

    void invalidate(UUID playerId) {
        String token = tokenByPlayer.remove(playerId);
        if (token != null) {
            tickets.remove(token);
        }
    }

    void clear() {
        tickets.clear();
        tokenByPlayer.clear();
    }

    private void purgeExpired() {
        long now = clock.millis();
        tickets.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().expiresAtEpochMillis() < now;
            if (expired) {
                tokenByPlayer.remove(entry.getValue().playerId(), entry.getKey());
            }
            return expired;
        });
    }

    record IssuedTicket(String token, PhotoTicket ticket) {
    }

    record PhotoTicket(UUID playerId, String playerName, long expiresAtEpochMillis,
                       LocationSnapshot location, String discordId, boolean bypassCooldown,
                       boolean publishToDiscord, CameraService.CaptureReservation reservation) {
    }

    record LocationSnapshot(String world, int x, int y, int z) {
    }
}

package dev.becerritoo.minemories.paper;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class UploadRateLimiter {
    private final Map<UUID, Long> lastByPlayer = new HashMap<>();
    private final ArrayDeque<Long> globalUploads = new ArrayDeque<>();
    private final Clock clock;

    UploadRateLimiter() {
        this(Clock.systemUTC());
    }

    UploadRateLimiter(Clock clock) {
        this.clock = clock;
    }

    synchronized Result acquire(UUID playerId, boolean bypassCooldown, int cooldownSeconds, int globalPerMinute) {
        long now = clock.millis();
        long cutoff = now - 60_000L;
        while (!globalUploads.isEmpty() && globalUploads.peekFirst() <= cutoff) {
            globalUploads.removeFirst();
        }
        if (globalUploads.size() >= globalPerMinute) {
            return new Result(false, "el servidor alcanzó su límite global de fotografías");
        }
        long last = lastByPlayer.getOrDefault(playerId, Long.MIN_VALUE / 2);
        long remaining = cooldownSeconds * 1000L - (now - last);
        if (!bypassCooldown && remaining > 0) {
            return new Result(false, "espera " + ((remaining + 999) / 1000) + " s antes de enviar otra fotografía");
        }
        lastByPlayer.put(playerId, now);
        globalUploads.addLast(now);
        return new Result(true, "");
    }

    record Result(boolean allowed, String reason) {
    }
}

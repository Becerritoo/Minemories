package dev.becerritoo.minemories.paper;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadRateLimiterTest {
    @Test
    void appliesPlayerCooldownAndGlobalQuota() {
        var clock = new MutableClock(1_000_000L);
        var limiter = new UploadRateLimiter(clock);
        UUID first = UUID.randomUUID();

        assertTrue(limiter.acquire(first, false, 30, 2).allowed());
        assertFalse(limiter.acquire(first, false, 30, 2).allowed());
        assertTrue(limiter.acquire(UUID.randomUUID(), false, 30, 2).allowed());
        assertFalse(limiter.acquire(UUID.randomUUID(), false, 30, 2).allowed());

        clock.millis += 60_001L;
        assertTrue(limiter.acquire(first, false, 30, 2).allowed());
    }

    private static final class MutableClock extends Clock {
        private long millis;

        private MutableClock(long millis) {
            this.millis = millis;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public long millis() {
            return millis;
        }
    }
}

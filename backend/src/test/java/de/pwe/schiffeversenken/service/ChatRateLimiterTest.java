package de.pwe.schiffeversenken.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatRateLimiterTest {

    private static final UUID ADA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    /** Zeitquelle, die der Test von Hand vorstellt. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();

    private ChatRateLimiter limiter(int maxMessages, Duration window) {
        ChatRateLimiter limiter = new ChatRateLimiter();
        limiter.clock = clock;
        limiter.maxMessages = maxMessages;
        limiter.window = window;
        return limiter;
    }

    @Test
    void messagesWithinTheLimitAreAllowed() {
        ChatRateLimiter limiter = limiter(3, Duration.ofSeconds(10));

        assertTrue(limiter.tryAcquire(ADA));
        assertTrue(limiter.tryAcquire(ADA));
        assertTrue(limiter.tryAcquire(ADA));
    }

    @Test
    void messageOverTheLimitIsRejected() {
        ChatRateLimiter limiter = limiter(3, Duration.ofSeconds(10));
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(ADA);
        }

        assertFalse(limiter.tryAcquire(ADA));
    }

    @Test
    void rejectedMessageDoesNotExtendTheBlock() {
        ChatRateLimiter limiter = limiter(1, Duration.ofSeconds(10));
        limiter.tryAcquire(ADA);
        clock.advance(Duration.ofSeconds(6));
        assertFalse(limiter.tryAcquire(ADA));

        clock.advance(Duration.ofSeconds(5));

        assertTrue(limiter.tryAcquire(ADA));
    }

    @Test
    void sendingIsPossibleAgainAfterTheWindow() {
        ChatRateLimiter limiter = limiter(2, Duration.ofSeconds(10));
        limiter.tryAcquire(ADA);
        limiter.tryAcquire(ADA);

        clock.advance(Duration.ofSeconds(10));

        assertTrue(limiter.tryAcquire(ADA));
    }

    @Test
    void windowSlidesInsteadOfResettingAllAtOnce() {
        ChatRateLimiter limiter = limiter(2, Duration.ofSeconds(10));
        limiter.tryAcquire(ADA);
        clock.advance(Duration.ofSeconds(6));
        limiter.tryAcquire(ADA);

        clock.advance(Duration.ofSeconds(5));

        assertTrue(limiter.tryAcquire(ADA), "Die erste Nachricht ist aus dem Fenster gefallen");
        assertFalse(limiter.tryAcquire(ADA), "Die zweite zählt noch");
    }

    @Test
    void playersAreLimitedIndependently() {
        ChatRateLimiter limiter = limiter(1, Duration.ofSeconds(10));
        limiter.tryAcquire(ADA);

        assertTrue(limiter.tryAcquire(BOB));
        assertFalse(limiter.tryAcquire(ADA));
    }

    @Test
    void secondsUntilNextMessageCountsDownToTheOldestMessageLeavingTheWindow() {
        ChatRateLimiter limiter = limiter(1, Duration.ofSeconds(10));
        limiter.tryAcquire(ADA);
        clock.advance(Duration.ofSeconds(4));

        assertTrue(limiter.retryAfterSeconds(ADA) >= 6 && limiter.retryAfterSeconds(ADA) <= 7);
    }
}

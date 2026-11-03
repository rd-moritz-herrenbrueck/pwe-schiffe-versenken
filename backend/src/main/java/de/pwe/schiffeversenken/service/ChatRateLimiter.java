package de.pwe.schiffeversenken.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Begrenzt, wie schnell ein Spieler chatten darf: höchstens {@code game.chat.rate-limit.max-messages} Nachrichten
 * in einem gleitenden Fenster von {@code game.chat.rate-limit.window}. Der Zähler liegt im Speicher der Instanz,
 * wie der Event-Bus (siehe Architektur, mehrere Instanzen bräuchten einen gemeinsamen Speicher).
 */
@ApplicationScoped
public class ChatRateLimiter {

    @Inject
    Clock clock;

    @ConfigProperty(name = "game.chat.rate-limit.max-messages")
    int maxMessages;

    @ConfigProperty(name = "game.chat.rate-limit.window")
    Duration window;

    private final Map<UUID, Deque<Instant>> sent = new HashMap<>();

    /** Zählt eine Nachricht, wenn das Limit es erlaubt. Abgelehnte Versuche zählen nicht mit. */
    public synchronized boolean tryAcquire(UUID playerId) {
        Instant now = clock.instant();
        Deque<Instant> recent = withoutExpired(sent.get(playerId), now);
        boolean allowed = recent.size() < maxMessages;
        if (allowed) {
            recent.addLast(now);
        }
        if (recent.isEmpty()) {
            sent.remove(playerId);
        } else {
            sent.put(playerId, recent);
        }
        return allowed;
    }

    /** Sekunden (aufgerundet, mindestens 1), bis der Spieler wieder senden darf. */
    public synchronized long retryAfterSeconds(UUID playerId) {
        Deque<Instant> times = sent.get(playerId);
        Instant oldest = times == null ? null : times.peekFirst();
        if (oldest == null) {
            return 1;
        }
        long millis = Duration.between(clock.instant(), oldest.plus(window)).toMillis();
        return Math.max(1, (millis + 999) / 1000);
    }

    private Deque<Instant> withoutExpired(Deque<Instant> times, Instant now) {
        Deque<Instant> recent = times == null ? new ArrayDeque<>() : times;
        Instant cutoff = now.minus(window);
        while (!recent.isEmpty() && !recent.peekFirst().isAfter(cutoff)) {
            recent.removeFirst();
        }
        return recent;
    }
}

package de.pwe.schiffeversenken.service;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.BackPressureStrategy;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-Memory-Event-Bus pro Instanz. Spiele und die Lobby-Liste sind getrennte Kanäle.
 *
 * <p>Verträge: Ereignisse eines Kanals kommen in Veröffentlichungsreihenfolge an. Es gibt keinen Replay, wer sich
 * später anmeldet, erhält nur neue Ereignisse. {@code publish} blockiert nie, auch nicht bei langsamen Abonnenten:
 * Deren Puffer ist begrenzt, läuft er über, endet nur ihr Stream mit einem Fehler (der Client verbindet neu und lädt
 * den Zustand). Beendet ein Abonnent (Abbruch), wird er sofort abgemeldet.
 */
@ApplicationScoped
public class GameEventBus {

    /** Ausstehende Ereignisse je Abonnent, bevor dessen Stream wegen Überlaufs fehlschlägt. */
    private static final int BUFFER_SIZE = 256;

    private final ConcurrentHashMap<UUID, Set<MultiEmitter<? super GameEvent>>> gameSubscribers =
            new ConcurrentHashMap<>();
    private final Set<MultiEmitter<? super LobbyEvent>> lobbySubscribers = ConcurrentHashMap.newKeySet();

    public void publish(GameEvent event) {
        Set<MultiEmitter<? super GameEvent>> subscribers = gameSubscribers.get(event.gameId());
        if (subscribers != null) {
            subscribers.forEach(subscriber -> emit(subscriber, event));
        }
    }

    public void publish(LobbyEvent event) {
        lobbySubscribers.forEach(subscriber -> emit(subscriber, event));
    }

    /** Emitter sind nicht für gleichzeitige Aufrufe ausgelegt, daher je Abonnent serialisieren. */
    private static <T> void emit(MultiEmitter<? super T> subscriber, T event) {
        synchronized (subscriber) {
            subscriber.emit(event);
        }
    }

    /** Ereignisse des Spiels, angemeldet ab dem Abonnieren des Streams. */
    public Multi<GameEvent> subscribe(UUID gameId) {
        return Multi.createFrom().<GameEvent>emitter(emitter -> {
            MultiEmitter<? super GameEvent> subscriber = emitter;
            gameSubscribers.compute(gameId, (id, subscribers) -> {
                Set<MultiEmitter<? super GameEvent>> updated = subscribers == null
                        ? ConcurrentHashMap.newKeySet() : subscribers;
                updated.add(subscriber);
                return updated;
            });
            emitter.onTermination(() -> gameSubscribers.computeIfPresent(gameId, (id, subscribers) -> {
                subscribers.remove(subscriber);
                return subscribers.isEmpty() ? null : subscribers;
            }));
        }, BackPressureStrategy.ERROR).onOverflow().buffer(BUFFER_SIZE);
    }

    public Multi<LobbyEvent> subscribeToLobbies() {
        return Multi.createFrom().<LobbyEvent>emitter(emitter -> {
            MultiEmitter<? super LobbyEvent> subscriber = emitter;
            lobbySubscribers.add(subscriber);
            emitter.onTermination(() -> lobbySubscribers.remove(subscriber));
        }, BackPressureStrategy.ERROR).onOverflow().buffer(BUFFER_SIZE);
    }

    /** Anzahl aktiver Abonnenten eines Spiels. */
    public int subscriberCount(UUID gameId) {
        Set<MultiEmitter<? super GameEvent>> subscribers = gameSubscribers.get(gameId);
        return subscribers == null ? 0 : subscribers.size();
    }

    /** Anzahl aktiver Abonnenten der Lobby-Liste. */
    public int lobbySubscriberCount() {
        return lobbySubscribers.size();
    }
}

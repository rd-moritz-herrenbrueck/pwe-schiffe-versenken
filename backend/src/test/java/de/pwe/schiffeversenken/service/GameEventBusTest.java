package de.pwe.schiffeversenken.service;

import de.pwe.schiffeversenken.domain.PlayerSide;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.Cancellable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.function.BooleanSupplier;

import static de.pwe.schiffeversenken.domain.GamePhase.FINISHED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Der Bus wird ohne Quarkus getestet. Ob ein Ereignis nicht ankommt, wird ohne Wartezeit geprüft: Nach dem
 * fraglichen Ereignis folgt ein Ereignis, das ankommen muss, und davor darf nichts eingetroffen sein.
 */
class GameEventBusTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final GameEventBus bus = new GameEventBus();
    private final UUID game1 = UUID.randomUUID();
    private final UUID game2 = UUID.randomUUID();

    private static <T> List<T> collect(Multi<T> stream) {
        List<T> received = Collections.synchronizedList(new ArrayList<>());
        stream.subscribe().with(received::add);
        return received;
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Bedingung nicht innerhalb von " + TIMEOUT + " erfüllt");
            }
            Thread.sleep(10);
        }
    }

    private static GameEvent event(UUID gameId, PlayerSide turn) {
        return GameEvent.turnChanged(gameId, turn);
    }

    @Test
    void subscriberReceivesEventsOfItsGameInPublishedOrder() throws InterruptedException {
        List<GameEvent> received = collect(bus.subscribe(game1));

        GameEvent first = event(game1, PlayerSide.A);
        GameEvent second = event(game1, PlayerSide.B);
        GameEvent third = GameEvent.gameFinished(game1, FINISHED, PlayerSide.A);
        bus.publish(first);
        bus.publish(second);
        bus.publish(third);

        await(() -> received.size() == 3);
        assertEquals(List.of(first, second, third), received);
    }

    @Test
    void subscriberDoesNotReceiveEventsOfOtherGames() throws InterruptedException {
        List<GameEvent> received = collect(bus.subscribe(game1));

        bus.publish(event(game2, PlayerSide.A));
        GameEvent own = event(game1, PlayerSide.B);
        bus.publish(own);

        await(() -> !received.isEmpty());
        assertEquals(List.of(own), received);
    }

    @Test
    void allSubscribersOfAGameReceiveTheEvent() throws InterruptedException {
        List<GameEvent> playerA = collect(bus.subscribe(game1));
        List<GameEvent> playerB = collect(bus.subscribe(game1));

        GameEvent event = event(game1, PlayerSide.A);
        bus.publish(event);

        await(() -> !playerA.isEmpty() && !playerB.isEmpty());
        assertEquals(List.of(event), playerA);
        assertEquals(List.of(event), playerB);
    }

    @Test
    void lateSubscriberDoesNotReceiveEarlierEvents() throws InterruptedException {
        bus.publish(event(game1, PlayerSide.A));

        List<GameEvent> received = collect(bus.subscribe(game1));
        GameEvent later = event(game1, PlayerSide.B);
        bus.publish(later);

        await(() -> !received.isEmpty());
        assertEquals(List.of(later), received);
    }

    @Test
    void publishingWithoutSubscribersIsHarmless() {
        bus.publish(event(game1, PlayerSide.A));
        bus.publish(LobbyEvent.closed(game1));

        assertEquals(0, bus.subscriberCount(game1));
        assertEquals(0, bus.lobbySubscriberCount());
    }

    // --- Abmelden ---

    @Test
    void cancelledSubscriberIsUnsubscribedAndReceivesNothingMore() throws InterruptedException {
        List<GameEvent> cancelled = new ArrayList<>();
        Cancellable subscription = bus.subscribe(game1).subscribe().with(cancelled::add);
        List<GameEvent> remaining = collect(bus.subscribe(game1));
        assertEquals(2, bus.subscriberCount(game1));

        subscription.cancel();
        await(() -> bus.subscriberCount(game1) == 1);
        bus.publish(event(game1, PlayerSide.A));

        await(() -> !remaining.isEmpty());
        assertTrue(cancelled.isEmpty());
    }

    @Test
    void subscriberCountIsPerGame() {
        collect(bus.subscribe(game1));
        collect(bus.subscribe(game1));
        collect(bus.subscribe(game2));

        assertEquals(2, bus.subscriberCount(game1));
        assertEquals(1, bus.subscriberCount(game2));
        assertEquals(0, bus.lobbySubscriberCount());
    }

    // --- Lobby ---

    @Test
    void lobbySubscribersReceiveLobbyEventsOnly() throws InterruptedException {
        List<LobbyEvent> lobbyEvents = collect(bus.subscribeToLobbies());
        List<GameEvent> gameEvents = collect(bus.subscribe(game1));

        bus.publish(event(game1, PlayerSide.A));
        LobbyEvent closed = LobbyEvent.closed(game1);
        bus.publish(closed);

        await(() -> !lobbyEvents.isEmpty() && !gameEvents.isEmpty());
        assertEquals(List.of(closed), lobbyEvents);
        assertEquals(List.of(event(game1, PlayerSide.A)), gameEvents);
        assertEquals(1, bus.lobbySubscriberCount());
    }

    // --- Langsame Abonnenten ---

    @Test
    void subscriberThatNeverRequestsDoesNotBlockPublishingOrOthers() throws InterruptedException {
        int count = 1000;
        bus.subscribe(game1).subscribe().withSubscriber(new NeverRequestingSubscriber<>());
        List<GameEvent> fast = collect(bus.subscribe(game1));

        assertTimeoutPreemptively(TIMEOUT, () -> {
            for (int i = 0; i < count; i++) {
                bus.publish(event(game1, i % 2 == 0 ? PlayerSide.A : PlayerSide.B));
            }
        });

        await(() -> fast.size() == count);
    }

    private static final class NeverRequestingSubscriber<T> implements Flow.Subscriber<T> {
        @Override
        public void onSubscribe(Flow.Subscription subscription) {
        }

        @Override
        public void onNext(T item) {
        }

        @Override
        public void onError(Throwable throwable) {
        }

        @Override
        public void onComplete() {
        }
    }
}

package de.pwe.schiffeversenken.api;

import de.pwe.schiffeversenken.api.SseTestClient.Event;
import de.pwe.schiffeversenken.api.TestApi.TwoPlayerGame;
import de.pwe.schiffeversenken.service.GameEventBus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static de.pwe.schiffeversenken.api.TestApi.ONE_SHIP_RULES;
import static de.pwe.schiffeversenken.api.TestApi.as;
import static de.pwe.schiffeversenken.api.TestApi.createLobby;
import static de.pwe.schiffeversenken.api.TestApi.fire;
import static de.pwe.schiffeversenken.api.TestApi.getGame;
import static de.pwe.schiffeversenken.api.TestApi.join;
import static de.pwe.schiffeversenken.api.TestApi.newPlayer;
import static de.pwe.schiffeversenken.api.TestApi.placeFleet;
import static de.pwe.schiffeversenken.api.TestApi.rowFleet;
import static de.pwe.schiffeversenken.api.TestApi.runningGame;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SSE-Stream eines Spiels: {@code GET /api/games/{id}/events}. Jeder Stream beginnt mit {@code CONNECTED}. Ab diesem
 * Ereignis ist der Client angemeldet, er lädt danach den Zustand per {@code GET /api/games/{id}} und verpasst so
 * nichts. Ereignisse werden erst nach dem Speichern des Zustands ausgeliefert.
 *
 * <p>Alle Spiele verwenden {@link TestApi#ONE_SHIP_RULES} (4x4, ein Zweier ab (0, 0) horizontal) oder
 * {@link #TWO_SHIPS_SHOOT_AGAIN} (6x6, Zweier ab (0, 0) und (0, 2), Nochmal-Schießen).
 */
@QuarkusTest
class GameEventsIntegrationTest {

    private static final String UNKNOWN_GAME_ID = "00000000-0000-0000-0000-000000000000";

    private static final Map<String, Object> TWO_SHIPS_SHOOT_AGAIN = Map.of(
            "boardWidth", 6, "boardHeight", 6, "fleet", List.of(2, 2),
            "shootAgainOnHit", true, "shipsMayTouch", false);

    @Inject
    GameEventBus bus;

    private static String eventsPath(String gameId) {
        return "/api/games/" + gameId + "/events";
    }

    private static TwoPlayerGame oneShipGame() {
        return runningGame(ONE_SHIP_RULES, rowFleet(List.of(2)));
    }

    private static TwoPlayerGame twoShipsGame() {
        return runningGame(TWO_SHIPS_SHOOT_AGAIN, rowFleet(List.of(2, 2)));
    }

    // --- Verbindung ---

    @Test
    void streamIsAnEventStreamThatStartsWithConnected() {
        TwoPlayerGame game = oneShipGame();

        try (SseTestClient stream = SseTestClient.connect(eventsPath(game.id()), game.tokenA())) {
            assertEquals(200, stream.status());
            assertTrue(stream.contentType().startsWith("text/event-stream"), stream.contentType());

            Event connected = stream.next();
            assertEquals("CONNECTED", connected.name());
            assertEquals("CONNECTED", connected.json().getString("type"));
            assertEquals(game.id(), connected.json().getString("gameId"));
        }
    }

    @Test
    void streamWithoutTokenIsUnauthorized() {
        TwoPlayerGame game = oneShipGame();

        try (SseTestClient stream = SseTestClient.connect(eventsPath(game.id()), null)) {
            assertEquals(401, stream.status());
        }
    }

    @Test
    void streamWithUnknownTokenIsUnauthorized() {
        TwoPlayerGame game = oneShipGame();

        try (SseTestClient stream = SseTestClient.connect(eventsPath(game.id()), "unbekanntes-token")) {
            assertEquals(401, stream.status());
        }
    }

    @Test
    void outsiderCannotListenToTheGame() {
        TwoPlayerGame game = oneShipGame();

        try (SseTestClient stream = SseTestClient.connect(eventsPath(game.id()), newPlayer("Eve"))) {
            assertEquals(404, stream.status());
            assertEquals("GAME_NOT_FOUND", stream.errorCode());
        }
    }

    @Test
    void unknownGameIsNotFound() {
        try (SseTestClient stream = SseTestClient.connect(eventsPath(UNKNOWN_GAME_ID), newPlayer("Ada"))) {
            assertEquals(404, stream.status());
            assertEquals("GAME_NOT_FOUND", stream.errorCode());
        }
    }

    @Test
    void browserCanAuthenticateWithTheTokenAsQueryParameter() {
        TwoPlayerGame game = oneShipGame();

        try (SseTestClient stream = SseTestClient.connectWithQueryToken(eventsPath(game.id()), game.tokenA())) {
            assertEquals(200, stream.status());
            assertEquals("CONNECTED", stream.next().name());
        }
    }

    @Test
    void queryParameterTokenIsAcceptedOnlyForEventStreams() {
        String token = newPlayer("Ada");

        given().queryParam("access_token", token).when().get("/api/games").then().statusCode(401);
    }

    @Test
    void streamSendsHeartbeats() {
        TwoPlayerGame game = oneShipGame();

        try (SseTestClient stream = SseTestClient.open(eventsPath(game.id()), game.tokenA())) {
            Event heartbeat = stream.nextHeartbeat();

            assertEquals("HEARTBEAT", heartbeat.json().getString("type"));
        }
    }

    @Test
    void closedStreamIsUnsubscribedFromTheBus() throws InterruptedException {
        TwoPlayerGame game = oneShipGame();
        UUID gameId = UUID.fromString(game.id());

        SseTestClient first = SseTestClient.open(eventsPath(game.id()), game.tokenA());
        SseTestClient second = SseTestClient.open(eventsPath(game.id()), game.tokenB());
        assertEquals(2, bus.subscriberCount(gameId));

        first.close();
        awaitSubscribers(gameId, 1);
        second.close();
        awaitSubscribers(gameId, 0);
    }

    private void awaitSubscribers(UUID gameId, int expected) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (bus.subscriberCount(gameId) != expected && Instant.now().isBefore(deadline)) {
            Thread.sleep(25);
        }
        assertEquals(expected, bus.subscriberCount(gameId));
    }

    @Test
    void reconnectedStreamDoesNotReplayMissedEvents() {
        TwoPlayerGame game = oneShipGame();
        try (SseTestClient first = SseTestClient.open(eventsPath(game.id()), game.tokenA())) {
            fire(game.tokenA(), game.id(), 3, 3).statusCode(200);
            assertEquals("SHOT", first.next().name());
        }
        fire(game.tokenB(), game.id(), 3, 3).statusCode(200);

        try (SseTestClient second = SseTestClient.open(eventsPath(game.id()), game.tokenA())) {
            fire(game.tokenA(), game.id(), 2, 3).statusCode(200);

            Event event = second.next();
            assertEquals("SHOT", event.name());
            assertEquals("A", event.json().getString("side"));
            assertEquals(2, event.json().getInt("x"));
        }
        getGame(game.tokenA(), game.id()).body("ownBoard.shots.size()", is(1));
    }

    // --- Spielverlauf ---

    @Test
    void creatorIsNotifiedWhenAnOpponentJoins() {
        String tokenA = newPlayer("Ada");
        String gameId = createLobby(tokenA, ONE_SHIP_RULES);

        try (SseTestClient stream = SseTestClient.open(eventsPath(gameId), tokenA)) {
            join(newPlayer("Bob"), gameId).statusCode(200);

            Event event = stream.next();
            assertEquals("PLAYER_JOINED", event.name());
            assertEquals("PLAYER_JOINED", event.json().getString("type"));
            assertEquals(gameId, event.json().getString("gameId"));
            assertEquals("Bob", event.json().getString("nickname"));
        }
    }

    @Test
    void bothPlayersAreNotifiedWhenFleetsAreReadyAndTheGameStarts() {
        String tokenA = newPlayer("Ada");
        String tokenB = newPlayer("Bob");
        String gameId = createLobby(tokenA, ONE_SHIP_RULES);
        join(tokenB, gameId).statusCode(200);

        try (SseTestClient streamA = SseTestClient.open(eventsPath(gameId), tokenA);
             SseTestClient streamB = SseTestClient.open(eventsPath(gameId), tokenB)) {
            placeFleet(tokenA, gameId, rowFleet(List.of(2))).statusCode(200);
            for (SseTestClient stream : List.of(streamA, streamB)) {
                Event ready = stream.next();
                assertEquals("FLEET_READY", ready.name());
                assertEquals("A", ready.json().getString("side"));
            }

            placeFleet(tokenB, gameId, rowFleet(List.of(2))).statusCode(200);
            for (SseTestClient stream : List.of(streamA, streamB)) {
                Event ready = stream.next();
                assertEquals("FLEET_READY", ready.name());
                assertEquals("B", ready.json().getString("side"));
                Event started = stream.next();
                assertEquals("GAME_STARTED", started.name());
                assertEquals("A", started.json().getString("currentTurn"));
            }
        }
    }

    @Test
    void missIsAnnouncedToBothPlayersTogetherWithTheTurnChange() {
        TwoPlayerGame game = oneShipGame();

        try (SseTestClient streamA = SseTestClient.open(eventsPath(game.id()), game.tokenA());
             SseTestClient streamB = SseTestClient.open(eventsPath(game.id()), game.tokenB())) {
            fire(game.tokenA(), game.id(), 3, 3).statusCode(200);

            for (SseTestClient stream : List.of(streamA, streamB)) {
                Event shot = stream.next();
                assertEquals("SHOT", shot.name());
                assertEquals(game.id(), shot.json().getString("gameId"));
                assertEquals("A", shot.json().getString("side"));
                assertEquals(3, shot.json().getInt("x"));
                assertEquals(3, shot.json().getInt("y"));
                assertEquals("MISS", shot.json().getString("result"));

                Event turn = stream.next();
                assertEquals("TURN_CHANGED", turn.name());
                assertEquals("B", turn.json().getString("currentTurn"));
            }
        }
    }

    @Test
    void hitWithShootAgainKeepsTheTurn() {
        TwoPlayerGame game = twoShipsGame();

        try (SseTestClient stream = SseTestClient.open(eventsPath(game.id()), game.tokenB())) {
            fire(game.tokenA(), game.id(), 0, 0).statusCode(200);
            fire(game.tokenA(), game.id(), 5, 5).statusCode(200);

            List<Event> events = stream.next(3);
            assertEquals(List.of("SHOT", "SHOT", "TURN_CHANGED"), events.stream().map(Event::name).toList());
            assertEquals("HIT", events.get(0).json().getString("result"));
            assertEquals("MISS", events.get(1).json().getString("result"));
            assertEquals("B", events.get(2).json().getString("currentTurn"));
        }
    }

    @Test
    void sunkShipIsRevealedInTheShotEvent() {
        TwoPlayerGame game = twoShipsGame();

        try (SseTestClient stream = SseTestClient.open(eventsPath(game.id()), game.tokenB())) {
            fire(game.tokenA(), game.id(), 0, 0).statusCode(200);
            fire(game.tokenA(), game.id(), 1, 0).statusCode(200);

            Event hit = stream.next();
            assertNull(hit.json().get("sunkShip"));
            Event sunk = stream.next();
            assertEquals("SUNK", sunk.json().getString("result"));
            assertEquals(0, sunk.json().getInt("sunkShip.x"));
            assertEquals(0, sunk.json().getInt("sunkShip.y"));
            assertEquals(2, sunk.json().getInt("sunkShip.length"));
            assertEquals("HORIZONTAL", sunk.json().getString("sunkShip.orientation"));
        }
    }

    @Test
    void winningShotIsFollowedByTheEndOfTheGame() {
        TwoPlayerGame game = oneShipGame();

        try (SseTestClient streamA = SseTestClient.open(eventsPath(game.id()), game.tokenA());
             SseTestClient streamB = SseTestClient.open(eventsPath(game.id()), game.tokenB())) {
            fire(game.tokenA(), game.id(), 0, 0).statusCode(200);
            fire(game.tokenB(), game.id(), 3, 3).statusCode(200);
            fire(game.tokenA(), game.id(), 1, 0).statusCode(200);

            for (SseTestClient stream : List.of(streamA, streamB)) {
                List<Event> events = stream.next(6);
                assertEquals(List.of("SHOT", "TURN_CHANGED", "SHOT", "TURN_CHANGED", "SHOT", "GAME_FINISHED"),
                        events.stream().map(Event::name).toList());
                assertEquals("WIN", events.get(4).json().getString("result"));
                assertEquals("FINISHED", events.get(5).json().getString("phase"));
                assertEquals("A", events.get(5).json().getString("winner"));
            }
        }
    }

    @Test
    void resignFinishesTheGameForTheOpponent() {
        TwoPlayerGame game = oneShipGame();

        try (SseTestClient streamA = SseTestClient.open(eventsPath(game.id()), game.tokenA())) {
            as(game.tokenB()).when().post("/api/games/{id}/resign", game.id()).then().statusCode(200);

            Event event = streamA.next();
            assertEquals("GAME_FINISHED", event.name());
            assertEquals("FINISHED", event.json().getString("phase"));
            assertEquals("A", event.json().getString("winner"));
        }
    }

    // --- Konsistenz ---

    @Test
    void rejectedActionsPublishNoEvent() {
        TwoPlayerGame game = oneShipGame();

        try (SseTestClient stream = SseTestClient.open(eventsPath(game.id()), game.tokenA())) {
            fire(game.tokenB(), game.id(), 3, 3).statusCode(409);
            fire(game.tokenA(), game.id(), 9, 9).statusCode(400);
            fire(game.tokenA(), game.id(), 3, 3).statusCode(200);

            Event first = stream.next();
            assertEquals("SHOT", first.name());
            assertEquals("A", first.json().getString("side"));
        }
    }

    @Test
    void eventsOfOtherGamesAreNotDelivered() {
        TwoPlayerGame game = oneShipGame();
        TwoPlayerGame other = oneShipGame();

        try (SseTestClient stream = SseTestClient.open(eventsPath(game.id()), game.tokenA())) {
            fire(other.tokenA(), other.id(), 2, 2).statusCode(200);
            fire(game.tokenA(), game.id(), 3, 3).statusCode(200);

            Event event = stream.next();
            assertEquals(game.id(), event.json().getString("gameId"));
            assertEquals(3, event.json().getInt("x"));
        }
    }

    @Test
    void stateIsAlreadySavedWhenTheEventArrives() {
        TwoPlayerGame game = oneShipGame();

        try (SseTestClient stream = SseTestClient.open(eventsPath(game.id()), game.tokenB())) {
            fire(game.tokenA(), game.id(), 3, 3).statusCode(200);

            assertEquals("SHOT", stream.next().name());
            getGame(game.tokenB(), game.id())
                    .body("yourTurn", is(true))
                    .body("ownBoard.shots.size()", is(1));
        }
    }

    // --- Anti-Cheat ---

    @Test
    void eventsNeverRevealShipsThatAreNotSunk() {
        TwoPlayerGame game = twoShipsGame();

        try (SseTestClient stream = SseTestClient.open(eventsPath(game.id()), game.tokenB())) {
            fire(game.tokenA(), game.id(), 0, 0).statusCode(200);
            fire(game.tokenA(), game.id(), 5, 5).statusCode(200);
            fire(game.tokenB(), game.id(), 0, 2).statusCode(200);

            for (Event event : stream.next(4)) {
                String result = event.json().getString("result");
                if (!"SUNK".equals(result) && !"WIN".equals(result)) {
                    assertFalse(event.data().contains("sunkShip"), event.data());
                    assertFalse(event.data().contains("length"), event.data());
                    assertFalse(event.data().contains("orientation"), event.data());
                }
                assertFalse(event.data().contains("ships"), event.data());
            }
        }
    }
}

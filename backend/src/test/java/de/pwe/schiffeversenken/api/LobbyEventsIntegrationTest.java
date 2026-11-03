package de.pwe.schiffeversenken.api;

import de.pwe.schiffeversenken.api.SseTestClient.Event;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static de.pwe.schiffeversenken.api.TestApi.ONE_SHIP_RULES;
import static de.pwe.schiffeversenken.api.TestApi.createLobby;
import static de.pwe.schiffeversenken.api.TestApi.fire;
import static de.pwe.schiffeversenken.api.TestApi.join;
import static de.pwe.schiffeversenken.api.TestApi.newPlayer;
import static de.pwe.schiffeversenken.api.TestApi.placeFleet;
import static de.pwe.schiffeversenken.api.TestApi.rowFleet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SSE-Stream der Lobby-Liste: {@code GET /api/lobbies/events}. Er beginnt wie der Spiel-Stream mit
 * {@code CONNECTED}, danach meldet er neue offene Lobbys ({@code LOBBY_OPENED}) und Lobbys, die nicht mehr offen
 * sind ({@code LOBBY_CLOSED}). Die Liste selbst lädt der Client per {@code GET /api/lobbies}.
 */
@QuarkusTest
class LobbyEventsIntegrationTest {

    private static final String PATH = "/api/lobbies/events";

    @Test
    void streamIsAnEventStreamThatStartsWithConnected() {
        try (SseTestClient stream = SseTestClient.connect(PATH, newPlayer("Ada"))) {
            assertEquals(200, stream.status());
            assertTrue(stream.contentType().startsWith("text/event-stream"), stream.contentType());

            Event connected = stream.next();
            assertEquals("CONNECTED", connected.name());
            assertEquals("CONNECTED", connected.json().getString("type"));
        }
    }

    @Test
    void streamWithoutOrWithUnknownTokenIsUnauthorized() {
        try (SseTestClient withoutToken = SseTestClient.connect(PATH, null);
             SseTestClient unknownToken = SseTestClient.connect(PATH, "unbekanntes-token")) {
            assertEquals(401, withoutToken.status());
            assertEquals(401, unknownToken.status());
        }
    }

    @Test
    void browserCanAuthenticateWithTheTokenAsQueryParameter() {
        try (SseTestClient stream = SseTestClient.connectWithQueryToken(PATH, newPlayer("Ada"))) {
            assertEquals(200, stream.status());
            assertEquals("CONNECTED", stream.next().name());
        }
    }

    @Test
    void streamSendsHeartbeats() {
        try (SseTestClient stream = SseTestClient.open(PATH, newPlayer("Ada"))) {
            assertEquals("HEARTBEAT", stream.nextHeartbeat().json().getString("type"));
        }
    }

    @Test
    void newLobbyIsAnnouncedWithItsSummary() {
        String tokenA = newPlayer("Ada");

        try (SseTestClient stream = SseTestClient.open(PATH, newPlayer("Cara"))) {
            String gameId = createLobby(tokenA, ONE_SHIP_RULES);

            Event event = stream.next();
            assertEquals("LOBBY_OPENED", event.name());
            assertEquals("LOBBY_OPENED", event.json().getString("type"));
            assertEquals(gameId, event.json().getString("gameId"));
            assertEquals(gameId, event.json().getString("lobby.id"));
            assertEquals("Ada", event.json().getString("lobby.creatorNickname"));
            assertEquals(4, event.json().getInt("lobby.rules.boardWidth"));
            assertEquals(List.of(2), event.json().getList("lobby.rules.fleet", Integer.class));
            assertNotNull(event.json().getString("lobby.createdAt"));
        }
    }

    @Test
    void creatorSeesItsOwnLobbyToo() {
        String tokenA = newPlayer("Ada");

        try (SseTestClient stream = SseTestClient.open(PATH, tokenA)) {
            String gameId = createLobby(tokenA);

            assertEquals(gameId, stream.next().json().getString("lobby.id"));
        }
    }

    @Test
    void lobbyIsClosedWhenSomeoneJoins() {
        String gameId = createLobby(newPlayer("Ada"));

        try (SseTestClient stream = SseTestClient.open(PATH, newPlayer("Cara"))) {
            join(newPlayer("Bob"), gameId).statusCode(200);

            Event event = stream.next();
            assertEquals("LOBBY_CLOSED", event.name());
            assertEquals("LOBBY_CLOSED", event.json().getString("type"));
            assertEquals(gameId, event.json().getString("gameId"));
            assertNull(event.json().get("lobby"));
        }
    }

    @Test
    void rejectedJoinPublishesNoEvent() {
        String tokenA = newPlayer("Ada");
        String gameId = createLobby(tokenA);

        try (SseTestClient stream = SseTestClient.open(PATH, newPlayer("Cara"))) {
            join(tokenA, gameId).statusCode(409);
            String nextLobby = createLobby(newPlayer("Dora"));

            Event event = stream.next();
            assertEquals("LOBBY_OPENED", event.name());
            assertEquals(nextLobby, event.json().getString("gameId"));
        }
    }

    @Test
    void gameEventsDoNotAppearInTheLobbyStream() {
        String tokenA = newPlayer("Ada");
        String tokenB = newPlayer("Bob");

        try (SseTestClient stream = SseTestClient.open(PATH, newPlayer("Cara"))) {
            String gameId = createLobby(tokenA, ONE_SHIP_RULES);
            join(tokenB, gameId).statusCode(200);
            placeFleet(tokenA, gameId, rowFleet(List.of(2))).statusCode(200);
            placeFleet(tokenB, gameId, rowFleet(List.of(2))).statusCode(200);
            fire(tokenA, gameId, 3, 3).statusCode(200);
            String nextLobby = createLobby(newPlayer("Dora"));

            List<Event> events = stream.next(3);
            assertEquals(List.of("LOBBY_OPENED", "LOBBY_CLOSED", "LOBBY_OPENED"),
                    events.stream().map(Event::name).toList());
            assertEquals(gameId, events.get(0).json().getString("gameId"));
            assertEquals(gameId, events.get(1).json().getString("gameId"));
            assertEquals(nextLobby, events.get(2).json().getString("gameId"));
        }
    }
}

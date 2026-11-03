package de.pwe.schiffeversenken.api;

import de.pwe.schiffeversenken.api.SseTestClient.Event;
import de.pwe.schiffeversenken.api.TestApi.TwoPlayerGame;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static de.pwe.schiffeversenken.api.TestApi.ONE_SHIP_RULES;
import static de.pwe.schiffeversenken.api.TestApi.fire;
import static de.pwe.schiffeversenken.api.TestApi.placeFleet;
import static de.pwe.schiffeversenken.api.TestApi.placementGame;
import static de.pwe.schiffeversenken.api.TestApi.rowFleet;
import static de.pwe.schiffeversenken.api.TestApi.runningGame;
import static de.pwe.schiffeversenken.api.TestApi.sendChat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@code CHAT}-Ereignis im Spiel-Stream: Beide Spieler (auch der Absender) erhalten jede gespeicherte Nachricht als
 * {@code {type, gameId, message: {id, side, nickname, text, createdAt}}}. Abgelehnte Nachrichten erzeugen keines.
 */
@QuarkusTest
class ChatEventsIntegrationTest {

    private static String eventsPath(String gameId) {
        return "/api/games/" + gameId + "/events";
    }

    @Test
    void bothPlayersReceiveTheMessage() {
        TwoPlayerGame game = placementGame(ONE_SHIP_RULES);

        try (SseTestClient streamA = SseTestClient.open(eventsPath(game.id()), game.tokenA());
             SseTestClient streamB = SseTestClient.open(eventsPath(game.id()), game.tokenB())) {
            String messageId = sendChat(game.tokenA(), game.id(), "Hallo Bob")
                    .statusCode(201).extract().path("id");

            for (SseTestClient stream : List.of(streamA, streamB)) {
                Event event = stream.next();
                assertEquals("CHAT", event.name());
                assertEquals("CHAT", event.json().getString("type"));
                assertEquals(game.id(), event.json().getString("gameId"));
                assertEquals(messageId, event.json().getString("message.id"));
                assertEquals("A", event.json().getString("message.side"));
                assertEquals("Ada", event.json().getString("message.nickname"));
                assertEquals("Hallo Bob", event.json().getString("message.text"));
                assertNotNull(event.json().getString("message.createdAt"));
            }
        }
    }

    @Test
    void messagesArriveInTheOrderTheyWereSent() {
        TwoPlayerGame game = placementGame(ONE_SHIP_RULES);

        try (SseTestClient stream = SseTestClient.open(eventsPath(game.id()), game.tokenB())) {
            sendChat(game.tokenA(), game.id(), "eins").statusCode(201);
            sendChat(game.tokenB(), game.id(), "zwei").statusCode(201);

            List<Event> events = stream.next(2);
            assertEquals("eins", events.get(0).json().getString("message.text"));
            assertEquals("zwei", events.get(1).json().getString("message.text"));
            assertEquals("B", events.get(1).json().getString("message.side"));
        }
    }

    @Test
    void rejectedMessageProducesNoEvent() {
        TwoPlayerGame game = placementGame(ONE_SHIP_RULES);

        try (SseTestClient stream = SseTestClient.open(eventsPath(game.id()), game.tokenB())) {
            sendChat(game.tokenA(), game.id(), "   ").statusCode(400);
            sendChat(game.tokenA(), game.id(), "x".repeat(501)).statusCode(400);
            sendChat(game.tokenA(), game.id(), "gültig").statusCode(201);

            assertEquals("gültig", stream.next().json().getString("message.text"));
        }
    }

    @Test
    void chatEventsAreInterleavedWithGameEvents() {
        TwoPlayerGame game = placementGame(ONE_SHIP_RULES);

        try (SseTestClient stream = SseTestClient.open(eventsPath(game.id()), game.tokenB())) {
            placeFleet(game.tokenA(), game.id(), rowFleet(List.of(2))).statusCode(200);
            sendChat(game.tokenA(), game.id(), "bereit").statusCode(201);

            assertEquals(List.of("FLEET_READY", "CHAT"), stream.next(2).stream().map(Event::name).toList());
        }
    }

    @Test
    void chatEventsDoNotLeakIntoOtherGames() {
        TwoPlayerGame game = placementGame(ONE_SHIP_RULES);
        TwoPlayerGame other = runningGame(ONE_SHIP_RULES, rowFleet(List.of(2)));

        try (SseTestClient stream = SseTestClient.open(eventsPath(other.id()), other.tokenA())) {
            sendChat(game.tokenA(), game.id(), "nicht für euch").statusCode(201);
            fire(other.tokenA(), other.id(), 3, 3).statusCode(200);

            assertEquals("SHOT", stream.next().name());
        }
    }
}

package de.pwe.schiffeversenken.service;

import de.pwe.schiffeversenken.api.SseTestClient;
import de.pwe.schiffeversenken.api.SseTestClient.Event;
import de.pwe.schiffeversenken.persistence.GameRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static de.pwe.schiffeversenken.api.TestApi.as;
import static de.pwe.schiffeversenken.api.TestApi.createLobby;
import static de.pwe.schiffeversenken.api.TestApi.createPrivateLobby;
import static de.pwe.schiffeversenken.api.TestApi.getGame;
import static de.pwe.schiffeversenken.api.TestApi.join;
import static de.pwe.schiffeversenken.api.TestApi.joinByCode;
import static de.pwe.schiffeversenken.api.TestApi.newPlayer;
import static de.pwe.schiffeversenken.api.TestApi.placementGame;
import static de.pwe.schiffeversenken.api.TestApi.ONE_SHIP_RULES;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offene Lobbys ohne Gegner werden nach {@code game.lobby.expire-after} (Standard 24 Stunden seit dem Erstellen)
 * gelöscht. Der geplante Job ist im Testprofil abgeschaltet, die Tests rufen den Service direkt auf und legen den
 * Erstellzeitpunkt in die Vergangenheit.
 */
@QuarkusTest
class LobbyExpiryIntegrationTest {

    private static final Instant LONG_AGO = Instant.parse("2000-01-01T00:00:00Z");

    @Inject
    LobbyService lobbyService;

    @Inject
    GameRepository games;

    @Test
    void expiredPublicLobbyIsDeletedAndNoLongerListed() {
        String token = newPlayer("Ada");
        String gameId = createLobby(token);
        makeOld(gameId);

        int deleted = lobbyService.deleteExpiredLobbies();

        assertTrue(deleted >= 1);
        getGame(token, gameId).statusCode(404);
        as(token).when().get("/api/lobbies").then().body("id", not(hasItem(gameId)));
    }

    @Test
    void expiredPrivateLobbyIsDeletedAndItsCodeStopsWorking() {
        String token = newPlayer("Ada");
        var created = createPrivateLobby(token, ONE_SHIP_RULES).statusCode(201).extract();
        String gameId = created.path("id");
        String code = created.path("inviteCode");
        makeOld(gameId);

        lobbyService.deleteExpiredLobbies();

        getGame(token, gameId).statusCode(404);
        joinByCode(newPlayer("Bob"), code).statusCode(404);
    }

    @Test
    void youngLobbyIsKept() {
        String token = newPlayer("Ada");
        String gameId = createLobby(token);

        lobbyService.deleteExpiredLobbies();

        getGame(token, gameId).statusCode(200);
    }

    @Test
    void oldGameWithAnOpponentIsNotDeleted() {
        var game = placementGame(ONE_SHIP_RULES);
        makeOld(game.id());

        lobbyService.deleteExpiredLobbies();

        getGame(game.tokenA(), game.id()).statusCode(200);
    }

    @Test
    void joiningAnExpiredAndDeletedLobbyIsNotFound() {
        String gameId = createLobby(newPlayer("Ada"));
        makeOld(gameId);
        lobbyService.deleteExpiredLobbies();

        join(newPlayer("Bob"), gameId).statusCode(404);
    }

    @Test
    void deletedLobbyIsRemovedFromTheLobbyStream() {
        String gameId = createLobby(newPlayer("Ada"));
        makeOld(gameId);

        try (SseTestClient stream = SseTestClient.open("/api/lobbies/events", newPlayer("Cara"))) {
            lobbyService.deleteExpiredLobbies();

            Event event = stream.next();
            assertEquals("LOBBY_CLOSED", event.name());
            assertEquals(gameId, event.json().getString("gameId"));
        }
    }

    @Test
    void deletedPrivateLobbyIsNotAnnouncedOnTheLobbyStream() {
        String gameId = createPrivateLobby(newPlayer("Ada"), ONE_SHIP_RULES).statusCode(201).extract().path("id");
        makeOld(gameId);

        try (SseTestClient stream = SseTestClient.open("/api/lobbies/events", newPlayer("Cara"))) {
            lobbyService.deleteExpiredLobbies();
            String marker = createLobby(newPlayer("Dora"));

            Event event = stream.next();
            assertEquals("LOBBY_OPENED", event.name());
            assertEquals(marker, event.json().getString("lobby.id"));
        }
    }

    private void makeOld(String gameId) {
        QuarkusTransaction.requiringNew().run(() ->
                games.update("createdAt = ?1 where id = ?2", LONG_AGO, UUID.fromString(gameId)));
    }
}

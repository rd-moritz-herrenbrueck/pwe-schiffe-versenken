package de.pwe.schiffeversenken.service;

import de.pwe.schiffeversenken.api.SseTestClient;
import de.pwe.schiffeversenken.api.SseTestClient.Event;
import de.pwe.schiffeversenken.api.TestApi.TwoPlayerGame;
import de.pwe.schiffeversenken.persistence.GameRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static de.pwe.schiffeversenken.api.TestApi.as;
import static de.pwe.schiffeversenken.api.TestApi.createLobby;
import static de.pwe.schiffeversenken.api.TestApi.getGame;
import static de.pwe.schiffeversenken.api.TestApi.newPlayer;
import static de.pwe.schiffeversenken.api.TestApi.runningStandardGame;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verwaiste Spiele: Der geplante Job ist im Testprofil abgeschaltet, die Tests rufen den Service direkt auf.
 * Statt die Zeit zu verstellen, wird die letzte Aktivität eines Spiels in die Vergangenheit gelegt.
 */
@QuarkusTest
class AbandonInactiveGamesIntegrationTest {

    private static final Instant LONG_AGO = Instant.parse("2000-01-01T00:00:00Z");

    @Inject
    GameService gameService;

    @Inject
    GameRepository games;

    @Test
    void inactiveRunningGameIsAbandoned() {
        TwoPlayerGame game = runningStandardGame();
        makeInactive(game.id());

        gameService.abandonInactiveGames();

        getGame(game.tokenA(), game.id())
                .body("phase", is("ABANDONED"))
                .body("yourTurn", is(false))
                .body("result", nullValue());
    }

    @Test
    void inactiveLobbyIsLeftToTheLobbyExpiry() {
        String token = newPlayer("Ada");
        String gameId = createLobby(token);
        makeInactive(gameId);

        gameService.abandonInactiveGames();

        getGame(token, gameId).body("phase", is("WAITING"));
    }

    @Test
    void recentlyActiveGameIsKept() {
        TwoPlayerGame game = runningStandardGame();

        gameService.abandonInactiveGames();

        getGame(game.tokenA(), game.id()).body("phase", is("RUNNING"));
    }

    @Test
    void finishedGameIsKept() {
        TwoPlayerGame game = runningStandardGame();
        as(game.tokenA()).when().post("/api/games/{id}/resign", game.id()).then().statusCode(200);
        makeInactive(game.id());

        gameService.abandonInactiveGames();

        getGame(game.tokenA(), game.id())
                .body("phase", is("FINISHED"))
                .body("result", is("LOST"));
    }

    @Test
    void abandonedGameIsAnnouncedToItsPlayersWithoutWinner() {
        TwoPlayerGame game = runningStandardGame();
        makeInactive(game.id());

        try (SseTestClient stream = SseTestClient.open("/api/games/" + game.id() + "/events", game.tokenA())) {
            gameService.abandonInactiveGames();

            Event event = stream.next();
            assertEquals("GAME_FINISHED", event.name());
            assertEquals("ABANDONED", event.json().getString("phase"));
            assertNull(event.json().get("winner"));
        }
    }

    private void makeInactive(String gameId) {
        QuarkusTransaction.requiringNew().run(() ->
                games.update("updatedAt = ?1 where id = ?2", LONG_AGO, UUID.fromString(gameId)));
    }
}

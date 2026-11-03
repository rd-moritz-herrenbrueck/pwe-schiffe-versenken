package de.pwe.schiffeversenken.api;

import de.pwe.schiffeversenken.api.SseTestClient.Event;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;


import static de.pwe.schiffeversenken.api.TestApi.ONE_SHIP_RULES;
import static de.pwe.schiffeversenken.api.TestApi.as;
import static de.pwe.schiffeversenken.api.TestApi.createAiGame;
import static de.pwe.schiffeversenken.api.TestApi.createLobby;
import static de.pwe.schiffeversenken.api.TestApi.createPrivateLobby;
import static de.pwe.schiffeversenken.api.TestApi.getGame;
import static de.pwe.schiffeversenken.api.TestApi.joinByCode;
import static de.pwe.schiffeversenken.api.TestApi.newPlayer;
import static de.pwe.schiffeversenken.api.TestApi.placementGame;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@code DELETE /api/lobbies/{id}}: Der Ersteller löscht eine Lobby, solange noch kein Gegner beigetreten ist. */
@QuarkusTest
class LobbyDeletionIntegrationTest {

    private static final String UNKNOWN_GAME_ID = "00000000-0000-0000-0000-000000000000";

    @Test
    void creatorCanDeleteHisOpenLobby() {
        String token = newPlayer("Ada");
        String gameId = createLobby(token);

        as(token).when().delete("/api/lobbies/{id}", gameId).then().statusCode(204);

        getGame(token, gameId).statusCode(404);
        as(token).when().get("/api/lobbies").then().body("id", not(hasItem(gameId)));
        as(token).when().get("/api/games").then().body("id", not(hasItem(gameId)));
    }

    @Test
    void creatorCanDeleteHisPrivateLobbyAndTheCodeStopsWorking() {
        String token = newPlayer("Ada");
        var created = createPrivateLobby(token, ONE_SHIP_RULES).statusCode(201).extract();
        String gameId = created.path("id");
        String code = created.path("inviteCode");

        as(token).when().delete("/api/lobbies/{id}", gameId).then().statusCode(204);

        joinByCode(newPlayer("Bob"), code).statusCode(404);
    }

    @Test
    void otherPlayersCannotDeleteTheLobby() {
        String gameId = createLobby(newPlayer("Ada"));
        String stranger = newPlayer("Bob");

        as(stranger).when().delete("/api/lobbies/{id}", gameId).then()
                .statusCode(404).body("code", is("GAME_NOT_FOUND"));
    }

    @Test
    void unknownLobbyIsNotFound() {
        as(newPlayer("Ada")).when().delete("/api/lobbies/{id}", UNKNOWN_GAME_ID).then().statusCode(404);
    }

    @Test
    void lobbyWithAnOpponentCannotBeDeleted() {
        TestApi.TwoPlayerGame game = placementGame(ONE_SHIP_RULES);

        as(game.tokenA()).when().delete("/api/lobbies/{id}", game.id()).then()
                .statusCode(409).body("code", is("WRONG_PHASE"));
        getGame(game.tokenA(), game.id()).statusCode(200);
    }

    @Test
    void gameAgainstTheAiCannotBeDeleted() {
        String token = newPlayer("Ada");
        String gameId = createAiGame(token, ONE_SHIP_RULES);

        as(token).when().delete("/api/lobbies/{id}", gameId).then().statusCode(409);
    }

    @Test
    void deletingRequiresAToken() {
        String gameId = createLobby(newPlayer("Ada"));

        given().when().delete("/api/lobbies/{id}", gameId).then().statusCode(401);
    }

    @Test
    void deletedLobbyIsRemovedFromTheLobbyStream() {
        String token = newPlayer("Ada");
        String gameId = createLobby(token);

        try (SseTestClient stream = SseTestClient.open("/api/lobbies/events", newPlayer("Cara"))) {
            as(token).when().delete("/api/lobbies/{id}", gameId).then().statusCode(204);

            Event event = stream.next();
            assertEquals("LOBBY_CLOSED", event.name());
            assertEquals(gameId, event.json().getString("gameId"));
        }
    }
}

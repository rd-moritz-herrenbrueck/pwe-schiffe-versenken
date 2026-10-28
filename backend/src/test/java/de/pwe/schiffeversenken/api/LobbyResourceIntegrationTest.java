package de.pwe.schiffeversenken.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static de.pwe.schiffeversenken.api.TestApi.as;
import static de.pwe.schiffeversenken.api.TestApi.createLobby;
import static de.pwe.schiffeversenken.api.TestApi.getGame;
import static de.pwe.schiffeversenken.api.TestApi.join;
import static de.pwe.schiffeversenken.api.TestApi.newPlayer;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class LobbyResourceIntegrationTest {

    @Test
    void lobbyWithoutRulesUsesStandardRulesAndWaitsForOpponent() {
        as(newPlayer("Ada")).body(Map.of())
                .when().post("/api/lobbies")
                .then().statusCode(201)
                .body("id", notNullValue())
                .body("phase", is("WAITING"))
                .body("you", is("A"))
                .body("opponentNickname", nullValue())
                .body("yourTurn", is(false))
                .body("rules.boardWidth", is(10))
                .body("rules.boardHeight", is(10))
                .body("rules.fleet", contains(5, 4, 3, 2))
                .body("rules.shootAgainOnHit", is(false))
                .body("rules.shipsMayTouch", is(false));
    }

    @Test
    void lobbyWithCustomRules() {
        Map<String, Object> rules = Map.of("boardWidth", 8, "boardHeight", 6, "fleet", List.of(4, 3, 2, 2),
                "shootAgainOnHit", true, "shipsMayTouch", true);

        as(newPlayer("Ada")).body(Map.of("rules", rules))
                .when().post("/api/lobbies")
                .then().statusCode(201)
                .body("rules.boardWidth", is(8))
                .body("rules.boardHeight", is(6))
                .body("rules.fleet", contains(4, 3, 2, 2))
                .body("rules.shootAgainOnHit", is(true))
                .body("rules.shipsMayTouch", is(true));
    }

    static Stream<Arguments> invalidRules() {
        return Stream.of(
                Arguments.of("Feld zu breit", "INVALID_RULE_SET", rules(26, 10, List.of(5, 4, 3, 2), false)),
                Arguments.of("Schiff zu kurz", "INVALID_RULE_SET", rules(10, 10, List.of(1), false)),
                Arguments.of("Flotte passt nicht", "FLEET_DOES_NOT_FIT", rules(4, 4, List.of(3, 3, 3), false)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRules")
    void invalidRulesAreRejected(String description, String code, Map<String, Object> rules) {
        as(newPlayer("Ada")).body(Map.of("rules", rules))
                .when().post("/api/lobbies")
                .then().statusCode(400)
                .body("code", is(code));
    }

    @Test
    void creatingLobbyRequiresToken() {
        given().contentType(ContentType.JSON).body(Map.of())
                .when().post("/api/lobbies")
                .then().statusCode(401);
    }

    @Test
    void openLobbyIsListedWithCreatorAndRules() {
        String gameId = createLobby(newPlayer("Lister"));

        as(newPlayer("Viewer")).when().get("/api/lobbies")
                .then().statusCode(200)
                .body("find { it.id == '%s' }.creatorNickname".formatted(gameId), is("Lister"))
                .body("find { it.id == '%s' }.rules.boardWidth".formatted(gameId), is(10))
                .body("find { it.id == '%s' }.createdAt".formatted(gameId), notNullValue());
    }

    @Test
    void joiningStartsPlacementForBothPlayers() {
        String tokenA = newPlayer("Ada");
        String gameId = createLobby(tokenA);

        join(newPlayer("Bob"), gameId)
                .statusCode(200)
                .body("id", is(gameId))
                .body("phase", is("PLACEMENT"))
                .body("you", is("B"))
                .body("opponentNickname", is("Ada"));

        getGame(tokenA, gameId)
                .statusCode(200)
                .body("phase", is("PLACEMENT"))
                .body("you", is("A"))
                .body("opponentNickname", is("Bob"));
    }

    @Test
    void joinedLobbyIsNoLongerListed() {
        String tokenB = newPlayer("Bob");
        String gameId = createLobby(newPlayer("Ada"));

        join(tokenB, gameId).statusCode(200);

        as(tokenB).when().get("/api/lobbies")
                .then().statusCode(200)
                .body("id", not(hasItem(gameId)));
    }

    @Test
    void joiningOwnLobbyIsRejected() {
        String tokenA = newPlayer("Ada");
        String gameId = createLobby(tokenA);

        join(tokenA, gameId)
                .statusCode(409)
                .body("code", is("CANNOT_JOIN_OWN_GAME"));
    }

    @Test
    void joiningFullLobbyIsRejected() {
        String gameId = createLobby(newPlayer("Ada"));
        join(newPlayer("Bob"), gameId).statusCode(200);

        join(newPlayer("Eve"), gameId)
                .statusCode(409)
                .body("code", is("WRONG_PHASE"));
    }

    @Test
    void joiningUnknownLobbyIsRejected() {
        join(newPlayer("Bob"), "00000000-0000-0000-0000-000000000000")
                .statusCode(404)
                .body("code", is("GAME_NOT_FOUND"));
    }

    @Test
    void joiningWithoutGameIdIsRejected() {
        as(newPlayer("Bob")).body(Map.of())
                .when().post("/api/lobbies/join")
                .then().statusCode(400)
                .body("code", is("INVALID_REQUEST"));
    }

    private static Map<String, Object> rules(int width, int height, List<Integer> fleet, boolean shipsMayTouch) {
        return Map.of("boardWidth", width, "boardHeight", height, "fleet", fleet,
                "shootAgainOnHit", false, "shipsMayTouch", shipsMayTouch);
    }
}

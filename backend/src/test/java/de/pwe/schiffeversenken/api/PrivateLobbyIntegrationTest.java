package de.pwe.schiffeversenken.api;

import de.pwe.schiffeversenken.api.SseTestClient.Event;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static de.pwe.schiffeversenken.api.TestApi.ONE_SHIP_RULES;
import static de.pwe.schiffeversenken.api.TestApi.as;
import static de.pwe.schiffeversenken.api.TestApi.createLobby;
import static de.pwe.schiffeversenken.api.TestApi.createPrivateLobby;
import static de.pwe.schiffeversenken.api.TestApi.getGame;
import static de.pwe.schiffeversenken.api.TestApi.join;
import static de.pwe.schiffeversenken.api.TestApi.joinByCode;
import static de.pwe.schiffeversenken.api.TestApi.newPlayer;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Private Lobbys: {@code POST /api/lobbies} mit {@code visibility = PRIVATE}. Der Ersteller erhält einen
 * Einladungscode (8 Zeichen aus {@code A-Z0-9}), die Lobby steht weder in der Liste noch im Lobby-Stream. Beitreten
 * geht nur mit {@code POST /api/lobbies/join} und {@code inviteCode}, nicht mit der Spiel-ID.
 */
@QuarkusTest
class PrivateLobbyIntegrationTest {

    private static final String CODE_PATTERN = "[A-Z0-9]{8}";
    private static final String LOBBY_EVENTS = "/api/lobbies/events";

    private static String inviteCodeOf(String creatorToken) {
        return createPrivateLobby(creatorToken, ONE_SHIP_RULES).statusCode(201).extract().path("inviteCode");
    }

    @Test
    void creatorOfPrivateLobbyReceivesInviteCode() {
        createPrivateLobby(newPlayer("Ada"), ONE_SHIP_RULES)
                .statusCode(201)
                .body("phase", is("WAITING"))
                .body("you", is("A"))
                .body("inviteCode", matchesPattern(CODE_PATTERN))
                .body("rules.boardWidth", is(4));
    }

    @Test
    void publicLobbyHasNoInviteCode() {
        as(newPlayer("Ada")).body(Map.of("visibility", "PUBLIC", "rules", ONE_SHIP_RULES))
                .when().post("/api/lobbies")
                .then().statusCode(201)
                .body("inviteCode", nullValue());
    }

    @Test
    void creatorSeesInviteCodeAgainWhenReloadingTheGame() {
        String token = newPlayer("Ada");
        var created = createPrivateLobby(token, ONE_SHIP_RULES).statusCode(201).extract();
        String gameId = created.path("id");
        String inviteCode = created.path("inviteCode");

        getGame(token, gameId).statusCode(200).body("inviteCode", is(inviteCode));
    }

    @Test
    void inviteCodesAreUnique() {
        String token = newPlayer("Ada");
        Set<String> codes = new HashSet<>();

        for (int i = 0; i < 25; i++) {
            codes.add(inviteCodeOf(token));
        }

        assertEquals(25, codes.size());
    }

    @Test
    void privateLobbyIsNotListed() {
        String tokenA = newPlayer("Ada");
        String gameId = createPrivateLobby(tokenA, ONE_SHIP_RULES).statusCode(201).extract().path("id");

        as(newPlayer("Cara")).when().get("/api/lobbies")
                .then().statusCode(200).body("id", not(hasItem(gameId)));
        as(tokenA).when().get("/api/lobbies")
                .then().statusCode(200).body("id", not(hasItem(gameId)));
    }

    @Test
    void privateLobbyIsListedInTheCreatorsOwnGames() {
        String tokenA = newPlayer("Ada");
        String gameId = createPrivateLobby(tokenA, ONE_SHIP_RULES).statusCode(201).extract().path("id");

        as(tokenA).when().get("/api/games")
                .then().statusCode(200)
                .body("find { it.id == '%s' }.phase".formatted(gameId), is("WAITING"));
    }

    @Test
    void joiningWithInviteCodeStartsPlacementForBothPlayers() {
        String tokenA = newPlayer("Ada");
        String code = inviteCodeOf(tokenA);

        String gameId = joinByCode(newPlayer("Bob"), code)
                .statusCode(200)
                .body("phase", is("PLACEMENT"))
                .body("you", is("B"))
                .body("opponentNickname", is("Ada"))
                .body("inviteCode", nullValue())
                .extract().path("id");

        getGame(tokenA, gameId).statusCode(200)
                .body("phase", is("PLACEMENT"))
                .body("opponentNickname", is("Bob"));
    }

    @Test
    void inviteCodeIsCaseInsensitiveAndIgnoresSurroundingWhitespace() {
        String code = inviteCodeOf(newPlayer("Ada"));

        joinByCode(newPlayer("Bob"), "  " + code.toLowerCase() + " ")
                .statusCode(200)
                .body("phase", is("PLACEMENT"));
    }

    @Test
    void privateLobbyCannotBeJoinedWithItsGameId() {
        String gameId = createPrivateLobby(newPlayer("Ada"), ONE_SHIP_RULES).statusCode(201).extract().path("id");

        join(newPlayer("Bob"), gameId).statusCode(404).body("code", is("GAME_NOT_FOUND"));
    }

    @ParameterizedTest(name = "Code \"{0}\"")
    @ValueSource(strings = {"ZZZZZZZZ", "abc", "00000000"})
    void unknownInviteCodeIsNotFound(String code) {
        joinByCode(newPlayer("Bob"), code).statusCode(404).body("code", is("GAME_NOT_FOUND"));
    }

    @Test
    void inviteCodeCannotBeUsedByTheCreator() {
        String tokenA = newPlayer("Ada");

        joinByCode(tokenA, inviteCodeOf(tokenA)).statusCode(409).body("code", is("CANNOT_JOIN_OWN_GAME"));
    }

    @Test
    void inviteCodeStopsWorkingOnceSomeoneHasJoined() {
        String code = inviteCodeOf(newPlayer("Ada"));
        joinByCode(newPlayer("Bob"), code).statusCode(200);

        joinByCode(newPlayer("Cara"), code).statusCode(409).body("code", is("WRONG_PHASE"));
    }

    @Test
    void joinNeedsGameIdOrInviteCode() {
        as(newPlayer("Bob")).body(Map.of())
                .when().post("/api/lobbies/join")
                .then().statusCode(400).body("code", is("INVALID_REQUEST"));
    }

    @Test
    void unknownVisibilityIsRejected() {
        as(newPlayer("Ada")).body(Map.of("visibility", "SECRET"))
                .when().post("/api/lobbies")
                .then().statusCode(400);
    }

    @Test
    void privateLobbyIsNotAnnouncedInTheLobbyStream() {
        String tokenA = newPlayer("Ada");

        try (SseTestClient stream = SseTestClient.open(LOBBY_EVENTS, newPlayer("Cara"))) {
            createPrivateLobby(tokenA, ONE_SHIP_RULES).statusCode(201);
            String publicLobby = createLobby(newPlayer("Dora"));

            Event event = stream.next();
            assertEquals("LOBBY_OPENED", event.name());
            assertEquals(publicLobby, event.json().getString("gameId"));
        }
    }

    @Test
    void joiningAPrivateLobbyDoesNotCloseAnyListedLobby() {
        String code = inviteCodeOf(newPlayer("Ada"));

        try (SseTestClient stream = SseTestClient.open(LOBBY_EVENTS, newPlayer("Cara"))) {
            joinByCode(newPlayer("Bob"), code).statusCode(200);
            String publicLobby = createLobby(newPlayer("Dora"));

            Event event = stream.next();
            assertEquals("LOBBY_OPENED", event.name());
            assertEquals(publicLobby, event.json().getString("gameId"));
        }
    }

    @Test
    void creatorIsNotifiedWhenAnOpponentJoinsWithTheCode() {
        String tokenA = newPlayer("Ada");
        var created = createPrivateLobby(tokenA, ONE_SHIP_RULES).statusCode(201).extract();

        try (SseTestClient stream = SseTestClient.open("/api/games/" + created.path("id") + "/events", tokenA)) {
            joinByCode(newPlayer("Bob"), created.path("inviteCode")).statusCode(200);

            Event event = stream.next();
            assertEquals("PLAYER_JOINED", event.name());
            assertEquals("Bob", event.json().getString("nickname"));
        }
    }

    @Test
    void otherPlayersCannotReadAPrivateGameBeforeJoining() {
        String gameId = createPrivateLobby(newPlayer("Ada"), ONE_SHIP_RULES).statusCode(201).extract().path("id");

        getGame(newPlayer("Cara"), gameId).statusCode(404).body("code", is("GAME_NOT_FOUND"));
    }
}

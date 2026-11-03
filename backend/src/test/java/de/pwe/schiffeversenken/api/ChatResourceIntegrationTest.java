package de.pwe.schiffeversenken.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static de.pwe.schiffeversenken.api.TestApi.ONE_SHIP_RULES;
import static de.pwe.schiffeversenken.api.TestApi.as;
import static de.pwe.schiffeversenken.api.TestApi.createLobby;
import static de.pwe.schiffeversenken.api.TestApi.newPlayer;
import static de.pwe.schiffeversenken.api.TestApi.placementGame;
import static de.pwe.schiffeversenken.api.TestApi.readChat;
import static de.pwe.schiffeversenken.api.TestApi.sendChat;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Chat eines Spiels: {@code GET/POST /api/games/{id}/chat}. Eine Nachricht hat {@code id}, {@code side} des
 * Absenders, {@code nickname}, {@code text} (getrimmt, 1–500 Zeichen) und {@code createdAt}. Der Verlauf ist
 * aufsteigend nach Zeit sortiert. Senden geht in {@code PLACEMENT}, {@code RUNNING} und {@code FINISHED}, Lesen
 * immer. Nur beteiligte Spieler haben Zugriff.
 */
@QuarkusTest
class ChatResourceIntegrationTest {

    private static final String UNKNOWN_GAME_ID = "00000000-0000-0000-0000-000000000000";

    @Test
    void sentMessageIsReturnedWithSenderAndTimestamp() {
        TestApi.TwoPlayerGame game = placementGame(ONE_SHIP_RULES);

        sendChat(game.tokenB(), game.id(), "Viel Glück!")
                .statusCode(201)
                .body("id", notNullValue())
                .body("side", is("B"))
                .body("nickname", is("Bob"))
                .body("text", is("Viel Glück!"))
                .body("createdAt", notNullValue());
    }

    @Test
    void textIsTrimmed() {
        TestApi.TwoPlayerGame game = placementGame(ONE_SHIP_RULES);

        sendChat(game.tokenA(), game.id(), "  hallo \n").statusCode(201).body("text", is("hallo"));
    }

    @Test
    void markupAndSpecialCharactersAreStoredUnchanged() {
        TestApi.TwoPlayerGame game = placementGame(ONE_SHIP_RULES);
        String text = "<b>fett</b> & \"zitat\" 'x' ✓";

        sendChat(game.tokenA(), game.id(), text).statusCode(201).body("text", is(text));
        readChat(game.tokenB(), game.id()).statusCode(200).body("text", contains(text));
    }

    @Test
    void bothPlayersSeeTheSameHistoryOldestFirst() {
        TestApi.TwoPlayerGame game = placementGame(ONE_SHIP_RULES);
        sendChat(game.tokenA(), game.id(), "eins").statusCode(201);
        sendChat(game.tokenB(), game.id(), "zwei").statusCode(201);
        sendChat(game.tokenA(), game.id(), "drei").statusCode(201);

        for (String token : List.of(game.tokenA(), game.tokenB())) {
            readChat(token, game.id()).statusCode(200)
                    .body("text", contains("eins", "zwei", "drei"))
                    .body("side", contains("A", "B", "A"))
                    .body("nickname", contains("Ada", "Bob", "Ada"));
        }
    }

    @Test
    void newGameHasAnEmptyHistory() {
        TestApi.TwoPlayerGame game = placementGame(ONE_SHIP_RULES);

        readChat(game.tokenA(), game.id()).statusCode(200).body("$", empty());
    }

    @Test
    void chatsOfDifferentGamesAreSeparate() {
        TestApi.TwoPlayerGame first = placementGame(ONE_SHIP_RULES);
        TestApi.TwoPlayerGame second = placementGame(ONE_SHIP_RULES);

        sendChat(first.tokenA(), first.id(), "nur im ersten Spiel").statusCode(201);

        readChat(second.tokenA(), second.id()).statusCode(200).body("$", empty());
    }

    @ParameterizedTest(name = "Text \"{0}\"")
    @ValueSource(strings = {"", " ", "\t\n"})
    void blankMessageIsRejected(String text) {
        TestApi.TwoPlayerGame game = placementGame(ONE_SHIP_RULES);

        sendChat(game.tokenA(), game.id(), text).statusCode(400).body("code", is("INVALID_REQUEST"));
        readChat(game.tokenA(), game.id()).body("$", empty());
    }

    @Test
    void requestWithoutTextIsRejected() {
        TestApi.TwoPlayerGame game = placementGame(ONE_SHIP_RULES);

        as(game.tokenA()).body(Map.of())
                .when().post("/api/games/{id}/chat", game.id())
                .then().statusCode(400).body("code", is("INVALID_REQUEST"));
    }

    @Test
    void messageOfExactlyFiveHundredCharactersIsAccepted() {
        TestApi.TwoPlayerGame game = placementGame(ONE_SHIP_RULES);

        sendChat(game.tokenA(), game.id(), "x".repeat(500)).statusCode(201);
    }

    @Test
    void longerMessageIsRejected() {
        TestApi.TwoPlayerGame game = placementGame(ONE_SHIP_RULES);

        sendChat(game.tokenA(), game.id(), "x".repeat(501)).statusCode(400).body("code", is("INVALID_REQUEST"));
    }

    @Test
    void lengthLimitAppliesAfterTrimming() {
        TestApi.TwoPlayerGame game = placementGame(ONE_SHIP_RULES);

        sendChat(game.tokenA(), game.id(), "  " + "x".repeat(500) + "  ").statusCode(201);
    }

    @Test
    void outsiderCannotReadOrWrite() {
        TestApi.TwoPlayerGame game = placementGame(ONE_SHIP_RULES);
        String outsider = newPlayer("Cara");

        readChat(outsider, game.id()).statusCode(404).body("code", is("GAME_NOT_FOUND"));
        sendChat(outsider, game.id(), "hallo").statusCode(404).body("code", is("GAME_NOT_FOUND"));
        readChat(game.tokenA(), game.id()).body("$", empty());
    }

    @Test
    void unknownGameIsNotFound() {
        String token = newPlayer("Ada");

        readChat(token, UNKNOWN_GAME_ID).statusCode(404).body("code", is("GAME_NOT_FOUND"));
        sendChat(token, UNKNOWN_GAME_ID, "hallo").statusCode(404).body("code", is("GAME_NOT_FOUND"));
    }

    @Test
    void chatRequiresToken() {
        TestApi.TwoPlayerGame game = placementGame(ONE_SHIP_RULES);

        given().when().get("/api/games/{id}/chat", game.id()).then().statusCode(401);
        given().contentType(ContentType.JSON).body(Map.of("text", "hallo"))
                .when().post("/api/games/{id}/chat", game.id()).then().statusCode(401);
    }

    @Test
    void queryParameterTokenIsNotAcceptedForChat() {
        TestApi.TwoPlayerGame game = placementGame(ONE_SHIP_RULES);

        given().queryParam("access_token", game.tokenA())
                .when().get("/api/games/{id}/chat", game.id()).then().statusCode(401);
    }

    @Test
    void chatIsNotPossibleWhileWaitingForAnOpponent() {
        String token = newPlayer("Ada");
        String gameId = createLobby(token, ONE_SHIP_RULES);

        sendChat(token, gameId, "Ist da wer?").statusCode(409).body("code", is("WRONG_PHASE"));
        readChat(token, gameId).statusCode(200).body("$", empty());
    }

    @Test
    void chatStaysOpenAfterTheGameIsFinished() {
        TestApi.TwoPlayerGame game = placementGame(ONE_SHIP_RULES);
        as(game.tokenA()).when().post("/api/games/{id}/resign", game.id()).then().statusCode(200);

        sendChat(game.tokenB(), game.id(), "gg").statusCode(201);
        readChat(game.tokenA(), game.id()).body("text", contains("gg"));
    }
}

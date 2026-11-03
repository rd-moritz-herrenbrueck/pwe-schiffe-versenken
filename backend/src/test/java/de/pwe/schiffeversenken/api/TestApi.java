package de.pwe.schiffeversenken.api;

import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * REST-Aufrufe für Integrationstests. Die Fleet-Helfer erzeugen Flotten nach dem Muster
 * „Schiff i liegt horizontal ab (0, 2*i)“, das für die verwendeten Regeln gültig ist.
 */
public final class TestApi {

    /** 4x4, ein Zweier, kein Nochmal-Schießen, keine Berührung: Ein Spiel ist nach wenigen Schüssen entschieden. */
    public static final Map<String, Object> ONE_SHIP_RULES = Map.of(
            "boardWidth", 4, "boardHeight", 4, "fleet", List.of(2),
            "shootAgainOnHit", false, "shipsMayTouch", false);

    private TestApi() {
    }

    public static RequestSpecification as(String token) {
        return given().contentType(ContentType.JSON).header("Authorization", "Bearer " + token);
    }

    /** Legt einen Spieler an und liefert sein Token. */
    public static String newPlayer(String nickname) {
        return given().contentType(ContentType.JSON).body(Map.of("nickname", nickname))
                .when().post("/api/players")
                .then().statusCode(201)
                .extract().path("token");
    }

    /** Lobby mit Standardregeln, liefert die Spiel-ID. */
    public static String createLobby(String token) {
        return as(token).body(Map.of()).when().post("/api/lobbies")
                .then().statusCode(201)
                .extract().path("id");
    }

    public static String createLobby(String token, Map<String, Object> rules) {
        return as(token).body(Map.of("rules", rules)).when().post("/api/lobbies")
                .then().statusCode(201)
                .extract().path("id");
    }

    public static ValidatableResponse join(String token, String gameId) {
        return as(token).body(Map.of("gameId", gameId)).when().post("/api/lobbies/join").then();
    }

    public static ValidatableResponse joinByCode(String token, String inviteCode) {
        return as(token).body(Map.of("inviteCode", inviteCode)).when().post("/api/lobbies/join").then();
    }

    /** Private Lobby mit Standardregeln, liefert die Antwort ({@code id}, {@code inviteCode}). */
    public static ValidatableResponse createPrivateLobby(String token, Map<String, Object> rules) {
        return as(token).body(Map.of("rules", rules, "visibility", "PRIVATE")).when().post("/api/lobbies").then();
    }

    /** Spiel gegen die KI, liefert die Spiel-ID. */
    public static String createAiGame(String token, Map<String, Object> rules) {
        return as(token).body(Map.of("rules", rules, "vsAi", true)).when().post("/api/lobbies")
                .then().statusCode(201)
                .extract().path("id");
    }

    public static ValidatableResponse sendChat(String token, String gameId, String text) {
        return as(token).body(Map.of("text", text)).when().post("/api/games/{id}/chat", gameId).then();
    }

    public static ValidatableResponse readChat(String token, String gameId) {
        return as(token).when().get("/api/games/{id}/chat", gameId).then();
    }

    /** Zwei Spieler, Lobby erstellt und beigetreten, die Flotten fehlen noch (Phase {@code PLACEMENT}). */
    public static TwoPlayerGame placementGame(Map<String, Object> rules) {
        String tokenA = newPlayer("Ada");
        String tokenB = newPlayer("Bob");
        String gameId = createLobby(tokenA, rules);
        join(tokenB, gameId).statusCode(200);
        return new TwoPlayerGame(tokenA, tokenB, gameId);
    }

    public static ValidatableResponse placeFleet(String token, String gameId, List<Map<String, Object>> ships) {
        return as(token).body(Map.of("ships", ships)).when().put("/api/games/{id}/fleet", gameId).then();
    }

    public static ValidatableResponse fire(String token, String gameId, int x, int y) {
        return as(token).body(Map.of("x", x, "y", y)).when().post("/api/games/{id}/shots", gameId).then();
    }

    public static ValidatableResponse getGame(String token, String gameId) {
        return as(token).when().get("/api/games/{id}", gameId).then();
    }

    public static Map<String, Object> ship(int x, int y, int length, String orientation) {
        return Map.of("x", x, "y", y, "length", length, "orientation", orientation);
    }

    /** Schiff i liegt horizontal ab (0, 2*i). */
    public static List<Map<String, Object>> rowFleet(List<Integer> lengths) {
        List<Map<String, Object>> ships = new ArrayList<>();
        for (int i = 0; i < lengths.size(); i++) {
            ships.add(ship(0, 2 * i, lengths.get(i), "HORIZONTAL"));
        }
        return ships;
    }

    public static List<Map<String, Object>> standardFleet() {
        return rowFleet(List.of(5, 4, 3, 2));
    }

    /** Zwei Spieler mit ihren Tokens und das gemeinsame Spiel. */
    public record TwoPlayerGame(String tokenA, String tokenB, String id) {
    }

    /** Zwei Spieler, Lobby erstellt, beigetreten, beide Flotten gesetzt. */
    public static TwoPlayerGame runningGame(Map<String, Object> rules, List<Map<String, Object>> fleet) {
        String tokenA = newPlayer("Ada");
        String tokenB = newPlayer("Bob");
        String gameId = createLobby(tokenA, rules);
        join(tokenB, gameId).statusCode(200);
        placeFleet(tokenA, gameId, fleet).statusCode(200);
        placeFleet(tokenB, gameId, fleet).statusCode(200);
        return new TwoPlayerGame(tokenA, tokenB, gameId);
    }

    public static TwoPlayerGame runningStandardGame() {
        return runningGame(Map.of(
                "boardWidth", 10, "boardHeight", 10, "fleet", List.of(5, 4, 3, 2),
                "shootAgainOnHit", false, "shipsMayTouch", false), standardFleet());
    }
}

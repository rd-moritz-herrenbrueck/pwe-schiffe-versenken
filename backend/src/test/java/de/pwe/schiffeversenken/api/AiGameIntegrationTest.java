package de.pwe.schiffeversenken.api;

import de.pwe.schiffeversenken.api.SseTestClient.Event;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.pwe.schiffeversenken.api.TestApi.ONE_SHIP_RULES;
import static de.pwe.schiffeversenken.api.TestApi.as;
import static de.pwe.schiffeversenken.api.TestApi.createAiGame;
import static de.pwe.schiffeversenken.api.TestApi.fire;
import static de.pwe.schiffeversenken.api.TestApi.getGame;
import static de.pwe.schiffeversenken.api.TestApi.join;
import static de.pwe.schiffeversenken.api.TestApi.newPlayer;
import static de.pwe.schiffeversenken.api.TestApi.placeFleet;
import static de.pwe.schiffeversenken.api.TestApi.rowFleet;
import static de.pwe.schiffeversenken.api.TestApi.standardFleet;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Spiel gegen die KI: {@code POST /api/lobbies} mit {@code vsAi = true}. Das Spiel startet sofort in
 * {@code PLACEMENT}, die KI (Spieler B, Nickname {@code KI}) hat ihre Flotte schon platziert. Nach dem Zug des
 * Menschen schießt die KI in derselben Anfrage zurück, die Antwort enthält also schon ihren Schuss. Solche Spiele
 * stehen nicht in der Lobby-Liste, haben keinen Einladungscode und lassen sich nicht beitreten.
 *
 * <p>Im Testprofil ist {@code game.ai.seed} gesetzt: Gleiche Eingaben des Menschen führen dann zu gleichen
 * Flotten und Antworten der KI, unabhängig von Spiel-ID und Reihenfolge der Spiele.
 */
@QuarkusTest
class AiGameIntegrationTest {

    private static final Map<String, Object> STANDARD_RULES = Map.of(
            "boardWidth", 10, "boardHeight", 10, "fleet", List.of(5, 4, 3, 2),
            "shootAgainOnHit", false, "shipsMayTouch", false);

    /** 4x4 mit einem Zweier: Nach einem Schuss kann das Spiel noch nicht entschieden sein. */
    private static String runningOneShipAiGame(String token) {
        String gameId = createAiGame(token, ONE_SHIP_RULES);
        placeFleet(token, gameId, rowFleet(List.of(2))).statusCode(200);
        return gameId;
    }

    @Test
    void aiGameStartsInPlacementWithTheFleetOfTheAiReady() {
        as(newPlayer("Ada")).body(Map.of("rules", ONE_SHIP_RULES, "vsAi", true))
                .when().post("/api/lobbies")
                .then().statusCode(201)
                .body("phase", is("PLACEMENT"))
                .body("you", is("A"))
                .body("opponentNickname", is("KI"))
                .body("yourFleetReady", is(false))
                .body("opponentFleetReady", is(true))
                .body("inviteCode", nullValue())
                .body("rules.boardWidth", is(4));
    }

    @Test
    void aiGameWithoutRulesUsesStandardRules() {
        as(newPlayer("Ada")).body(Map.of("vsAi", true))
                .when().post("/api/lobbies")
                .then().statusCode(201)
                .body("rules.boardWidth", is(10))
                .body("rules.fleet", is(List.of(5, 4, 3, 2)));
    }

    @Test
    void aiGameIsNeitherListedNorJoinableAndIgnoresVisibility() {
        String gameId = createAiGame(newPlayer("Ada"), ONE_SHIP_RULES);
        String other = newPlayer("Bob");

        as(other).when().get("/api/lobbies").then().statusCode(200).body("id", not(hasItem(gameId)));
        join(other, gameId).statusCode(404).body("code", is("GAME_NOT_FOUND"));
    }

    @Test
    void aiGameWithPrivateVisibilityHasNoInviteCode() {
        as(newPlayer("Ada")).body(Map.of("rules", ONE_SHIP_RULES, "vsAi", true, "visibility", "PRIVATE"))
                .when().post("/api/lobbies")
                .then().statusCode(201)
                .body("inviteCode", nullValue());
    }

    @Test
    void aiGameAppearsInTheOwnGames() {
        String token = newPlayer("Ada");
        String gameId = createAiGame(token, ONE_SHIP_RULES);

        as(token).when().get("/api/games")
                .then().statusCode(200)
                .body("find { it.id == '%s' }.opponentNickname".formatted(gameId), is("KI"))
                .body("find { it.id == '%s' }.phase".formatted(gameId), is("PLACEMENT"));
    }

    @Test
    void invalidRulesAreRejectedForAiGamesToo() {
        as(newPlayer("Ada"))
                .body(Map.of("vsAi", true, "rules", Map.of("boardWidth", 26, "boardHeight", 10,
                        "fleet", List.of(5), "shootAgainOnHit", false, "shipsMayTouch", false)))
                .when().post("/api/lobbies")
                .then().statusCode(400).body("code", is("INVALID_RULE_SET"));
    }

    @Test
    void placingTheFleetStartsTheGameWithTheHumanToMove() {
        String token = newPlayer("Ada");
        String gameId = createAiGame(token, STANDARD_RULES);

        placeFleet(token, gameId, standardFleet())
                .statusCode(200)
                .body("phase", is("RUNNING"))
                .body("yourTurn", is(true))
                .body("opponentFleetReady", is(true))
                .body("enemyBoard.ships", empty())
                .body("enemyBoard.shots", empty())
                .body("ownBoard.shots", empty());
    }

    @Test
    void aiAnswersEveryShotInTheSameRequest() {
        String token = newPlayer("Ada");
        String gameId = runningOneShipAiGame(token);

        fire(token, gameId, 3, 3).statusCode(200)
                .body("game.yourTurn", is(true))
                .body("game.enemyBoard.shots.size()", is(1))
                .body("game.ownBoard.shots.size()", is(1));
        fire(token, gameId, 1, 1).statusCode(200)
                .body("game.yourTurn", is(true))
                .body("game.ownBoard.shots.size()", is(2));
    }

    @Test
    void aiNeverShootsTheSameCellTwiceAndTheGameEndsWithinTheBoardSize() {
        String token = newPlayer("Ada");
        String gameId = runningOneShipAiGame(token);
        JsonPath last = null;

        for (int i = 0; i < 16 && (last == null || !"FINISHED".equals(last.getString("game.phase"))); i++) {
            last = fire(token, gameId, i % 4, i / 4).statusCode(200).extract().jsonPath();
        }

        assertEquals("FINISHED", last.getString("game.phase"));
        assertTrue(Set.of("WON", "LOST").contains(last.getString("game.result")));
        List<Map<String, Integer>> aiShots = last.getList("game.ownBoard.shots");
        assertEquals(aiShots.size(), new HashSet<>(aiShots).size(), "KI hat ein Feld doppelt beschossen");
    }

    @Test
    void finishedAiGameRejectsFurtherShots() {
        String token = newPlayer("Ada");
        String gameId = runningOneShipAiGame(token);
        as(token).when().post("/api/games/{id}/resign", gameId).then().statusCode(200);

        fire(token, gameId, 0, 0).statusCode(409).body("code", is("WRONG_PHASE"));
    }

    @Test
    void resigningAgainstTheAiLosesTheGame() {
        String token = newPlayer("Ada");
        String gameId = runningOneShipAiGame(token);

        as(token).when().post("/api/games/{id}/resign", gameId)
                .then().statusCode(200)
                .body("phase", is("FINISHED"))
                .body("result", is("LOST"));
    }

    @Test
    void humanKeepsTheTurnAfterAHitWhenShootingAgainIsAllowed() {
        Map<String, Object> rules = Map.of("boardWidth", 6, "boardHeight", 6, "fleet", List.of(2, 2),
                "shootAgainOnHit", true, "shipsMayTouch", false);
        String token = newPlayer("Ada");
        String gameId = createAiGame(token, rules);
        placeFleet(token, gameId, rowFleet(List.of(2, 2))).statusCode(200);

        int aiShots = 0;
        for (int i = 0; i < 36; i++) {
            JsonPath response = fire(token, gameId, i % 6, i / 6).statusCode(200).extract().jsonPath();
            if ("FINISHED".equals(response.getString("game.phase"))) {
                return;
            }
            int aiShotsNow = response.getList("game.ownBoard.shots").size();
            if (Set.of("HIT", "SUNK").contains(response.getString("result"))) {
                assertEquals(aiShots, aiShotsNow, "Nach einem Treffer schießt der Mensch nochmal, die KI wartet");
            } else {
                assertTrue(aiShotsNow > aiShots, "Nach einem Fehlschuss antwortet die KI");
            }
            assertTrue(response.getBoolean("game.yourTurn"));
            aiShots = aiShotsNow;
        }
        fail("Das Spiel hätte nach höchstens 36 Schüssen enden müssen");
    }

    @Test
    void aiGameEventsFollowTheHumanShotWithTheAiShot() {
        String token = newPlayer("Ada");
        String gameId = runningOneShipAiGame(token);

        try (SseTestClient stream = SseTestClient.open("/api/games/" + gameId + "/events", token)) {
            fire(token, gameId, 3, 3).statusCode(200);

            List<Event> events = stream.next(4);
            assertEquals(List.of("SHOT", "TURN_CHANGED", "SHOT", "TURN_CHANGED"),
                    events.stream().map(Event::name).toList());
            assertEquals("A", events.get(0).json().getString("side"));
            assertEquals("B", events.get(1).json().getString("currentTurn"));
            assertEquals("B", events.get(2).json().getString("side"));
            assertEquals("A", events.get(3).json().getString("currentTurn"));
        }
    }

    @Test
    void sameHumanMovesLeadToTheSameGameBecauseOfTheFixedSeed() {
        List<JsonPath> first = playScriptedGame();
        List<JsonPath> second = playScriptedGame();

        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).getList("game.ownBoard.shots"), second.get(i).getList("game.ownBoard.shots"),
                    "Schüsse der KI nach Zug " + i);
            assertEquals(first.get(i).getList("game.enemyBoard.shots"),
                    second.get(i).getList("game.enemyBoard.shots"), "Ergebnisse des Menschen nach Zug " + i);
        }
    }

    @Test
    void aiGameCanBeReloadedAndContinued() {
        String token = newPlayer("Ada");
        String gameId = runningOneShipAiGame(token);
        fire(token, gameId, 3, 3).statusCode(200);

        getGame(token, gameId).statusCode(200)
                .body("phase", is("RUNNING"))
                .body("yourTurn", is(true))
                .body("ownBoard.shots.size()", is(1));
        fire(token, gameId, 1, 1).statusCode(200).body("game.ownBoard.shots.size()", is(2));
    }

    private static List<JsonPath> playScriptedGame() {
        String token = newPlayer("Ada");
        String gameId = createAiGame(token, STANDARD_RULES);
        placeFleet(token, gameId, standardFleet()).statusCode(200);
        List<JsonPath> states = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            var response = fire(token, gameId, i % 10, i / 10 * 3).extract();
            if (response.statusCode() != 200) {
                break;
            }
            states.add(response.jsonPath());
            if ("FINISHED".equals(response.path("game.phase"))) {
                break;
            }
        }
        return states;
    }
}

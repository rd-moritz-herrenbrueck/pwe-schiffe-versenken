package de.pwe.schiffeversenken.api;

import de.pwe.schiffeversenken.api.TestApi.TwoPlayerGame;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static de.pwe.schiffeversenken.api.TestApi.ONE_SHIP_RULES;
import static de.pwe.schiffeversenken.api.TestApi.as;
import static de.pwe.schiffeversenken.api.TestApi.createLobby;
import static de.pwe.schiffeversenken.api.TestApi.fire;
import static de.pwe.schiffeversenken.api.TestApi.getGame;
import static de.pwe.schiffeversenken.api.TestApi.join;
import static de.pwe.schiffeversenken.api.TestApi.newPlayer;
import static de.pwe.schiffeversenken.api.TestApi.placeFleet;
import static de.pwe.schiffeversenken.api.TestApi.rowFleet;
import static de.pwe.schiffeversenken.api.TestApi.runningGame;
import static de.pwe.schiffeversenken.api.TestApi.runningStandardGame;
import static de.pwe.schiffeversenken.api.TestApi.ship;
import static de.pwe.schiffeversenken.api.TestApi.standardFleet;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class GameResourceIntegrationTest {

    private static final String UNKNOWN_GAME_ID = "00000000-0000-0000-0000-000000000000";

    // --- Zugriff ---

    @Test
    void participantSeesOwnFleetButNotTheEnemyFleet() {
        TwoPlayerGame game = runningStandardGame();

        getGame(game.tokenA(), game.id())
                .statusCode(200)
                .body("phase", is("RUNNING"))
                .body("you", is("A"))
                .body("yourTurn", is(true))
                .body("yourFleetReady", is(true))
                .body("opponentFleetReady", is(true))
                .body("ownBoard.ships", hasSize(4))
                .body("enemyBoard.ships", empty())
                .body("enemyBoard.shots", empty());
    }

    @Test
    void secondPlayerWaitsForTheFirstShot() {
        TwoPlayerGame game = runningStandardGame();

        getGame(game.tokenB(), game.id())
                .statusCode(200)
                .body("you", is("B"))
                .body("yourTurn", is(false));
    }

    @Test
    void outsiderCannotSeeTheGame() {
        TwoPlayerGame game = runningStandardGame();

        getGame(newPlayer("Eve"), game.id())
                .statusCode(404)
                .body("code", is("GAME_NOT_FOUND"));
    }

    @Test
    void unknownGameIsNotFound() {
        getGame(newPlayer("Ada"), UNKNOWN_GAME_ID)
                .statusCode(404)
                .body("code", is("GAME_NOT_FOUND"));
    }

    @Test
    void outsiderCannotAct() {
        TwoPlayerGame game = runningStandardGame();
        String outsider = newPlayer("Eve");

        fire(outsider, game.id(), 0, 0).statusCode(404);
        as(outsider).when().post("/api/games/{id}/resign", game.id()).then().statusCode(404);
    }

    // --- Flotte ---

    @Test
    void placingBothFleetsStartsTheGame() {
        String tokenA = newPlayer("Ada");
        String tokenB = newPlayer("Bob");
        String gameId = createLobby(tokenA);
        join(tokenB, gameId).statusCode(200);

        placeFleet(tokenA, gameId, standardFleet())
                .statusCode(200)
                .body("phase", is("PLACEMENT"))
                .body("yourFleetReady", is(true))
                .body("opponentFleetReady", is(false));
        placeFleet(tokenB, gameId, standardFleet())
                .statusCode(200)
                .body("phase", is("RUNNING"))
                .body("yourTurn", is(false));
    }

    @Test
    void overlappingFleetIsRejectedAndNotStored() {
        String tokenA = newPlayer("Ada");
        String gameId = createLobby(tokenA);
        join(newPlayer("Bob"), gameId).statusCode(200);

        placeFleet(tokenA, gameId, List.of(
                ship(0, 0, 5, "HORIZONTAL"), ship(0, 0, 4, "VERTICAL"),
                ship(0, 6, 3, "HORIZONTAL"), ship(0, 8, 2, "HORIZONTAL")))
                .statusCode(400)
                .body("code", is("SHIPS_OVERLAP"));

        getGame(tokenA, gameId).body("yourFleetReady", is(false));
    }

    @Test
    void fleetNotMatchingTheRulesIsRejected() {
        String tokenA = newPlayer("Ada");
        String gameId = createLobby(tokenA);
        join(newPlayer("Bob"), gameId).statusCode(200);

        placeFleet(tokenA, gameId, rowFleet(List.of(5, 4, 3)))
                .statusCode(400)
                .body("code", is("FLEET_MISMATCH"));
    }

    @Test
    void incompleteShipIsRejected() {
        String tokenA = newPlayer("Ada");
        String gameId = createLobby(tokenA);
        join(newPlayer("Bob"), gameId).statusCode(200);

        placeFleet(tokenA, gameId, List.of(Map.of("x", 0, "y", 0, "length", 2)))
                .statusCode(400)
                .body("code", is("INVALID_REQUEST"));
    }

    @Test
    void placingFleetBeforeOpponentJoinedIsRejected() {
        String tokenA = newPlayer("Ada");
        String gameId = createLobby(tokenA);

        placeFleet(tokenA, gameId, standardFleet())
                .statusCode(409)
                .body("code", is("WRONG_PHASE"));
    }

    @Test
    void placingFleetTwiceIsRejected() {
        String tokenA = newPlayer("Ada");
        String gameId = createLobby(tokenA);
        join(newPlayer("Bob"), gameId).statusCode(200);
        placeFleet(tokenA, gameId, standardFleet()).statusCode(200);

        placeFleet(tokenA, gameId, standardFleet())
                .statusCode(409)
                .body("code", is("FLEET_ALREADY_PLACED"));
    }

    @Test
    void placingFleetInRunningGameIsRejected() {
        TwoPlayerGame game = runningStandardGame();

        placeFleet(game.tokenA(), game.id(), standardFleet())
                .statusCode(409)
                .body("code", is("WRONG_PHASE"));
    }

    // --- Schießen ---

    @Test
    void hitIsReturnedAndStoredForBothPlayers() {
        TwoPlayerGame game = runningStandardGame();

        fire(game.tokenA(), game.id(), 0, 0)
                .statusCode(200)
                .body("result", is("HIT"))
                .body("sunkShip", nullValue())
                .body("game.yourTurn", is(false))
                .body("game.enemyBoard.shots", hasSize(1))
                .body("game.enemyBoard.shots[0].x", is(0))
                .body("game.enemyBoard.shots[0].y", is(0))
                .body("game.enemyBoard.shots[0].hit", is(true))
                .body("game.enemyBoard.ships", empty());

        getGame(game.tokenB(), game.id())
                .body("yourTurn", is(true))
                .body("ownBoard.shots", hasSize(1))
                .body("ownBoard.shots[0].hit", is(true));
    }

    @Test
    void missIsReturned() {
        TwoPlayerGame game = runningStandardGame();

        fire(game.tokenA(), game.id(), 9, 9)
                .statusCode(200)
                .body("result", is("MISS"))
                .body("game.enemyBoard.shots[0].hit", is(false));
    }

    @Test
    void shootingOutOfTurnIsRejected() {
        TwoPlayerGame game = runningStandardGame();

        fire(game.tokenB(), game.id(), 0, 0)
                .statusCode(409)
                .body("code", is("NOT_YOUR_TURN"));
    }

    @Test
    void shotOutsideTheBoardIsRejected() {
        TwoPlayerGame game = runningStandardGame();

        fire(game.tokenA(), game.id(), 10, 0)
                .statusCode(400)
                .body("code", is("SHOT_OUT_OF_BOUNDS"));
    }

    @Test
    void shotWithoutCoordinatesIsRejected() {
        TwoPlayerGame game = runningStandardGame();

        as(game.tokenA()).body(Map.of("x", 1)).when().post("/api/games/{id}/shots", game.id())
                .then().statusCode(400)
                .body("code", is("INVALID_REQUEST"));
    }

    @Test
    void shotOnAlreadyShotCellIsRejected() {
        TwoPlayerGame game = runningStandardGame();
        fire(game.tokenA(), game.id(), 9, 9).statusCode(200);
        fire(game.tokenB(), game.id(), 9, 9).statusCode(200);

        fire(game.tokenA(), game.id(), 9, 9)
                .statusCode(409)
                .body("code", is("CELL_ALREADY_SHOT"));
    }

    @Test
    void sinkingTheLastShipWinsTheGame() {
        TwoPlayerGame game = runningGame(ONE_SHIP_RULES, rowFleet(List.of(2)));
        fire(game.tokenA(), game.id(), 0, 0).statusCode(200);
        fire(game.tokenB(), game.id(), 3, 3).statusCode(200);

        fire(game.tokenA(), game.id(), 1, 0)
                .statusCode(200)
                .body("result", is("WIN"))
                .body("sunkShip.x", is(0))
                .body("sunkShip.y", is(0))
                .body("sunkShip.length", is(2))
                .body("sunkShip.orientation", is("HORIZONTAL"))
                .body("game.phase", is("FINISHED"))
                .body("game.result", is("WON"))
                .body("game.enemyBoard.ships", hasSize(1));

        getGame(game.tokenB(), game.id())
                .body("phase", is("FINISHED"))
                .body("result", is("LOST"));
        fire(game.tokenB(), game.id(), 2, 2)
                .statusCode(409)
                .body("code", is("WRONG_PHASE"));
    }

    // --- Aufgeben ---

    @Test
    void resigningLetsTheOpponentWin() {
        String tokenA = newPlayer("Ada");
        String tokenB = newPlayer("Bob");
        String gameId = createLobby(tokenA);
        join(tokenB, gameId).statusCode(200);

        as(tokenA).when().post("/api/games/{id}/resign", gameId)
                .then().statusCode(200)
                .body("phase", is("FINISHED"))
                .body("result", is("LOST"));

        getGame(tokenB, gameId).body("result", is("WON"));
    }

    @Test
    void resigningWhileWaitingIsRejected() {
        String tokenA = newPlayer("Ada");
        String gameId = createLobby(tokenA);

        as(tokenA).when().post("/api/games/{id}/resign", gameId)
                .then().statusCode(409)
                .body("code", is("WRONG_PHASE"));
    }

    // --- Eigene Spiele ---

    @Test
    void ownGamesAreListedWithTheMostRecentlyActiveFirst() {
        String tokenA = newPlayer("Ada");
        String tokenB = newPlayer("Bob");
        String first = createLobby(tokenA);
        String second = createLobby(tokenA);
        createLobby(newPlayer("Eve"));
        join(tokenB, first).statusCode(200);

        as(tokenA).when().get("/api/games")
                .then().statusCode(200)
                .body("id", contains(first, second))
                .body("[0].phase", is("PLACEMENT"))
                .body("[0].opponentNickname", is("Bob"))
                .body("[0].yourTurn", is(false))
                .body("[0].result", nullValue())
                .body("[1].phase", is("WAITING"))
                .body("[1].opponentNickname", nullValue());
    }

    @Test
    void finishedGamesStayInTheListWithTheirResult() {
        TwoPlayerGame game = runningStandardGame();
        as(game.tokenB()).when().post("/api/games/{id}/resign", game.id()).then().statusCode(200);

        as(game.tokenA()).when().get("/api/games")
                .then().statusCode(200)
                .body("find { it.id == '%s' }.phase".formatted(game.id()), is("FINISHED"))
                .body("find { it.id == '%s' }.result".formatted(game.id()), is("WON"));
    }
}

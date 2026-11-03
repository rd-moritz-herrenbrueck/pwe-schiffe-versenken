package de.pwe.schiffeversenken.service;

import de.pwe.schiffeversenken.domain.Coordinate;
import de.pwe.schiffeversenken.domain.Game;
import de.pwe.schiffeversenken.domain.GamePhase;
import de.pwe.schiffeversenken.domain.Orientation;
import de.pwe.schiffeversenken.domain.RuleSet;
import de.pwe.schiffeversenken.domain.ShipPlacement;
import de.pwe.schiffeversenken.domain.ShotOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.UUID;

import static de.pwe.schiffeversenken.domain.PlayerSide.A;
import static de.pwe.schiffeversenken.domain.PlayerSide.B;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Welche Ereignisse eine Spielaktion auslöst und in welcher Reihenfolge. Die Spiele laufen mit echter Domänenlogik.
 * Schiffe liegen horizontal ab (0, 0) und (0, 2), beide Spieler haben dieselbe Aufstellung.
 */
class GameEventsTest {

    private static final UUID GAME_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final ShipPlacement FIRST_SHIP = new ShipPlacement(new Coordinate(0, 0), 2, Orientation.HORIZONTAL);
    private static final ShipPlacement SECOND_SHIP = new ShipPlacement(new Coordinate(0, 2), 2,
            Orientation.HORIZONTAL);
    private static final Coordinate WATER = new Coordinate(5, 5);

    private static RuleSet twoShips(boolean shootAgainOnHit) {
        return new RuleSet(6, 6, List.of(2, 2), shootAgainOnHit, false);
    }

    private static RuleSet oneShip() {
        return new RuleSet(6, 6, List.of(2), false, false);
    }

    private static Game gameInPlacement(RuleSet rules) {
        Game game = Game.create(rules);
        game.join();
        return game;
    }

    private static Game runningGame(RuleSet rules) {
        Game game = gameInPlacement(rules);
        List<ShipPlacement> fleet = rules.fleet().size() == 1 ? List.of(FIRST_SHIP) : List.of(FIRST_SHIP, SECOND_SHIP);
        game.placeFleet(A, fleet);
        game.placeFleet(B, fleet);
        return game;
    }

    // --- Flotte ---

    @Test
    void firstFleetOnlyAnnouncesTheFleet() {
        Game game = gameInPlacement(oneShip());
        game.placeFleet(A, List.of(FIRST_SHIP));

        assertEquals(List.of(GameEvent.fleetReady(GAME_ID, A)), GameEvents.afterFleetPlaced(GAME_ID, A, game));
    }

    @Test
    void secondFleetStartsTheGameWithPlayerAToMove() {
        Game game = gameInPlacement(oneShip());
        game.placeFleet(A, List.of(FIRST_SHIP));
        game.placeFleet(B, List.of(FIRST_SHIP));

        assertEquals(List.of(GameEvent.fleetReady(GAME_ID, B), GameEvent.gameStarted(GAME_ID, A)),
                GameEvents.afterFleetPlaced(GAME_ID, B, game));
    }

    // --- Schüsse ---

    @Test
    void missPassesTheTurnToTheOpponent() {
        Game game = runningGame(twoShips(false));
        ShotOutcome outcome = game.fire(A, WATER);

        assertEquals(List.of(GameEvent.shot(GAME_ID, A, outcome), GameEvent.turnChanged(GAME_ID, B)),
                GameEvents.afterShot(GAME_ID, A, outcome, game));
    }

    @Test
    void missByPlayerBPassesTheTurnBackToA() {
        Game game = runningGame(twoShips(false));
        game.fire(A, WATER);
        ShotOutcome outcome = game.fire(B, WATER);

        assertEquals(List.of(GameEvent.shot(GAME_ID, B, outcome), GameEvent.turnChanged(GAME_ID, A)),
                GameEvents.afterShot(GAME_ID, B, outcome, game));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void hitChangesTheTurnOnlyWithoutShootAgain(boolean shootAgainOnHit) {
        Game game = runningGame(twoShips(shootAgainOnHit));
        ShotOutcome outcome = game.fire(A, new Coordinate(0, 0));

        List<GameEvent> expected = shootAgainOnHit
                ? List.of(GameEvent.shot(GAME_ID, A, outcome))
                : List.of(GameEvent.shot(GAME_ID, A, outcome), GameEvent.turnChanged(GAME_ID, B));
        assertEquals(expected, GameEvents.afterShot(GAME_ID, A, outcome, game));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sunkShipIsAnnouncedAndChangesTheTurnOnlyWithoutShootAgain(boolean shootAgainOnHit) {
        Game game = runningGame(twoShips(shootAgainOnHit));
        game.fire(A, new Coordinate(0, 0));
        if (!shootAgainOnHit) {
            game.fire(B, WATER);
        }
        ShotOutcome outcome = game.fire(A, new Coordinate(1, 0));

        List<GameEvent> events = GameEvents.afterShot(GAME_ID, A, outcome, game);

        assertEquals(GameEvent.shot(GAME_ID, A, outcome), events.get(0));
        assertEquals(FIRST_SHIP, events.get(0).sunkShip());
        assertEquals(shootAgainOnHit ? 1 : 2, events.size());
        if (!shootAgainOnHit) {
            assertEquals(GameEvent.turnChanged(GAME_ID, B), events.get(1));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void winningShotFinishesTheGameWithoutTurnChange(boolean shootAgainOnHit) {
        RuleSet rules = new RuleSet(6, 6, List.of(2), shootAgainOnHit, false);
        Game game = runningGame(rules);
        game.fire(A, new Coordinate(0, 0));
        if (!shootAgainOnHit) {
            game.fire(B, WATER);
        }
        ShotOutcome outcome = game.fire(A, new Coordinate(1, 0));

        assertEquals(List.of(GameEvent.shot(GAME_ID, A, outcome),
                        GameEvent.gameFinished(GAME_ID, GamePhase.FINISHED, A)),
                GameEvents.afterShot(GAME_ID, A, outcome, game));
    }

    // --- Ende ---

    @Test
    void resignFinishesTheGameWithTheOpponentAsWinner() {
        Game game = runningGame(oneShip());
        game.resign(B);

        assertEquals(List.of(GameEvent.gameFinished(GAME_ID, GamePhase.FINISHED, A)),
                GameEvents.afterResign(GAME_ID, game));
    }

    @Test
    void resignDuringPlacementFinishesTheGame() {
        Game game = gameInPlacement(oneShip());
        game.resign(A);

        assertEquals(List.of(GameEvent.gameFinished(GAME_ID, GamePhase.FINISHED, B)),
                GameEvents.afterResign(GAME_ID, game));
    }

    @Test
    void abandonedGameHasNoWinner() {
        assertEquals(List.of(GameEvent.gameFinished(GAME_ID, GamePhase.ABANDONED, null)),
                GameEvents.afterAbandon(GAME_ID));
    }
}

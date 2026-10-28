package de.pwe.schiffeversenken.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.pwe.schiffeversenken.domain.Coordinate;
import de.pwe.schiffeversenken.domain.Game;
import de.pwe.schiffeversenken.domain.GamePhase;
import de.pwe.schiffeversenken.domain.Orientation;
import de.pwe.schiffeversenken.domain.RuleSet;
import de.pwe.schiffeversenken.domain.ShipPlacement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static de.pwe.schiffeversenken.domain.PlayerSide.A;
import static de.pwe.schiffeversenken.domain.PlayerSide.B;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Speicherformat eines Spiels in {@link GameEntity}, ohne Datenbank. Das JSON-Format ist bewusst festgeschrieben:
 * Gespeicherte Spiele müssen auch nach Refactorings der Domäne lesbar bleiben.
 */
class GameStateMapperTest {

    private static final RuleSet ONE_SHIP = new RuleSet(4, 4, List.of(2), false, false);
    private static final List<ShipPlacement> ONE_SHIP_FLEET =
            List.of(new ShipPlacement(new Coordinate(0, 0), 2, Orientation.HORIZONTAL));
    private static final String ONE_SHIP_RULES_JSON = """
            {"boardWidth": 4, "boardHeight": 4, "fleet": [2], "shootAgainOnHit": false, "shipsMayTouch": false}
            """;
    private static final String ONE_SHIP_STATE_JSON = """
            {"boards": {
              "A": {"ships": [{"start": {"x": 0, "y": 0}, "length": 2, "orientation": "HORIZONTAL"}],
                    "shotsReceived": []},
              "B": {"ships": [{"start": {"x": 0, "y": 0}, "length": 2, "orientation": "HORIZONTAL"}],
                    "shotsReceived": [{"x": 3, "y": 3}]}
            }}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GameStateMapper mapper = new GameStateMapper(objectMapper);

    static Stream<Arguments> games() {
        Game placement = placementGame(RuleSet.standard());
        placement.placeFleet(B, List.of(
                new ShipPlacement(new Coordinate(0, 0), 5, Orientation.HORIZONTAL),
                new ShipPlacement(new Coordinate(9, 2), 4, Orientation.VERTICAL),
                new ShipPlacement(new Coordinate(0, 9), 3, Orientation.HORIZONTAL),
                new ShipPlacement(new Coordinate(4, 4), 2, Orientation.VERTICAL)));

        Game running = runningGame(new RuleSet(25, 25, List.of(25, 12, 2), true, true));
        running.fire(A, new Coordinate(0, 0));
        running.fire(A, new Coordinate(24, 24));
        running.fire(B, new Coordinate(3, 0));

        Game abandoned = runningGame(ONE_SHIP);
        abandoned.abandon();

        return Stream.of(
                Arguments.of("WAITING", Game.create(RuleSet.standard())),
                Arguments.of("PLACEMENT", placement),
                Arguments.of("RUNNING", running),
                Arguments.of("FINISHED", finishedGameWonByA()),
                Arguments.of("ABANDONED", abandoned));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("games")
    void gameSurvivesRoundTripThroughTheEntity(String phase, Game original) {
        GameEntity entity = new GameEntity();

        mapper.write(original, entity);

        assertEquals(original.snapshot(), mapper.read(entity).snapshot());
    }

    @Test
    void phaseTurnAndWinnerAreStoredInQueryableColumns() {
        GameEntity running = new GameEntity();
        mapper.write(runningGame(ONE_SHIP), running);
        GameEntity finished = new GameEntity();
        mapper.write(finishedGameWonByA(), finished);

        assertEquals(GamePhase.RUNNING, running.status);
        assertEquals(A, running.currentTurn);
        assertNull(running.winner);
        assertEquals(GamePhase.FINISHED, finished.status);
        assertNull(finished.currentTurn);
        assertEquals(A, finished.winner);
    }

    @Test
    void rulesAreStoredInAStableJsonFormat() throws Exception {
        GameEntity entity = new GameEntity();

        mapper.write(Game.create(ONE_SHIP), entity);

        assertEquals(objectMapper.readTree(ONE_SHIP_RULES_JSON), objectMapper.readTree(entity.rules));
    }

    @Test
    void boardsAreStoredInAStableJsonFormat() throws Exception {
        Game game = runningGame(ONE_SHIP);
        game.fire(A, new Coordinate(3, 3));
        GameEntity entity = new GameEntity();

        mapper.write(game, entity);

        assertEquals(objectMapper.readTree(ONE_SHIP_STATE_JSON), objectMapper.readTree(entity.state));
    }

    @Test
    void gameIsReadFromStoredJsonAndColumns() {
        GameEntity entity = new GameEntity();
        entity.status = GamePhase.RUNNING;
        entity.currentTurn = B;
        entity.rules = ONE_SHIP_RULES_JSON;
        entity.state = ONE_SHIP_STATE_JSON;

        Game expected = runningGame(ONE_SHIP);
        expected.fire(A, new Coordinate(3, 3));
        assertEquals(expected.snapshot(), mapper.read(entity).snapshot());
    }

    // --- Hilfsmethoden ---

    private static Game placementGame(RuleSet rules) {
        Game game = Game.create(rules);
        game.join();
        return game;
    }

    /** Schiff i liegt horizontal ab (0, 2*i), für beide Spieler gleich. */
    private static Game runningGame(RuleSet rules) {
        List<ShipPlacement> fleet = new ArrayList<>();
        for (int i = 0; i < rules.fleet().size(); i++) {
            fleet.add(new ShipPlacement(new Coordinate(0, 2 * i), rules.fleet().get(i), Orientation.HORIZONTAL));
        }
        Game game = placementGame(rules);
        game.placeFleet(A, fleet);
        game.placeFleet(B, fleet);
        return game;
    }

    private static Game finishedGameWonByA() {
        Game game = placementGame(ONE_SHIP);
        game.placeFleet(A, ONE_SHIP_FLEET);
        game.placeFleet(B, ONE_SHIP_FLEET);
        game.fire(A, new Coordinate(0, 0));
        game.fire(B, new Coordinate(3, 3));
        game.fire(A, new Coordinate(1, 0));
        return game;
    }
}

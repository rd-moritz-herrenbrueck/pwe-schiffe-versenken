package de.pwe.schiffeversenken.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * KI-Strategie Hunt/Target. Der Zufall ist mit festem Seed injiziert, alle Tests sind deterministisch.
 */
class HuntTargetStrategyTest {

    private static final int SEEDS = 25;

    private static AiStrategy strategy(long seed) {
        return new HuntTargetStrategy(new Random(seed));
    }

    private static ShotOutcome miss(int x, int y) {
        return new ShotOutcome(new Coordinate(x, y), ShotResult.MISS, Optional.empty());
    }

    private static ShotOutcome hit(int x, int y) {
        return new ShotOutcome(new Coordinate(x, y), ShotResult.HIT, Optional.empty());
    }

    private static ShotOutcome sunk(int x, int y, ShipPlacement ship) {
        return new ShotOutcome(new Coordinate(x, y), ShotResult.SUNK, Optional.of(ship));
    }

    private static Set<Coordinate> cells(int... xy) {
        Set<Coordinate> cells = new HashSet<>();
        for (int i = 0; i < xy.length; i += 2) {
            cells.add(new Coordinate(xy[i], xy[i + 1]));
        }
        return cells;
    }

    // --- Platzierung ---

    @ParameterizedTest(name = "{0}")
    @MethodSource("de.pwe.schiffeversenken.domain.TestFleets#ruleVariants")
    void randomFleetIsValidForEveryRuleVariant(RuleSet rules) {
        for (long seed = 0; seed < SEEDS; seed++) {
            List<ShipPlacement> fleet = strategy(seed).placeFleet(rules);

            Board.withFleet(rules, fleet);
        }
    }

    @Test
    void sameSeedPlacesTheSameFleet() {
        RuleSet rules = RuleSet.standard();

        assertEquals(strategy(7).placeFleet(rules), strategy(7).placeFleet(rules));
    }

    @Test
    void differentSeedsPlaceDifferentFleets() {
        RuleSet rules = RuleSet.standard();

        long distinct = IntStream.range(0, SEEDS)
                .mapToObj(seed -> strategy(seed).placeFleet(rules))
                .distinct().count();

        assertTrue(distinct > SEEDS / 2, "Nur " + distinct + " verschiedene Flotten bei " + SEEDS + " Seeds");
    }

    @Test
    void placementUsesBothOrientations() {
        RuleSet rules = RuleSet.standard();

        Set<Orientation> orientations = new HashSet<>();
        for (long seed = 0; seed < SEEDS; seed++) {
            strategy(seed).placeFleet(rules).forEach(ship -> orientations.add(ship.orientation()));
        }

        assertEquals(Set.of(Orientation.HORIZONTAL, Orientation.VERTICAL), orientations);
    }

    @Test
    void fleetOnATightBoardIsStillPlaced() {
        RuleSet rules = new RuleSet(4, 4, List.of(3, 3, 3), false, true);

        for (long seed = 0; seed < SEEDS; seed++) {
            Board.withFleet(rules, strategy(seed).placeFleet(rules));
        }
    }

    @Test
    void fleetThatFitsOnlyWithCornerContactIsStillPlaced() {
        RuleSet rules = new RuleSet(5, 5, List.of(5, 4, 3, 2), false, false);

        for (long seed = 0; seed < SEEDS; seed++) {
            Board.withFleet(rules, strategy(seed).placeFleet(rules));
        }
    }

    // --- Schießen: Hunt ---

    @ParameterizedTest(name = "{0}")
    @MethodSource("de.pwe.schiffeversenken.domain.TestFleets#ruleVariants")
    void aiSinksTheWholeFleetWithoutShootingTwiceOrOffTheBoard(RuleSet rules) {
        for (long seed = 0; seed < 10; seed++) {
            AiStrategy ai = strategy(seed);
            Board board = Board.withFleet(rules, ai.placeFleet(rules));
            List<ShotOutcome> history = new ArrayList<>();
            Set<Coordinate> shot = new HashSet<>();
            int cellCount = rules.boardWidth() * rules.boardHeight();

            ShotOutcome outcome = null;
            for (int i = 0; i < cellCount && (outcome == null || outcome.result() != ShotResult.WIN); i++) {
                Coordinate target = ai.nextShot(rules, List.copyOf(history));
                assertTrue(rules.isOnBoard(target), "Schuss außerhalb des Felds: " + target);
                assertTrue(shot.add(target), "Feld doppelt beschossen: " + target);
                outcome = board.receiveShot(target);
                history.add(outcome);
            }

            assertEquals(ShotResult.WIN, outcome.result(), "Seed " + seed + " hat nicht gewonnen");
        }
    }

    @Test
    void sameSeedAndHistoryGiveTheSameShot() {
        RuleSet rules = RuleSet.standard();
        List<ShotOutcome> history = List.of(miss(1, 1), hit(5, 5));

        assertEquals(strategy(3).nextShot(rules, history), strategy(3).nextShot(rules, history));
    }

    @Test
    void lastRemainingCellIsChosen() {
        RuleSet rules = new RuleSet(2, 2, List.of(2), false, false);
        List<ShotOutcome> history = List.of(miss(0, 0), miss(1, 0), miss(0, 1));

        assertEquals(new Coordinate(1, 1), strategy(0).nextShot(rules, history));
    }

    @Test
    void shootingOnAFullyShotBoardIsAnError() {
        RuleSet rules = new RuleSet(2, 2, List.of(2), false, false);
        List<ShotOutcome> history = List.of(miss(0, 0), miss(1, 0), miss(0, 1), miss(1, 1));

        assertThrows(IllegalStateException.class, () -> strategy(0).nextShot(rules, history));
    }

    @Test
    void huntingSpreadsShotsOverTheBoard() {
        RuleSet rules = RuleSet.standard();

        Set<Coordinate> firstShots = new HashSet<>();
        for (long seed = 0; seed < SEEDS; seed++) {
            firstShots.add(strategy(seed).nextShot(rules, List.of()));
        }

        assertTrue(firstShots.size() > SEEDS / 2, "Erste Schüsse: " + firstShots);
    }

    @Test
    void huntTargetNeedsFarFewerShotsThanBlindFiring() {
        RuleSet rules = RuleSet.standard();
        int games = 40;
        int totalShots = 0;

        for (long seed = 0; seed < games; seed++) {
            AiStrategy ai = strategy(seed);
            Board board = Board.withFleet(rules, strategy(1000 + seed).placeFleet(rules));
            List<ShotOutcome> history = new ArrayList<>();
            ShotOutcome outcome;
            do {
                outcome = board.receiveShot(ai.nextShot(rules, List.copyOf(history)));
                history.add(outcome);
            } while (outcome.result() != ShotResult.WIN);
            totalShots += history.size();
        }

        // Blindes Schießen braucht auf 10x10 mit 14 Schiffsfeldern im Schnitt etwa 94 Schüsse.
        double average = (double) totalShots / games;
        assertTrue(average < 85, "Durchschnitt " + average + " Schüsse");
    }

    // --- Schießen: Target ---

    static Stream<Arguments> singleHits() {
        return Stream.of(
                Arguments.of(4, 4, cells(4, 3, 4, 5, 3, 4, 5, 4)),
                Arguments.of(0, 0, cells(1, 0, 0, 1)),
                Arguments.of(9, 9, cells(8, 9, 9, 8)),
                Arguments.of(0, 5, cells(0, 4, 0, 6, 1, 5)),
                Arguments.of(9, 0, cells(8, 0, 9, 1)));
    }

    @ParameterizedTest(name = "Treffer auf ({0}, {1})")
    @MethodSource("singleHits")
    void afterASingleHitTheStrategyShootsAnAdjacentCell(int x, int y, Set<Coordinate> neighbours) {
        RuleSet rules = RuleSet.standard();

        for (long seed = 0; seed < SEEDS; seed++) {
            Coordinate next = strategy(seed).nextShot(rules, List.of(miss(7, 7), hit(x, y)));

            assertTrue(neighbours.contains(next), "Seed " + seed + ": " + next + " liegt nicht neben " + x + "," + y);
        }
    }

    @Test
    void afterTwoHitsInARowTheStrategyContinuesTheLine() {
        RuleSet rules = RuleSet.standard();

        for (long seed = 0; seed < SEEDS; seed++) {
            assertTrue(cells(2, 4, 5, 4).contains(strategy(seed).nextShot(rules, List.of(hit(3, 4), hit(4, 4)))));
            assertTrue(cells(4, 2, 4, 5).contains(strategy(seed).nextShot(rules, List.of(hit(4, 3), hit(4, 4)))));
        }
    }

    @Test
    void lineIsContinuedAtTheOtherEndWhenOneEndIsBlocked() {
        RuleSet rules = RuleSet.standard();
        List<ShotOutcome> history = List.of(hit(3, 4), hit(4, 4), miss(5, 4));

        for (long seed = 0; seed < SEEDS; seed++) {
            assertEquals(new Coordinate(2, 4), strategy(seed).nextShot(rules, history));
        }
    }

    @Test
    void lineEndingAtTheBoardEdgeIsContinuedOnTheOtherSide() {
        RuleSet rules = RuleSet.standard();
        List<ShotOutcome> history = List.of(hit(8, 0), hit(9, 0));

        for (long seed = 0; seed < SEEDS; seed++) {
            assertEquals(new Coordinate(7, 0), strategy(seed).nextShot(rules, history));
        }
    }

    @Test
    void singleHitWithThreeMissesAroundLeavesOnlyOneNeighbour() {
        RuleSet rules = RuleSet.standard();
        List<ShotOutcome> history = List.of(hit(4, 4), miss(3, 4), miss(5, 4), miss(4, 3));

        for (long seed = 0; seed < SEEDS; seed++) {
            assertEquals(new Coordinate(4, 5), strategy(seed).nextShot(rules, history));
        }
    }

    @Test
    void afterSinkingAShipTheStrategyDoesNotKeepShootingAtIt() {
        RuleSet rules = new RuleSet(10, 10, List.of(5, 4, 3, 2), false, true);
        ShipPlacement destroyer = new ShipPlacement(new Coordinate(4, 4), 2, Orientation.HORIZONTAL);
        List<ShotOutcome> history = List.of(hit(4, 4), sunk(5, 4, destroyer));

        for (long seed = 0; seed < SEEDS; seed++) {
            Coordinate next = strategy(seed).nextShot(rules, history);

            assertNotEquals(new Coordinate(4, 4), next);
            assertNotEquals(new Coordinate(5, 4), next);
        }
    }

    @Test
    void afterSinkingAShipItsEdgeNeighboursAreSkippedWhenShipsMayNotTouch() {
        RuleSet rules = RuleSet.standard();
        ShipPlacement destroyer = new ShipPlacement(new Coordinate(4, 4), 2, Orientation.HORIZONTAL);
        List<ShotOutcome> history = List.of(hit(4, 4), sunk(5, 4, destroyer));
        Set<Coordinate> edgeNeighbours = Set.of(
                new Coordinate(3, 4), new Coordinate(6, 4),
                new Coordinate(4, 3), new Coordinate(5, 3),
                new Coordinate(4, 5), new Coordinate(5, 5));
        Set<Coordinate> corners = Set.of(
                new Coordinate(3, 3), new Coordinate(6, 3),
                new Coordinate(3, 5), new Coordinate(6, 5));

        Set<Coordinate> shots = new HashSet<>();
        for (long seed = 0; seed < 500; seed++) {
            Coordinate next = strategy(seed).nextShot(rules, history);

            assertTrue(!edgeNeighbours.contains(next), "Seed " + seed + ": " + next + " grenzt an ein versenktes Schiff");
            shots.add(next);
        }
        assertTrue(shots.stream().anyMatch(corners::contains), "Felder über Eck dürfen Schiffe enthalten");
    }

    @Test
    void hitsOfAnAlreadySunkShipAreNotTargetedAgain() {
        RuleSet rules = new RuleSet(10, 10, List.of(5, 4, 3, 2), false, true);
        ShipPlacement destroyer = new ShipPlacement(new Coordinate(4, 4), 2, Orientation.HORIZONTAL);
        List<ShotOutcome> history = List.of(hit(4, 4), sunk(5, 4, destroyer), hit(0, 9));

        for (long seed = 0; seed < SEEDS; seed++) {
            assertTrue(cells(0, 8, 1, 9).contains(strategy(seed).nextShot(rules, history)),
                    "Seed " + seed + ": Der offene Treffer bei (0, 9) wird verfolgt");
        }
    }
}

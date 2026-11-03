package de.pwe.schiffeversenken.service;

import de.pwe.schiffeversenken.domain.Game;
import de.pwe.schiffeversenken.domain.GamePhase;
import de.pwe.schiffeversenken.domain.PlayerSide;
import de.pwe.schiffeversenken.domain.RuleSet;
import de.pwe.schiffeversenken.domain.Coordinate;
import de.pwe.schiffeversenken.domain.Orientation;
import de.pwe.schiffeversenken.domain.ShipPlacement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiOpponentTest {

    private static final UUID GAME_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static AiOpponent ai(Long seed) {
        AiOpponent ai = new AiOpponent();
        ai.seed = Optional.ofNullable(seed);
        return ai;
    }

    private static Game gameAgainstAi(AiOpponent ai, RuleSet rules) {
        Game game = Game.create(rules);
        game.join();
        game.placeFleet(PlayerSide.B, ai.placeFleet(rules));
        List<ShipPlacement> fleet = new ArrayList<>();
        for (int i = 0; i < rules.fleet().size(); i++) {
            fleet.add(new ShipPlacement(new Coordinate(0, 2 * i), rules.fleet().get(i), Orientation.HORIZONTAL));
        }
        game.placeFleet(PlayerSide.A, fleet);
        return game;
    }

    @Test
    void doesNothingWhileTheHumanIsToMove() {
        AiOpponent ai = ai(1L);
        Game game = gameAgainstAi(ai, RuleSet.standard());

        assertEquals(List.of(), ai.respond(GAME_ID, game));
        assertEquals(PlayerSide.A, game.currentTurn().orElseThrow());
    }

    @Test
    void shootsOnceAndHandsTheTurnBackAfterTheHumanMissed() {
        AiOpponent ai = ai(1L);
        Game game = gameAgainstAi(ai, RuleSet.standard());
        game.fire(PlayerSide.A, new Coordinate(9, 9));

        List<GameEvent> events = ai.respond(GAME_ID, game);

        assertEquals(GameEventType.SHOT, events.getFirst().type());
        assertEquals(PlayerSide.B, events.getFirst().side());
        assertEquals(GameEventType.TURN_CHANGED, events.getLast().type());
        assertEquals(PlayerSide.A, game.currentTurn().orElseThrow());
    }

    @Test
    void keepsShootingAfterAHitWhenShootingAgainIsAllowed() {
        RuleSet rules = new RuleSet(6, 6, List.of(2, 2), true, false);
        AiOpponent ai = ai(5L);
        Game game = gameAgainstAi(ai, rules);
        for (int i = 0; i < 36 && game.currentTurn().filter(turn -> turn == PlayerSide.A).isPresent(); i++) {
            game.fire(PlayerSide.A, new Coordinate(i % 6, i / 6));
        }
        assertEquals(PlayerSide.B, game.currentTurn().orElseThrow());

        List<GameEvent> events = ai.respond(GAME_ID, game);

        long shots = events.stream().filter(event -> event.type() == GameEventType.SHOT).count();
        assertTrue(shots >= 1);
        assertTrue(game.phase() == GamePhase.FINISHED || game.currentTurn().orElseThrow() == PlayerSide.A);
    }

    @Test
    void sameSeedGivesTheSameFleetAndTheSameShotsInEveryGame() {
        AiOpponent ai = ai(42L);
        Game first = gameAgainstAi(ai, RuleSet.standard());
        Game second = gameAgainstAi(ai, RuleSet.standard());
        first.fire(PlayerSide.A, new Coordinate(9, 9));
        second.fire(PlayerSide.A, new Coordinate(9, 9));

        ai.respond(GAME_ID, first);
        ai.respond(UUID.randomUUID(), second);

        assertEquals(first.snapshot(), second.snapshot());
    }

    @Test
    void differentSeedsPlayDifferently() {
        Game first = gameAgainstAi(ai(1L), RuleSet.standard());
        Game second = gameAgainstAi(ai(2L), RuleSet.standard());

        assertNotEquals(first.snapshot(), second.snapshot());
    }
}

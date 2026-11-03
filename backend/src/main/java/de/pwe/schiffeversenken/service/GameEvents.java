package de.pwe.schiffeversenken.service;

import de.pwe.schiffeversenken.domain.Game;
import de.pwe.schiffeversenken.domain.GamePhase;
import de.pwe.schiffeversenken.domain.PlayerSide;
import de.pwe.schiffeversenken.domain.ShotOutcome;
import de.pwe.schiffeversenken.domain.ShotResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Leitet aus einer Spielaktion die Ereignisse ab, in der Reihenfolge, in der sie ausgeliefert werden. Reine Logik
 * ohne Infrastruktur. Die {@code game}-Parameter sind der Spielzustand <em>nach</em> der Aktion.
 */
public final class GameEvents {

    private GameEvents() {
    }

    /** {@code FLEET_READY}, bei der zweiten Flotte zusätzlich {@code GAME_STARTED}. */
    public static List<GameEvent> afterFleetPlaced(UUID gameId, PlayerSide side, Game game) {
        List<GameEvent> events = new ArrayList<>();
        events.add(GameEvent.fleetReady(gameId, side));
        if (game.phase() == GamePhase.RUNNING) {
            events.add(GameEvent.gameStarted(gameId, game.currentTurn().orElseThrow()));
        }
        return events;
    }

    /**
     * {@code SHOT}, danach {@code GAME_FINISHED} bei {@code WIN}, sonst {@code TURN_CHANGED}, wenn der Gegner an die
     * Reihe kommt.
     */
    public static List<GameEvent> afterShot(UUID gameId, PlayerSide shooter, ShotOutcome outcome, Game game) {
        List<GameEvent> events = new ArrayList<>();
        events.add(GameEvent.shot(gameId, shooter, outcome));
        if (outcome.result() == ShotResult.WIN) {
            events.add(GameEvent.gameFinished(gameId, game.phase(), game.winner().orElse(null)));
        } else if (game.currentTurn().filter(turn -> turn != shooter).isPresent()) {
            events.add(GameEvent.turnChanged(gameId, game.currentTurn().get()));
        }
        return events;
    }

    /** {@code GAME_FINISHED} mit dem Gegner als Sieger. */
    public static List<GameEvent> afterResign(UUID gameId, Game game) {
        return List.of(GameEvent.gameFinished(gameId, game.phase(), game.winner().orElse(null)));
    }

    /** {@code GAME_FINISHED} mit Phase {@code ABANDONED} und ohne Sieger. */
    public static List<GameEvent> afterAbandon(UUID gameId) {
        return List.of(GameEvent.gameFinished(gameId, GamePhase.ABANDONED, null));
    }
}

package de.pwe.schiffeversenken.service;

import de.pwe.schiffeversenken.domain.AiStrategy;
import de.pwe.schiffeversenken.domain.Board;
import de.pwe.schiffeversenken.domain.Coordinate;
import de.pwe.schiffeversenken.domain.Game;
import de.pwe.schiffeversenken.domain.GamePhase;
import de.pwe.schiffeversenken.domain.HuntTargetStrategy;
import de.pwe.schiffeversenken.domain.PlayerSide;
import de.pwe.schiffeversenken.domain.RuleSet;
import de.pwe.schiffeversenken.domain.ShipPlacement;
import de.pwe.schiffeversenken.domain.ShotOutcome;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Der KI-Gegner (immer Spieler B). Er hält keinen Zustand: Seine bisherigen Schüsse ergeben sich aus den Schüssen auf
 * das Feld des Menschen, so bleibt das Backend zustandslos.
 *
 * <p>Mit {@code game.ai.seed} ist der Zufall je Entscheidung aus Seed und Zugnummer abgeleitet, nicht aus der
 * Spiel-ID oder einem gemeinsamen Generator. Gleiche Eingaben des Menschen führen dann in jedem Spiel zu gleichen
 * Flotten und Schüssen. Ohne Seed entscheidet der Zufall.
 */
@ApplicationScoped
public class AiOpponent {

    static final PlayerSide SIDE = PlayerSide.B;

    private static final int PLACEMENT_SALT = -1;

    @ConfigProperty(name = "game.ai.seed")
    Optional<Long> seed;

    /** Zufällige, regelkonforme Flotte der KI. */
    List<ShipPlacement> placeFleet(RuleSet rules) {
        return new HuntTargetStrategy(random(PLACEMENT_SALT)).placeFleet(rules);
    }

    /**
     * Schießt, solange die KI am Zug ist (mehrfach bei Nochmal-Schießen nach Treffer). Tut nichts, wenn der Mensch
     * am Zug ist oder das Spiel nicht läuft.
     *
     * @return die Ereignisse der Schüsse, in der Reihenfolge der Auslieferung
     */
    List<GameEvent> respond(UUID gameId, Game game) {
        List<GameEvent> events = new ArrayList<>();
        if (!isAiTurn(game)) {
            return events;
        }
        RuleSet rules = game.rules();
        List<ShotOutcome> history = history(game);
        while (isAiTurn(game)) {
            AiStrategy strategy = new HuntTargetStrategy(random(history.size()));
            Coordinate target = strategy.nextShot(rules, history);
            ShotOutcome outcome = game.fire(SIDE, target);
            history.add(outcome);
            events.addAll(GameEvents.afterShot(gameId, SIDE, outcome, game));
        }
        return events;
    }

    private static boolean isAiTurn(Game game) {
        return game.phase() == GamePhase.RUNNING && game.currentTurn().filter(turn -> turn == SIDE).isPresent();
    }

    /** Bisherige Schüsse der KI samt Ergebnis: die Schüsse auf das Feld des Menschen, auf einem Board nachgespielt. */
    private static List<ShotOutcome> history(Game game) {
        var humanBoard = game.snapshot().boards().get(SIDE.opponent());
        Board replay = Board.withFleet(game.rules(), humanBoard.ships());
        List<ShotOutcome> history = new ArrayList<>();
        humanBoard.shotsReceived().forEach(shot -> history.add(replay.receiveShot(shot)));
        return history;
    }

    private RandomGenerator random(int salt) {
        return seed.<RandomGenerator>map(value -> new Random(value * 31 + salt))
                .orElseGet(ThreadLocalRandom::current);
    }
}

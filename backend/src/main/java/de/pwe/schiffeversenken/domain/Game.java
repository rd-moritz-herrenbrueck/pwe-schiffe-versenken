package de.pwe.schiffeversenken.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Ein Spiel zwischen Spieler A und B. Regelverstöße werden mit {@link RuleViolationException} abgelehnt.
 */
public final class Game {

    private final RuleSet rules;
    private final Map<PlayerSide, Board> boards = new EnumMap<>(PlayerSide.class);
    private GamePhase phase = GamePhase.WAITING;
    private PlayerSide currentTurn;
    private PlayerSide winner;

    private Game(RuleSet rules) {
        this.rules = rules;
    }

    /** Neues Spiel in Phase {@link GamePhase#WAITING}; Spieler A ist der Ersteller. */
    public static Game create(RuleSet rules) {
        return new Game(Objects.requireNonNull(rules, "rules"));
    }

    /** Spieler B tritt bei: {@link GamePhase#WAITING} → {@link GamePhase#PLACEMENT}. */
    public void join() {
        requirePhase(GamePhase.WAITING);
        phase = GamePhase.PLACEMENT;
    }

    /** Platziert und bestätigt die Flotte. Sind beide Flotten bereit, startet das Spiel. */
    public void placeFleet(PlayerSide side, List<ShipPlacement> placements) {
        Objects.requireNonNull(side, "side");
        requirePhase(GamePhase.PLACEMENT);
        if (boards.containsKey(side)) {
            throw new RuleViolationException(Violation.FLEET_ALREADY_PLACED,
                    "Spieler " + side + " hat seine Flotte bereits platziert");
        }
        boards.put(side, Board.withFleet(rules, placements));
        if (boards.size() == PlayerSide.values().length) {
            phase = GamePhase.RUNNING;
            currentTurn = PlayerSide.A;
        }
    }

    public boolean isFleetReady(PlayerSide side) {
        return boards.containsKey(side);
    }

    /** Schuss von {@code shooter} auf das Board des Gegners. */
    public ShotOutcome fire(PlayerSide shooter, Coordinate target) {
        Objects.requireNonNull(shooter, "shooter");
        requirePhase(GamePhase.RUNNING);
        if (shooter != currentTurn) {
            throw new RuleViolationException(Violation.NOT_YOUR_TURN, "Spieler " + shooter + " ist nicht am Zug");
        }
        ShotOutcome outcome = boards.get(shooter.opponent()).receiveShot(target);
        switch (outcome.result()) {
            case WIN -> finish(shooter);
            case MISS -> currentTurn = shooter.opponent();
            case HIT, SUNK -> {
                if (!rules.shootAgainOnHit()) {
                    currentTurn = shooter.opponent();
                }
            }
        }
        return outcome;
    }

    /** Aufgeben: Der Gegner gewinnt. */
    public void resign(PlayerSide side) {
        Objects.requireNonNull(side, "side");
        requirePhase(GamePhase.PLACEMENT, GamePhase.RUNNING);
        finish(side.opponent());
    }

    /** Markiert ein nicht beendetes Spiel als verwaist. */
    public void abandon() {
        requirePhase(GamePhase.WAITING, GamePhase.PLACEMENT, GamePhase.RUNNING);
        phase = GamePhase.ABANDONED;
        currentTurn = null;
    }

    public RuleSet rules() {
        return rules;
    }

    public GamePhase phase() {
        return phase;
    }

    public Optional<PlayerSide> currentTurn() {
        return Optional.ofNullable(currentTurn);
    }

    public Optional<PlayerSide> winner() {
        return Optional.ofNullable(winner);
    }

    private void finish(PlayerSide winningSide) {
        phase = GamePhase.FINISHED;
        winner = winningSide;
        currentTurn = null;
    }

    private void requirePhase(GamePhase... allowed) {
        if (!List.of(allowed).contains(phase)) {
            throw new RuleViolationException(Violation.WRONG_PHASE,
                    "Aktion in Phase " + phase + " nicht erlaubt, erwartet: " + List.of(allowed));
        }
    }
}

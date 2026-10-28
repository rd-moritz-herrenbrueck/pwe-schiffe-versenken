package de.pwe.schiffeversenken.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Unveränderlicher, speicherbarer Zustand eines {@link Game}. {@code currentTurn} und {@code winner} sind
 * {@code null}, wenn sie in der jeweiligen Phase nicht gesetzt sind. {@code boards} enthält nur Seiten, die ihre
 * Flotte bereits platziert haben.
 */
public record GameSnapshot(RuleSet rules, GamePhase phase, PlayerSide currentTurn, PlayerSide winner,
                           Map<PlayerSide, BoardSnapshot> boards) {

    public GameSnapshot {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(phase, "phase");
        boards = Map.copyOf(Objects.requireNonNull(boards, "boards"));
    }
}

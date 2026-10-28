package de.pwe.schiffeversenken.domain;

/**
 * Grund, aus dem eine Aktion gegen die Spielregeln verstößt.
 */
public enum Violation {
    INVALID_RULE_SET,
    SHIP_OUT_OF_BOUNDS,
    SHIPS_OVERLAP,
    SHIPS_TOUCHING,
    FLEET_DOES_NOT_FIT,
    FLEET_MISMATCH,
    FLEET_ALREADY_PLACED,
    SHOT_OUT_OF_BOUNDS,
    CELL_ALREADY_SHOT,
    NOT_YOUR_TURN,
    WRONG_PHASE
}

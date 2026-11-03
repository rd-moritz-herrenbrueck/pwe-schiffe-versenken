package de.pwe.schiffeversenken.service;

/** Ereignistypen eines Spiels. {@code CHAT} wird erst mit dem Chat (Phase 6) ausgelöst. */
public enum GameEventType {
    PLAYER_JOINED,
    FLEET_READY,
    GAME_STARTED,
    SHOT,
    TURN_CHANGED,
    GAME_FINISHED,
    CHAT
}

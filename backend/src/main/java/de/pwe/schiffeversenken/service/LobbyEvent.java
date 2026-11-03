package de.pwe.schiffeversenken.service;

import java.util.UUID;

/**
 * Änderung der Liste offener Lobbys. {@code lobby} ist nur bei {@link Type#LOBBY_OPENED} gesetzt.
 */
public record LobbyEvent(Type type, UUID gameId, OpenLobby lobby) {

    public enum Type {
        /** Eine neue offene Lobby ist verfügbar. */
        LOBBY_OPENED,
        /** Die Lobby ist nicht mehr offen (beigetreten oder verwaist). */
        LOBBY_CLOSED
    }

    public static LobbyEvent opened(OpenLobby lobby) {
        return new LobbyEvent(Type.LOBBY_OPENED, lobby.id(), lobby);
    }

    public static LobbyEvent closed(UUID gameId) {
        return new LobbyEvent(Type.LOBBY_CLOSED, gameId, null);
    }
}

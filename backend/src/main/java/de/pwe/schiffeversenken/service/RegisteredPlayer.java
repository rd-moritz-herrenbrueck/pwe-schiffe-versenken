package de.pwe.schiffeversenken.service;

import java.util.UUID;

/** Neu angelegter Spieler mit Klartext-Token (wird nur einmal herausgegeben). */
public record RegisteredPlayer(UUID id, String nickname, String token) {
}

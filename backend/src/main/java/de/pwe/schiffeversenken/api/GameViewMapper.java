package de.pwe.schiffeversenken.api;

import de.pwe.schiffeversenken.api.dto.GameView;
import de.pwe.schiffeversenken.domain.Game;
import de.pwe.schiffeversenken.domain.PlayerSide;

import java.util.UUID;

/**
 * Projiziert ein {@link Game} auf die Sicht eines Spielers (Anti-Cheat): Vom gegnerischen Feld sind nur die
 * eigenen Schüsse samt Treffer/Fehlschuss und bereits versenkte Schiffe sichtbar.
 */
public final class GameViewMapper {

    private GameViewMapper() {
    }

    /**
     * @param opponentNickname {@code null}, solange kein Gegner beigetreten ist
     */
    public static GameView toView(UUID gameId, PlayerSide viewer, String opponentNickname, Game game) {
        throw new UnsupportedOperationException("Phase 3: noch nicht implementiert");
    }
}

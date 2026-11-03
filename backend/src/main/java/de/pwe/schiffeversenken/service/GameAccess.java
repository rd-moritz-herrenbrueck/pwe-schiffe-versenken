package de.pwe.schiffeversenken.service;

import de.pwe.schiffeversenken.domain.Game;
import de.pwe.schiffeversenken.domain.PlayerSide;
import de.pwe.schiffeversenken.persistence.GameEntity;
import de.pwe.schiffeversenken.persistence.GameRepository;
import de.pwe.schiffeversenken.persistence.GameStateMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.util.UUID;

/**
 * Gemeinsame Hilfen von {@link LobbyService} und {@link GameService}: Spiel laden, Seite des Spielers bestimmen,
 * speichern. Muss innerhalb einer Transaktion aufgerufen werden.
 */
@ApplicationScoped
class GameAccess {

    @Inject
    GameRepository games;

    @Inject
    GameStateMapper mapper;

    @Inject
    Clock clock;

    /**
     * @throws ServiceException {@link ErrorCode#GAME_NOT_FOUND}, wenn das Spiel fehlt oder der Spieler nicht daran
     *                          beteiligt ist
     */
    GameEntity loadAsParticipant(UUID playerId, UUID gameId) {
        GameEntity entity = games.findById(gameId);
        if (entity == null || sideOf(entity, playerId) == null) {
            throw notFound();
        }
        return entity;
    }

    static ServiceException notFound() {
        return new ServiceException(ErrorCode.GAME_NOT_FOUND, "Spiel nicht gefunden");
    }

    /** Seite des Spielers im Spiel, {@code null} für Unbeteiligte. */
    static PlayerSide sideOf(GameEntity entity, UUID playerId) {
        if (entity.playerA.id.equals(playerId)) {
            return PlayerSide.A;
        }
        return entity.playerB != null && entity.playerB.id.equals(playerId) ? PlayerSide.B : null;
    }

    /** Nickname der KI in Spielen gegen den Computer. */
    static final String AI_NICKNAME = "KI";

    static String opponentNickname(GameEntity entity, PlayerSide viewer) {
        if (entity.aiOpponent) {
            return viewer == PlayerSide.A ? AI_NICKNAME : null;
        }
        var opponent = viewer == PlayerSide.A ? entity.playerB : entity.playerA;
        return opponent == null ? null : opponent.nickname;
    }

    PlayerGame view(GameEntity entity, PlayerSide viewer, Game game) {
        String inviteCode = viewer == PlayerSide.A ? entity.inviteCode : null;
        return new PlayerGame(entity.id, viewer, opponentNickname(entity, viewer), inviteCode, game);
    }

    /** Schreibt den Spielzustand und erzwingt die Versionsprüfung, damit Konflikte als Fehler ankommen. */
    void save(GameEntity entity, Game game) {
        mapper.write(game, entity);
        entity.updatedAt = clock.instant();
        games.flush();
    }
}

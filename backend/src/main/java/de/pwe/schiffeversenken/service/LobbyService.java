package de.pwe.schiffeversenken.service;

import de.pwe.schiffeversenken.domain.Game;
import de.pwe.schiffeversenken.domain.PlayerSide;
import de.pwe.schiffeversenken.domain.RuleSet;
import de.pwe.schiffeversenken.persistence.GameEntity;
import de.pwe.schiffeversenken.persistence.GameRepository;
import de.pwe.schiffeversenken.persistence.GameStateMapper;
import de.pwe.schiffeversenken.persistence.PlayerEntity;
import de.pwe.schiffeversenken.persistence.PlayerRepository;
import de.pwe.schiffeversenken.persistence.Visibility;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class LobbyService {

    @Inject
    GameRepository games;

    @Inject
    PlayerRepository players;

    @Inject
    GameStateMapper mapper;

    @Inject
    GameAccess access;

    @Inject
    Clock clock;

    /** Erstellt eine öffentliche Lobby. Der Ersteller ist Spieler A. */
    @Transactional
    public PlayerGame create(UUID playerId, RuleSet rules) {
        PlayerEntity creator = players.findById(playerId);
        Game game = Game.create(rules);
        Instant now = clock.instant();

        GameEntity entity = new GameEntity();
        entity.id = UUID.randomUUID();
        entity.visibility = Visibility.PUBLIC;
        entity.playerA = creator;
        entity.aiOpponent = false;
        entity.createdAt = now;
        entity.updatedAt = now;
        mapper.write(game, entity);
        games.persist(entity);
        return access.view(entity, PlayerSide.A, game);
    }

    /** Offene öffentliche Lobbys, neueste zuerst. */
    @Transactional
    public List<OpenLobby> listOpen() {
        return games.listOpenLobbies().stream()
                .map(lobby -> new OpenLobby(lobby.id, lobby.playerA.nickname, mapper.read(lobby).rules(),
                        lobby.createdAt))
                .toList();
    }

    /**
     * Tritt als Spieler B bei.
     *
     * @throws ServiceException {@link ErrorCode#GAME_NOT_FOUND}, {@link ErrorCode#CANNOT_JOIN_OWN_GAME}
     * @throws de.pwe.schiffeversenken.domain.RuleViolationException wenn die Lobby nicht mehr offen ist
     */
    @Transactional
    public PlayerGame join(UUID playerId, UUID gameId) {
        GameEntity entity = games.findById(gameId);
        // Private Lobbys sind nur über den Einladungscode erreichbar (Phase 6).
        if (entity == null || entity.visibility != Visibility.PUBLIC) {
            throw GameAccess.notFound();
        }
        if (entity.playerA.id.equals(playerId)) {
            throw new ServiceException(ErrorCode.CANNOT_JOIN_OWN_GAME, "Der eigenen Lobby kann man nicht beitreten");
        }
        Game game = mapper.read(entity);
        game.join();
        entity.playerB = players.findById(playerId);
        access.save(entity, game);
        return access.view(entity, PlayerSide.B, game);
    }
}

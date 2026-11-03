package de.pwe.schiffeversenken.service;

import de.pwe.schiffeversenken.domain.Coordinate;
import de.pwe.schiffeversenken.domain.Game;
import de.pwe.schiffeversenken.domain.PlayerSide;
import de.pwe.schiffeversenken.domain.ShipPlacement;
import de.pwe.schiffeversenken.domain.ShotOutcome;
import de.pwe.schiffeversenken.persistence.GameEntity;
import de.pwe.schiffeversenken.persistence.GameRepository;
import de.pwe.schiffeversenken.persistence.GameStateMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Spielaktionen. Jede Aktion lädt das Spiel, wendet die Domänenlogik an und speichert es (optimistisches Locking).
 * Nicht beteiligte Spieler erhalten {@link ErrorCode#GAME_NOT_FOUND}, damit die Existenz eines Spiels nicht
 * preisgegeben wird.
 */
@ApplicationScoped
public class GameService {

    @Inject
    GameRepository games;

    @Inject
    GameStateMapper mapper;

    @Inject
    GameAccess access;

    @Inject
    Clock clock;

    @ConfigProperty(name = "game.abandon.inactive-after")
    Duration abandonInactiveAfter;

    @Transactional
    public PlayerGame get(UUID playerId, UUID gameId) {
        GameEntity entity = access.loadAsParticipant(playerId, gameId);
        return access.view(entity, GameAccess.sideOf(entity, playerId), mapper.read(entity));
    }

    /** Eigene Spiele (alle Phasen), zuletzt aktive zuerst. */
    @Transactional
    public List<OwnGame> listOwn(UUID playerId) {
        return games.listByPlayer(playerId).stream().map(entity -> {
            PlayerSide viewer = GameAccess.sideOf(entity, playerId);
            return new OwnGame(entity.id, entity.status, viewer, GameAccess.opponentNickname(entity, viewer),
                    entity.currentTurn, entity.winner, entity.updatedAt);
        }).toList();
    }

    @Transactional
    public PlayerGame placeFleet(UUID playerId, UUID gameId, List<ShipPlacement> fleet) {
        return act(playerId, gameId, (game, side) -> {
            game.placeFleet(side, fleet);
            return null;
        }).game();
    }

    @Transactional
    public FireResult fire(UUID playerId, UUID gameId, Coordinate target) {
        Acted acted = act(playerId, gameId, (game, side) -> game.fire(side, target));
        return new FireResult(acted.outcome(), acted.game());
    }

    @Transactional
    public PlayerGame resign(UUID playerId, UUID gameId) {
        return act(playerId, gameId, (game, side) -> {
            game.resign(side);
            return null;
        }).game();
    }

    /**
     * Markiert nicht beendete Spiele ohne Aktivität seit {@code game.abandon.inactive-after} als
     * {@code ABANDONED}.
     *
     * @return Anzahl der markierten Spiele
     */
    @Transactional
    public int abandonInactiveGames() {
        List<GameEntity> inactive = games.listUnfinishedInactiveSince(clock.instant().minus(abandonInactiveAfter));
        for (GameEntity entity : inactive) {
            Game game = mapper.read(entity);
            game.abandon();
            access.save(entity, game);
        }
        return inactive.size();
    }

    private Acted act(UUID playerId, UUID gameId, Action action) {
        GameEntity entity = access.loadAsParticipant(playerId, gameId);
        PlayerSide side = GameAccess.sideOf(entity, playerId);
        Game game = mapper.read(entity);
        ShotOutcome outcome = action.apply(game, side);
        access.save(entity, game);
        return new Acted(outcome, access.view(entity, side, game));
    }

    @FunctionalInterface
    private interface Action {
        ShotOutcome apply(Game game, PlayerSide side);
    }

    private record Acted(ShotOutcome outcome, PlayerGame game) {
    }
}

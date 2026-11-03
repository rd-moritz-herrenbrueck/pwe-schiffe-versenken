package de.pwe.schiffeversenken.service;

import de.pwe.schiffeversenken.domain.Game;
import de.pwe.schiffeversenken.domain.GamePhase;
import de.pwe.schiffeversenken.domain.PlayerSide;
import de.pwe.schiffeversenken.domain.RuleSet;
import de.pwe.schiffeversenken.domain.RuleViolationException;
import de.pwe.schiffeversenken.domain.Violation;
import de.pwe.schiffeversenken.persistence.GameEntity;
import de.pwe.schiffeversenken.persistence.GameRepository;
import de.pwe.schiffeversenken.persistence.GameStateMapper;
import de.pwe.schiffeversenken.persistence.PlayerEntity;
import de.pwe.schiffeversenken.persistence.PlayerRepository;
import de.pwe.schiffeversenken.persistence.Visibility;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@ApplicationScoped
public class LobbyService {

    /** Länge und Zeichen des Einladungscodes. */
    static final int INVITE_CODE_LENGTH = 8;
    private static final String INVITE_CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final SecureRandom inviteCodeRandom = new SecureRandom();

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

    @Inject
    EventPublisher events;

    @Inject
    AiOpponent ai;

    @ConfigProperty(name = "game.lobby.expire-after")
    Duration expireAfter;

    /** Erstellt eine öffentliche Lobby. Der Ersteller ist Spieler A. */
    @Transactional
    public PlayerGame create(UUID playerId, RuleSet rules) {
        return create(playerId, rules, Visibility.PUBLIC, false);
    }

    /**
     * Erstellt eine Lobby. Der Ersteller ist Spieler A.
     *
     * <ul>
     *   <li>{@code PUBLIC}: steht in der Liste offener Lobbys und wird dort gemeldet.</li>
     *   <li>{@code PRIVATE}: bekommt einen Einladungscode, steht nicht in der Liste und wird nicht gemeldet.</li>
     *   <li>{@code vsAi}: Der Gegner ist die KI, sie hat ihre Flotte schon platziert und das Spiel startet in
     *       {@code PLACEMENT}. Solche Spiele sind nie in der Liste und ohne Einladungscode, {@code visibility}
     *       wird ignoriert.</li>
     * </ul>
     */
    @Transactional
    public PlayerGame create(UUID playerId, RuleSet rules, Visibility visibility, boolean vsAi) {
        PlayerEntity creator = players.findById(playerId);
        Game game = Game.create(rules);
        Instant now = clock.instant();

        GameEntity entity = new GameEntity();
        entity.id = UUID.randomUUID();
        entity.visibility = vsAi ? Visibility.PRIVATE : visibility;
        entity.playerA = creator;
        entity.aiOpponent = vsAi;
        entity.createdAt = now;
        entity.updatedAt = now;
        if (vsAi) {
            game.join();
            game.placeFleet(AiOpponent.SIDE, ai.placeFleet(rules));
        } else if (visibility == Visibility.PRIVATE) {
            entity.inviteCode = newInviteCode();
        }
        mapper.write(game, entity);
        games.persist(entity);
        if (entity.visibility == Visibility.PUBLIC) {
            events.publishLobbyEvent(LobbyEvent.opened(new OpenLobby(entity.id, creator.nickname, rules, now)));
        }
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
     * Tritt einer öffentlichen Lobby als Spieler B bei. Private Lobbys sind so nicht erreichbar, sie gelten als
     * unbekannt.
     *
     * @throws ServiceException {@link ErrorCode#GAME_NOT_FOUND}, {@link ErrorCode#CANNOT_JOIN_OWN_GAME}
     * @throws de.pwe.schiffeversenken.domain.RuleViolationException wenn die Lobby nicht mehr offen ist
     */
    @Transactional
    public PlayerGame join(UUID playerId, UUID gameId) {
        GameEntity entity = games.findById(gameId);
        if (entity == null || entity.visibility != Visibility.PUBLIC) {
            throw GameAccess.notFound();
        }
        return join(playerId, entity);
    }

    /**
     * Tritt einer privaten Lobby mit Einladungscode bei. Der Code wird getrimmt und ohne Beachtung der Groß- und
     * Kleinschreibung verglichen.
     *
     * @throws ServiceException {@link ErrorCode#GAME_NOT_FOUND} bei unbekanntem Code,
     *                          {@link ErrorCode#CANNOT_JOIN_OWN_GAME}
     * @throws de.pwe.schiffeversenken.domain.RuleViolationException wenn die Lobby nicht mehr offen ist
     */
    @Transactional
    public PlayerGame joinByInviteCode(UUID playerId, String inviteCode) {
        String normalized = inviteCode == null ? "" : inviteCode.strip().toUpperCase(Locale.ROOT);
        GameEntity entity = games.findByInviteCode(normalized).orElseThrow(GameAccess::notFound);
        return join(playerId, entity);
    }

    /**
     * Löscht eine Lobby, in der noch kein Gegner ist. Nur der Ersteller darf das, für andere gilt die Lobby als
     * unbekannt.
     *
     * @throws ServiceException {@link ErrorCode#GAME_NOT_FOUND}
     * @throws RuleViolationException {@link Violation#WRONG_PHASE}, wenn das Spiel schon begonnen hat (auch gegen die
     *                                KI, dort bleibt nur das Aufgeben)
     */
    @Transactional
    public void delete(UUID playerId, UUID gameId) {
        GameEntity entity = access.loadAsParticipant(playerId, gameId);
        if (entity.status != GamePhase.WAITING) {
            throw new RuleViolationException(Violation.WRONG_PHASE,
                    "Nur Lobbys ohne Gegner lassen sich löschen, laufende Spiele nur aufgeben");
        }
        remove(entity);
    }

    /**
     * Löscht Lobbys, die seit {@code game.lobby.expire-after} auf einen Gegner warten.
     *
     * @return Anzahl der gelöschten Lobbys
     */
    @Transactional
    public int deleteExpiredLobbies() {
        List<GameEntity> expired = games.listWaitingCreatedBefore(clock.instant().minus(expireAfter));
        expired.forEach(this::remove);
        return expired.size();
    }

    private void remove(GameEntity entity) {
        games.delete(entity);
        // Private Lobbys stehen nicht in der öffentlichen Liste und werden dort auch nicht gemeldet.
        if (entity.visibility == Visibility.PUBLIC) {
            events.publishLobbyClosed(entity.id);
        }
    }

    private PlayerGame join(UUID playerId, GameEntity entity) {
        if (entity.playerA.id.equals(playerId)) {
            throw new ServiceException(ErrorCode.CANNOT_JOIN_OWN_GAME, "Der eigenen Lobby kann man nicht beitreten");
        }
        Game game = mapper.read(entity);
        game.join();
        entity.playerB = players.findById(playerId);
        access.save(entity, game);
        events.publishGameEvents(List.of(GameEvent.playerJoined(entity.id, entity.playerB.nickname)));
        if (entity.visibility == Visibility.PUBLIC) {
            events.publishLobbyClosed(entity.id);
        }
        return access.view(entity, PlayerSide.B, game);
    }

    private String newInviteCode() {
        String code;
        do {
            StringBuilder builder = new StringBuilder(INVITE_CODE_LENGTH);
            for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
                builder.append(INVITE_CODE_ALPHABET.charAt(inviteCodeRandom.nextInt(INVITE_CODE_ALPHABET.length())));
            }
            code = builder.toString();
        } while (games.findByInviteCode(code).isPresent());
        return code;
    }
}

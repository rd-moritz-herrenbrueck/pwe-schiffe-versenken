package de.pwe.schiffeversenken.service;

import de.pwe.schiffeversenken.domain.GamePhase;
import de.pwe.schiffeversenken.domain.PlayerSide;
import de.pwe.schiffeversenken.domain.RuleViolationException;
import de.pwe.schiffeversenken.domain.Violation;
import de.pwe.schiffeversenken.persistence.ChatMessageEntity;
import de.pwe.schiffeversenken.persistence.ChatMessageRepository;
import de.pwe.schiffeversenken.persistence.GameEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Chat eines Spiels. Nur Beteiligte haben Zugriff, sonst {@link ErrorCode#GAME_NOT_FOUND}. */
@ApplicationScoped
public class ChatService {

    /** Maximale Länge einer Nachricht (nach Entfernen von Leerzeichen am Rand), passend zur Spalte. */
    public static final int MAX_TEXT_LENGTH = 500;

    private static final Set<GamePhase> SENDING_PHASES = Set.of(GamePhase.PLACEMENT, GamePhase.RUNNING,
            GamePhase.FINISHED);

    @Inject
    ChatMessageRepository messages;

    @Inject
    GameAccess access;

    @Inject
    Clock clock;

    @Inject
    EventPublisher events;

    @Inject
    ChatRateLimiter rateLimiter;

    /** Verlauf, älteste Nachricht zuerst. Lesen ist in jeder Phase möglich. */
    @Transactional
    public List<ChatEntry> history(UUID playerId, UUID gameId) {
        GameEntity game = access.loadAsParticipant(playerId, gameId);
        return messages.listByGame(gameId).stream().map(message -> toEntry(game, message)).toList();
    }

    /**
     * Speichert eine Nachricht und meldet sie allen Spielern.
     *
     * @throws ServiceException       {@link ErrorCode#GAME_NOT_FOUND}, {@link ErrorCode#INVALID_REQUEST} bei leerem oder
     *                                zu langem Text,
     *                                {@link ErrorCode#RATE_LIMITED} bei zu vielen Nachrichten in kurzer Zeit
     * @throws RuleViolationException {@link Violation#WRONG_PHASE} in {@code WAITING} und {@code ABANDONED}
     */
    @Transactional
    public ChatEntry send(UUID playerId, UUID gameId, String text) {
        GameEntity game = access.loadAsParticipant(playerId, gameId);
        if (!SENDING_PHASES.contains(game.status)) {
            throw new RuleViolationException(Violation.WRONG_PHASE,
                    "Chat in Phase " + game.status + " nicht möglich");
        }
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_TEXT_LENGTH) {
            throw new ServiceException(ErrorCode.INVALID_REQUEST,
                    "Nachricht muss 1 bis " + MAX_TEXT_LENGTH + " Zeichen lang sein");
        }
        if (!rateLimiter.tryAcquire(playerId)) {
            throw new ServiceException(ErrorCode.RATE_LIMITED,
                    "Du schreibst zu schnell, bitte warte einen Moment", rateLimiter.retryAfterSeconds(playerId));
        }
        ChatMessageEntity message = new ChatMessageEntity();
        message.id = UUID.randomUUID();
        message.gameId = gameId;
        message.playerId = playerId;
        message.text = trimmed;
        message.createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        messages.persist(message);

        ChatEntry entry = toEntry(game, message);
        events.publishGameEvents(List.of(GameEvent.chat(gameId, entry)));
        return entry;
    }

    private static ChatEntry toEntry(GameEntity game, ChatMessageEntity message) {
        PlayerSide side = GameAccess.sideOf(game, message.playerId);
        String nickname = side == PlayerSide.A ? game.playerA.nickname : game.playerB.nickname;
        return new ChatEntry(message.id, side, nickname, message.text, message.createdAt);
    }
}

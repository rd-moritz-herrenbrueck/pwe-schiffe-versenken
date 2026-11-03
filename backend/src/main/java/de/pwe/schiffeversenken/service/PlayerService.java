package de.pwe.schiffeversenken.service;

import de.pwe.schiffeversenken.persistence.PlayerEntity;
import de.pwe.schiffeversenken.persistence.PlayerRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PlayerService {

    /** Maximale Länge eines Nicknames (nach Entfernen von Leerzeichen am Rand). */
    public static final int MAX_NICKNAME_LENGTH = 20;

    @Inject
    PlayerRepository players;

    @Inject
    Clock clock;

    /**
     * Legt einen Spieler an. Der Nickname wird getrimmt und muss 1 bis {@value #MAX_NICKNAME_LENGTH} Zeichen lang
     * sein, sonst {@link ErrorCode#INVALID_NICKNAME}.
     */
    @Transactional
    public RegisteredPlayer register(String nickname) {
        String trimmed = nickname == null ? "" : nickname.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NICKNAME_LENGTH
                || trimmed.chars().anyMatch(Character::isISOControl)) {
            throw new ServiceException(ErrorCode.INVALID_NICKNAME,
                    "Nickname muss 1 bis " + MAX_NICKNAME_LENGTH + " Zeichen lang sein, ohne Steuerzeichen");
        }
        String token = PlayerTokens.generate();
        PlayerEntity player = new PlayerEntity();
        player.id = UUID.randomUUID();
        player.nickname = trimmed;
        player.tokenHash = PlayerTokens.hash(token);
        player.createdAt = clock.instant();
        players.persist(player);
        return new RegisteredPlayer(player.id, player.nickname, token);
    }

    /** Spieler zum Token, leer bei unbekanntem Token. */
    public Optional<AuthenticatedPlayer> authenticate(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        return players.findByTokenHash(PlayerTokens.hash(token))
                .map(player -> new AuthenticatedPlayer(player.id, player.nickname));
    }
}

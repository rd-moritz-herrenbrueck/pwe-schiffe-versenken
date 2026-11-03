package de.pwe.schiffeversenken.api.dto;

import de.pwe.schiffeversenken.domain.PlayerSide;
import de.pwe.schiffeversenken.service.ChatEntry;

import java.time.Instant;
import java.util.UUID;

/** Chatnachricht. {@code side} ist die Seite des Absenders. */
public record ChatMessageDto(UUID id, PlayerSide side, String nickname, String text, Instant createdAt) {

    public static ChatMessageDto from(ChatEntry entry) {
        return new ChatMessageDto(entry.id(), entry.side(), entry.nickname(), entry.text(), entry.createdAt());
    }
}

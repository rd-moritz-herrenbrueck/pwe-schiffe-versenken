package de.pwe.schiffeversenken.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Löscht abgelaufene Lobbys regelmäßig. Intervall: {@code game.lobby.check-interval}. */
@ApplicationScoped
public class ExpiredLobbiesJob {

    @Inject
    LobbyService lobbyService;

    @Scheduled(every = "{game.lobby.check-interval}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void deleteExpiredLobbies() {
        lobbyService.deleteExpiredLobbies();
    }
}

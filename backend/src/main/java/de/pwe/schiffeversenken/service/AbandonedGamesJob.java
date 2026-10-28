package de.pwe.schiffeversenken.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Markiert verwaiste Spiele regelmäßig als {@code ABANDONED}. Intervall: {@code game.abandon.check-interval}. */
@ApplicationScoped
public class AbandonedGamesJob {

    @Inject
    GameService gameService;

    @Scheduled(every = "{game.abandon.check-interval}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void abandonInactiveGames() {
        gameService.abandonInactiveGames();
    }
}

package de.pwe.schiffeversenken.persistence;

import de.pwe.schiffeversenken.domain.Coordinate;
import de.pwe.schiffeversenken.domain.Game;
import de.pwe.schiffeversenken.domain.Orientation;
import de.pwe.schiffeversenken.domain.PlayerSide;
import de.pwe.schiffeversenken.domain.RuleSet;
import de.pwe.schiffeversenken.domain.ShipPlacement;
import de.pwe.schiffeversenken.service.PlayerTokens;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.StaleStateException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Speichern von Spielen in PostgreSQL: JSONB-Spalten und optimistisches Locking. */
@QuarkusTest
class GamePersistenceIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T12:00:00Z");

    @Inject
    GameRepository games;

    @Inject
    PlayerRepository players;

    @Inject
    GameStateMapper mapper;

    @Test
    void rulesAndStateAreStoredAsJsonbObjects() {
        UUID id = persist(Game.create(RuleSet.standard()));

        Object[] types = QuarkusTransaction.requiringNew().call(() -> (Object[]) games.getEntityManager()
                .createNativeQuery("select jsonb_typeof(rules), jsonb_typeof(state) from game where id = :id")
                .setParameter("id", id)
                .getSingleResult());

        assertEquals("object", types[0]);
        assertEquals("object", types[1]);
    }

    @Test
    void storedGameIsRestoredUnchanged() {
        Game game = runningGame();
        game.fire(PlayerSide.A, new Coordinate(0, 0));
        game.fire(PlayerSide.B, new Coordinate(9, 9));
        UUID id = persist(game);

        Game loaded = QuarkusTransaction.requiringNew().call(() -> mapper.read(games.findById(id)));

        assertEquals(game.snapshot(), loaded.snapshot());
    }

    @Test
    void versionIncreasesWithEveryUpdate() {
        UUID id = persist(Game.create(RuleSet.standard()));

        QuarkusTransaction.requiringNew().run(() -> join(games.findById(id)));

        assertEquals(1L, QuarkusTransaction.requiringNew().call(() -> games.findById(id).version));
    }

    @Test
    void updateBasedOnAnOutdatedVersionIsRejected() {
        UUID id = persist(Game.create(RuleSet.standard()));
        GameEntity outdated = QuarkusTransaction.requiringNew().call(() -> games.findById(id));
        QuarkusTransaction.requiringNew().run(() -> join(games.findById(id)));

        Exception exception = assertThrows(Exception.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            join(outdated);
            games.getEntityManager().merge(outdated);
            games.flush();
        }));

        assertTrue(isCausedByOptimisticLock(exception), () -> "Unerwartete Ausnahme: " + exception);
    }

    // --- Hilfsmethoden ---

    private void join(GameEntity entity) {
        Game game = mapper.read(entity);
        game.join();
        mapper.write(game, entity);
    }

    private UUID persist(Game game) {
        return QuarkusTransaction.requiringNew().call(() -> {
            PlayerEntity player = new PlayerEntity();
            player.id = UUID.randomUUID();
            player.nickname = "Ada";
            player.tokenHash = PlayerTokens.hash(player.id.toString());
            player.createdAt = CREATED_AT;
            players.persist(player);

            GameEntity entity = new GameEntity();
            entity.id = UUID.randomUUID();
            entity.visibility = Visibility.PUBLIC;
            entity.playerA = player;
            entity.createdAt = CREATED_AT;
            entity.updatedAt = CREATED_AT;
            mapper.write(game, entity);
            games.persist(entity);
            return entity.id;
        });
    }

    private static Game runningGame() {
        List<ShipPlacement> fleet = List.of(
                new ShipPlacement(new Coordinate(0, 0), 5, Orientation.HORIZONTAL),
                new ShipPlacement(new Coordinate(0, 2), 4, Orientation.HORIZONTAL),
                new ShipPlacement(new Coordinate(0, 4), 3, Orientation.HORIZONTAL),
                new ShipPlacement(new Coordinate(0, 6), 2, Orientation.HORIZONTAL));
        Game game = Game.create(RuleSet.standard());
        game.join();
        game.placeFleet(PlayerSide.A, fleet);
        game.placeFleet(PlayerSide.B, fleet);
        return game;
    }

    private static boolean isCausedByOptimisticLock(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof OptimisticLockException || cause instanceof StaleStateException) {
                return true;
            }
        }
        return false;
    }
}

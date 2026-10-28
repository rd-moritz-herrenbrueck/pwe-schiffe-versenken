package de.pwe.schiffeversenken.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PlayerRepository implements PanacheRepositoryBase<PlayerEntity, UUID> {

    public Optional<PlayerEntity> findByTokenHash(String tokenHash) {
        throw new UnsupportedOperationException("Phase 3: noch nicht implementiert");
    }
}

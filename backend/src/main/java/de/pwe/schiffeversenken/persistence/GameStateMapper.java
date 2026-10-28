package de.pwe.schiffeversenken.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.pwe.schiffeversenken.domain.Game;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Überträgt ein {@link Game} in die Spalten von {@link GameEntity} und zurück. Regeln und Spielfelder werden als
 * JSON gespeichert. Das JSON-Format ist Teil des Datenbankschemas und muss abwärtskompatibel bleiben.
 */
@ApplicationScoped
public class GameStateMapper {

    private final ObjectMapper objectMapper;

    public GameStateMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Schreibt Regeln, Spielfelder, Phase, Zug und Sieger. Metadaten (Spieler, Zeitstempel) bleiben unberührt. */
    public void write(Game game, GameEntity target) {
        throw new UnsupportedOperationException("Phase 3: noch nicht implementiert");
    }

    public Game read(GameEntity source) {
        throw new UnsupportedOperationException("Phase 3: noch nicht implementiert");
    }
}

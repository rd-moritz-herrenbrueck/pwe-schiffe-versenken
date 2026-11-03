package de.pwe.schiffeversenken.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.pwe.schiffeversenken.domain.BoardSnapshot;
import de.pwe.schiffeversenken.domain.Coordinate;
import de.pwe.schiffeversenken.domain.Game;
import de.pwe.schiffeversenken.domain.GameSnapshot;
import de.pwe.schiffeversenken.domain.Orientation;
import de.pwe.schiffeversenken.domain.PlayerSide;
import de.pwe.schiffeversenken.domain.RuleSet;
import de.pwe.schiffeversenken.domain.ShipPlacement;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
        GameSnapshot snapshot = game.snapshot();
        Map<PlayerSide, BoardJson> boards = new EnumMap<>(PlayerSide.class);
        snapshot.boards().forEach((side, board) -> boards.put(side, BoardJson.from(board)));
        try {
            target.rules = objectMapper.writeValueAsString(RulesJson.from(snapshot.rules()));
            target.state = objectMapper.writeValueAsString(new StateJson(boards));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Spielzustand lässt sich nicht als JSON schreiben", e);
        }
        target.status = snapshot.phase();
        target.currentTurn = snapshot.currentTurn();
        target.winner = snapshot.winner();
    }

    public Game read(GameEntity source) {
        try {
            RuleSet rules = objectMapper.readValue(source.rules, RulesJson.class).toDomain();
            StateJson state = objectMapper.readValue(source.state, StateJson.class);
            Map<PlayerSide, BoardSnapshot> boards = new EnumMap<>(PlayerSide.class);
            state.boards().forEach((side, board) -> boards.put(side, board.toDomain()));
            return Game.restore(new GameSnapshot(rules, source.status, source.currentTurn, source.winner, boards));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Gespeicherter Spielzustand ist nicht lesbar: " + source.id, e);
        }
    }

    // Eigene JSON-Typen entkoppeln das Speicherformat von den Domänenklassen.

    record RulesJson(int boardWidth, int boardHeight, List<Integer> fleet, boolean shootAgainOnHit,
                     boolean shipsMayTouch) {

        static RulesJson from(RuleSet rules) {
            return new RulesJson(rules.boardWidth(), rules.boardHeight(), rules.fleet(), rules.shootAgainOnHit(),
                    rules.shipsMayTouch());
        }

        RuleSet toDomain() {
            return new RuleSet(boardWidth, boardHeight, fleet, shootAgainOnHit, shipsMayTouch);
        }
    }

    record StateJson(Map<PlayerSide, BoardJson> boards) {
    }

    record BoardJson(List<ShipJson> ships, List<CoordinateJson> shotsReceived) {

        static BoardJson from(BoardSnapshot board) {
            return new BoardJson(board.ships().stream().map(ShipJson::from).toList(),
                    board.shotsReceived().stream().map(CoordinateJson::from).toList());
        }

        BoardSnapshot toDomain() {
            return new BoardSnapshot(ships.stream().map(ShipJson::toDomain).toList(),
                    shotsReceived.stream().map(CoordinateJson::toDomain).toList());
        }
    }

    record ShipJson(CoordinateJson start, int length, Orientation orientation) {

        static ShipJson from(ShipPlacement placement) {
            return new ShipJson(CoordinateJson.from(placement.start()), placement.length(),
                    placement.orientation());
        }

        ShipPlacement toDomain() {
            return new ShipPlacement(start.toDomain(), length, orientation);
        }
    }

    record CoordinateJson(int x, int y) {

        static CoordinateJson from(Coordinate coordinate) {
            return new CoordinateJson(coordinate.x(), coordinate.y());
        }

        Coordinate toDomain() {
            return new Coordinate(x, y);
        }
    }
}

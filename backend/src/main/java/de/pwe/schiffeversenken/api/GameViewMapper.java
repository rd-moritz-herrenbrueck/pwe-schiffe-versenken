package de.pwe.schiffeversenken.api;

import de.pwe.schiffeversenken.api.dto.BoardView;
import de.pwe.schiffeversenken.api.dto.GameResult;
import de.pwe.schiffeversenken.api.dto.GameView;
import de.pwe.schiffeversenken.api.dto.RuleSetDto;
import de.pwe.schiffeversenken.api.dto.ShipView;
import de.pwe.schiffeversenken.api.dto.ShotView;
import de.pwe.schiffeversenken.domain.BoardSnapshot;
import de.pwe.schiffeversenken.domain.Coordinate;
import de.pwe.schiffeversenken.domain.Game;
import de.pwe.schiffeversenken.domain.GameSnapshot;
import de.pwe.schiffeversenken.domain.PlayerSide;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Projiziert ein {@link Game} auf die Sicht eines Spielers (Anti-Cheat): Vom gegnerischen Feld sind nur die
 * eigenen Schüsse samt Treffer/Fehlschuss und bereits versenkte Schiffe sichtbar.
 */
public final class GameViewMapper {

    private GameViewMapper() {
    }

    /**
     * @param opponentNickname {@code null}, solange kein Gegner beigetreten ist
     */
    public static GameView toView(UUID gameId, PlayerSide viewer, String opponentNickname, Game game) {
        GameSnapshot snapshot = game.snapshot();
        PlayerSide opponent = viewer.opponent();
        BoardSnapshot own = snapshot.boards().get(viewer);
        BoardSnapshot enemy = snapshot.boards().get(opponent);
        GameResult result = snapshot.winner() == null ? null
                : snapshot.winner() == viewer ? GameResult.WON : GameResult.LOST;
        return new GameView(gameId, snapshot.phase(), viewer, opponentNickname, RuleSetDto.from(snapshot.rules()),
                snapshot.currentTurn() == viewer, own != null, enemy != null, result,
                boardView(own, false), boardView(enemy, true));
    }

    /**
     * @param onlySunkShips auf dem gegnerischen Feld nur versenkte Schiffe zeigen. Die Schüsse sind immer
     *                      sichtbar, weil sie auf diesem Feld die Schüsse des Betrachters bzw. gegen ihn sind.
     */
    private static BoardView boardView(BoardSnapshot board, boolean onlySunkShips) {
        if (board == null) {
            return new BoardView(List.of(), List.of());
        }
        Set<Coordinate> shotCells = new HashSet<>(board.shotsReceived());
        Set<Coordinate> shipCells = new HashSet<>();
        board.ships().forEach(ship -> shipCells.addAll(ship.cells()));

        List<ShipView> ships = board.ships().stream()
                .map(ship -> new ShipView(ship.start().x(), ship.start().y(), ship.length(), ship.orientation(),
                        shotCells.containsAll(ship.cells())))
                .filter(ship -> ship.sunk() || !onlySunkShips)
                .toList();
        List<ShotView> shots = board.shotsReceived().stream()
                .map(shot -> new ShotView(shot.x(), shot.y(), shipCells.contains(shot)))
                .toList();
        return new BoardView(ships, shots);
    }
}

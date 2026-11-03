package de.pwe.schiffeversenken.domain;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Prüft, ob sich eine Flotte so platzieren lässt, dass sich Schiffe nicht über eine Kante berühren (Ecken sind
 * erlaubt). Die Rechteck-Umformung aus {@link FleetPlacementSearch} gilt nur, wenn auch Ecken als Berührung zählen,
 * daher wird hier direkt gesucht.
 *
 * <p>Jedes Schiff beginnt an seinem ersten Feld in Zeilenreihenfolge. Die Tiefensuche läuft die Felder in dieser
 * Reihenfolge ab, an jedem Feld beginnt höchstens ein Schiff, oder das Feld bleibt frei. Sie ist wie dort durch ein
 * Schrittbudget begrenzt. Wird es überschritten, gilt die Flotte als nicht platzierbar.
 */
final class EdgeSeparatedFleetSearch {

    private final int width;
    private final int height;
    private final int[] lengths;
    private final int[] remaining;
    private final boolean[] occupied;
    private int shipsLeft;
    private int cellsLeft;
    private long steps;

    private EdgeSeparatedFleetSearch(int width, int height, int[] lengths, int[] counts) {
        this.width = width;
        this.height = height;
        this.lengths = lengths;
        this.remaining = counts;
        this.occupied = new boolean[width * height];
        for (int i = 0; i < lengths.length; i++) {
            shipsLeft += counts[i];
            cellsLeft += counts[i] * lengths[i];
        }
    }

    static boolean fits(int width, int height, List<Integer> fleet) {
        Map<Integer, Integer> countsByLength = new TreeMap<>((a, b) -> Integer.compare(b, a));
        fleet.forEach(length -> countsByLength.merge(length, 1, Integer::sum));
        int[] lengths = countsByLength.keySet().stream().mapToInt(Integer::intValue).toArray();
        int[] counts = countsByLength.values().stream().mapToInt(Integer::intValue).toArray();
        return new EdgeSeparatedFleetSearch(width, height, lengths, counts).search(0);
    }

    private boolean search(int fromCell) {
        if (shipsLeft == 0) {
            return true;
        }
        for (int cell = fromCell; cell < occupied.length; cell++) {
            if (occupied.length - cell < cellsLeft || ++steps > FleetPlacementSearch.STEP_BUDGET) {
                return false;
            }
            int x = cell % width;
            int y = cell / width;
            for (int i = 0; i < lengths.length; i++) {
                if (remaining[i] > 0
                        && (tryPlace(i, x, y, true, cell) || tryPlace(i, x, y, false, cell))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean tryPlace(int type, int x, int y, boolean horizontal, int cell) {
        int length = lengths[type];
        if (!isFree(x, y, length, horizontal)) {
            return false;
        }
        setOccupied(x, y, length, horizontal, true);
        remaining[type]--;
        shipsLeft--;
        cellsLeft -= length;
        boolean found = search(cell + 1);
        cellsLeft += length;
        shipsLeft++;
        remaining[type]++;
        setOccupied(x, y, length, horizontal, false);
        return found;
    }

    /** Alle Felder des Schiffs liegen im Feld, sind frei und haben keinen belegten Kantennachbarn. */
    private boolean isFree(int x, int y, int length, boolean horizontal) {
        if (horizontal ? x + length > width : y + length > height) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            int cx = horizontal ? x + i : x;
            int cy = horizontal ? y : y + i;
            if (isOccupied(cx, cy) || isOccupied(cx + 1, cy) || isOccupied(cx - 1, cy)
                    || isOccupied(cx, cy + 1) || isOccupied(cx, cy - 1)) {
                return false;
            }
        }
        return true;
    }

    private boolean isOccupied(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height && occupied[y * width + x];
    }

    private void setOccupied(int x, int y, int length, boolean horizontal, boolean value) {
        for (int i = 0; i < length; i++) {
            occupied[(horizontal ? y : y + i) * width + (horizontal ? x + i : x)] = value;
        }
    }
}

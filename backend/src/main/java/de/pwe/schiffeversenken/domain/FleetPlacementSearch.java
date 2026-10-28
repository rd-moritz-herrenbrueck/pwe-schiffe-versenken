package de.pwe.schiffeversenken.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Prüft, ob sich eine Flotte regelkonform auf einem Feld platzieren lässt.
 *
 * <p>Die Prüfung wird als Rechteck-Packung gelöst: Mit Berührung belegt ein Schiff der Länge L ein L×1-Rechteck im
 * W×H-Feld. Ohne Berührung wird jedes Schiff um eins nach rechts und unten zu einem (L+1)×2-Rechteck im
 * (W+1)×(H+1)-Feld erweitert. Diese Rechtecke überschneiden sich genau dann nicht, wenn sich die Schiffe nicht
 * berühren, die Umformung ist also exakt.
 *
 * <p>Gesucht wird per Tiefensuche über das jeweils erste freie Feld (zeilenweise): Entweder beginnt dort ein
 * Rechteck, oder das Feld bleibt leer (Verschnitt). Ein Zweig wird verworfen, sobald der bisherige Verschnitt plus
 * eine untere Schranke für den unvermeidbaren weiteren Verschnitt die Differenz aus Feld- und Flottenfläche
 * übersteigt.
 *
 * <p>Die Suche ist durch ein Schrittbudget begrenzt, damit die Laufzeit auch für ungünstige Eingaben beschränkt
 * bleibt. Wird es überschritten, gilt die Flotte als nicht platzierbar. Erlaubt werden also nur Flotten, für die
 * tatsächlich eine Platzierung gefunden wurde.
 */
final class FleetPlacementSearch {

    static final long STEP_BUDGET = 200_000;

    private final int columns;
    private final int rows;
    private final int[] longSides;
    private final int shortSide;
    private final int[] remaining;
    private final boolean[] covered;
    private final int[] coveredPrefix;
    private final int[] coverable;
    private final int[] bestLineFill;
    private final long maxWaste;
    private int shipsLeft;
    private long steps;

    private FleetPlacementSearch(int columns, int rows, int[] longSides, int shortSide, int[] counts, long maxWaste) {
        this.columns = columns;
        this.rows = rows;
        this.longSides = longSides;
        this.shortSide = shortSide;
        this.remaining = counts;
        this.covered = new boolean[columns * rows];
        this.coveredPrefix = new int[(columns + 1) * (rows + 1)];
        this.coverable = new int[(columns + 1) * (rows + 1)];
        this.bestLineFill = new int[Math.max(columns, rows) + 1];
        this.maxWaste = maxWaste;
        for (int count : counts) {
            shipsLeft += count;
        }
    }

    static boolean fits(int width, int height, List<Integer> fleet, boolean shipsMayTouch) {
        int margin = shipsMayTouch ? 0 : 1;
        int columns = width + margin;
        int rows = height + margin;
        int shortSide = 1 + margin;

        Map<Integer, Integer> countsByLength = new TreeMap<>((a, b) -> Integer.compare(b, a));
        fleet.forEach(length -> countsByLength.merge(length, 1, Integer::sum));
        int[] longSides = countsByLength.keySet().stream().mapToInt(length -> length + margin).toArray();
        int[] counts = countsByLength.values().stream().mapToInt(Integer::intValue).toArray();

        if (longSides[0] > Math.max(columns, rows)) {
            return false;
        }
        long requiredArea = 0;
        for (int i = 0; i < longSides.length; i++) {
            requiredArea += (long) longSides[i] * shortSide * counts[i];
        }
        long availableArea = (long) columns * rows;
        if (requiredArea > availableArea) {
            return false;
        }
        return new FleetPlacementSearch(columns, rows, longSides, shortSide, counts, availableArea - requiredArea)
                .search(0, 0);
    }

    private boolean search(int fromCell, long waste) {
        if (shipsLeft == 0) {
            return true;
        }
        int cell = fromCell;
        while (cell < covered.length && covered[cell]) {
            cell++;
        }
        if (cell == covered.length || ++steps > STEP_BUDGET
                || waste + unavoidableWaste() > maxWaste) {
            return false;
        }
        int x = cell % columns;
        int y = cell / columns;

        for (int i = 0; i < longSides.length; i++) {
            if (remaining[i] == 0) {
                continue;
            }
            if (tryPlace(i, x, y, longSides[i], shortSide, cell, waste)
                    || tryPlace(i, x, y, shortSide, longSides[i], cell, waste)) {
                return true;
            }
        }

        if (waste < maxWaste) {
            covered[cell] = true;
            boolean found = search(cell + 1, waste + 1);
            covered[cell] = false;
            return found;
        }
        return false;
    }

    /** Untere Schranke für den Verschnitt, der in den noch freien Feldern zwingend entsteht. */
    private int unavoidableWaste() {
        updateBestLineFill();
        return Math.max(countUncoverableFreeCells(), Math.max(lineWaste(true), lineWaste(false)));
    }

    /**
     * {@code bestLineFill[g]}: Wie viele Felder eines freien Zeilen- oder Spaltenabschnitts der Länge g höchstens
     * belegt werden können. Jedes Rechteck schneidet eine Linie in einem Stück der Länge seiner kurzen oder langen
     * Seite.
     */
    private void updateBestLineFill() {
        boolean[] reachable = new boolean[bestLineFill.length];
        reachable[0] = true;
        for (int g = 1; g < bestLineFill.length; g++) {
            reachable[g] = g >= shortSide && reachable[g - shortSide];
            for (int i = 0; i < longSides.length && !reachable[g]; i++) {
                reachable[g] = remaining[i] > 0 && g >= longSides[i] && reachable[g - longSides[i]];
            }
            bestLineFill[g] = reachable[g] ? g : bestLineFill[g - 1];
        }
    }

    private int lineWaste(boolean byRow) {
        int lines = byRow ? rows : columns;
        int length = byRow ? columns : rows;
        int waste = 0;
        for (int line = 0; line < lines; line++) {
            int segment = 0;
            for (int pos = 0; pos <= length; pos++) {
                boolean free = pos < length
                        && !covered[byRow ? line * columns + pos : pos * columns + line];
                if (free) {
                    segment++;
                } else {
                    waste += segment - bestLineFill[segment];
                    segment = 0;
                }
            }
        }
        return waste;
    }

    /**
     * Zählt freie Felder, die von keinem verbleibenden Schiff mehr abgedeckt werden können. Alle Rechtecke haben
     * dieselbe kurze Seite, daher genügt das kürzeste verbleibende: Passt ein längeres über ein Feld, passt auch das
     * kürzere.
     */
    private int countUncoverableFreeCells() {
        int shortestLongSide = 0;
        for (int i = longSides.length - 1; i >= 0; i--) {
            if (remaining[i] > 0) {
                shortestLongSide = longSides[i];
                break;
            }
        }
        int stride = columns + 1;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                coveredPrefix[(y + 1) * stride + x + 1] = (covered[y * columns + x] ? 1 : 0)
                        + coveredPrefix[y * stride + x + 1] + coveredPrefix[(y + 1) * stride + x]
                        - coveredPrefix[y * stride + x];
            }
        }
        Arrays.fill(coverable, 0);
        markFreeWindows(shortestLongSide, shortSide);
        markFreeWindows(shortSide, shortestLongSide);

        int uncoverable = 0;
        for (int y = 0; y < rows; y++) {
            int rowSum = 0;
            for (int x = 0; x < columns; x++) {
                rowSum += coverable[y * stride + x];
                coverable[y * stride + x] = rowSum + (y > 0 ? coverable[(y - 1) * stride + x] : 0);
                if (!covered[y * columns + x] && coverable[y * stride + x] == 0) {
                    uncoverable++;
                }
            }
        }
        return uncoverable;
    }

    /** Trägt jedes freie w×h-Fenster als 2D-Differenz in {@link #coverable} ein. */
    private void markFreeWindows(int w, int h) {
        int stride = columns + 1;
        for (int y = 0; y + h <= rows; y++) {
            for (int x = 0; x + w <= columns; x++) {
                int coveredInWindow = coveredPrefix[(y + h) * stride + x + w] - coveredPrefix[y * stride + x + w]
                        - coveredPrefix[(y + h) * stride + x] + coveredPrefix[y * stride + x];
                if (coveredInWindow == 0) {
                    coverable[y * stride + x]++;
                    coverable[y * stride + x + w]--;
                    coverable[(y + h) * stride + x]--;
                    coverable[(y + h) * stride + x + w]++;
                }
            }
        }
    }

    private boolean tryPlace(int type, int x, int y, int w, int h, int cell, long waste) {
        if (!isFree(x, y, w, h)) {
            return false;
        }
        setCovered(x, y, w, h, true);
        remaining[type]--;
        shipsLeft--;
        boolean found = search(cell + 1, waste);
        shipsLeft++;
        remaining[type]++;
        setCovered(x, y, w, h, false);
        return found;
    }

    private boolean isFree(int x, int y, int w, int h) {
        if (x + w > columns || y + h > rows) {
            return false;
        }
        for (int cy = y; cy < y + h; cy++) {
            for (int cx = x; cx < x + w; cx++) {
                if (covered[cy * columns + cx]) {
                    return false;
                }
            }
        }
        return true;
    }

    private void setCovered(int x, int y, int w, int h, boolean value) {
        for (int cy = y; cy < y + h; cy++) {
            for (int cx = x; cx < x + w; cx++) {
                covered[cy * columns + cx] = value;
            }
        }
    }
}

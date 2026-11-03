package de.pwe.schiffeversenken.domain;

import java.util.List;

/**
 * Strategie des KI-Gegners. Sie hält keinen Zustand: Der nächste Schuss ergibt sich allein aus den Regeln und den
 * bisherigen eigenen Schüssen, so lässt sich ein Spiel nach einem Neustart des Servers fortsetzen.
 */
public interface AiStrategy {

    /** Zufällige, regelkonforme Platzierung der gesamten Flotte (gültig für {@link Board#withFleet}). */
    List<ShipPlacement> placeFleet(RuleSet rules);

    /**
     * Nächstes Ziel auf dem Feld des Gegners.
     *
     * @param shotsSoFar eigene bisherige Schüsse in der Reihenfolge, in der sie abgegeben wurden
     * @return ein Feld im Spielfeld, auf das noch nicht geschossen wurde
     * @throws IllegalStateException wenn schon auf alle Felder geschossen wurde
     */
    Coordinate nextShot(RuleSet rules, List<ShotOutcome> shotsSoFar);
}

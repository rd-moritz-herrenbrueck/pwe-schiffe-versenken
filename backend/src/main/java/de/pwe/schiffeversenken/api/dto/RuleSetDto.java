package de.pwe.schiffeversenken.api.dto;

import de.pwe.schiffeversenken.domain.RuleSet;

import java.util.List;

public record RuleSetDto(int boardWidth, int boardHeight, List<Integer> fleet, boolean shootAgainOnHit,
                         boolean shipsMayTouch) {

    public static RuleSetDto from(RuleSet rules) {
        return new RuleSetDto(rules.boardWidth(), rules.boardHeight(), rules.fleet(), rules.shootAgainOnHit(),
                rules.shipsMayTouch());
    }

    /** @throws de.pwe.schiffeversenken.domain.RuleViolationException bei ungültigen Regeln */
    public RuleSet toDomain() {
        return new RuleSet(boardWidth, boardHeight, fleet, shootAgainOnHit, shipsMayTouch);
    }
}

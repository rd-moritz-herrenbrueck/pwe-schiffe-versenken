package de.pwe.schiffeversenken.service;

import de.pwe.schiffeversenken.domain.ShotOutcome;

public record FireResult(ShotOutcome outcome, PlayerGame game) {
}

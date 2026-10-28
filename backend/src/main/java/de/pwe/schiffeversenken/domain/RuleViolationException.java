package de.pwe.schiffeversenken.domain;

/**
 * Eine Aktion verstößt gegen die Spielregeln. Der Zustand bleibt in diesem Fall unverändert.
 */
public class RuleViolationException extends RuntimeException {

    private final Violation violation;

    public RuleViolationException(Violation violation, String message) {
        super(message);
        this.violation = violation;
    }

    public Violation violation() {
        return violation;
    }
}

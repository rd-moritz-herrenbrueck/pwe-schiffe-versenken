package de.pwe.schiffeversenken.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Ergebnis der Regelprüfung. {@code code} und {@code message} sind nur bei {@code valid = false} gesetzt. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuleCheckResponse(boolean valid, String code, String message) {

    public static RuleCheckResponse ok() {
        return new RuleCheckResponse(true, null, null);
    }

    public static RuleCheckResponse invalid(String code, String message) {
        return new RuleCheckResponse(false, code, message);
    }
}

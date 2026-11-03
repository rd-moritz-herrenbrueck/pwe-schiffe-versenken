package de.pwe.schiffeversenken.api;

import de.pwe.schiffeversenken.api.dto.RuleCheckResponse;
import de.pwe.schiffeversenken.api.dto.RuleSetDto;
import de.pwe.schiffeversenken.domain.RuleViolationException;
import de.pwe.schiffeversenken.service.ErrorCode;
import de.pwe.schiffeversenken.service.ServiceException;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Path("/api/rules")
public class RulesResource {

    @Inject
    CurrentPlayer currentPlayer;

    /**
     * Prüft Regeln, ohne ein Spiel anzulegen. Ungültige Regeln sind kein Fehler der Anfrage, sie kommen als
     * {@code valid = false} mit dem Code der Verletzung zurück.
     */
    @POST
    @Path("/check")
    public RuleCheckResponse check(RuleSetDto rules) {
        currentPlayer.require();
        if (rules == null) {
            throw new ServiceException(ErrorCode.INVALID_REQUEST, "Regeln fehlen");
        }
        try {
            rules.toDomain();
            return RuleCheckResponse.ok();
        } catch (RuleViolationException e) {
            return RuleCheckResponse.invalid(e.violation().name(), e.getMessage());
        }
    }
}

package de.pwe.schiffeversenken.api;

import de.pwe.schiffeversenken.api.dto.ErrorResponse;
import de.pwe.schiffeversenken.domain.RuleViolationException;
import de.pwe.schiffeversenken.service.ErrorCode;
import de.pwe.schiffeversenken.service.ServiceException;
import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Fehler als {@link ErrorResponse}. Ungültige Eingaben → 400, unbekannte bzw. fremde Spiele → 404, Aktionen, die
 * im aktuellen Spielzustand nicht erlaubt sind → 409.
 */
public class ExceptionMappers {

    @ServerExceptionMapper
    public Response mapRuleViolation(RuleViolationException exception) {
        Status status = switch (exception.violation()) {
            case WRONG_PHASE, NOT_YOUR_TURN, CELL_ALREADY_SHOT, FLEET_ALREADY_PLACED -> Status.CONFLICT;
            case INVALID_RULE_SET, FLEET_DOES_NOT_FIT, SHIP_OUT_OF_BOUNDS, SHIPS_OVERLAP, SHIPS_TOUCHING,
                 FLEET_MISMATCH, SHOT_OUT_OF_BOUNDS -> Status.BAD_REQUEST;
        };
        return error(status, exception.violation().name(), exception.getMessage());
    }

    @ServerExceptionMapper
    public Response mapServiceException(ServiceException exception) {
        Status status = switch (exception.code()) {
            case INVALID_REQUEST, INVALID_NICKNAME -> Status.BAD_REQUEST;
            case GAME_NOT_FOUND -> Status.NOT_FOUND;
            case CANNOT_JOIN_OWN_GAME, CONCURRENT_MODIFICATION -> Status.CONFLICT;
        };
        return error(status, exception.code().name(), exception.getMessage());
    }

    @ServerExceptionMapper
    public Response mapOptimisticLock(OptimisticLockException exception) {
        return error(Status.CONFLICT, ErrorCode.CONCURRENT_MODIFICATION.name(),
                "Das Spiel wurde gleichzeitig geändert, bitte Zustand neu laden");
    }

    private static Response error(Status status, String code, String message) {
        return Response.status(status).entity(new ErrorResponse(code, message)).build();
    }
}

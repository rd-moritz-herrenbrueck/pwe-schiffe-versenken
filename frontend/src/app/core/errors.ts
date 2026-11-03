import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from './models';

/**
 * Macht aus einem Fehler der HTTP-Schicht einen `ApiError`. Meldet der Server `code` und `message`, gelten diese.
 * Sonst (z. B. Fehlerseite eines Proxys oder keine Antwort) entscheidet der Statuscode über einen verständlichen Text.
 */
export function toApiError(error: unknown): ApiError {
  if (!(error instanceof HttpErrorResponse)) {
    return NETWORK_ERROR;
  }
  const body = error.error as Partial<ApiError> | null;
  if (body?.code && body.message) {
    return { code: body.code, message: body.message };
  }
  if (error.status === 401) {
    return {
      code: 'UNAUTHORIZED',
      message: 'Deine Anmeldung ist nicht mehr gültig. Bitte melde dich neu an.',
    };
  }
  if (error.status === 429) {
    return {
      code: 'RATE_LIMITED',
      message: 'Du machst das zu schnell. Bitte warte einen Moment und versuche es erneut.',
    };
  }
  if (error.status >= 500) {
    return {
      code: 'SERVER_ERROR',
      message: 'Der Server hat einen Fehler gemeldet. Bitte versuche es gleich noch einmal.',
    };
  }
  return NETWORK_ERROR;
}

const NETWORK_ERROR: ApiError = {
  code: 'NETWORK_ERROR',
  message: 'Der Server ist nicht erreichbar. Bitte prüfe deine Verbindung und versuche es erneut.',
};

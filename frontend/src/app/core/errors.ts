import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from './models';

/** Macht aus einem Fehler der HTTP-Schicht einen `ApiError`. Ohne Antwort des Servers gilt ein Netzwerkfehler. */
export function toApiError(error: unknown): ApiError {
  const body =
    error instanceof HttpErrorResponse ? (error.error as Partial<ApiError> | null) : null;
  return body?.code && body.message
    ? { code: body.code, message: body.message }
    : { code: 'NETWORK_ERROR', message: 'Der Server ist nicht erreichbar.' };
}

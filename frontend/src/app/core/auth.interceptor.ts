import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { PlayerService } from './player.service';

/**
 * Sendet das Token als `Authorization: Bearer` an `/api`-Anfragen. Lehnt der Server das Token ab (z. B. nach einem
 * Zurücksetzen der Datenbank), wird es verworfen und die Startseite zeigt wieder die Anmeldung.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const player = inject(PlayerService);
  const router = inject(Router);
  const token = player.token();
  if (!req.url.startsWith('/api/')) {
    return next(req);
  }
  const request = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
  return next(request).pipe(
    catchError((error) => {
      if (token && error instanceof HttpErrorResponse && error.status === 401) {
        player.clear();
        void router.navigateByUrl('/');
      }
      return throwError(() => error);
    }),
  );
};

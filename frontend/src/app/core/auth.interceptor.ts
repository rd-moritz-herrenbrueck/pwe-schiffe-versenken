import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { PlayerService } from './player.service';

/** Sendet das Token als `Authorization: Bearer …` an `/api`-Anfragen. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(PlayerService).token();
  if (token && req.url.startsWith('/api/')) {
    return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
  }
  return next(req);
};

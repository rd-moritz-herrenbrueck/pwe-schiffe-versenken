import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { PlayerService } from './player.service';

/** Lässt nur Spieler mit Token durch, alle anderen landen auf der Startseite. */
export const authGuard: CanActivateFn = () =>
  inject(PlayerService).loggedIn() ? true : inject(Router).createUrlTree(['/']);

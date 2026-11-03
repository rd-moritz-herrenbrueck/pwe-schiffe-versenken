import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { GamePage } from './pages/game/game-page';
import { HomePage } from './pages/home/home-page';
import { LobbyListPage } from './pages/lobby-list/lobby-list-page';
import { MyGamesPage } from './pages/my-games/my-games-page';

export const routes: Routes = [
  { path: '', component: HomePage },
  { path: 'lobbies', component: LobbyListPage, canActivate: [authGuard] },
  { path: 'games', component: MyGamesPage, canActivate: [authGuard] },
  { path: 'games/:id', component: GamePage, canActivate: [authGuard] },
  { path: '**', redirectTo: '' },
];

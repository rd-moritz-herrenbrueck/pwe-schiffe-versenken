import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  GameSummary,
  GameView,
  LobbySummary,
  PlayerCreated,
  RuleSet,
  ShipPlacement,
  ShotResponse,
} from './models';

/** Dünne Schicht über der REST-API des Backends. Fehler kommen als `HttpErrorResponse` mit `ApiError` im Body. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  createPlayer(nickname: string): Observable<PlayerCreated> {
    return this.http.post<PlayerCreated>('/api/players', { nickname });
  }

  listLobbies(): Observable<LobbySummary[]> {
    return this.http.get<LobbySummary[]>('/api/lobbies');
  }

  createLobby(rules?: RuleSet): Observable<GameView> {
    return this.http.post<GameView>('/api/lobbies', rules ? { rules } : {});
  }

  joinLobby(gameId: string): Observable<GameView> {
    return this.http.post<GameView>('/api/lobbies/join', { gameId });
  }

  listGames(): Observable<GameSummary[]> {
    return this.http.get<GameSummary[]>('/api/games');
  }

  getGame(id: string): Observable<GameView> {
    return this.http.get<GameView>(`/api/games/${id}`);
  }

  placeFleet(id: string, ships: ShipPlacement[]): Observable<GameView> {
    return this.http.put<GameView>(`/api/games/${id}/fleet`, { ships });
  }

  fire(id: string, x: number, y: number): Observable<ShotResponse> {
    return this.http.post<ShotResponse>(`/api/games/${id}/shots`, { x, y });
  }

  resign(id: string): Observable<GameView> {
    return this.http.post<GameView>(`/api/games/${id}/resign`, {});
  }
}

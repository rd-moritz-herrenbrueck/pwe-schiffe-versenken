import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ChatMessage,
  CreateLobbyOptions,
  GameSummary,
  GameView,
  LobbySummary,
  PlayerCreated,
  RuleCheck,
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

  createLobby(options: CreateLobbyOptions = {}): Observable<GameView> {
    const body: CreateLobbyOptions = {};
    if (options.rules) {
      body.rules = options.rules;
    }
    if (options.visibility) {
      body.visibility = options.visibility;
    }
    if (options.vsAi !== undefined) {
      body.vsAi = options.vsAi;
    }
    return this.http.post<GameView>('/api/lobbies', body);
  }

  joinLobby(gameId: string): Observable<GameView> {
    return this.http.post<GameView>('/api/lobbies/join', { gameId });
  }

  joinByInviteCode(inviteCode: string): Observable<GameView> {
    return this.http.post<GameView>('/api/lobbies/join', { inviteCode });
  }

  /** Der Server prüft, ob die Regeln gültig und die Flotte platzierbar ist. */
  checkRules(rules: RuleSet): Observable<RuleCheck> {
    return this.http.post<RuleCheck>('/api/rules/check', rules);
  }

  listChat(id: string): Observable<ChatMessage[]> {
    return this.http.get<ChatMessage[]>(`/api/games/${id}/chat`);
  }

  sendChat(id: string, text: string): Observable<ChatMessage> {
    return this.http.post<ChatMessage>(`/api/games/${id}/chat`, { text });
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

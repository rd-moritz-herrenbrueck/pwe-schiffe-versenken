import { Injectable, InjectionToken, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { GameEvent, LobbyEvent } from './models';
import { PlayerService } from './player.service';

/** Erzeugt die `EventSource`. Austauschbar, damit Tests keinen Browser brauchen. */
export const EVENT_SOURCE_FACTORY = new InjectionToken<(url: string) => EventSource>(
  'EVENT_SOURCE_FACTORY',
  {
    providedIn: 'root',
    factory: () => (url: string) => new EventSource(url),
  },
);

/** Wartezeiten vor den Wiederverbindungsversuchen, wenn der Browser die Verbindung endgültig geschlossen hat. */
export const RECONNECT_DELAYS_MS: readonly number[] = [1000, 2000, 5000];

const EVENT_SOURCE_CLOSED = 2;
const GAME_EVENTS = [
  'CONNECTED',
  'PLAYER_JOINED',
  'FLEET_READY',
  'GAME_STARTED',
  'SHOT',
  'TURN_CHANGED',
  'GAME_FINISHED',
  'CHAT',
] as const;
const LOBBY_EVENTS = ['CONNECTED', 'LOBBY_OPENED', 'LOBBY_CLOSED'] as const;

/** SSE-Streams des Backends als Observables. Abmelden schließt die Verbindung. */
@Injectable({ providedIn: 'root' })
export class EventService {
  private readonly player = inject(PlayerService);
  private readonly createSource = inject(EVENT_SOURCE_FACTORY);

  watchGame(gameId: string): Observable<GameEvent> {
    return this.watch<GameEvent>(`/api/games/${gameId}/events`, GAME_EVENTS);
  }

  watchLobbies(): Observable<LobbyEvent> {
    return this.watch<LobbyEvent>('/api/lobbies/events', LOBBY_EVENTS);
  }

  private watch<T>(path: string, names: readonly string[]): Observable<T> {
    return new Observable<T>((subscriber) => {
      const token = this.player.token();
      if (!token) {
        subscriber.error(new Error('Kein Token vorhanden'));
        return undefined;
      }
      const url = `${path}?access_token=${encodeURIComponent(token)}`;
      let source: EventSource | undefined;
      let timer: ReturnType<typeof setTimeout> | undefined;
      let failedAttempts = 0;

      const connect = (): void => {
        const current = this.createSource(url);
        source = current;
        for (const name of names) {
          current.addEventListener(name, (event) => {
            if (name === 'CONNECTED') {
              failedAttempts = 0;
            }
            subscriber.next(JSON.parse((event as MessageEvent<string>).data) as T);
          });
        }
        current.addEventListener('error', () => {
          // Solange der Browser selbst neu verbindet, ist nichts zu tun.
          if (current.readyState !== EVENT_SOURCE_CLOSED) {
            return;
          }
          current.close();
          if (failedAttempts >= RECONNECT_DELAYS_MS.length) {
            subscriber.error(new Error('Ereignisstrom unterbrochen'));
            return;
          }
          timer = setTimeout(connect, RECONNECT_DELAYS_MS[failedAttempts++]);
        });
      };

      connect();
      return () => {
        clearTimeout(timer);
        source?.close();
      };
    });
  }
}

import { Injectable, Signal, inject, signal } from '@angular/core';
import { EMPTY, Observable, Subject, Subscription, catchError, switchMap } from 'rxjs';
import { ApiService } from './api.service';
import { toApiError } from './errors';
import { EventService } from './event.service';
import { ApiError, ChatMessage, GameView, ShipPlacement, ShotResponse } from './models';

/** Zustand des gerade geöffneten Spiels. Der Server ist die Wahrheit, der Store lädt nach jedem Ereignis neu. */
@Injectable({ providedIn: 'root' })
export class GameStore {
  private readonly api = inject(ApiService);
  private readonly events = inject(EventService);

  private readonly gameState = signal<GameView | null>(null);
  private readonly errorState = signal<ApiError | null>(null);
  private readonly lastShotState = signal<Pick<ShotResponse, 'result' | 'sunkShip'> | null>(null);
  private readonly messagesState = signal<ChatMessage[]>([]);
  private readonly unreadState = signal(0);

  readonly game: Signal<GameView | null> = this.gameState.asReadonly();
  readonly error: Signal<ApiError | null> = this.errorState.asReadonly();
  readonly lastShot: Signal<Pick<ShotResponse, 'result' | 'sunkShip'> | null> =
    this.lastShotState.asReadonly();

  /** Chatverlauf des geöffneten Spiels, aufsteigend nach Zeit. */
  readonly messages: Signal<ChatMessage[]> = this.messagesState.asReadonly();
  /** Nachrichten des Gegners, die eingegangen sind, während der Chat nicht sichtbar war. */
  readonly unreadChat: Signal<number> = this.unreadState.asReadonly();

  private gameId: string | null = null;
  private subscription = new Subscription();
  private reload = new Subject<void>();
  private chatLoaded = false;
  private chatVisible = false;

  open(id: string): void {
    this.close();
    this.gameId = id;
    this.errorState.set(null);

    this.subscription.add(
      this.reload
        .pipe(
          switchMap(() =>
            this.api.getGame(id).pipe(
              catchError((error) => {
                this.fail(error);
                return EMPTY;
              }),
            ),
          ),
        )
        .subscribe((game) => {
          this.errorState.set(null);
          this.gameState.set(game);
        }),
    );
    this.subscription.add(
      this.events.watchGame(id).subscribe({
        next: (event) => {
          if (event.type === 'CHAT') {
            if (event.message) {
              this.addMessage(event.message, true);
            }
            return;
          }
          if (event.type === 'CONNECTED' && this.chatLoaded) {
            this.loadChat();
          }
          this.reload.next();
        },
        error: () =>
          this.errorState.set({
            code: 'STREAM_CLOSED',
            message: 'Die Verbindung zum Server ist abgebrochen. Bitte Seite neu laden.',
          }),
      }),
    );
    this.reload.next();
  }

  close(): void {
    this.subscription.unsubscribe();
    this.subscription = new Subscription();
    this.reload = new Subject<void>();
    this.gameId = null;
    this.gameState.set(null);
    this.errorState.set(null);
    this.lastShotState.set(null);
    this.messagesState.set([]);
    this.unreadState.set(0);
    this.chatLoaded = false;
    this.chatVisible = false;
  }

  placeFleet(ships: ShipPlacement[]): void {
    this.act(
      (id) => this.api.placeFleet(id, ships),
      (game) => this.gameState.set(game),
    );
  }

  fire(x: number, y: number): void {
    this.act(
      (id) => this.api.fire(id, x, y),
      (response) => {
        this.lastShotState.set({ result: response.result, sunkShip: response.sunkShip });
        this.gameState.set(response.game);
      },
    );
  }

  resign(): void {
    this.act(
      (id) => this.api.resign(id),
      (game) => this.gameState.set(game),
    );
  }

  /** Lädt den Verlauf und führt ihn mit bereits per Ereignis eingegangenen Nachrichten zusammen. */
  loadChat(): void {
    const id = this.gameId;
    if (id === null) {
      return;
    }
    this.chatLoaded = true;
    this.subscription.add(
      this.api.listChat(id).subscribe({
        next: (history) => this.mergeHistory(history),
        error: (error) => this.fail(error),
      }),
    );
  }

  sendChat(text: string): void {
    this.act(
      (id) => this.api.sendChat(id, text),
      (message) => this.addMessage(message, false),
    );
  }

  /** Solange der Chat sichtbar ist, bleibt `unreadChat` bei 0. */
  setChatVisible(visible: boolean): void {
    this.chatVisible = visible;
    if (visible) {
      this.unreadState.set(0);
    }
  }

  /** Der Verlauf des Servers geht vor, danach folgen Nachrichten, die er noch nicht kannte (per Ereignis). */
  private mergeHistory(history: ChatMessage[]): void {
    this.messagesState.update((known) => [
      ...history,
      ...known.filter((message) => !history.some((stored) => stored.id === message.id)),
    ]);
  }

  /** Nachrichten sind über die `id` eindeutig (Antwort auf das Senden und Ereignis liefern dieselbe). */
  private addMessage(message: ChatMessage, live: boolean): void {
    if (this.messagesState().some((known) => known.id === message.id)) {
      return;
    }
    this.messagesState.update((list) => [...list, message]);
    if (live && !this.chatVisible && message.side !== this.gameState()?.you) {
      this.unreadState.update((count) => count + 1);
    }
  }

  private act<T>(request: (id: string) => Observable<T>, apply: (response: T) => void): void {
    const id = this.gameId;
    if (id === null) {
      return;
    }
    this.errorState.set(null);
    this.subscription.add(
      request(id).subscribe({
        next: apply,
        error: (error) => {
          this.fail(error);
          if (this.error()?.code === 'CONCURRENT_MODIFICATION') {
            this.reload.next();
          }
        },
      }),
    );
  }

  private fail(error: unknown): void {
    this.errorState.set(toApiError(error));
  }
}

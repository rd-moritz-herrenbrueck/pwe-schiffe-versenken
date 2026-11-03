import { Injectable, Signal, inject, signal } from '@angular/core';
import { EMPTY, Observable, Subject, Subscription, catchError, switchMap } from 'rxjs';
import { ApiService } from './api.service';
import { toApiError } from './errors';
import { EventService } from './event.service';
import { ApiError, GameView, ShipPlacement, ShotResponse } from './models';

/** Zustand des gerade geöffneten Spiels. Der Server ist die Wahrheit, der Store lädt nach jedem Ereignis neu. */
@Injectable({ providedIn: 'root' })
export class GameStore {
  private readonly api = inject(ApiService);
  private readonly events = inject(EventService);

  private readonly gameState = signal<GameView | null>(null);
  private readonly errorState = signal<ApiError | null>(null);
  private readonly lastShotState = signal<Pick<ShotResponse, 'result' | 'sunkShip'> | null>(null);

  readonly game: Signal<GameView | null> = this.gameState.asReadonly();
  readonly error: Signal<ApiError | null> = this.errorState.asReadonly();
  readonly lastShot: Signal<Pick<ShotResponse, 'result' | 'sunkShip'> | null> =
    this.lastShotState.asReadonly();

  private gameId: string | null = null;
  private subscription = new Subscription();
  private reload = new Subject<void>();

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
        next: () => this.reload.next(),
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

import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { ButtonDirective } from 'primeng/button';
import { Card } from 'primeng/card';
import { Message } from 'primeng/message';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { EventService } from '../../core/event.service';
import { toApiError } from '../../core/errors';
import { GameView, LobbyEvent, LobbySummary } from '../../core/models';

@Component({
  selector: 'app-lobby-list-page',
  imports: [ButtonDirective, Card, Message],
  styleUrl: './lobby-list-page.scss',
  template: `
    <p-card header="Offene Lobbys">
      <div class="toolbar">
        <button pButton type="button" data-testid="create-lobby-button" (click)="create()">
          <i class="pi pi-plus" aria-hidden="true"></i>
          Lobby erstellen
        </button>
      </div>

      @if (error(); as message) {
        <p-message severity="error" data-testid="error-message">{{ message }}</p-message>
      }

      @if (lobbies(); as list) {
        @if (list.length === 0) {
          <p data-testid="lobby-empty">Gerade ist keine Lobby offen. Erstelle eine eigene!</p>
        } @else {
          <ul class="lobbies">
            @for (lobby of list; track lobby.id) {
              <li class="lobby" [attr.data-testid]="'lobby-' + lobby.id">
                <span class="creator">{{ lobby.creatorNickname }}</span>
                <span class="rules">
                  {{ lobby.rules.boardWidth }}×{{ lobby.rules.boardHeight }}, Flotte
                  {{ lobby.rules.fleet.join(', ') }}
                </span>
                <button
                  pButton
                  type="button"
                  size="small"
                  [attr.data-testid]="'join-lobby-' + lobby.id"
                  (click)="join(lobby)"
                >
                  Beitreten
                </button>
              </li>
            }
          </ul>
        }
      }
    </p-card>
  `,
})
export class LobbyListPage {
  private readonly api = inject(ApiService);
  private readonly events = inject(EventService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly lobbies = signal<LobbySummary[] | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.load();
    this.events
      .watchLobbies()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (event) => this.onEvent(event),
        error: () =>
          this.error.set('Die Live-Aktualisierung ist abgebrochen. Bitte Seite neu laden.'),
      });
  }

  protected create(): void {
    this.enter(this.api.createLobby());
  }

  protected join(lobby: LobbySummary): void {
    this.enter(this.api.joinLobby(lobby.id));
  }

  private enter(request: Observable<GameView>): void {
    this.error.set(null);
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (game) => void this.router.navigate(['/games', game.id]),
      error: (error) => this.error.set(toApiError(error).message),
    });
  }

  private load(): void {
    this.api
      .listLobbies()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (lobbies) => {
          this.error.set(null);
          this.lobbies.set(lobbies);
        },
        error: (error) => this.error.set(toApiError(error).message),
      });
  }

  private onEvent(event: LobbyEvent): void {
    switch (event.type) {
      case 'CONNECTED':
        this.load();
        break;
      case 'LOBBY_OPENED': {
        const opened = event.lobby;
        if (opened) {
          this.lobbies.update((list) =>
            (list ?? []).some((l) => l.id === opened.id) ? list : [...(list ?? []), opened],
          );
        }
        break;
      }
      case 'LOBBY_CLOSED':
        this.lobbies.update((list) => (list ?? []).filter((l) => l.id !== event.gameId));
        break;
    }
  }
}

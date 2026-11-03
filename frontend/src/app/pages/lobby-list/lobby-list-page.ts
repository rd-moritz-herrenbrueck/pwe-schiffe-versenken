import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { ButtonDirective } from 'primeng/button';
import { Card } from 'primeng/card';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Observable } from 'rxjs';
import { CreateLobbyForm } from '../../components/create-lobby-form/create-lobby-form';
import { ApiService } from '../../core/api.service';
import { EventService } from '../../core/event.service';
import { toApiError } from '../../core/errors';
import { CreateLobbyOptions, GameView, LobbyEvent, LobbySummary } from '../../core/models';

@Component({
  selector: 'app-lobby-list-page',
  imports: [ButtonDirective, Card, CreateLobbyForm, InputText, Message],
  styleUrl: './lobby-list-page.scss',
  template: `
    <p-card header="Offene Lobbys">
      <div class="toolbar">
        <button pButton type="button" data-testid="create-lobby-button" (click)="create()">
          <i class="pi pi-plus" aria-hidden="true"></i>
          Lobby erstellen
        </button>
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="open-lobby-form-button"
          [attr.aria-expanded]="formOpen()"
          (click)="formOpen.set(!formOpen())"
        >
          <i class="pi pi-sliders-h" aria-hidden="true"></i>
          Mit Optionen
        </button>
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="play-vs-ai-button"
          (click)="playAgainstAi()"
        >
          <i class="pi pi-desktop" aria-hidden="true"></i>
          Gegen die KI spielen
        </button>
      </div>

      @if (formOpen()) {
        <app-create-lobby-form data-testid="create-lobby-form" (submitted)="createWith($event)" />
      }

      <div class="invite">
        <input
          pInputText
          data-testid="invite-code-input"
          aria-label="Einladungscode"
          placeholder="Einladungscode"
          autocomplete="off"
          [value]="inviteCode()"
          (input)="inviteCode.set($any($event.target).value)"
          (keydown.enter)="joinByCode()"
        />
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="join-by-code-button"
          [disabled]="!inviteCode().trim()"
          (click)="joinByCode()"
        >
          Mit Code beitreten
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
  protected readonly formOpen = signal(false);
  protected readonly inviteCode = signal('');

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

  protected createWith(options: Required<CreateLobbyOptions>): void {
    this.enter(this.api.createLobby(options));
  }

  protected playAgainstAi(): void {
    this.enter(this.api.createLobby({ vsAi: true }));
  }

  protected joinByCode(): void {
    const code = this.inviteCode().trim();
    if (code) {
      this.enter(this.api.joinByInviteCode(code));
    }
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

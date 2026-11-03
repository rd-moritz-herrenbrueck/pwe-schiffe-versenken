import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Card } from 'primeng/card';
import { Message } from 'primeng/message';
import { Tag } from 'primeng/tag';
import { ApiService } from '../../core/api.service';
import { toApiError } from '../../core/errors';
import { GamePhase, GameSummary } from '../../core/models';

const PHASE_LABELS: Record<GamePhase, string> = {
  WAITING: 'Wartet auf Gegner',
  PLACEMENT: 'Flotte platzieren',
  RUNNING: 'Läuft',
  FINISHED: 'Beendet',
  ABANDONED: 'Abgebrochen',
};

@Component({
  selector: 'app-my-games-page',
  imports: [Card, Message, RouterLink, Tag],
  styleUrl: './my-games-page.scss',
  template: `
    <p-card header="Meine Spiele">
      @if (error(); as message) {
        <p-message severity="error" data-testid="error-message">{{ message }}</p-message>
      }
      @if (games(); as list) {
        @if (list.length === 0) {
          <p data-testid="no-games">Du hast noch keine Spiele. Such dir eine Lobby aus!</p>
        } @else {
          <ul class="games">
            @for (game of list; track game.id) {
              <li
                [attr.data-testid]="'game-' + game.id"
                [attr.data-phase]="game.phase"
                [attr.data-your-turn]="game.yourTurn"
                [attr.data-result]="game.result"
              >
                <a [routerLink]="['/games', game.id]">
                  <span class="opponent">
                    {{
                      game.opponentNickname ? 'gegen ' + game.opponentNickname : 'Noch kein Gegner'
                    }}
                  </span>
                  <span class="status">
                    <p-tag [value]="phaseLabel(game.phase)" severity="secondary" />
                    @if (game.result === 'WON') {
                      <p-tag value="Gewonnen" severity="success" />
                    } @else if (game.result === 'LOST') {
                      <p-tag value="Verloren" severity="danger" />
                    } @else if (game.phase === 'RUNNING' && game.yourTurn) {
                      <p-tag value="Du bist dran" severity="info" />
                    }
                  </span>
                </a>
              </li>
            }
          </ul>
        }
      }
    </p-card>
  `,
})
export class MyGamesPage {
  private readonly destroyRef = inject(DestroyRef);

  protected readonly games = signal<GameSummary[] | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    inject(ApiService)
      .listGames()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (games) => this.games.set(games),
        error: (error) => this.error.set(toApiError(error).message),
      });
  }

  protected phaseLabel(phase: GamePhase): string {
    return PHASE_LABELS[phase];
  }
}

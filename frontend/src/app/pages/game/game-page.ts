import { Component, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { ButtonDirective } from 'primeng/button';
import { Card } from 'primeng/card';
import { Message } from 'primeng/message';
import { Tag } from 'primeng/tag';
import { GameStore } from '../../core/game.store';
import { ShotResult } from '../../core/models';
import { Chat } from '../../components/chat/chat';
import { Board, CellPosition } from '../../components/board/board';
import { FleetPlacement } from '../../components/fleet-placement/fleet-placement';

const SHOT_LABELS: Record<ShotResult, string> = {
  MISS: 'Wasser',
  HIT: 'Treffer!',
  SUNK: 'Schiff versenkt!',
  WIN: 'Letztes Schiff versenkt!',
};

@Component({
  selector: 'app-game-page',
  imports: [Board, ButtonDirective, Card, Chat, FleetPlacement, Message, Tag],
  styleUrl: './game-page.scss',
  template: `
    @if (store.error(); as error) {
      <p-message severity="error" data-testid="error-message">{{ error.message }}</p-message>
    }

    @if (store.game(); as game) {
      <p-card>
        <div class="header">
          <h2>
            @if (game.opponentNickname) {
              Spiel gegen <span data-testid="opponent-name">{{ game.opponentNickname }}</span>
            } @else {
              Neues Spiel
            }
          </h2>
          @if (game.phase === 'PLACEMENT' || game.phase === 'RUNNING') {
            @if (confirmingResign()) {
              <span class="resign-confirm">
                Wirklich aufgeben?
                <button
                  pButton
                  type="button"
                  size="small"
                  severity="danger"
                  data-testid="resign-confirm-button"
                  (click)="resign()"
                >
                  Ja, aufgeben
                </button>
                <button
                  pButton
                  type="button"
                  size="small"
                  severity="secondary"
                  data-testid="resign-cancel-button"
                  (click)="confirmingResign.set(false)"
                >
                  Abbrechen
                </button>
              </span>
            } @else {
              <button
                pButton
                type="button"
                size="small"
                severity="danger"
                [outlined]="true"
                data-testid="resign-button"
                (click)="confirmingResign.set(true)"
              >
                Aufgeben
              </button>
            }
          }
        </div>

        @if (game.phase === 'PLACEMENT' || game.phase === 'RUNNING' || game.phase === 'FINISHED') {
          <div class="chat-bar">
            <button
              pButton
              type="button"
              size="small"
              severity="secondary"
              data-testid="chat-toggle"
              [attr.aria-expanded]="chatOpen()"
              (click)="toggleChat()"
            >
              <i class="pi pi-comments" aria-hidden="true"></i>
              Chat
            </button>
            @if (store.unreadChat() > 0) {
              <p-tag
                severity="danger"
                data-testid="chat-unread"
                [value]="store.unreadChat().toString()"
              />
            }
          </div>
          @if (chatOpen()) {
            <app-chat
              data-testid="chat"
              [messages]="store.messages()"
              [you]="game.you"
              (send)="store.sendChat($event)"
            />
          }
        }

        @switch (game.phase) {
          @case ('WAITING') {
            <p data-testid="waiting-for-opponent">Warte auf einen Gegner …</p>
            @if (game.inviteCode) {
              <p>
                Einladungscode für deinen Gegner:
                <strong class="invite-code" data-testid="invite-code">{{ game.inviteCode }}</strong>
              </p>
            }
          }
          @case ('PLACEMENT') {
            @if (game.yourFleetReady) {
              <p data-testid="waiting-for-fleet">
                Deine Flotte steht. Der Gegner platziert noch seine Schiffe …
              </p>
              <app-board
                data-testid="own-board"
                [width]="game.rules.boardWidth"
                [height]="game.rules.boardHeight"
                [ships]="game.ownBoard.ships"
                [shots]="game.ownBoard.shots"
              />
            } @else {
              <app-fleet-placement
                data-testid="fleet-placement"
                [rules]="game.rules"
                (confirmed)="store.placeFleet($event)"
              />
            }
          }
          @case ('ABANDONED') {
            <p data-testid="abandoned-message">Dieses Spiel wurde wegen Inaktivität abgebrochen.</p>
          }
          @default {
            @if (game.phase === 'RUNNING') {
              <p-tag
                data-testid="turn-indicator"
                [attr.data-your-turn]="game.yourTurn"
                [severity]="game.yourTurn ? 'success' : 'secondary'"
                [value]="game.yourTurn ? 'Du bist dran' : 'Gegner ist dran'"
              />
            } @else if (game.result) {
              <p-message
                data-testid="result-message"
                [attr.data-result]="game.result"
                [severity]="game.result === 'WON' ? 'success' : 'error'"
              >
                {{ game.result === 'WON' ? 'Du hast gewonnen!' : 'Du hast verloren.' }}
              </p-message>
            }

            @if (store.lastShot(); as shot) {
              <p data-testid="shot-feedback" [attr.data-result]="shot.result">
                Dein letzter Schuss: {{ shotLabel(shot.result) }}
              </p>
            }

            <div class="boards">
              <section>
                <h3>Deine Flotte</h3>
                <app-board
                  data-testid="own-board"
                  [width]="game.rules.boardWidth"
                  [height]="game.rules.boardHeight"
                  [ships]="game.ownBoard.ships"
                  [shots]="game.ownBoard.shots"
                />
              </section>
              <section>
                <h3>Gegnerische Gewässer</h3>
                <app-board
                  data-testid="enemy-board"
                  [width]="game.rules.boardWidth"
                  [height]="game.rules.boardHeight"
                  [ships]="game.enemyBoard.ships"
                  [shots]="game.enemyBoard.shots"
                  [interactive]="game.phase === 'RUNNING' && game.yourTurn"
                  (cellSelected)="fire($event)"
                />
              </section>
            </div>
          }
        }
      </p-card>
    }
  `,
})
export class GamePage {
  /** Kommt aus der Route `games/:id` (Router-Input-Binding). */
  readonly id = input.required<string>();

  protected readonly store = inject(GameStore);
  protected readonly confirmingResign = signal(false);
  protected readonly chatOpen = signal(false);

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => {
        this.confirmingResign.set(false);
        this.chatOpen.set(false);
        this.store.open(id);
      });
    });
    inject(DestroyRef).onDestroy(() => this.store.close());
  }

  protected toggleChat(): void {
    const open = !this.chatOpen();
    this.chatOpen.set(open);
    this.store.setChatVisible(open);
    if (open) {
      this.store.loadChat();
    }
  }

  protected fire(cell: CellPosition): void {
    this.store.fire(cell.x, cell.y);
  }

  protected resign(): void {
    this.confirmingResign.set(false);
    this.store.resign();
  }

  protected shotLabel(result: ShotResult): string {
    return SHOT_LABELS[result];
  }
}

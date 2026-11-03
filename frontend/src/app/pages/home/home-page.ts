import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ButtonDirective } from 'primeng/button';
import { Card } from 'primeng/card';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { ApiService } from '../../core/api.service';
import { toApiError } from '../../core/errors';
import { PlayerService } from '../../core/player.service';

const MAX_NICKNAME_LENGTH = 20;

@Component({
  selector: 'app-home-page',
  imports: [ButtonDirective, Card, InputText, Message],
  styleUrl: './home-page.scss',
  template: `
    <p-card header="Willkommen an Bord">
      <p>Wie sollen dich deine Gegner nennen?</p>
      <div class="form">
        <label for="nickname">Nickname</label>
        <input
          pInputText
          id="nickname"
          data-testid="nickname-input"
          autocomplete="nickname"
          [attr.maxlength]="maxLength"
          [value]="nickname()"
          (input)="nickname.set($any($event.target).value)"
          (keydown.enter)="start()"
        />
        <button
          pButton
          type="button"
          data-testid="start-button"
          [disabled]="!canStart()"
          (click)="start()"
        >
          Los geht's
        </button>
        @if (error(); as message) {
          <p-message severity="error" data-testid="error-message">{{ message }}</p-message>
        }
      </div>
    </p-card>
  `,
})
export class HomePage {
  private readonly api = inject(ApiService);
  private readonly player = inject(PlayerService);
  private readonly router = inject(Router);

  protected readonly maxLength = MAX_NICKNAME_LENGTH;
  protected readonly nickname = signal('');
  protected readonly error = signal<string | null>(null);
  private readonly pending = signal(false);

  protected readonly canStart = computed(() => {
    const length = this.nickname().trim().length;
    return length >= 1 && length <= MAX_NICKNAME_LENGTH && !this.pending();
  });

  constructor() {
    if (this.player.loggedIn()) {
      void this.router.navigateByUrl('/lobbies');
    }
  }

  protected start(): void {
    if (!this.canStart()) {
      return;
    }
    this.pending.set(true);
    this.error.set(null);
    this.api.createPlayer(this.nickname().trim()).subscribe({
      next: (created) => {
        this.player.save({ nickname: created.nickname, token: created.token });
        void this.router.navigateByUrl('/lobbies');
      },
      error: (error) => {
        this.pending.set(false);
        this.error.set(toApiError(error).message);
      },
    });
  }
}

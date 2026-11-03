import { Component, input, output, signal } from '@angular/core';
import { ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { ChatMessage, PlayerSide } from '../../core/models';

const MAX_MESSAGE_LENGTH = 500;

/** Chatverlauf und Eingabe. Rein darstellend: Nachrichten kommen von außen, `send` meldet den getrimmten Text. */
@Component({
  selector: 'app-chat',
  imports: [ButtonDirective, InputText],
  styleUrl: './chat.scss',
  template: `
    @if (messages().length === 0) {
      <p class="empty" data-testid="chat-empty">Noch keine Nachrichten.</p>
    } @else {
      <ul class="messages" aria-live="polite">
        @for (message of messages(); track message.id) {
          <li
            class="message"
            [attr.data-testid]="'chat-message-' + message.id"
            [attr.data-own]="message.side === you()"
          >
            <span class="sender">{{ message.nickname }}</span>
            <span class="text">{{ message.text }}</span>
          </li>
        }
      </ul>
    }
    <div class="compose">
      <input
        pInputText
        data-testid="chat-input"
        aria-label="Nachricht"
        autocomplete="off"
        [attr.maxlength]="maxLength"
        [value]="draft()"
        (input)="draft.set($any($event.target).value)"
        (keydown.enter)="submit()"
      />
      <button
        pButton
        type="button"
        data-testid="chat-send-button"
        [disabled]="!draft().trim()"
        (click)="submit()"
      >
        Senden
      </button>
    </div>
  `,
})
export class Chat {
  readonly messages = input.required<ChatMessage[]>();
  /** Eigene Seite, um eigene und fremde Nachrichten zu unterscheiden. */
  readonly you = input.required<PlayerSide>();
  readonly send = output<string>();

  protected readonly maxLength = MAX_MESSAGE_LENGTH;
  protected readonly draft = signal('');

  protected submit(): void {
    const text = this.draft().trim();
    if (!text) {
      return;
    }
    this.send.emit(text);
    this.draft.set('');
  }
}

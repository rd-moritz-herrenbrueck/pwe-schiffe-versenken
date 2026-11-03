import { Injectable, Signal, computed, signal } from '@angular/core';

const TOKEN_KEY = 'schiffe.token';
const NICKNAME_KEY = 'schiffe.nickname';

/** Hält Token und Nickname des Spielers und speichert sie im `localStorage`. */
@Injectable({ providedIn: 'root' })
export class PlayerService {
  private readonly tokenState = signal<string | null>(localStorage.getItem(TOKEN_KEY));
  private readonly nicknameState = signal<string | null>(localStorage.getItem(NICKNAME_KEY));

  readonly token: Signal<string | null> = this.tokenState.asReadonly();
  readonly nickname: Signal<string | null> = this.nicknameState.asReadonly();
  readonly loggedIn: Signal<boolean> = computed(() => this.tokenState() !== null);

  save(player: { nickname: string; token: string }): void {
    localStorage.setItem(TOKEN_KEY, player.token);
    localStorage.setItem(NICKNAME_KEY, player.nickname);
    this.tokenState.set(player.token);
    this.nicknameState.set(player.nickname);
  }

  clear(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(NICKNAME_KEY);
    this.tokenState.set(null);
    this.nicknameState.set(null);
  }
}

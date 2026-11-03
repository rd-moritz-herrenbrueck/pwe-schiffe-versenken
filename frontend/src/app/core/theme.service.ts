import { Injectable, Signal, signal } from '@angular/core';

export type ThemeMode = 'light' | 'dark';

/** CSS-Klasse am `<html>`-Element, die den Dunkelmodus aktiviert (PrimeNG `darkModeSelector`). */
export const DARK_MODE_CLASS = 'app-dark';

const STORAGE_KEY = 'schiffe.theme';

function initialMode(): ThemeMode {
  const stored = localStorage.getItem(STORAGE_KEY);
  if (stored === 'light' || stored === 'dark') {
    return stored;
  }
  return typeof matchMedia === 'function' && matchMedia('(prefers-color-scheme: dark)').matches
    ? 'dark'
    : 'light';
}

/** Hell-/Dunkelmodus. Startwert: gespeicherte Wahl, sonst Systemeinstellung, sonst hell. */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly state = signal<ThemeMode>(initialMode());
  readonly mode: Signal<ThemeMode> = this.state.asReadonly();

  constructor() {
    this.apply(this.state());
  }

  set(mode: ThemeMode): void {
    localStorage.setItem(STORAGE_KEY, mode);
    this.state.set(mode);
    this.apply(mode);
  }

  toggle(): void {
    this.set(this.state() === 'dark' ? 'light' : 'dark');
  }

  private apply(mode: ThemeMode): void {
    document.documentElement.classList.toggle(DARK_MODE_CLASS, mode === 'dark');
  }
}

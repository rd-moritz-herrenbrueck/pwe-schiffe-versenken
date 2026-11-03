import { Component, DestroyRef, inject, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Subject, catchError, map, of, switchMap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { toApiError } from '../../core/errors';
import { CreateLobbyOptions, RuleSet } from '../../core/models';

const MIN_BOARD_SIZE = 1;
const MAX_BOARD_SIZE = 25;
const MIN_SHIP_LENGTH = 2;

type CheckState = 'valid' | 'checking' | 'invalid';
type CheckResult = { valid: true } | { valid: false; message: string };

/**
 * Formular für Regeln und Optionen einer neuen Lobby. Die Regeln prüft der Server (`ApiService.checkRules`),
 * abgeschickt wird nur, was er als gültig bestätigt hat. Die Standardregeln gelten ohne Anfrage als gültig.
 */
@Component({
  selector: 'app-create-lobby-form',
  imports: [ButtonDirective, InputText, Message],
  styleUrl: './create-lobby-form.scss',
  template: `
    <div class="field-row">
      <label>
        Breite
        <input
          pInputText
          type="number"
          data-testid="rules-width"
          [attr.min]="minBoardSize"
          [attr.max]="maxBoardSize"
          [value]="width()"
          (input)="setWidth($any($event.target).value)"
        />
      </label>
      <label>
        Höhe
        <input
          pInputText
          type="number"
          data-testid="rules-height"
          [attr.min]="minBoardSize"
          [attr.max]="maxBoardSize"
          [value]="height()"
          (input)="setHeight($any($event.target).value)"
        />
      </label>
    </div>

    <fieldset>
      <legend>Flotte</legend>
      <ul class="fleet">
        @for (length of fleet(); track $index) {
          <li [attr.data-testid]="'fleet-ship-' + $index" [attr.data-length]="length">
            Schiff der Länge {{ length }}
            <button
              pButton
              type="button"
              size="small"
              severity="secondary"
              [text]="true"
              [attr.data-testid]="'fleet-remove-' + $index"
              [attr.aria-label]="'Schiff der Länge ' + length + ' entfernen'"
              (click)="removeShip($index)"
            >
              <i class="pi pi-times" aria-hidden="true"></i>
            </button>
          </li>
        }
      </ul>
      <div class="field-row">
        <label>
          Neues Schiff, Länge
          <input
            pInputText
            type="number"
            data-testid="fleet-new-length"
            [attr.min]="minShipLength"
            [value]="newLength()"
            (input)="newLength.set(+$any($event.target).value)"
          />
        </label>
        <button
          pButton
          type="button"
          size="small"
          data-testid="fleet-add-button"
          [disabled]="!canAddShip()"
          (click)="addShip()"
        >
          Hinzufügen
        </button>
      </div>
    </fieldset>

    <div class="options">
      <label>
        <input
          type="checkbox"
          data-testid="rules-shoot-again"
          [checked]="shootAgainOnHit()"
          (change)="shootAgainOnHit.set($any($event.target).checked); rulesChanged()"
        />
        Nach einem Treffer nochmal schießen
      </label>
      <label>
        <input
          type="checkbox"
          data-testid="rules-ships-touch"
          [checked]="shipsMayTouch()"
          (change)="shipsMayTouch.set($any($event.target).checked); rulesChanged()"
        />
        Schiffe dürfen sich berühren
      </label>
      <label>
        <input
          type="checkbox"
          data-testid="visibility-private"
          [checked]="isPrivate()"
          [disabled]="vsAi()"
          (change)="isPrivate.set($any($event.target).checked)"
        />
        Privat (nur mit Einladungscode)
      </label>
      <label>
        <input
          type="checkbox"
          data-testid="vs-ai"
          [checked]="vsAi()"
          (change)="setVsAi($any($event.target).checked)"
        />
        Gegen die KI spielen
      </label>
    </div>

    @if (errorMessage(); as message) {
      <p-message severity="error" data-testid="rules-error">{{ message }}</p-message>
    }

    <button
      pButton
      type="button"
      data-testid="create-submit-button"
      [disabled]="state() !== 'valid'"
      (click)="submit()"
    >
      Lobby erstellen
    </button>
  `,
})
export class CreateLobbyForm {
  /** Vollständige Optionen mit Regeln, Sichtbarkeit und `vsAi`. */
  readonly submitted = output<Required<CreateLobbyOptions>>();

  protected readonly minBoardSize = MIN_BOARD_SIZE;
  protected readonly maxBoardSize = MAX_BOARD_SIZE;
  protected readonly minShipLength = MIN_SHIP_LENGTH;

  protected readonly width = signal(10);
  protected readonly height = signal(10);
  protected readonly fleet = signal<number[]>([5, 4, 3, 2]);
  protected readonly newLength = signal(MIN_SHIP_LENGTH);
  protected readonly shootAgainOnHit = signal(false);
  protected readonly shipsMayTouch = signal(false);
  protected readonly isPrivate = signal(false);
  protected readonly vsAi = signal(false);
  protected readonly state = signal<CheckState>('valid');
  protected readonly errorMessage = signal<string | null>(null);

  private readonly api = inject(ApiService);
  private readonly checks = new Subject<RuleSet | string>();

  constructor() {
    this.checks
      .pipe(
        // Nur die Antwort auf die letzte Änderung zählt, ältere Anfragen werden abgebrochen.
        switchMap((check) => this.check(check)),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((result) => {
        this.state.set(result.valid ? 'valid' : 'invalid');
        this.errorMessage.set(result.valid ? null : result.message);
      });
  }

  protected setWidth(value: string): void {
    this.width.set(Number(value));
    this.rulesChanged();
  }

  protected setHeight(value: string): void {
    this.height.set(Number(value));
    this.rulesChanged();
  }

  protected canAddShip(): boolean {
    const length = this.newLength();
    return Number.isInteger(length) && length >= MIN_SHIP_LENGTH;
  }

  protected addShip(): void {
    if (!this.canAddShip()) {
      return;
    }
    this.fleet.update((ships) => [...ships, this.newLength()]);
    this.rulesChanged();
  }

  protected removeShip(index: number): void {
    this.fleet.update((ships) => ships.filter((_, i) => i !== index));
    this.rulesChanged();
  }

  protected setVsAi(enabled: boolean): void {
    this.vsAi.set(enabled);
    if (enabled) {
      this.isPrivate.set(false);
    }
  }

  protected rulesChanged(): void {
    const sizeError = this.sizeError();
    if (sizeError) {
      this.state.set('invalid');
      this.checks.next(sizeError);
      return;
    }
    this.state.set('checking');
    this.checks.next(this.rules());
  }

  protected submit(): void {
    if (this.state() !== 'valid') {
      return;
    }
    this.submitted.emit({
      rules: this.rules(),
      visibility: this.isPrivate() ? 'PRIVATE' : 'PUBLIC',
      vsAi: this.vsAi(),
    });
  }

  private rules(): RuleSet {
    return {
      boardWidth: this.width(),
      boardHeight: this.height(),
      fleet: this.fleet(),
      shootAgainOnHit: this.shootAgainOnHit(),
      shipsMayTouch: this.shipsMayTouch(),
    };
  }

  /** Offensichtlich falsche Feldgrößen fängt das Formular ab, ohne den Server zu fragen. */
  private sizeError(): string | null {
    const valid = (size: number) =>
      Number.isInteger(size) && size >= MIN_BOARD_SIZE && size <= MAX_BOARD_SIZE;
    return valid(this.width()) && valid(this.height())
      ? null
      : `Breite und Höhe müssen zwischen ${MIN_BOARD_SIZE} und ${MAX_BOARD_SIZE} liegen.`;
  }

  private check(check: RuleSet | string) {
    if (typeof check === 'string') {
      return of<CheckResult>({ valid: false, message: check });
    }
    return this.api.checkRules(check).pipe(
      map((response): CheckResult =>
        response.valid
          ? { valid: true }
          : { valid: false, message: response.message ?? 'Die Regeln sind ungültig.' },
      ),
      catchError((error) => of<CheckResult>({ valid: false, message: toApiError(error).message })),
    );
  }
}

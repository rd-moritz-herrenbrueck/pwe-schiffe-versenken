import { Component, HostListener, computed, input, output, signal } from '@angular/core';
import { ButtonDirective } from 'primeng/button';
import { Orientation, RuleSet, ShipPlacement, ShipView } from '../../core/models';
import { Board, CellPosition, PreviewCell } from '../board/board';

function cellsOf(ship: ShipPlacement): CellPosition[] {
  return Array.from({ length: ship.length }, (_, i) =>
    ship.orientation === 'HORIZONTAL' ? { x: ship.x + i, y: ship.y } : { x: ship.x, y: ship.y + i },
  );
}

/**
 * Flotte per Klick platzieren. Geprüft werden nur Feldgrenzen und Überlappung, die Berührungsregel prüft der Server.
 */
@Component({
  selector: 'app-fleet-placement',
  imports: [Board, ButtonDirective],
  styleUrl: './fleet-placement.scss',
  template: `
    <div class="fleet" role="list" aria-label="Flotte">
      @for (length of rules().fleet; track $index) {
        <button
          pButton
          type="button"
          role="listitem"
          size="small"
          [outlined]="selected() !== $index"
          [severity]="placements()[$index] ? 'success' : 'primary'"
          [attr.data-testid]="'fleet-ship-' + $index"
          [attr.data-length]="length"
          [attr.data-placed]="placements()[$index] !== null"
          [attr.data-selected]="selected() === $index"
          draggable="true"
          (click)="select($index)"
          (dragstart)="select($index)"
          (dragend)="hovered.set(null)"
        >
          @if (placements()[$index]) {
            <i class="pi pi-check" aria-hidden="true"></i>
          }
          {{ length }} Felder
        </button>
      }
    </div>

    <div class="actions">
      <button
        pButton
        type="button"
        severity="secondary"
        size="small"
        data-testid="rotate-button"
        [attr.data-orientation]="orientation()"
        (click)="rotate()"
      >
        <i class="pi pi-refresh" aria-hidden="true"></i>
        Drehen (R): {{ orientation() === 'HORIZONTAL' ? 'waagerecht' : 'senkrecht' }}
      </button>
    </div>

    <p class="hint">
      Schiff wählen und aufs Feld klicken oder ziehen. Die Vorschau zeigt vorab, ob es passt.
    </p>
    <ul class="legend" aria-label="Legende der Vorschau" data-testid="placement-legend">
      <li class="valid">passt</li>
      @if (!rules().shipsMayTouch) {
        <li class="touching">berührt ein anderes Schiff (nicht erlaubt)</li>
      }
      <li class="invalid">geht nicht</li>
    </ul>

    <app-board
      data-testid="own-board"
      [width]="rules().boardWidth"
      [height]="rules().boardHeight"
      [ships]="placedShips()"
      [preview]="preview()"
      [interactive]="true"
      (cellHovered)="hovered.set($event)"
      (hoverEnded)="hovered.set(null)"
      (cellSelected)="clickCell($event)"
      (cellDragStarted)="clickCell($event)"
      (cellDropped)="place($event)"
    />

    <div class="actions">
      <button
        pButton
        type="button"
        data-testid="confirm-fleet-button"
        [disabled]="!complete()"
        (click)="confirm()"
      >
        Flotte bestätigen
      </button>
    </div>
  `,
})
export class FleetPlacement {
  readonly rules = input.required<RuleSet>();
  readonly confirmed = output<ShipPlacement[]>();

  protected readonly orientation = signal<Orientation>('HORIZONTAL');
  protected readonly selected = signal<number | null>(0);
  protected readonly hovered = signal<CellPosition | null>(null);
  private readonly overrides = signal<(ShipPlacement | null)[] | null>(null);

  protected readonly placements = computed(
    () => this.overrides() ?? this.rules().fleet.map(() => null),
  );
  protected readonly placedShips = computed<ShipView[]>(() =>
    this.placements()
      .filter((ship): ship is ShipPlacement => ship !== null)
      .map((ship) => ({ ...ship, sunk: false })),
  );
  /** Wo das gewählte Schiff läge, wenn man jetzt auf das Feld unter dem Zeiger klickt oder dort loslässt. */
  protected readonly preview = computed<PreviewCell[]>(() => {
    const index = this.selected();
    const cell = this.hovered();
    if (index === null || cell === null) {
      return [];
    }
    const ship: ShipPlacement = {
      ...cell,
      length: this.rules().fleet[index],
      orientation: this.orientation(),
    };
    const { boardWidth, boardHeight, shipsMayTouch } = this.rules();
    const state = !this.fits(ship)
      ? 'invalid'
      : !shipsMayTouch && this.touchesPlacedShip(ship)
        ? 'touching'
        : 'valid';
    return cellsOf(ship)
      .filter((c) => c.x < boardWidth && c.y < boardHeight)
      .map((c) => ({ ...c, state }));
  });
  protected readonly complete = computed(() => this.placements().every((ship) => ship !== null));

  @HostListener('document:keydown', ['$event'])
  protected onKey(event: KeyboardEvent): void {
    if (event.key === 'r' || event.key === 'R') {
      this.rotate();
    }
  }

  protected rotate(): void {
    this.orientation.update((o) => (o === 'HORIZONTAL' ? 'VERTICAL' : 'HORIZONTAL'));
  }

  protected select(index: number): void {
    this.pickUp(index);
  }

  protected clickCell(cell: CellPosition): void {
    const placedIndex = this.placements().findIndex(
      (ship) => ship !== null && cellsOf(ship).some((c) => c.x === cell.x && c.y === cell.y),
    );
    if (placedIndex >= 0) {
      this.pickUp(placedIndex);
      return;
    }
    this.place(cell);
  }

  protected confirm(): void {
    if (this.complete()) {
      this.confirmed.emit(this.placements() as ShipPlacement[]);
    }
  }

  private pickUp(index: number): void {
    const ship = this.placements()[index];
    if (ship) {
      this.orientation.set(ship.orientation);
      this.setPlacement(index, null);
    }
    this.selected.set(index);
  }

  protected place(cell: CellPosition): void {
    const index = this.selected();
    if (index === null) {
      return;
    }
    const candidate: ShipPlacement = {
      x: cell.x,
      y: cell.y,
      length: this.rules().fleet[index],
      orientation: this.orientation(),
    };
    if (!this.fits(candidate)) {
      return;
    }
    this.setPlacement(index, candidate);
    this.hovered.set(null);
    const next = this.placements().findIndex((ship) => ship === null);
    this.selected.set(next >= 0 ? next : null);
  }

  private fits(ship: ShipPlacement): boolean {
    const { boardWidth, boardHeight } = this.rules();
    const cells = cellsOf(ship);
    const inside = cells.every((c) => c.x < boardWidth && c.y < boardHeight);
    const occupied = new Set(
      this.placedShips().flatMap((placed) => cellsOf(placed).map((c) => `${c.x},${c.y}`)),
    );
    return inside && cells.every((c) => !occupied.has(`${c.x},${c.y}`));
  }

  /** Berührung zählt auch diagonal. */
  private touchesPlacedShip(ship: ShipPlacement): boolean {
    const own = cellsOf(ship);
    return this.placedShips().some((placed) =>
      cellsOf(placed).some((c) =>
        own.some((o) => Math.abs(o.x - c.x) <= 1 && Math.abs(o.y - c.y) <= 1),
      ),
    );
  }

  private setPlacement(index: number, ship: ShipPlacement | null): void {
    this.overrides.set(this.placements().map((current, i) => (i === index ? ship : current)));
  }
}

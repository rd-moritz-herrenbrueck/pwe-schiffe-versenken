import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { ShipView, ShotView } from '../../core/models';

export interface CellPosition {
  x: number;
  y: number;
}

type CellState = 'empty' | 'ship' | 'hit' | 'miss' | 'sunk';

/** Vorschau eines Schiffs beim Platzieren: passt (`valid`) oder passt nicht (`invalid`). */
export interface PreviewCell extends CellPosition {
  state: 'valid' | 'invalid';
}

interface Cell {
  x: number;
  y: number;
  state: CellState;
  shot: boolean;
  label: string;
  preview: PreviewCell['state'] | null;
}

const STATE_LABELS: Record<CellState, string> = {
  empty: 'leer',
  ship: 'Schiff',
  hit: 'Treffer',
  miss: 'Wasser',
  sunk: 'versenkt',
};

/** Spielfeld als Raster. Die Zellen sind Schaltflächen `cell-{x}-{y}` mit `data-state`. */
@Component({
  selector: 'app-board',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './board.scss',
  template: `
    <div
      class="grid"
      [style.--cols]="width()"
      (mouseleave)="hoverEnded.emit()"
      (dragleave)="gridDragLeave($event)"
    >
      @for (cell of cells(); track cell.y * width() + cell.x) {
        <button
          type="button"
          class="cell"
          [attr.data-testid]="'cell-' + cell.x + '-' + cell.y"
          [attr.data-state]="cell.state"
          [attr.data-preview]="cell.preview"
          [attr.aria-label]="cell.label"
          [disabled]="!interactive() || cell.shot"
          [draggable]="interactive() && cell.state === 'ship'"
          (click)="cellSelected.emit({ x: cell.x, y: cell.y })"
          (mouseenter)="hover(cell)"
          (dragstart)="dragStart($event, cell)"
          (dragenter)="hover(cell)"
          (dragover)="allowDrop($event, cell)"
          (dragend)="hoverEnded.emit()"
          (drop)="drop($event, cell)"
        ></button>
      }
    </div>
  `,
})
export class Board {
  readonly width = input.required<number>();
  readonly height = input.required<number>();
  readonly ships = input<ShipView[]>([]);
  readonly shots = input<ShotView[]>([]);
  readonly interactive = input(false);
  /** Felder, die als Vorschau hervorgehoben werden (z. B. das Schiff unter dem Mauszeiger). */
  readonly preview = input<PreviewCell[]>([]);
  readonly cellSelected = output<CellPosition>();
  /** Ein Schiff wurde auf ein Feld gezogen. */
  readonly cellDropped = output<CellPosition>();
  /** Das Ziehen eines Schiffs hat auf einem seiner Felder begonnen. */
  readonly cellDragStarted = output<CellPosition>();
  /** Maus oder gezogenes Schiff ist über einem Feld. */
  readonly cellHovered = output<CellPosition>();
  /** Maus oder gezogenes Schiff hat das Spielfeld verlassen oder das Ziehen ist zu Ende. */
  readonly hoverEnded = output<void>();

  protected dragStart(event: DragEvent, cell: CellPosition): void {
    if (!this.interactive()) {
      return;
    }
    // Firefox startet das Ziehen nur, wenn Daten gesetzt sind.
    event.dataTransfer?.setData('text/plain', 'ship');
    this.cellDragStarted.emit({ x: cell.x, y: cell.y });
  }

  protected hover(cell: CellPosition): void {
    if (this.interactive()) {
      this.cellHovered.emit({ x: cell.x, y: cell.y });
    }
  }

  protected allowDrop(event: Event, cell: CellPosition): void {
    if (this.interactive()) {
      event.preventDefault();
      this.hover(cell);
    }
  }

  /** `dragleave` kommt auch beim Wechsel zwischen zwei Feldern, nur das Verlassen des Rasters zählt. */
  protected gridDragLeave(event: DragEvent): void {
    const target = event.relatedTarget as Node | null;
    if (!target || !(event.currentTarget as HTMLElement).contains(target)) {
      this.hoverEnded.emit();
    }
  }

  protected drop(event: Event, cell: CellPosition): void {
    if (this.interactive()) {
      event.preventDefault();
      this.cellDropped.emit({ x: cell.x, y: cell.y });
    }
  }

  protected readonly cells = computed<Cell[]>(() => {
    const width = this.width();
    const height = this.height();
    const states = new Map<number, CellState>();
    const shot = new Set<number>();
    const key = (x: number, y: number) => y * width + x;
    const previews = new Map<number, PreviewCell['state']>();
    for (const { x, y, state } of this.preview()) {
      previews.set(key(x, y), state);
    }

    for (const ship of this.ships()) {
      for (let i = 0; i < ship.length; i++) {
        const x = ship.orientation === 'HORIZONTAL' ? ship.x + i : ship.x;
        const y = ship.orientation === 'VERTICAL' ? ship.y + i : ship.y;
        states.set(key(x, y), ship.sunk ? 'sunk' : 'ship');
      }
    }
    for (const { x, y, hit } of this.shots()) {
      shot.add(key(x, y));
      const current = states.get(key(x, y));
      if (hit && current !== 'sunk') {
        states.set(key(x, y), 'hit');
      } else if (!hit && current === undefined) {
        states.set(key(x, y), 'miss');
      }
    }

    const cells: Cell[] = [];
    for (let y = 0; y < height; y++) {
      for (let x = 0; x < width; x++) {
        const state = states.get(key(x, y)) ?? 'empty';
        cells.push({
          x,
          y,
          state,
          shot: shot.has(key(x, y)),
          label: `Spalte ${x + 1}, Zeile ${y + 1}, ${STATE_LABELS[state]}`,
          preview: previews.get(key(x, y)) ?? null,
        });
      }
    }
    return cells;
  });
}

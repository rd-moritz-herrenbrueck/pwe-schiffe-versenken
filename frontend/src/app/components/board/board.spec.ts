import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ShipView, ShotView } from '../../core/models';
import { Board, CellPosition, PreviewCell } from './board';

describe('Board', () => {
  let fixture: ComponentFixture<Board>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [Board] });
    fixture = TestBed.createComponent(Board);
  });

  async function render(inputs: {
    width: number;
    height: number;
    ships?: ShipView[];
    shots?: ShotView[];
    interactive?: boolean;
    preview?: PreviewCell[];
  }): Promise<void> {
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    await fixture.whenStable();
  }

  const root = () => fixture.nativeElement as HTMLElement;
  const cell = (x: number, y: number) =>
    root().querySelector<HTMLButtonElement>(`[data-testid="cell-${x}-${y}"]`);
  const state = (x: number, y: number) => cell(x, y)?.getAttribute('data-state');
  const ship = (overrides: Partial<ShipView> = {}): ShipView => ({
    x: 0,
    y: 0,
    length: 3,
    orientation: 'HORIZONTAL',
    sunk: false,
    ...overrides,
  });

  describe('grid', () => {
    it.each([
      [10, 10],
      [5, 8],
      [25, 25],
      [1, 1],
    ])('rendersOneCellPerFieldFor%ix%i', async (width, height) => {
      await render({ width, height });

      expect(root().querySelectorAll('[data-testid^="cell-"]')).toHaveLength(width * height);
      expect(cell(width - 1, height - 1)).not.toBeNull();
      expect(cell(width, 0)).toBeNull();
      expect(cell(0, height)).toBeNull();
    });

    it('usesXAsColumnAndYAsRow', async () => {
      await render({ width: 4, height: 2 });

      const cells = Array.from(root().querySelectorAll('[data-testid^="cell-"]')).map((c) =>
        c.getAttribute('data-testid'),
      );
      expect(cells.slice(0, 5)).toEqual([
        'cell-0-0',
        'cell-1-0',
        'cell-2-0',
        'cell-3-0',
        'cell-0-1',
      ]);
    });

    it('startsWithEmptyCells', async () => {
      await render({ width: 3, height: 3 });

      expect(state(1, 1)).toBe('empty');
    });
  });

  describe('cellStates', () => {
    it('showsHorizontalShipOnItsCells', async () => {
      await render({ width: 10, height: 10, ships: [ship({ x: 2, y: 1, length: 3 })] });

      expect([state(2, 1), state(3, 1), state(4, 1)]).toEqual(['ship', 'ship', 'ship']);
      expect([state(1, 1), state(5, 1), state(2, 0), state(2, 2)]).toEqual([
        'empty',
        'empty',
        'empty',
        'empty',
      ]);
    });

    it('showsVerticalShipOnItsCells', async () => {
      await render({
        width: 10,
        height: 10,
        ships: [ship({ x: 2, y: 1, length: 3, orientation: 'VERTICAL' })],
      });

      expect([state(2, 1), state(2, 2), state(2, 3)]).toEqual(['ship', 'ship', 'ship']);
      expect([state(3, 1), state(2, 4)]).toEqual(['empty', 'empty']);
    });

    it('showsMissAndHit', async () => {
      await render({
        width: 10,
        height: 10,
        shots: [
          { x: 5, y: 5, hit: false },
          { x: 6, y: 5, hit: true },
        ],
      });

      expect(state(5, 5)).toBe('miss');
      expect(state(6, 5)).toBe('hit');
    });

    it('showsHitOnOwnShip', async () => {
      await render({ width: 10, height: 10, ships: [ship()], shots: [{ x: 1, y: 0, hit: true }] });

      expect([state(0, 0), state(1, 0), state(2, 0)]).toEqual(['ship', 'hit', 'ship']);
    });

    it('showsSunkShipOnAllItsCellsInsteadOfHits', async () => {
      await render({
        width: 10,
        height: 10,
        ships: [ship({ sunk: true })],
        shots: [
          { x: 0, y: 0, hit: true },
          { x: 1, y: 0, hit: true },
          { x: 2, y: 0, hit: true },
        ],
      });

      expect([state(0, 0), state(1, 0), state(2, 0)]).toEqual(['sunk', 'sunk', 'sunk']);
    });
  });

  describe('selection', () => {
    let selected: CellPosition[];

    beforeEach(() => {
      selected = [];
      fixture.componentInstance.cellSelected.subscribe((position) => selected.push(position));
    });

    it('emitsCoordinatesOfClickedCellWhenInteractive', async () => {
      await render({ width: 10, height: 10, interactive: true });

      cell(3, 7)!.click();

      expect(selected).toEqual([{ x: 3, y: 7 }]);
    });

    it('emitsNothingWhenNotInteractive', async () => {
      await render({ width: 10, height: 10, interactive: false });

      cell(3, 7)!.click();

      expect(selected).toEqual([]);
    });

    it('doesNotAllowShootingTwiceAtSameCell', async () => {
      await render({
        width: 10,
        height: 10,
        interactive: true,
        shots: [{ x: 3, y: 7, hit: false }],
      });

      cell(3, 7)!.click();

      expect(cell(3, 7)!.disabled).toBe(true);
      expect(selected).toEqual([]);
    });

    it('keepsUnshotCellsEnabled', async () => {
      await render({
        width: 10,
        height: 10,
        interactive: true,
        shots: [{ x: 3, y: 7, hit: false }],
      });

      expect(cell(3, 8)!.disabled).toBe(false);
    });

    it('emitsCoordinatesOfDropTargetWhenInteractive', async () => {
      const dropped: CellPosition[] = [];
      fixture.componentInstance.cellDropped.subscribe((position) => dropped.push(position));
      await render({ width: 10, height: 10, interactive: true });

      cell(2, 3)!.dispatchEvent(new Event('drop', { bubbles: true, cancelable: true }));

      expect(dropped).toEqual([{ x: 2, y: 3 }]);
    });

    it('ignoresDropsWhenNotInteractive', async () => {
      const dropped: CellPosition[] = [];
      fixture.componentInstance.cellDropped.subscribe((position) => dropped.push(position));
      await render({ width: 10, height: 10, interactive: false });

      cell(2, 3)!.dispatchEvent(new Event('drop', { bubbles: true, cancelable: true }));

      expect(dropped).toEqual([]);
    });

    it('emitsCoordinatesWhenDraggingStartsOnShipCell', async () => {
      const started: CellPosition[] = [];
      fixture.componentInstance.cellDragStarted.subscribe((position) => started.push(position));
      await render({ width: 10, height: 10, interactive: true, ships: [ship()] });

      cell(1, 0)!.dispatchEvent(new Event('dragstart', { bubbles: true, cancelable: true }));

      expect(started).toEqual([{ x: 1, y: 0 }]);
    });

    it('doesNotStartDraggingEmptyCells', async () => {
      await render({ width: 10, height: 10, interactive: true });

      expect(cell(1, 0)!.draggable).toBe(false);
    });

    it('disablesAllCellsWhenNotInteractive', async () => {
      await render({ width: 3, height: 3, interactive: false });

      expect(cell(1, 1)!.disabled).toBe(true);
    });
  });

  it('labelsCellsForScreenReadersWithCoordinates', async () => {
    await render({ width: 10, height: 10 });

    expect(cell(3, 7)!.getAttribute('aria-label')).toMatch(/Spalte 4.*Zeile 8/);
  });

  describe('preview', () => {
    const previewOf = (x: number, y: number) => cell(x, y)?.getAttribute('data-preview');

    it('marksPreviewCellsWithTheirState', async () => {
      await render({
        width: 5,
        height: 5,
        preview: [
          { x: 0, y: 0, state: 'valid' },
          { x: 1, y: 0, state: 'invalid' },
          { x: 2, y: 0, state: 'touching' },
        ],
      });

      expect([previewOf(0, 0), previewOf(1, 0), previewOf(2, 0)]).toEqual([
        'valid',
        'invalid',
        'touching',
      ]);
      expect(cell(3, 0)?.hasAttribute('data-preview')).toBe(false);
    });

    it('showsNoPreviewByDefault', async () => {
      await render({ width: 3, height: 3 });

      expect(root().querySelectorAll('[data-preview]')).toHaveLength(0);
    });

    it('keepsTheCellStateUnderThePreview', async () => {
      await render({
        width: 5,
        height: 5,
        ships: [ship()],
        preview: [{ x: 0, y: 0, state: 'invalid' }],
      });

      expect(state(0, 0)).toBe('ship');
    });
  });

  describe('hovering', () => {
    let hovered: CellPosition[];
    let hoverEnds: number;

    beforeEach(() => {
      hovered = [];
      hoverEnds = 0;
      fixture.componentInstance.cellHovered.subscribe((c) => hovered.push(c));
      fixture.componentInstance.hoverEnded.subscribe(() => hoverEnds++);
    });

    const dragEvent = (type: string) => new Event(type, { bubbles: true, cancelable: true });

    it('reportsTheCellUnderTheMouseWhenInteractive', async () => {
      await render({ width: 5, height: 5, interactive: true });

      cell(2, 3)!.dispatchEvent(new Event('mouseenter'));

      expect(hovered).toEqual([{ x: 2, y: 3 }]);
    });

    it('reportsTheCellUnderADraggedShip', async () => {
      await render({ width: 5, height: 5, interactive: true });

      cell(4, 1)!.dispatchEvent(dragEvent('dragenter'));
      cell(0, 2)!.dispatchEvent(dragEvent('dragover'));

      expect(hovered).toEqual([
        { x: 4, y: 1 },
        { x: 0, y: 2 },
      ]);
    });

    it('reportsNothingWhenNotInteractive', async () => {
      await render({ width: 5, height: 5, interactive: false });

      cell(2, 3)!.dispatchEvent(new Event('mouseenter'));
      cell(2, 3)!.dispatchEvent(dragEvent('dragenter'));

      expect(hovered).toEqual([]);
    });

    it('reportsWhenThePointerLeavesTheBoard', async () => {
      await render({ width: 5, height: 5, interactive: true });

      root().querySelector('.grid')!.dispatchEvent(new Event('mouseleave'));

      expect(hoverEnds).toBe(1);
    });

    it('ignoresDragLeaveBetweenCellsOfTheBoard', async () => {
      await render({ width: 5, height: 5, interactive: true });
      const leave = new MouseEvent('dragleave', { bubbles: true, relatedTarget: cell(1, 0) });

      cell(0, 0)!.dispatchEvent(leave);

      expect(hoverEnds).toBe(0);
    });

    it('reportsDragLeaveOutOfTheBoard', async () => {
      await render({ width: 5, height: 5, interactive: true });
      const leave = new MouseEvent('dragleave', { bubbles: true, relatedTarget: document.body });

      cell(0, 0)!.dispatchEvent(leave);

      expect(hoverEnds).toBe(1);
    });
  });
});

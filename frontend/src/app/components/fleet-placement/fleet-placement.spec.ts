import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RuleSet, ShipPlacement } from '../../core/models';
import { rules } from '../../testing/fixtures';
import { FleetPlacement } from './fleet-placement';

describe('FleetPlacement', () => {
  let fixture: ComponentFixture<FleetPlacement>;
  let confirmed: ShipPlacement[][];

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [FleetPlacement] });
    fixture = TestBed.createComponent(FleetPlacement);
    confirmed = [];
    fixture.componentInstance.confirmed.subscribe((ships) => confirmed.push(ships));
  });

  async function render(ruleSet: RuleSet = rules()): Promise<void> {
    fixture.componentRef.setInput('rules', ruleSet);
    await fixture.whenStable();
  }

  const root = () => fixture.nativeElement as HTMLElement;
  const byId = (id: string) => root().querySelector<HTMLButtonElement>(`[data-testid="${id}"]`);
  const cell = (x: number, y: number) => byId(`cell-${x}-${y}`)!;
  const state = (x: number, y: number) => cell(x, y).getAttribute('data-state');
  const shipEntry = (index: number) => byId(`fleet-ship-${index}`)!;

  async function clickCell(x: number, y: number): Promise<void> {
    cell(x, y).click();
    await fixture.whenStable();
  }

  async function click(id: string): Promise<void> {
    byId(id)!.click();
    await fixture.whenStable();
  }

  async function placeWholeDefaultFleet(): Promise<void> {
    await clickCell(0, 0);
    await clickCell(0, 2);
    await clickCell(0, 4);
    await clickCell(0, 6);
  }

  describe('layout', () => {
    it('showsBoardWithSizeFromRuleSet', async () => {
      await render(rules({ boardWidth: 6, boardHeight: 4, fleet: [2, 3] }));

      expect(byId('own-board')!.querySelectorAll('[data-testid^="cell-"]')).toHaveLength(24);
    });

    it('listsOneEntryPerShipOfFleetWithItsLength', async () => {
      await render();

      expect([0, 1, 2, 3].map((i) => shipEntry(i).getAttribute('data-length'))).toEqual([
        '5',
        '4',
        '3',
        '2',
      ]);
      expect(byId('fleet-ship-4')).toBeNull();
    });

    it('listsShipsOfSameLengthSeparately', async () => {
      await render(rules({ fleet: [2, 2, 3] }));

      expect([0, 1, 2].map((i) => shipEntry(i).getAttribute('data-length'))).toEqual([
        '2',
        '2',
        '3',
      ]);
    });

    it('selectsFirstShipInitiallyAndStartsHorizontal', async () => {
      await render();

      expect(shipEntry(0).getAttribute('data-selected')).toBe('true');
      expect(shipEntry(1).getAttribute('data-selected')).toBe('false');
      expect(byId('rotate-button')!.getAttribute('data-orientation')).toBe('HORIZONTAL');
    });

    it('disablesConfirmUntilAllShipsArePlaced', async () => {
      await render();

      expect(byId('confirm-fleet-button')!.disabled).toBe(true);
    });
  });

  describe('placing', () => {
    it('placesSelectedShipHorizontallyStartingAtClickedCell', async () => {
      await render();

      await clickCell(2, 3);

      expect([2, 3, 4, 5, 6].map((x) => state(x, 3))).toEqual([
        'ship',
        'ship',
        'ship',
        'ship',
        'ship',
      ]);
      expect([state(1, 3), state(7, 3), state(2, 2), state(2, 4)]).toEqual([
        'empty',
        'empty',
        'empty',
        'empty',
      ]);
    });

    it('marksPlacedShipAndSelectsNextOne', async () => {
      await render();

      await clickCell(2, 3);

      expect(shipEntry(0).getAttribute('data-placed')).toBe('true');
      expect(shipEntry(0).getAttribute('data-selected')).toBe('false');
      expect(shipEntry(1).getAttribute('data-selected')).toBe('true');
    });

    it('placesNextShipWithItsOwnLength', async () => {
      await render();
      await clickCell(0, 0);

      await clickCell(0, 2);

      expect([0, 1, 2, 3, 4].map((x) => state(x, 2))).toEqual([
        'ship',
        'ship',
        'ship',
        'ship',
        'empty',
      ]);
    });

    it('placesVerticallyAfterRotateButton', async () => {
      await render();

      await click('rotate-button');
      await clickCell(2, 3);

      expect(byId('rotate-button')!.getAttribute('data-orientation')).toBe('VERTICAL');
      expect([3, 4, 5, 6, 7].map((y) => state(2, y))).toEqual([
        'ship',
        'ship',
        'ship',
        'ship',
        'ship',
      ]);
      expect(state(3, 3)).toBe('empty');
    });

    it.each(['r', 'R'])('rotatesWithKey%s', async (key) => {
      await render();

      document.dispatchEvent(new KeyboardEvent('keydown', { key }));
      await fixture.whenStable();

      expect(byId('rotate-button')!.getAttribute('data-orientation')).toBe('VERTICAL');
    });

    it('rotatesBackToHorizontalOnSecondRotate', async () => {
      await render();

      await click('rotate-button');
      await click('rotate-button');

      expect(byId('rotate-button')!.getAttribute('data-orientation')).toBe('HORIZONTAL');
    });

    it('placesShipChosenFromListInsteadOfTheSelectedOne', async () => {
      await render();

      await click('fleet-ship-3');
      await clickCell(0, 0);

      expect([0, 1, 2].map((x) => state(x, 0))).toEqual(['ship', 'ship', 'empty']);
      expect(shipEntry(3).getAttribute('data-placed')).toBe('true');
      expect(shipEntry(0).getAttribute('data-selected')).toBe('true');
    });
  });

  describe('rejectedPlacement', () => {
    it('rejectsHorizontalShipThatLeavesTheBoardOnTheRight', async () => {
      await render();

      await clickCell(6, 0);

      expect(state(6, 0)).toBe('empty');
      expect(shipEntry(0).getAttribute('data-placed')).toBe('false');
    });

    it('acceptsHorizontalShipThatEndsExactlyAtBoardEdge', async () => {
      await render();

      await clickCell(5, 0);

      expect(state(9, 0)).toBe('ship');
    });

    it('rejectsVerticalShipThatLeavesTheBoardAtTheBottom', async () => {
      await render();
      await click('rotate-button');

      await clickCell(0, 6);

      expect(state(0, 6)).toBe('empty');
      expect(shipEntry(0).getAttribute('data-placed')).toBe('false');
    });

    it('rejectsShipOverlappingAnotherShip', async () => {
      await render();
      await clickCell(3, 2);

      await click('rotate-button');
      await clickCell(5, 0);

      expect(shipEntry(1).getAttribute('data-placed')).toBe('false');
      expect(state(5, 0)).toBe('empty');
    });

    it('rejectsShipTouchingAnotherShipAtAnEdgeWhenTouchingIsForbidden', async () => {
      await render(rules({ shipsMayTouch: false }));
      await clickCell(0, 0);

      await clickCell(0, 1);

      expect(shipEntry(1).getAttribute('data-placed')).toBe('false');
      expect(state(0, 1)).toBe('empty');
    });

    it('rejectsShipTouchingAnotherShipAtTheEndWhenTouchingIsForbidden', async () => {
      await render(rules({ shipsMayTouch: false }));
      await clickCell(0, 0);

      await click('rotate-button');
      await clickCell(5, 0);

      expect(shipEntry(1).getAttribute('data-placed')).toBe('false');
    });

    it('allowsShipsTouchingOnlyAtACornerWhenTouchingIsForbidden', async () => {
      await render(rules({ shipsMayTouch: false }));
      await clickCell(0, 0);

      await clickCell(5, 1);

      expect(shipEntry(1).getAttribute('data-placed')).toBe('true');
    });

    it('allowsShipsToTouchWhenTheRuleAllowsIt', async () => {
      await render(rules({ shipsMayTouch: true }));
      await clickCell(0, 0);

      await clickCell(0, 1);

      expect(shipEntry(1).getAttribute('data-placed')).toBe('true');
    });
  });

  describe('movingShips', () => {
    it('picksUpPlacedShipWhenClickingItsCell', async () => {
      await render();
      await clickCell(0, 0);

      await clickCell(3, 0);

      expect(state(0, 0)).toBe('empty');
      expect(shipEntry(0).getAttribute('data-placed')).toBe('false');
      expect(shipEntry(0).getAttribute('data-selected')).toBe('true');
    });

    it('picksUpPlacedShipWhenChoosingItFromList', async () => {
      await render();
      await clickCell(0, 0);

      await click('fleet-ship-0');

      expect(state(0, 0)).toBe('empty');
      expect(shipEntry(0).getAttribute('data-selected')).toBe('true');
    });

    it('movesShipByPickingItUpAndPlacingItElsewhere', async () => {
      await render();
      await clickCell(0, 0);

      await clickCell(0, 0);
      await clickCell(0, 5);

      expect(state(0, 0)).toBe('empty');
      expect(state(4, 5)).toBe('ship');
    });
  });

  describe('dragAndDrop', () => {
    const dragEvent = (type: string) => new Event(type, { bubbles: true, cancelable: true });

    it('placesDraggedShipAtDropTarget', async () => {
      await render();

      shipEntry(2).dispatchEvent(dragEvent('dragstart'));
      cell(4, 4).dispatchEvent(dragEvent('drop'));
      await fixture.whenStable();

      expect([4, 5, 6].map((x) => state(x, 4))).toEqual(['ship', 'ship', 'ship']);
      expect(shipEntry(2).getAttribute('data-placed')).toBe('true');
    });

    it('allowsDroppingOntoBoardCells', async () => {
      await render();
      const over = dragEvent('dragover');

      cell(4, 4).dispatchEvent(over);

      expect(over.defaultPrevented).toBe(true);
    });

    it('movesPlacedShipByDraggingItsCell', async () => {
      await render();
      await clickCell(0, 0);

      cell(1, 0).dispatchEvent(dragEvent('dragstart'));
      cell(0, 5).dispatchEvent(dragEvent('drop'));
      await fixture.whenStable();

      expect(state(0, 0)).toBe('empty');
      expect([0, 4].map((x) => state(x, 5))).toEqual(['ship', 'ship']);
    });

    it('rejectsDropOfAShipTouchingAnotherShipWhenTouchingIsForbidden', async () => {
      await render(rules({ shipsMayTouch: false }));
      await clickCell(0, 0);

      cell(0, 1).dispatchEvent(dragEvent('drop'));
      await fixture.whenStable();

      expect(shipEntry(1).getAttribute('data-placed')).toBe('false');
    });

    it('rejectsDropOutsideTheBoard', async () => {
      await render();

      shipEntry(0).dispatchEvent(dragEvent('dragstart'));
      cell(8, 0).dispatchEvent(dragEvent('drop'));
      await fixture.whenStable();

      expect(shipEntry(0).getAttribute('data-placed')).toBe('false');
    });
  });

  describe('preview', () => {
    const previewOf = (x: number, y: number) => cell(x, y).getAttribute('data-preview');
    const previewCells = () =>
      Array.from(root().querySelectorAll('[data-preview]')).map((c) => [
        c.getAttribute('data-testid'),
        c.getAttribute('data-preview'),
      ]);
    const dragEvent = (type: string) => new Event(type, { bubbles: true, cancelable: true });

    async function hover(x: number, y: number): Promise<void> {
      cell(x, y).dispatchEvent(new Event('mouseenter'));
      await fixture.whenStable();
    }

    it('showsNoPreviewWithoutHover', async () => {
      await render();

      expect(previewCells()).toEqual([]);
    });

    it('showsWhereTheSelectedShipWouldLie', async () => {
      await render();

      await hover(2, 3);

      expect(previewCells()).toEqual([2, 3, 4, 5, 6].map((x) => [`cell-${x}-3`, 'valid']));
    });

    it('followsTheOrientation', async () => {
      await render();
      await click('rotate-button');

      await hover(2, 3);

      expect(previewCells()).toEqual([3, 4, 5, 6, 7].map((y) => [`cell-2-${y}`, 'valid']));
    });

    it('usesTheLengthOfTheSelectedShip', async () => {
      await render();
      await click('fleet-ship-3');

      await hover(0, 0);

      expect(previewCells()).toEqual([
        ['cell-0-0', 'valid'],
        ['cell-1-0', 'valid'],
      ]);
    });

    it('marksTheVisiblePartInvalidWhenTheShipLeavesTheBoard', async () => {
      await render();

      await hover(8, 0);

      expect(previewCells()).toEqual([
        ['cell-8-0', 'invalid'],
        ['cell-9-0', 'invalid'],
      ]);
    });

    it('marksOverlapWithAPlacedShipInvalid', async () => {
      await render();
      await clickCell(3, 2);

      await hover(0, 2);

      expect(previewOf(0, 2)).toBe('invalid');
      expect(previewOf(2, 2)).toBe('invalid');
    });

    it('marksPlacementTouchingAShipInvalidWhenTheRuleForbidsIt', async () => {
      await render(rules({ shipsMayTouch: false }));
      await clickCell(0, 0);

      await hover(0, 1);
      expect(previewOf(0, 1)).toBe('invalid');

      await hover(0, 2);
      expect(previewOf(0, 2)).toBe('valid');
    });

    it('doesNotCountCornerContactAsTouching', async () => {
      await render(rules({ shipsMayTouch: false }));
      await clickCell(0, 0);

      await hover(5, 1);

      expect(previewOf(5, 1)).toBe('valid');
    });

    it('keepsPreviewValidWhenShipsMayTouch', async () => {
      await render(rules({ shipsMayTouch: true }));
      await clickCell(0, 0);

      await hover(0, 1);

      expect(previewOf(0, 1)).toBe('valid');
    });

    it('showsNoPreviewOnceAllShipsArePlaced', async () => {
      await render();
      await placeWholeDefaultFleet();

      await hover(0, 8);

      expect(previewCells()).toEqual([]);
    });

    it('disappearsWhenThePointerLeavesTheBoard', async () => {
      await render();
      await hover(2, 3);

      byId('own-board')!.querySelector('.grid')!.dispatchEvent(new Event('mouseleave'));
      await fixture.whenStable();

      expect(previewCells()).toEqual([]);
    });

    it('showsThePreviewWhileDraggingAShipFromTheList', async () => {
      await render();

      shipEntry(2).dispatchEvent(dragEvent('dragstart'));
      cell(4, 4).dispatchEvent(dragEvent('dragenter'));
      await fixture.whenStable();

      expect(previewCells()).toEqual([
        ['cell-4-4', 'valid'],
        ['cell-5-4', 'valid'],
        ['cell-6-4', 'valid'],
      ]);
    });

    it('showsThePreviewWhileDraggingAPlacedShip', async () => {
      await render();
      await clickCell(0, 0);

      cell(1, 0).dispatchEvent(dragEvent('dragstart'));
      cell(0, 5).dispatchEvent(dragEvent('dragenter'));
      await fixture.whenStable();

      expect(previewCells().map(([id]) => id)).toEqual([0, 1, 2, 3, 4].map((x) => `cell-${x}-5`));
    });

    it('disappearsAfterDropping', async () => {
      await render();
      shipEntry(2).dispatchEvent(dragEvent('dragstart'));
      cell(4, 4).dispatchEvent(dragEvent('dragenter'));

      cell(4, 4).dispatchEvent(dragEvent('drop'));
      await fixture.whenStable();

      expect(previewCells()).toEqual([]);
    });

    it('disappearsWhenDraggingIsCancelled', async () => {
      await render();
      shipEntry(2).dispatchEvent(dragEvent('dragstart'));
      cell(4, 4).dispatchEvent(dragEvent('dragenter'));

      shipEntry(2).dispatchEvent(dragEvent('dragend'));
      await fixture.whenStable();

      expect(previewCells()).toEqual([]);
    });
  });

  describe('confirm', () => {
    it('enablesConfirmWhenAllShipsArePlaced', async () => {
      await render();

      await placeWholeDefaultFleet();

      expect(byId('confirm-fleet-button')!.disabled).toBe(false);
    });

    it('disablesConfirmAgainWhenShipIsPickedUp', async () => {
      await render();
      await placeWholeDefaultFleet();

      await click('fleet-ship-2');

      expect(byId('confirm-fleet-button')!.disabled).toBe(true);
    });

    it('emitsAllPlacementsInFleetOrder', async () => {
      await render();
      await click('fleet-ship-3');
      await clickCell(9, 8);
      await clickCell(8, 8);
      await click('rotate-button');
      await clickCell(0, 0);
      await clickCell(2, 0);
      await clickCell(4, 0);

      await click('confirm-fleet-button');

      expect(confirmed).toEqual([
        [
          { x: 0, y: 0, length: 5, orientation: 'VERTICAL' },
          { x: 2, y: 0, length: 4, orientation: 'VERTICAL' },
          { x: 4, y: 0, length: 3, orientation: 'VERTICAL' },
          { x: 8, y: 8, length: 2, orientation: 'HORIZONTAL' },
        ],
      ]);
    });

    it('emitsNothingWhileFleetIsIncomplete', async () => {
      await render();
      await clickCell(0, 0);

      await click('confirm-fleet-button');

      expect(confirmed).toEqual([]);
    });
  });
});

import { expect, test } from '@playwright/test';
import { closeAll, registerPlayer, uniqueName } from './helpers';

test.describe('Flotte platzieren', () => {
  test('Schiff aus der Liste per Drag-and-drop auf das Feld ziehen', async ({ browser }) => {
    const player = await registerPlayer(browser, uniqueName('Zieher'));
    const { page } = player;

    await page.getByTestId('play-vs-ai-button').click();
    const board = page.getByTestId('own-board');
    await expect(board).toBeVisible();

    await page.getByTestId('fleet-ship-0').dragTo(board.getByTestId('cell-2-3'));

    await expect(page.getByTestId('fleet-ship-0')).toHaveAttribute('data-placed', 'true');
    for (let x = 2; x < 7; x++) {
      await expect(board.getByTestId(`cell-${x}-3`)).toHaveAttribute('data-state', 'ship');
    }
    await expect(board.getByTestId('cell-7-3')).toHaveAttribute('data-state', 'empty');

    // Ein gesetztes Schiff lässt sich auf ein anderes Feld ziehen.
    await board.getByTestId('cell-2-3').dragTo(board.getByTestId('cell-0-0'));

    await expect(board.getByTestId('cell-2-3')).toHaveAttribute('data-state', 'empty');
    await expect(board.getByTestId('cell-4-0')).toHaveAttribute('data-state', 'ship');

    await closeAll(player);
  });

  test('Flotte lässt sich erst bestätigen, wenn alle Schiffe stehen', async ({ browser }) => {
    const player = await registerPlayer(browser, uniqueName('Planer'));
    const { page } = player;

    await page.getByTestId('play-vs-ai-button').click();
    await expect(page.getByTestId('confirm-fleet-button')).toBeDisabled();

    await page.getByTestId('own-board').getByTestId('cell-0-0').click();
    await expect(page.getByTestId('confirm-fleet-button')).toBeDisabled();

    await page.getByTestId('rotate-button').click();
    await expect(page.getByTestId('rotate-button')).toHaveAttribute('data-orientation', 'VERTICAL');

    await closeAll(player);
  });
});

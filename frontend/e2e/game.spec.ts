import { expect, test } from '@playwright/test';
import {
  FLEET_CELLS,
  WATER_CELLS,
  closeAll,
  expectTurn,
  fire,
  registerPlayer,
  startRunningGame,
  uniqueName,
} from './helpers';

test.describe('Spielverlauf mit zwei Spielern', () => {
  test('Spieler A gewinnt, Spieler B verliert', async ({ browser }) => {
    const a = await registerPlayer(browser, uniqueName('Sieg'));
    const b = await registerPlayer(browser, uniqueName('Pech'));
    await startRunningGame(a, b);

    // A trifft jedes Schiffsfeld, B schießt abwechselnd ins Wasser.
    for (const [index, [x, y]] of FLEET_CELLS.entries()) {
      await expectTurn(a.page, true);
      await fire(a.page, x, y);
      const last = index === FLEET_CELLS.length - 1;
      if (last) {
        break;
      }
      await expectTurn(a.page, false);
      await expectTurn(b.page, true);
      const [waterX, waterY] = WATER_CELLS[index];
      await fire(b.page, waterX, waterY);
    }

    await expect(a.page.getByTestId('result-message')).toHaveAttribute('data-result', 'WON');
    await expect(b.page.getByTestId('result-message')).toHaveAttribute('data-result', 'LOST');
    await expect(a.page.getByTestId('shot-feedback')).toHaveAttribute('data-result', 'WIN');
    await expect(a.page.getByTestId('turn-indicator')).toHaveCount(0);

    // Der Verlierer sieht die versenkten Schiffe auf seinem Feld.
    await expect(b.page.getByTestId('own-board').getByTestId('cell-0-0')).toHaveAttribute(
      'data-state',
      'sunk',
    );

    await closeAll(a, b);
  });

  test('Aufgeben beendet das Spiel für beide', async ({ browser }) => {
    const a = await registerPlayer(browser, uniqueName('Gibt'));
    const b = await registerPlayer(browser, uniqueName('Auf'));
    await startRunningGame(a, b);

    await a.page.getByTestId('resign-button').click();
    await a.page.getByTestId('resign-confirm-button').click();

    await expect(a.page.getByTestId('result-message')).toHaveAttribute('data-result', 'LOST');
    await expect(b.page.getByTestId('result-message')).toHaveAttribute('data-result', 'WON');

    await closeAll(a, b);
  });

  test('Reload mitten im Spiel stellt den Zustand wieder her', async ({ browser }) => {
    const a = await registerPlayer(browser, uniqueName('Reload'));
    const b = await registerPlayer(browser, uniqueName('Gegner'));
    await startRunningGame(a, b);

    await fire(a.page, 0, 0);
    await expectTurn(b.page, true);
    await fire(b.page, 0, 9);
    await expectTurn(a.page, true);

    await a.page.reload();

    await expectTurn(a.page, true);
    await expect(a.page.getByTestId('enemy-board').getByTestId('cell-0-0')).toHaveAttribute(
      'data-state',
      'hit',
    );
    await expect(a.page.getByTestId('own-board').getByTestId('cell-0-9')).toHaveAttribute(
      'data-state',
      'miss',
    );
    await expect(a.page.getByTestId('own-board').getByTestId('cell-0-0')).toHaveAttribute(
      'data-state',
      'ship',
    );

    // Das Spiel läuft nach dem Reload weiter, die SSE-Verbindung ist wieder aufgebaut.
    await fire(a.page, 1, 0);
    await expectTurn(b.page, true);
    await expect(b.page.getByTestId('own-board').getByTestId('cell-1-0')).toHaveAttribute(
      'data-state',
      'hit',
    );

    await closeAll(a, b);
  });

  test('Reload des Spielers, der nicht am Zug ist, behält Gegner und Zugrecht', async ({
    browser,
  }) => {
    const a = await registerPlayer(browser, uniqueName('Platz'));
    const b = await registerPlayer(browser, uniqueName('Halter'));
    await startRunningGame(a, b);

    await b.page.reload();

    await expectTurn(b.page, false);
    await expect(b.page.getByTestId('opponent-name')).toHaveText(a.nickname);

    await closeAll(a, b);
  });
});

import { expect, test } from '@playwright/test';
import {
  closeAll,
  expectTurn,
  fire,
  placeStandardFleet,
  registerPlayer,
  uniqueName,
} from './helpers';

test.describe('Spiel gegen die KI', () => {
  test('KI antwortet auf Schüsse, Aufgeben beendet das Spiel', async ({ browser }) => {
    const player = await registerPlayer(browser, uniqueName('Mensch'));
    const { page } = player;

    await page.getByTestId('play-vs-ai-button').click();
    await expect(page.getByTestId('fleet-placement')).toBeVisible();
    // Gegen die KI gibt es keine Lobby zum Beitreten und keinen Einladungscode.
    await expect(page.getByTestId('waiting-for-opponent')).toHaveCount(0);

    await placeStandardFleet(page);
    await expectTurn(page, true);

    await fire(page, 5, 5);

    // Die KI antwortet sofort mit genau einem Schuss auf das eigene Feld.
    await expect(page.getByTestId('shot-feedback')).toBeVisible();
    await expectTurn(page, true);
    await expect(
      page.getByTestId('own-board').locator('[data-state="hit"], [data-state="miss"]'),
    ).toHaveCount(1);

    await page.getByTestId('resign-button').click();
    await page.getByTestId('resign-confirm-button').click();
    await expect(page.getByTestId('result-message')).toHaveAttribute('data-result', 'LOST');

    await closeAll(player);
  });
});

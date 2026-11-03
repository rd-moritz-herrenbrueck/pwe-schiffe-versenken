import { expect, test } from '@playwright/test';
import { closeAll, expectTurn, placeStandardFleet, registerPlayer, uniqueName } from './helpers';

test.describe('Responsive Darstellung', () => {
  test.use({ viewport: { width: 360, height: 740 } });

  test('Spiel ist auf einem schmalen Bildschirm ohne seitliches Scrollen bedienbar', async ({
    browser,
  }) => {
    const player = await registerPlayer(browser, uniqueName('Handy'));
    const { page } = player;
    const horizontalOverflow = () =>
      page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);

    expect(await horizontalOverflow()).toBeLessThanOrEqual(0);

    await page.getByTestId('play-vs-ai-button').click();
    await expect(page.getByTestId('fleet-placement')).toBeVisible();
    expect(await horizontalOverflow()).toBeLessThanOrEqual(0);

    await placeStandardFleet(page);
    await expectTurn(page, true);
    expect(await horizontalOverflow()).toBeLessThanOrEqual(0);

    const cell = page.getByTestId('enemy-board').getByTestId('cell-0-0');
    const box = await cell.boundingBox();
    expect(box?.width).toBeGreaterThanOrEqual(20);
    await cell.click();
    await expect(page.getByTestId('shot-feedback')).toBeVisible();

    await closeAll(player);
  });
});

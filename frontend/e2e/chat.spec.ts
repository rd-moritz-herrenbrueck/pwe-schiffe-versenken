import { expect, test } from '@playwright/test';
import { closeAll, registerPlayer, startOpenGame, uniqueName } from './helpers';

test.describe('Chat', () => {
  test('Nachrichten kommen live beim Gegner an, ungelesene werden gezählt', async ({ browser }) => {
    const a = await registerPlayer(browser, uniqueName('Plauder'));
    const b = await registerPlayer(browser, uniqueName('Zuhörer'));
    await startOpenGame(a, b);

    await a.page.getByTestId('chat-toggle').click();
    await a.page.getByTestId('chat-input').fill('Hallo Kapitän!');
    await a.page.getByTestId('chat-send-button').click();
    await expect(a.page.getByTestId('chat').getByText('Hallo Kapitän!')).toBeVisible();

    // B hat den Chat zu: ungelesener Zähler, nach dem Öffnen ist die Nachricht da.
    await expect(b.page.getByTestId('chat-unread')).toHaveText('1');
    await b.page.getByTestId('chat-toggle').click();
    await expect(b.page.getByTestId('chat-unread')).toHaveCount(0);
    await expect(b.page.getByTestId('chat').getByText('Hallo Kapitän!')).toBeVisible();

    await b.page.getByTestId('chat-input').fill('Ahoi!');
    await b.page.getByTestId('chat-input').press('Enter');
    await expect(a.page.getByTestId('chat').getByText('Ahoi!')).toBeVisible();

    await closeAll(a, b);
  });

  test('Chatverlauf bleibt nach Reload erhalten, HTML wird nicht interpretiert', async ({
    browser,
  }) => {
    const a = await registerPlayer(browser, uniqueName('Schreib'));
    const b = await registerPlayer(browser, uniqueName('Leser'));
    await startOpenGame(a, b);

    await a.page.getByTestId('chat-toggle').click();
    await a.page.getByTestId('chat-input').fill('<b>fett</b>');
    await a.page.getByTestId('chat-send-button').click();
    await expect(a.page.getByTestId('chat').getByText('<b>fett</b>')).toBeVisible();

    await b.page.reload();
    await b.page.getByTestId('chat-toggle').click();
    await expect(b.page.getByTestId('chat').getByText('<b>fett</b>')).toBeVisible();
    await expect(b.page.getByTestId('chat').locator('b')).toHaveCount(0);

    await closeAll(a, b);
  });
});

import { expect, test } from '@playwright/test';
import { closeAll, gameIdOf, registerPlayer, uniqueName } from './helpers';

test.describe('Spiel erstellen und beitreten', () => {
  test('offene Lobby erscheint in der Liste, Beitritt startet die Platzierung', async ({
    browser,
  }) => {
    const a = await registerPlayer(browser, uniqueName('Anna'));
    const b = await registerPlayer(browser, uniqueName('Ben'));

    await a.page.getByTestId('create-lobby-button').click();
    await expect(a.page.getByTestId('waiting-for-opponent')).toBeVisible();
    const gameId = gameIdOf(a.page);

    // Die Lobby-Liste von B aktualisiert sich live per SSE.
    await expect(b.page.getByTestId(`lobby-${gameId}`)).toContainText(a.nickname);
    await b.page.getByTestId(`join-lobby-${gameId}`).click();

    await expect(b.page).toHaveURL(new RegExp(`/games/${gameId}$`));
    await expect(a.page.getByTestId('opponent-name')).toHaveText(b.nickname);
    await expect(b.page.getByTestId('opponent-name')).toHaveText(a.nickname);
    await expect(a.page.getByTestId('fleet-placement')).toBeVisible();
    await expect(b.page.getByTestId('fleet-placement')).toBeVisible();
    // Die gestartete Lobby verschwindet aus der Liste.
    await b.page.goto('/lobbies');
    await expect(b.page.getByTestId(`lobby-${gameId}`)).toHaveCount(0);

    await closeAll(a, b);
  });

  test('private Lobby ist nur mit Einladungscode erreichbar', async ({ browser }) => {
    const a = await registerPlayer(browser, uniqueName('Clara'));
    const b = await registerPlayer(browser, uniqueName('Dirk'));

    await a.page.getByTestId('open-lobby-form-button').click();
    await a.page.getByTestId('visibility-private').check();
    await a.page.getByTestId('create-submit-button').click();
    await expect(a.page.getByTestId('waiting-for-opponent')).toBeVisible();
    const gameId = gameIdOf(a.page);
    const inviteCode = (await a.page.getByTestId('invite-code').innerText()).trim();
    expect(inviteCode).not.toBe('');

    // Eine private Lobby taucht nicht in der öffentlichen Liste auf.
    await b.page.reload();
    await expect(b.page.getByTestId('create-lobby-button')).toBeVisible();
    await expect(b.page.getByTestId(`lobby-${gameId}`)).toHaveCount(0);

    await b.page.getByTestId('invite-code-input').fill(inviteCode);
    await b.page.getByTestId('join-by-code-button').click();

    await expect(b.page).toHaveURL(new RegExp(`/games/${gameId}$`));
    await expect(a.page.getByTestId('fleet-placement')).toBeVisible();
    await expect(b.page.getByTestId('fleet-placement')).toBeVisible();

    await closeAll(a, b);
  });

  test('unbekannter Einladungscode zeigt eine Fehlermeldung', async ({ browser }) => {
    const a = await registerPlayer(browser, uniqueName('Emil'));

    await a.page.getByTestId('invite-code-input').fill('ZZZZZZ');
    await a.page.getByTestId('join-by-code-button').click();

    await expect(a.page.getByTestId('error-message')).toBeVisible();
    await expect(a.page).toHaveURL(/\/lobbies$/);

    await closeAll(a);
  });
});

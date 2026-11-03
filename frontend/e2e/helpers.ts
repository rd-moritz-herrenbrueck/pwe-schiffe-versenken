import { Browser, BrowserContext, Page, expect } from '@playwright/test';

/** Spieler mit eigenem Browser-Context, damit `localStorage` (Token) getrennt bleibt. */
export interface TestPlayer {
  context: BrowserContext;
  page: Page;
  nickname: string;
}

const FLEET_LENGTHS = [5, 4, 3, 2];
/** Standardflotte waagerecht in den Zeilen 0, 2, 4 und 6, Schiffe berühren sich nicht. */
const FLEET_ROWS = [0, 2, 4, 6];

/** Felder der Standardflotte aus `placeStandardFleet`. */
export const FLEET_CELLS: [number, number][] = FLEET_LENGTHS.flatMap((length, index) =>
  Array.from({ length }, (_, x): [number, number] => [x, FLEET_ROWS[index]]),
);

/** Felder ohne Schiff der Standardflotte, in Zeile 8 und 9. */
export const WATER_CELLS: [number, number][] = [8, 9].flatMap((y) =>
  Array.from({ length: 10 }, (_, x): [number, number] => [x, y]),
);

/** Legt einen Spieler an und wartet, bis die Lobby-Liste offen ist. */
export async function registerPlayer(browser: Browser, nickname: string): Promise<TestPlayer> {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto('/');
  await page.getByTestId('nickname-input').fill(nickname);
  await page.getByTestId('start-button').click();
  await expect(page).toHaveURL(/\/lobbies$/);
  await expect(page.getByTestId('player-nickname')).toHaveText(nickname);
  return { context, page, nickname };
}

/** Eindeutiger Nickname je Testlauf (höchstens 20 Zeichen). */
export function uniqueName(prefix: string): string {
  return `${prefix}${Date.now().toString(36)}`.slice(0, 20);
}

export function gameIdOf(page: Page): string {
  const id = new URL(page.url()).pathname.split('/').pop();
  if (!id) {
    throw new Error(`Keine Spiel-ID in ${page.url()}`);
  }
  return id;
}

/** Platziert die Standardflotte per Klick und bestätigt sie. */
export async function placeStandardFleet(page: Page): Promise<void> {
  const board = page.getByTestId('own-board');
  for (const y of FLEET_ROWS) {
    await board.getByTestId(`cell-0-${y}`).click();
  }
  await page.getByTestId('confirm-fleet-button').click();
  await expect(
    page.getByTestId('waiting-for-fleet').or(page.getByTestId('turn-indicator')),
  ).toBeVisible();
}

/** Spieler A erstellt eine offene Lobby, Spieler B tritt über die Lobby-Liste bei. Beide sind danach in der Platzierung. */
export async function startOpenGame(a: TestPlayer, b: TestPlayer): Promise<string> {
  await a.page.getByTestId('create-lobby-button').click();
  await expect(a.page.getByTestId('waiting-for-opponent')).toBeVisible();
  const gameId = gameIdOf(a.page);

  await b.page.getByTestId(`join-lobby-${gameId}`).click();
  await expect(b.page).toHaveURL(new RegExp(`/games/${gameId}$`));
  await expect(a.page.getByTestId('fleet-placement')).toBeVisible();
  await expect(b.page.getByTestId('fleet-placement')).toBeVisible();
  return gameId;
}

/** Beide platzieren die Standardflotte, danach läuft das Spiel und A ist am Zug. */
export async function startRunningGame(a: TestPlayer, b: TestPlayer): Promise<string> {
  const gameId = await startOpenGame(a, b);
  await placeStandardFleet(a.page);
  await placeStandardFleet(b.page);
  await expectTurn(a.page, true);
  await expectTurn(b.page, false);
  return gameId;
}

export async function expectTurn(page: Page, yourTurn: boolean): Promise<void> {
  await expect(page.getByTestId('turn-indicator')).toHaveAttribute(
    'data-your-turn',
    String(yourTurn),
  );
}

export async function fire(page: Page, x: number, y: number): Promise<void> {
  await page.getByTestId('enemy-board').getByTestId(`cell-${x}-${y}`).click();
}

export async function closeAll(...players: TestPlayer[]): Promise<void> {
  await Promise.all(players.map((player) => player.context.close()));
}

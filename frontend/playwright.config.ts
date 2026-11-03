import { defineConfig, devices } from '@playwright/test';

/**
 * E2E-Tests laufen gegen den Docker-Compose-Stack (siehe docs/Architektur.md, Abschnitt E2E).
 * Standard ist die Frontend-Adresse des Stacks, über `E2E_BASE_URL` lässt sich ein anderes Ziel setzen
 * (z. B. `http://localhost:4200` mit `npm start` und laufendem Backend).
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.spec.ts',
  // Jeder Test legt eigene Spieler an, die Tests teilen aber eine Datenbank und die öffentliche Lobby-Liste.
  fullyParallel: false,
  workers: 1,
  retries: process.env['CI'] ? 1 : 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: process.env['CI'] ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:8081',
    trace: 'retain-on-failure',
    locale: 'de-DE',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});

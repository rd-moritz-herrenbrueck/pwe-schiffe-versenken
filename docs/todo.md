# Todo

Legende: `[ ]` offen · `[x]` erledigt · *(optional)* = Nice-to-have

## Phase 0: Konzept
- [x] Anforderungen und Technologien abgestimmt
- [x] README.md, Architektur.md und todo.md erstellt

## Phase 1: Projektgerüst
- [x] Verzeichnisstruktur `backend/`, `frontend/`, `docs/` anlegen
- [x] Quarkus-Projekt mit Maven erzeugen (REST, Hibernate Panache, Flyway, PostgreSQL, SSE)
- [x] Angular-Projekt erzeugen (Standalone, Router)
- [x] `docker-compose.yml` mit PostgreSQL
- [x] `.gitignore` ergänzen (`target/`, `node_modules/`, `.idea/` …), IDE-Dateien aus dem Repository entfernt
- [x] Smoke-Test `HealthTest` (App + DB bereit) außerhalb der Sandbox mit Docker ausführen

## Phase 2: Domäne (Backend)
- [x] Domänentests vorab geschrieben (TDD, rot) und API-Skelette angelegt
- [x] Abnahme der Domänentests
- [x] `RuleSet` mit Standardwerten (10x10, 5/4/3/2), Feld max. 25x25, frei konfigurierbare Schiffslängen (≥ 2)
- [x] Plausibilitätsprüfung: Flotte muss platzierbar sein (abhängig von `shipsMayTouch`)
- [x] `Board`, `Ship`, Koordinaten, Schussergebnisse
- [x] Flottenplatzierung inkl. Validierung
- [x] Schusslogik (Miss/Hit/Sunk/Win), Rundenwechsel
- [x] Spielphasen und Zustandsübergänge
- [x] Parametrisierte Unit-Tests (JUnit 5), auch für Regelvarianten

## Phase 3: Persistenz und REST
- [x] Abwägung: Unit-Tests nur für reine Logik (Snapshot/Restore, Speicherformat, Spielersicht, Token-Hash), DB und REST über `@QuarkusTest`
- [x] Tests vorab geschrieben (TDD, rot) und API-Skelette angelegt
- [ ] Abnahme der Tests
- [ ] `Game.snapshot()`/`Game.restore(…)` (Spielzustand speichern und wiederherstellen)
- [ ] Flyway-Migration: `player`, `game`, `chat_message`
- [ ] Panache-Entities und Repositories, JSONB-Mapping für Regeln und Zustand
- [ ] Optimistisches Locking (`version`)
- [ ] Spieler anlegen und Token-Authentifizierung (Hash in der DB)
- [ ] Lobby: erstellen, auflisten, beitreten
- [ ] Spiel: Zustand abrufen (ohne gegnerische Schiffe), Flotte setzen, schießen, aufgeben
- [ ] Liste eigener Spiele, Fortsetzen
- [ ] Integrationstests (`@QuarkusTest`, Testcontainers, REST Assured)
- [ ] Job für verwaiste Spiele (`ABANDONED`)

## Phase 4: Echtzeit (SSE)
- [ ] Event-Bus (In-Memory) und Spiel-Events
- [ ] SSE-Endpunkt `/api/games/{id}/events` inkl. Heartbeat
- [ ] SSE-Endpunkt für die Lobby-Liste
- [ ] Integrationstests für SSE

## Phase 5: Frontend
- [ ] `PlayerService` (Token im localStorage), `ApiService`, `EventService`, `GameStore`
- [ ] Startseite mit Nickname
- [ ] Lobby-Liste und Lobby erstellen (Standardregeln)
- [ ] Flotte platzieren (Klick/Drag, Drehen)
- [ ] Spielansicht (eigenes und gegnerisches Feld, Rundenanzeige)
- [ ] „Meine Spiele“ und Fortsetzen nach Reload
- [ ] Reconnect-Logik für SSE (Zustand neu laden)
- [ ] `data-testid`-Attribute für E2E
- [ ] Unit-Tests für Komponenten und Services

## Phase 6: Erweiterungen
- [ ] Einstellbare Regeln, Feldgrößen (max. 25x25) und Schiffslängen im UI, nur platzierbare Flotten auswählbar *(optional)*
- [ ] Private Lobby mit Einladungscode *(optional)*
- [ ] Chat (Backend, SSE, UI)
- [ ] KI-Gegner (zufällige Platzierung, Hunt/Target), „gegen KI spielen“ im UI
- [ ] Fester KI-Seed im Testprofil
- [x] CI/CD-Konzept mit GitHub Actions und IONOS-VPS abgestimmt (vorgezogen, siehe `docs/Deployment.md`)
- [x] Dockerfiles für Backend und Frontend (nginx mit `/api`-Proxy, SSE ohne Buffering), vorgezogen aus Phase 7
- [x] CI-Workflow `ci.yml`: `mvnw verify` (inkl. Testcontainers), Frontend-Tests und -Build bei PR und Push auf `main`
- [x] Deploy-Workflow `deploy.yml`: bei Tag `v*` Images nach GHCR, per SSH auf den VPS ausrollen, Rollback per `workflow_dispatch`
- [x] `docker-compose.prod.yml` (nur `127.0.0.1:APP_PORT` nach außen, Postgres nicht erreichbar)
- [ ] GitHub-Environment `production` mit Variables und Secrets anlegen
- [ ] VPS einrichten: Deploy-User, SSH-Key, vHost für `moritzherrenbrueck.de` im vorhandenen Reverse-Proxy, Backup per Cron
- [ ] Erstes Release-Tag ausrollen (setzt grüne Tests aus Phase 3 voraus)

## Phase 7: E2E und Docker
- [ ] Playwright einrichten (`frontend/e2e/`, `playwright.config.ts`)
- [ ] E2E: Spiel erstellen/beitreten (offen und privat)
- [ ] E2E: Zwei Spieler spielen bis zum Sieg (getrennte Browser-Contexts)
- [ ] E2E: Reload mitten im Spiel
- [ ] E2E: Spiel gegen KI
- [ ] E2E: Chat
- [x] Dockerfiles für Backend und Frontend (vorgezogen in Phase 6)
- [ ] `docker compose up` startet den gesamten Stack
- [ ] Testprofil mit leerer Datenbank für E2E

## Phase 8: Abschluss
- [x] CI-Pipeline (GitHub Actions): `mvn verify`, Frontend-Tests (vorgezogen in Phase 6)
- [ ] Playwright-E2E in die CI aufnehmen *(optional)*
- [ ] README mit realen Start- und Testbefehlen aktualisieren
- [ ] Feinschliff (Fehlermeldungen, Responsive Design, Rate-Limiting für Chat)

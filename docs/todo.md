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
- [x] Abnahme der Tests
- [x] `Game.snapshot()`/`Game.restore(…)` (Spielzustand speichern und wiederherstellen)
- [x] Flyway-Migration: `player`, `game`, `chat_message`
- [x] Panache-Entities und Repositories, JSONB-Mapping für Regeln und Zustand
- [x] Optimistisches Locking (`version`)
- [x] Spieler anlegen und Token-Authentifizierung (Hash in der DB)
- [x] Lobby: erstellen, auflisten, beitreten
- [x] Spiel: Zustand abrufen (ohne gegnerische Schiffe), Flotte setzen, schießen, aufgeben
- [x] Liste eigener Spiele, Fortsetzen
- [x] Integrationstests (`@QuarkusTest`, Testcontainers, REST Assured) geschrieben
- [x] Job für verwaiste Spiele (`ABANDONED`)
- [x] Integrationstests außerhalb der Sandbox mit Docker ausführen (`cd backend && ./mvnw verify`)

## Phase 4: Echtzeit (SSE)
- [x] Tests vorab geschrieben (TDD, rot) und Skelette angelegt
- [x] Abnahme der Tests
- [x] Event-Bus (In-Memory) und Spiel-Events
- [x] SSE-Endpunkt `/api/games/{id}/events` inkl. Heartbeat
- [x] SSE-Endpunkt für die Lobby-Liste
- [x] Integrationstests für SSE außerhalb der Sandbox mit Docker ausführen (`cd backend && ./mvnw verify`)

## Phase 5: Frontend
- [x] Tests vorab geschrieben (TDD, rot) und Skelette angelegt
- [x] Abnahme der Tests
- [x] PrimeNG (+ `@primeuix/themes`, `@angular/cdk`) einbinden und Standardelemente damit umsetzen, soweit möglich
- [x] Hell- und Dunkelmodus (`ThemeService`, Umschalter, Klasse `app-dark`, Systemeinstellung als Startwert)
- [x] `PlayerService` (Token im localStorage), `ApiService`, `EventService`, `GameStore`
- [x] Startseite mit Nickname
- [x] Lobby-Liste und Lobby erstellen (Standardregeln)
- [x] Flotte platzieren (Klick/Drag, Drehen)
- [x] Spielansicht (eigenes und gegnerisches Feld, Rundenanzeige)
- [x] „Meine Spiele“ und Fortsetzen nach Reload
- [x] Reconnect-Logik für SSE (Zustand neu laden)
- [x] `data-testid`-Attribute für E2E
- [x] Unit-Tests für Komponenten und Services
- [x] Frontend außerhalb der Sandbox im Browser gegen das laufende Backend prüfen (`docker compose up -d db`, `./mvnw quarkus:dev`, `npm start`; zwei Browser-Contexts, Hell-/Dunkelmodus)

## Phase 6: Erweiterungen
- [x] Tests für die offenen Punkte vorab geschrieben (TDD, rot) und Skelette angelegt (`AiStrategy`/`HuntTargetStrategy`, `Chat`, `CreateLobbyForm`, API-/Store-Methoden)
- [x] Abnahme der Tests
- [x] Einstellbare Regeln, Feldgrößen (max. 25x25) und Schiffslängen im UI, nur platzierbare Flotten auswählbar *(optional)*; dafür Endpunkt `POST /api/rules/check`
- [x] Private Lobby mit Einladungscode *(optional)*
- [x] Chat (Backend, SSE, UI)
- [x] KI-Gegner (zufällige Platzierung, Hunt/Target), „gegen KI spielen“ im UI
- [x] Fester KI-Seed im Testprofil (`%test.game.ai.seed`, gleiche Eingaben ergeben gleiche KI-Flotten und -Schüsse)
- [x] Integrationstests der Phase 6 außerhalb der Sandbox mit Docker ausführen (`cd backend && ./mvnw verify`)
- [x] Phase-6-Funktionen außerhalb der Sandbox im Browser prüfen (privat per Code beitreten mit zwei Browser-Contexts, Chat, Spiel gegen die KI, Regelformular, Hell-/Dunkelmodus)
- [x] CI/CD-Konzept mit GitHub Actions und IONOS-VPS abgestimmt (vorgezogen, siehe `docs/Deployment.md`)
- [x] Dockerfiles für Backend und Frontend (nginx mit `/api`-Proxy, SSE ohne Buffering), vorgezogen aus Phase 7
- [x] CI-Workflow `ci.yml`: `mvnw verify` (inkl. Testcontainers), Frontend-Tests und -Build bei PR und Push auf `main`
- [x] Deploy-Workflow `deploy.yml`: bei Tag `v*` Images nach GHCR, per SSH auf den VPS ausrollen, Rollback per `workflow_dispatch`
- [x] `docker-compose.prod.yml` (nur `127.0.0.1:APP_PORT` nach außen, Postgres nicht erreichbar)
- [x] GitHub-Environment `production` mit Variables und Secrets anlegen
- [x] VPS einrichten: Deploy-User, SSH-Key, vHost für `moritzherrenbrueck.de` im vorhandenen Reverse-Proxy, Backup per Cron
- [x] Erstes Release-Tag ausrollen (setzt grüne Tests aus Phase 3 voraus)

## Phase 7: E2E und Docker
- [x] Playwright einrichten (`frontend/e2e/`, `playwright.config.ts`)
- [x] E2E: Spiel erstellen/beitreten (offen und privat)
- [x] E2E: Zwei Spieler spielen bis zum Sieg (getrennte Browser-Contexts)
- [x] E2E: Reload mitten im Spiel
- [x] E2E: Spiel gegen KI
- [x] E2E: Chat
- [x] Dockerfiles für Backend und Frontend (vorgezogen in Phase 6)
- [x] `docker compose up` startet den gesamten Stack
- [x] Testprofil mit leerer Datenbank für E2E

## Phase 8: Abschluss
- [x] CI-Pipeline (GitHub Actions): `mvn verify`, Frontend-Tests (vorgezogen in Phase 6)
- [x] Playwright-E2E in die CI aufnehmen *(optional)*
- [x] README mit realen Start- und Testbefehlen aktualisieren
- [x] Feinschliff (Fehlermeldungen, Responsive Design, Rate-Limiting für Chat)
- [x] UI Optimierung des Schiffe platzieren: Bessere Visualisierung beim Drag & Drop
- [x] Offene Lobbies, die noch nicht gestartet wurden, nach 24h automatisch löschen und manuelles löschen ermöglichen

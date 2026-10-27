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
- [ ] `RuleSet` mit Standardwerten (10x10, 5/4/3/3/2)
- [ ] `Board`, `Ship`, Koordinaten, Schussergebnisse
- [ ] Flottenplatzierung inkl. Validierung
- [ ] Schusslogik (Miss/Hit/Sunk/Win), Rundenwechsel
- [ ] Spielphasen und Zustandsübergänge
- [ ] Parametrisierte Unit-Tests (JUnit 5), auch für Regelvarianten

## Phase 3: Persistenz und REST
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
- [ ] Einstellbare Regeln und Feldgrößen im UI *(optional)*
- [ ] Private Lobby mit Einladungscode *(optional)*
- [ ] Chat (Backend, SSE, UI)
- [ ] KI-Gegner (zufällige Platzierung, Hunt/Target), „gegen KI spielen“ im UI
- [ ] Fester KI-Seed im Testprofil

## Phase 7: E2E und Docker
- [ ] Playwright einrichten (`frontend/e2e/`, `playwright.config.ts`)
- [ ] E2E: Spiel erstellen/beitreten (offen und privat)
- [ ] E2E: Zwei Spieler spielen bis zum Sieg (getrennte Browser-Contexts)
- [ ] E2E: Reload mitten im Spiel
- [ ] E2E: Spiel gegen KI
- [ ] E2E: Chat
- [ ] Dockerfiles für Backend und Frontend (nginx mit `/api`-Proxy, SSE ohne Buffering)
- [ ] `docker compose up` startet den gesamten Stack
- [ ] Testprofil mit leerer Datenbank für E2E

## Phase 8: Abschluss
- [ ] CI-Pipeline (GitHub Actions): `mvn verify`, Frontend-Tests, Playwright *(optional)*
- [ ] README mit realen Start- und Testbefehlen aktualisieren
- [ ] Feinschliff (Fehlermeldungen, Responsive Design, Rate-Limiting für Chat)

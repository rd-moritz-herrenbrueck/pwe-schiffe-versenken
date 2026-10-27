# Copilot Instructions – PWE-SchiffeVersenken

Online-Multiplayer-Version von „Schiffe versenken“ als Webanwendung. Mehrere Spiele laufen parallel, Spielstände werden gespeichert und können jederzeit fortgesetzt werden.

> Das Projektgerüst steht (Phase 1). Maßgeblich sind [`docs/Architektur.md`](../docs/Architektur.md) (Architektur) und [`docs/todo.md`](../docs/todo.md) (Roadmap). Befehle in der README beschreiben den Zielzustand.

## Sprache und Kommunikation

- Dokumentation, Commit-Messages und Erklärungen auf **Deutsch**.
- Code-Bezeichner (Klassen, Methoden, Variablen) auf **Englisch**, wie im Domänenmodell (`RuleSet`, `Board`, `Ship`, `Game`).
- Annahmen, die nicht abgestimmt sind, explizit benennen und Alternativen mit Vor- und Nachteilen aufzeigen, statt sie stillschweigend zu treffen.

## Tech-Stack

| Bereich | Technologie |
|---|---|
| Frontend | TypeScript, Angular (Standalone Components, Signals, Router, `HttpClient`) |
| Backend | Java, Quarkus (REST + Server-Sent Events), Hibernate Panache |
| Datenbank | PostgreSQL, Flyway-Migrationen, JSONB für Spielzustand |
| Build | Maven (Backend), npm (Frontend) |
| Tests | JUnit 5, `@QuarkusTest`, REST Assured, Testcontainers, Vitest, Playwright (E2E) |
| Versionen | Java 25, Quarkus 3.40 (LTS), Angular 22, PostgreSQL 17 |
| Betrieb | Docker / Docker Compose |

## Projektstruktur

```
backend/              Quarkus-Anwendung (Maven)
frontend/             Angular-Anwendung
  e2e/                Playwright-Tests
docs/
  Architektur.md      Architekturbeschreibung
  todo.md             Aufgabenliste / Roadmap
  ai-journal.md       Dokumentation der KI-gestützten Arbeit
docker-compose.yml    Postgres + Backend + Frontend
```

## Bevorzugte Arbeitsweise: Test-Driven Development (TDD)

Neue Funktionalität und Bugfixes werden **testgetrieben** entwickelt:

1. **Red** – Zuerst einen kleinen, fehlschlagenden Test schreiben, der das gewünschte Verhalten beschreibt. Test ausführen und sicherstellen, dass er aus dem erwarteten Grund fehlschlägt.
2. **Green** – Nur so viel Produktionscode schreiben, wie nötig ist, damit der Test besteht.
3. **Refactor** – Code und Tests aufräumen, ohne das Verhalten zu ändern. Alle Tests bleiben grün.

Regeln dazu:

- Kleine Schritte: ein Verhalten pro Zyklus.
- Bei Bugs zuerst einen Test schreiben, der den Fehler reproduziert.
- Vorschläge für Code immer zusammen mit dem passenden Test liefern, der Test kommt zuerst.
- Die passende Testebene wählen (siehe Tabelle unten). Spielregeln werden auf Domänenebene getestet, nicht über REST oder UI.
- Regelvarianten (`RuleSet`) mit **parametrisierten Tests** (JUnit 5 `@ParameterizedTest`) abdecken.
- Tests sind deterministisch: kein echter Zufall und keine echte Zeit in Tests (z. B. fester KI-Seed im Testprofil, injizierbarer Zufall bzw. `Clock`).
- Tests beschreiben Verhalten, nicht Implementierungsdetails. Testnamen drücken das erwartete Verhalten aus (z. B. `shotOnAlreadyHitCellIsRejected`).

### Testebenen

| Ebene | Werkzeug | Umfang |
|---|---|---|
| Domäne | JUnit 5 | Platzierung, Schussauswertung, Siegbedingung, `RuleSet`-Varianten, KI-Strategie |
| Backend-Integration | `@QuarkusTest`, REST Assured, Testcontainers (Postgres) | REST, SSE, Persistenz, Berechtigungen |
| Frontend-Unit | Vitest (Angular-Standard, `ng test`) | Komponenten und Services |
| E2E | Playwright (`frontend/e2e/`) | Gegen den Docker-Compose-Stack |

### Entwicklungsumgebung: Sandbox

Copilot entwickelt in einer **Sandbox**. Dort stehen Docker, PostgreSQL und ggf. weitere Infrastruktur **nicht** zur Verfügung. Getestet werden kann jedoch **außerhalb der Sandbox** durch die Entwickler:innen.

- In der Sandbox nur ausführen, was ohne Infrastruktur läuft: Kompilieren (`./mvnw package -DskipTests`), Domänen-Unit-Tests (JUnit ohne `@QuarkusTest`), Frontend-Unit-Tests (`npm test`) und Frontend-Build.
- Keine Infrastruktur (z. B. PostgreSQL) lokal in der Sandbox installieren. Die Datenbank kommt ausschließlich aus `docker-compose.yml` bzw. Quarkus Dev Services (Testcontainers).
- Tests, die Docker benötigen (`@QuarkusTest` mit Dev Services/Testcontainers, `docker compose`, Playwright gegen den Stack), trotzdem testgetrieben schreiben. Sie werden außerhalb der Sandbox ausgeführt.
- Am Ende jeder Aufgabe explizit nennen, welche Tests in der Sandbox gelaufen sind und welche außerhalb ausgeführt werden müssen, inklusive der konkreten Befehle.

### Befehle

```bash
cd backend && ./mvnw verify                                       # Unit- und Integrationstests (Docker nötig)
cd backend && ./mvnw test -Dtest='de.pwe.schiffeversenken.domain.**'  # Nur Domänentests (ohne Docker, Sandbox)
cd frontend && npm test -- --watch=false                         # Unit-Tests (Vitest)
cd frontend && npx playwright test                               # E2E-Tests (ab Phase 7)
```

Entwicklung:

```bash
docker compose up -d db                          # Datenbank
cd backend && ./mvnw quarkus:dev                 # Backend (http://localhost:8080)
cd frontend && npm install && npm start          # Frontend (http://localhost:4200)
docker compose up --build                        # Gesamter Stack (ab Phase 7)
```

## Architekturgrundsätze

- **Client-Server:** Angular-SPA ↔ Quarkus-Backend. **Aktionen** (Lobby erstellen, Flotte setzen, schießen, chatten) über REST, **Updates** über Server-Sent Events.
- **Server ist die einzige Wahrheit:** Alle Spielregeln werden im Backend geprüft, der Client ist reine Darstellung.
- **Anti-Cheat:** Gegnerische Schiffspositionen verlassen den Server nie. Der Client erhält nur eigene Schiffe und Schussergebnisse auf das gegnerische Feld.
- **Zustandsloses Backend:** Der Spielzustand liegt in der DB, jeder Zug wird persistiert. Neustart oder Reload verliert nichts.
- Nach Reload oder SSE-Reconnect lädt der Client den Zustand immer per `GET /api/games/{id}` neu.

### Backend-Schichten

Basis-Package: `de.pwe.schiffeversenken`

```
api/          REST-Ressourcen, SSE-Endpunkte, DTOs
service/      Anwendungslogik (Lobby, Spiel, Chat, KI-Orchestrierung)
domain/       Reine Spiellogik ohne Framework-Abhängigkeiten
persistence/  Panache-Entities, Repositories
```

- `domain/` hat **keine** Abhängigkeiten zu Quarkus oder der Datenbank und ist isoliert testbar. Hier beginnt TDD für jede Spielregel.
- DTOs in `api/` nicht mit Entities oder Domänenobjekten vermischen.

### Domänenmodell

- **RuleSet:** `boardWidth`, `boardHeight`, `fleet` (Schiffslängen), `shootAgainOnHit`, `shipsMayTouch`. Standard: 10x10, je ein Schiff der Länge 5/4/3/2, kein Nochmal-Schießen, Schiffe dürfen sich nicht berühren. Feld höchstens 25x25, Flotte frei konfigurierbar (nur gerade Schiffe, Länge ≥ 2), muss aber platzierbar sein (Plausibilitätsprüfung abhängig von `shipsMayTouch`).
- **Board:** Raster, platzierte `Ship`s, empfangene Schüsse.
- **Ship:** Startkoordinate, Länge, Ausrichtung, Trefferzustand.
- **Game:** Spieler A/B, je ein `Board`, `phase` (`WAITING`, `PLACEMENT`, `RUNNING`, `FINISHED`, `ABANDONED`), `currentTurn`, `winner`.
- **Shot-Ergebnis:** `MISS`, `HIT`, `SUNK` (mit Schiff), `WIN`.

Validierungen (serverseitig):
- `RuleSet` gültig (Feldgröße 1–25, Schiffslängen ≥ 2, Flotte platzierbar).
- Schiffe liegen im Feld, überschneiden sich nicht und entsprechen der Flotte des `RuleSet`.
- Schüsse nur in der eigenen Runde, nur in Phase `RUNNING`, nur auf noch nicht beschossene Felder.

Keine festen Werte wie `10` oder feste Flotten im Code: Feldgröße und Flotte immer aus dem `RuleSet` lesen.

### Identität

- Beim ersten Besuch legt der Server einen `Player` (UUID + Nickname) an und gibt ein Token zurück.
- Client speichert das Token im `localStorage` und sendet es im `Authorization`-Header.
- Keine Passwörter oder Konten. Das Token wird in der DB nur als **Hash** gespeichert.
- Zugriff auf ein Spiel nur für beteiligte Spieler.

### REST-API (Entwurf)

| Methode | Pfad | Zweck |
|---|---|---|
| POST | `/api/players` | Spieler anlegen, Token erhalten |
| GET | `/api/lobbies` | Offene Lobbys auflisten |
| POST | `/api/lobbies` | Lobby/Spiel erstellen (`RuleSet`, `visibility`, `vsAi`) |
| POST | `/api/lobbies/join` | Beitreten per ID oder Einladungscode |
| GET | `/api/games` | Eigene Spiele |
| GET | `/api/games/{id}` | Spielzustand aus Sicht des Spielers |
| PUT | `/api/games/{id}/fleet` | Flotte platzieren/bestätigen |
| POST | `/api/games/{id}/shots` | Schuss abgeben |
| POST | `/api/games/{id}/resign` | Aufgeben |
| GET/POST | `/api/games/{id}/chat` | Chat lesen / senden |
| GET | `/api/games/{id}/events` | SSE-Stream für das Spiel |
| GET | `/api/lobbies/events` | SSE-Stream für die Lobby-Liste |

### Server-Sent Events

- Umsetzung mit `Multi<…>` und `@RestStreamElementType`, In-Memory-Event-Bus pro Instanz, Heartbeats senden.
- Ereignistypen: `PLAYER_JOINED`, `FLEET_READY`, `GAME_STARTED`, `SHOT`, `TURN_CHANGED`, `GAME_FINISHED`, `CHAT`.

### KI-Gegner

- Strategie hinter dem Interface `AiStrategy`. Standard: zufällige, regelkonforme Platzierung und Hunt/Target beim Schießen.
- Zufall injizierbar, im Testprofil fester Seed.

### Datenbank

- Schemaänderungen **nur über Flyway-Migrationen**, bestehende Migrationen nie ändern.
- Tabellen: `player`, `game`, `chat_message`.
- Spielzustand (Boards, Schiffe, Schüsse) als JSONB in `game.state`, Regeln als JSONB in `game.rules`.
- `game.version` für optimistisches Locking.
- Indizes auf `(visibility, status)` und `invite_code`.
- Inaktive, nicht beendete Spiele werden per geplantem Job als `ABANDONED` markiert.

### Frontend

- Standalone Components und Signals, keine NgModules.
- Services: `ApiService` (REST), `EventService` (SSE über `EventSource`), `PlayerService` (Token), `GameStore` (Signals).
- Spielfeld als CSS-Grid, Größe aus dem `RuleSet`.
- Stabile `data-testid`-Attribute für E2E (z. B. `cell-{x}-{y}`, `own-board`, `enemy-board`).

### Docker

- Dienste `db` (Postgres, Volume), `backend` (Quarkus, Multi-Stage-Build mit Maven), `frontend` (nginx mit Angular-Bundle und Reverse-Proxy auf `/api`).
- Konfiguration über Umgebungsvariablen (`QUARKUS_DATASOURCE_*`, `GAME_*`).
- SSE im nginx-Proxy ohne Buffering.

## Sicherheit

- Eingaben serverseitig validieren, Längenbegrenzung und Rate-Limiting für Chat und Nicknames.
- Keine Geheimnisse ins Repository einchecken.
- Build-Artefakte und IDE-Dateien (`target/`, `node_modules/`, `.idea/`) nicht committen.

## Dokumentation pflegen

- Erledigte Aufgaben in `docs/todo.md` abhaken, neue Aufgaben der passenden Phase zuordnen.
- Architekturentscheidungen in `docs/Architektur.md` nachziehen, wenn sie sich ändern.
- KI-gestützte Arbeitsschritte in `docs/ai-journal.md` festhalten (Datum, Ausgangsfrage, Kern der Antwort, Verfeinerungen, kritische Reflexion).
- README-Befehle aktualisieren, sobald sie tatsächlich lauffähig sind.

## Offene Punkte (vor Umsetzung abstimmen)

- JSONB vs. relationales Modell für den Spielzustand.
- Berührungsregel (`shipsMayTouch`) als Standard.
- Mehrere Backend-Instanzen würden einen verteilten Event-Bus erfordern (Postgres `LISTEN/NOTIFY` oder Broker).

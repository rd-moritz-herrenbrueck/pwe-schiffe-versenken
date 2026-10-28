# Architektur

## 1. Ziele und Anforderungen

| # | Anforderung | Umsetzung |
|---|---|---|
| M1 | Client-Server-Architektur | Angular-SPA ↔ Quarkus-Backend über REST und SSE |
| M2 | Webapp | Angular (TypeScript) |
| M3 | Spielstände speichern/fortsetzen | Jeder Zug wird in PostgreSQL persistiert; Spieler werden über Token wiedererkannt |
| M4 | Standardregeln 10x10 | Default-`RuleSet` |
| M5 | Mehrere Spiele parallel | Eigene Spiel-ID, zustandsloses Backend |
| O1 | Eigene Regeln/Feldgrößen | Konfigurierbares `RuleSet` |
| O2 | Open/Private-Lobby | `visibility` + Einladungscode |
| Z1 | Chat | Nachrichten pro Spiel (REST + SSE) |
| Z2 | KI-Gegner | Serverseitiger Spielertyp |
| Z3 | Docker | `docker-compose.yml` inkl. Datenbank |
| Z4 | Tests | JUnit 5, Testcontainers, Playwright |

## 2. Systemüberblick

```
┌────────────────┐   REST (JSON)    ┌──────────────────┐   JDBC   ┌────────────┐
│ Angular (SPA)  │ ───────────────► │ Quarkus Backend  │ ───────► │ PostgreSQL │
│                │ ◄─────────────── │                  │          └────────────┘
│                │   SSE (Events)   │  REST · SSE      │
└────────────────┘                  │  Spiellogik · KI │
                                    └──────────────────┘
```

- **Aktionen** (Lobby erstellen, Schiffe platzieren, schießen, chatten) laufen über REST.
- **Updates** (Gegner hat geschossen, Spieler beigetreten, Chatnachricht) kommen über Server-Sent Events.
- Das Backend ist zustandslos: Der Spielzustand liegt in der DB. Ein Neustart oder Reload verliert nichts.

## 3. Backend (Quarkus, Java)

### 3.1 Schichten

Java 25, Quarkus 3.40 (LTS), Basis-Package `de.pwe.schiffeversenken`.

```
api/          REST-Ressourcen, SSE-Endpunkte, DTOs
service/      Anwendungslogik (Lobby, Spiel, Chat, KI-Orchestrierung)
domain/       Reine Spiellogik ohne Framework-Abhängigkeiten
persistence/  Panache-Entities, Repositories
```

Die `domain`-Schicht enthält die Regeln und ist ohne Quarkus und DB testbar.

### 3.2 Domänenmodell

- **RuleSet**: `boardWidth`, `boardHeight`, `fleet` (Liste von Schiffslängen), `shootAgainOnHit`, `shipsMayTouch`. Standard: 10x10, je ein Schiff der Länge 5, 4, 3 und 2, kein Nochmal-Schießen, Schiffe dürfen sich nicht berühren (konfigurierbar).
  - Feldgröße höchstens 25x25.
  - Flotte frei konfigurierbar: Anzahl und Länge der Schiffe (Länge ≥ 2). Schiffe sind gerade (Breite 1), keine breiten oder gewinkelten Schiffe.
  - Plausibilitätsprüfung: Ein `RuleSet` ist nur gültig, wenn es mindestens eine regelkonforme Platzierung der gesamten Flotte gibt (abhängig von `shipsMayTouch`). Eine reine Flächenabschätzung genügt nicht (Beispiel: 4x4 mit drei Dreiern ohne Berührung ist unmöglich).
  - Umsetzung (`FleetPlacementSearch`): Die Prüfung wird als Rechteck-Packung gelöst. Mit Berührung belegt ein Schiff der Länge L ein L×1-Rechteck im W×H-Feld, ohne Berührung ein (L+1)×2-Rechteck im (W+1)×(H+1)-Feld (jedes Schiffsfeld um eins nach rechts und unten erweitert; die Rechtecke überschneiden sich genau dann nicht, wenn sich die Schiffe nicht berühren). Gesucht wird per Tiefensuche über das erste freie Feld mit Schranken für den unvermeidbaren Verschnitt (nicht mehr abdeckbare Felder, freie Zeilen-/Spaltenabschnitte).
  - Die Suche hat ein festes Schrittbudget (deterministisch, im ungünstigsten Fall etwa eine Sekunde Rechenzeit). Wird es überschritten, gilt die Flotte als nicht platzierbar. Das betrifft nur extrem dichte Flotten (Stichprobe: Füllgrad der erweiterten Fläche ab ca. 86 %, unter 0,3 % zufälliger Flotten); erlaubt sind nur Flotten, für die tatsächlich eine Platzierung gefunden wurde.
- **Board**: Raster, platzierte `Ship`s, empfangene Schüsse.
- **Ship**: Startkoordinate, Länge, Ausrichtung, Trefferzustand.
- **Game**: Spieler A/B, jeweils ein `Board`, `phase` (`WAITING`, `PLACEMENT`, `RUNNING`, `FINISHED`, `ABANDONED`), `currentTurn`, `winner`.
- **Shot-Ergebnis**: `MISS`, `HIT`, `SUNK` (mit Schiff), `WIN`.
- Regelverstöße werden als `RuleViolationException` mit Grund (`Violation`) abgelehnt, der Zustand bleibt dabei unverändert.

Festlegungen der Domäne:
- Koordinaten sind 0-basiert, `x` ist die Spalte, `y` die Zeile. Ein Schiff liegt ab seiner Startkoordinate nach rechts (`HORIZONTAL`) bzw. nach unten (`VERTICAL`).
- Ohne `shipsMayTouch` gilt auch diagonale Nachbarschaft als Berühren.
- Die Flotte wird in einem Schritt gesetzt und damit bestätigt; die Reihenfolge der Schiffe ist egal.
- Sind beide Flotten gesetzt, beginnt Spieler A (`RUNNING`).
- Ein Fehlschuss gibt den Zug immer ab. Treffer und Versenken geben den Zug nur ab, wenn `shootAgainOnHit` aus ist.
- Aufgeben ist in `PLACEMENT` und `RUNNING` möglich, der Gegner gewinnt. Verwaisen (`ABANDONED`) ist in jeder nicht beendeten Phase möglich.

Validierungen (alle serverseitig):
- `RuleSet` gültig (Feldgröße 1–25, Schiffslängen ≥ 2, Flotte platzierbar).
- Schiffe liegen im Feld, überschneiden sich nicht und entsprechen der Flotte des `RuleSet`.
- Schüsse nur in der eigenen Runde, nur in der Phase `RUNNING`, nur auf noch nicht beschossene Felder.

### 3.3 Spielablauf

1. Spieler A erstellt eine Lobby mit `RuleSet` und `visibility`.
2. Spieler B tritt bei (offene Liste oder Einladungscode).
3. Beide platzieren ihre Flotte und bestätigen („bereit“).
4. Abwechselnd schießen, bis alle Schiffe eines Spielers versenkt sind.
5. Das Spiel gilt als `FINISHED` und bleibt einsehbar.

Gegen die KI startet die Lobby sofort. Die KI platziert ihre Flotte selbst und antwortet nach jedem Zug des Menschen.

### 3.4 Identität

- Beim ersten Besuch erzeugt der Server einen `Player` (UUID + Nickname) und gibt ein Token zurück.
- Der Client speichert das Token im `localStorage` und sendet es als `Authorization: Bearer <token>`.
- Das Token ist der einzige „Login“: Wer es hat, kann seine Spiele fortsetzen. Es gibt keine Passwörter oder Konten.
- Einschränkung: Wird das `localStorage` gelöscht, ist der Zugriff verloren. Das ist bewusst so akzeptiert.
- Token: 256 Bit aus `SecureRandom`, Base64url ohne Padding. In `player.token_hash` steht nur der SHA-256-Hash (Hex). Ein schneller Hash genügt, weil das Token zufällig und lang ist (kein Passwort).
- Nickname: Leerzeichen am Rand werden entfernt, danach 1–20 Zeichen.
- `POST /api/players` ist der einzige Endpunkt ohne Token. Alle anderen antworten ohne gültiges Token mit `401`.

### 3.5 Sicherheit und Fairness

- Gegnerische Schiffspositionen verlassen den Server nie. Der Client erhält nur eigene Schiffe und die Schussergebnisse auf das gegnerische Feld.
- Alle Regeln werden im Backend geprüft, der Client ist reine Darstellung.
- Zugriff auf ein Spiel nur für beteiligte Spieler (Zuschauer sind nicht vorgesehen).
- Rate-Limiting und Längenbegrenzung für Chat und Nicknames.

### 3.6 REST-API (Entwurf)

| Methode | Pfad | Zweck |
|---|---|---|
| POST | `/api/players` | Spieler anlegen, Token erhalten |
| GET | `/api/lobbies` | Offene Lobbys auflisten |
| POST | `/api/lobbies` | Lobby/Spiel erstellen (`RuleSet`, `visibility`, `vsAi`) |
| POST | `/api/lobbies/join` | Beitreten per ID oder Einladungscode |
| GET | `/api/games` | Eigene Spiele (laufende und beendete) |
| GET | `/api/games/{id}` | Spielzustand aus Sicht des Spielers |
| PUT | `/api/games/{id}/fleet` | Flotte platzieren/bestätigen |
| POST | `/api/games/{id}/shots` | Schuss abgeben |
| POST | `/api/games/{id}/resign` | Aufgeben |
| GET/POST | `/api/games/{id}/chat` | Chatverlauf lesen / Nachricht senden |
| GET | `/api/games/{id}/events` | SSE-Stream für das Spiel |
| GET | `/api/lobbies/events` | SSE-Stream für die Lobby-Liste |

Festlegungen (Phase 3):
- `POST /api/lobbies` ohne `rules` nutzt die Standardregeln. Lobbys sind vorerst öffentlich; `visibility`, Einladungscode und `vsAi` folgen in Phase 6.
- `POST /api/lobbies/join` erwartet `{ "gameId": … }`. `GET /api/lobbies` listet alle offenen öffentlichen Lobbys, auch eigene.
- Spielaktionen antworten mit dem Spielzustand aus Sicht des Spielers (`GameView`), ein Schuss zusätzlich mit `result` und ggf. `sunkShip`.
- `GameView` enthält eigenes Feld (Schiffe mit `sunk`, empfangene Schüsse) und gegnerisches Feld (eigene Schüsse mit `hit`, nur versenkte Schiffe). Nicht versenkte gegnerische Schiffe bleiben auch nach Spielende verborgen.
- Fehler kommen als `{ "code", "message" }`. `code` ist eine `Violation` der Domäne oder ein `ErrorCode` des Service.

| Status | Bedeutung | Codes |
|---|---|---|
| 400 | Ungültige Eingabe | `INVALID_REQUEST`, `INVALID_NICKNAME`, `INVALID_RULE_SET`, `FLEET_DOES_NOT_FIT`, `SHIP_OUT_OF_BOUNDS`, `SHIPS_OVERLAP`, `SHIPS_TOUCHING`, `FLEET_MISMATCH`, `SHOT_OUT_OF_BOUNDS` |
| 401 | Kein oder unbekanntes Token | – |
| 404 | Spiel unbekannt **oder** Spieler nicht beteiligt (Existenz wird nicht preisgegeben) | `GAME_NOT_FOUND` |
| 409 | Im aktuellen Zustand nicht erlaubt | `WRONG_PHASE`, `NOT_YOUR_TURN`, `CELL_ALREADY_SHOT`, `FLEET_ALREADY_PLACED`, `CANNOT_JOIN_OWN_GAME`, `CONCURRENT_MODIFICATION` |

### 3.7 Server-Sent Events

- Realisiert mit `Multi<…>` (`@RestStreamElementType`).
- Ein In-Memory-Event-Bus pro Instanz verteilt Ereignisse an die offenen Streams.
- Ereignistypen: `PLAYER_JOINED`, `FLEET_READY`, `GAME_STARTED`, `SHOT`, `TURN_CHANGED`, `GAME_FINISHED`, `CHAT`.
- Browser verbinden sich bei Abbruch automatisch neu. Nach dem Reconnect lädt der Client den Zustand per `GET /api/games/{id}` nach.
- Skalierung: Bei mehreren Backend-Instanzen müsste der Event-Bus durch Postgres `LISTEN/NOTIFY` oder einen Broker ersetzt werden. Für das Projekt reicht eine Instanz.

### 3.8 KI-Gegner

- Platzierung: zufällig, regelkonform.
- Schießen: Hunt/Target. Zufällig oder im Schachbrettmuster suchen, nach einem Treffer die Nachbarfelder verfolgen, bis das Schiff versenkt ist.
- Die Strategie liegt hinter einem Interface (`AiStrategy`), damit stärkere Varianten ergänzt werden können.
- Im Testprofil kann ein fester Seed gesetzt werden, damit die Spiele deterministisch sind.

## 4. Datenbank (PostgreSQL)

Migrationen per Flyway (`backend/src/main/resources/db/migration`). Hibernate erzeugt kein Schema.

Laufzeitprofile:
- **dev:** Datenbank aus `docker-compose.yml` (`localhost:5432`, Datenbank/Benutzer `schiffe`).
- **test:** Quarkus Dev Services startet PostgreSQL per Testcontainers (Docker erforderlich).
- **prod:** Verbindung über `QUARKUS_DATASOURCE_*`.

| Tabelle | Inhalt |
|---|---|
| `player` | `id` (UUID), `nickname`, `token_hash`, `created_at` |
| `game` | `id`, `status`, `visibility`, `invite_code`, `rules` (JSONB), `state` (JSONB), `player_a`, `player_b`, `ai_opponent`, `current_turn`, `winner`, `version`, `created_at`, `updated_at` |
| `chat_message` | `id`, `game_id`, `player_id`, `text`, `created_at` |

Entscheidungen:
- Der Spielzustand (Boards, Schiffe, Schüsse) liegt als **JSONB** in `game.state`. Das ist für Spielstand speichern und fortsetzen am einfachsten, weil das Spiel immer als Ganzes geladen wird.
- `game` hat eine `version`-Spalte für optimistisches Locking, damit gleichzeitige Züge nicht kollidieren.
- Das Token wird nur als Hash gespeichert.
- Index auf `(visibility, status)` für die Lobby-Liste und auf `invite_code`.
- Nicht beendete Spiele ohne Aktivität werden nach einer konfigurierbaren Frist als `ABANDONED` markiert (geplanter Job). Konfiguration: `game.abandon.inactive-after` (Standard `P7D`), `game.abandon.check-interval` (Standard `1h`, im Testprofil `off`). Aktivität ist `game.updated_at`, das jede Aktion setzt.

Speicherformat (`GameStateMapper`):
- Die Domäne liefert mit `Game.snapshot()` einen unveränderlichen `GameSnapshot` und stellt mit `Game.restore(…)` das Spiel wieder her. Flotten werden dabei erneut validiert und Schüsse in ihrer Reihenfolge nachgespielt. Der Trefferzustand der Schiffe wird also nicht gespeichert, sondern abgeleitet.
- `game.rules`: Felder des `RuleSet`, z. B. `{"boardWidth":10,"boardHeight":10,"fleet":[5,4,3,2],"shootAgainOnHit":false,"shipsMayTouch":false}`.
- `game.state`: `{"boards":{"A":{"ships":[{"start":{"x":0,"y":0},"length":5,"orientation":"HORIZONTAL"},…],"shotsReceived":[{"x":3,"y":3},…]},"B":…}}`. Es enthält nur Seiten mit platzierter Flotte.
- Phase, Zug und Sieger liegen nicht im JSON, sondern in den Spalten `status`, `current_turn` und `winner` (Seite `A`/`B`), damit Lobby-Liste und „Meine Spiele“ ohne JSON-Auswertung auskommen.
- Das JSON-Format ist Teil des Schemas: Umbenennungen in `RuleSet`, `ShipPlacement`, `Coordinate` oder `BoardSnapshot` brechen gespeicherte Spiele. Unit-Tests schreiben das Format fest.
- Hibernate bekommt das JSON als `String` mit `@JdbcTypeCode(SqlTypes.JSON)` und reicht es unverändert an die JSONB-Spalte weiter. So ist die Serialisierung ohne Datenbank testbar.
- Gleichzeitige Änderungen am selben Spiel scheitern am `version`-Vergleich und werden mit `409 CONCURRENT_MODIFICATION` abgelehnt. Der Client lädt dann neu.

## 5. Frontend (Angular, TypeScript)

- Angular 22: Standalone Components, Signals, Angular Router, `HttpClient`.
- Im Dev-Server leitet `proxy.conf.json` `/api` an `localhost:8080` weiter (kein CORS nötig).
- **Seiten:** Startseite (Nickname), Lobby-Liste, Lobby erstellen (Regeln), Flotte platzieren, Spiel, Meine Spiele.
- **Services:** `ApiService` (REST), `EventService` (SSE über `EventSource`), `PlayerService` (Token), `GameStore` (Signals).
- **Spielfeld:** CSS-Grid, Größe aus dem `RuleSet`. Flotte per Klick/Drag platzieren, Drehen per Taste oder Button.
- **Testbarkeit:** Stabile `data-testid`-Attribute (zum Beispiel `cell-{x}-{y}`, `own-board`, `enemy-board`).
- Nach Reload oder Reconnect wird der Zustand immer vom Server neu geladen.

## 6. Tests

| Ebene | Werkzeug | Umfang |
|---|---|---|
| Domäne | JUnit 5 | Platzierung, Schussauswertung, Siegbedingung, `RuleSet`-Varianten, Speichern/Wiederherstellen (`GameSnapshot`), KI-Strategie |
| Backend-Unit (ohne DB) | JUnit 5 | Reine Logik außerhalb der Domäne: Speicherformat (`GameStateMapper`), Spielersicht/Anti-Cheat (`GameViewMapper`), Token-Hash |
| Backend-Integration | `@QuarkusTest`, REST Assured, Testcontainers (Postgres) | REST, SSE, Persistenz (JSONB, Locking), Berechtigungen, verwaiste Spiele |
| Frontend-Unit | Vitest (Angular-Standard, `ng test`) | Komponenten und Services |
| E2E | Playwright (`frontend/e2e/`) | Gegen den Docker-Compose-Stack |

Abgrenzung im Backend: Unit-Tests nur dort, wo reine Logik steckt. Services und Ressourcen sind überwiegend Orchestrierung (laden, Domäne aufrufen, speichern). Sie werden ohne Mocks über `@QuarkusTest` gegen eine echte PostgreSQL getestet, weil Mock-Tests dort nur die Implementierung nachbilden würden. Tests mit `@QuarkusTest` heißen `*IntegrationTest`. Ohne Docker laufen nur die übrigen Tests (`./mvnw test -Dtest='!*IntegrationTest'`).

E2E-Szenarien:
1. Spiel erstellen und beitreten (offen und privat mit Code).
2. Zwei Spieler als getrennte Browser-Contexts, jeweils mit eigenem `localStorage`: Flotte platzieren und bis zum Sieg spielen.
3. Reload mitten im Spiel, danach läuft das Spiel weiter.
4. Spiel gegen die KI.
5. Chat zwischen zwei Spielern.
6. Eigene Regeln/Feldgröße.

## 7. Betrieb mit Docker

`docker-compose.yml`:

| Dienst | Image | Port |
|---|---|---|
| `db` | `postgres` (mit Volume für Daten) | 5432 |
| `backend` | Quarkus (JVM-Image, Multi-Stage-Build mit Maven) | 8080 |
| `frontend` | nginx mit gebautem Angular-Bundle, Reverse-Proxy auf `/api` | 4200 |

- Konfiguration über Umgebungsvariablen (`QUARKUS_DATASOURCE_*`, `GAME_*`).
- Der Reverse-Proxy im `frontend`-Container vermeidet CORS-Probleme. SSE braucht deaktiviertes Proxy-Buffering.
- Für lokale Entwicklung startet nur `db` per Compose, Backend und Frontend laufen im Dev-Modus.

## 8. Offene Punkte und Risiken

- SSE hinter Proxys: Buffering und Timeouts beachten, Heartbeats senden.
- Mehrere Backend-Instanzen erfordern einen verteilten Event-Bus (siehe 3.7).
- Tokenverlust (localStorage gelöscht) bedeutet Verlust des Zugriffs auf laufende Spiele.
- Regel-Varianten erhöhen die Testmatrix. Die Domänentests sollten daher parametrisiert sein.
- Beim Laden eines Spiels validiert der `RuleSet`-Konstruktor die Regeln erneut, inklusive Platzierbarkeitsprüfung. Für extrem dichte Flotten kann das bis zu etwa eine Sekunde dauern, und zwar bei jeder Aktion und für jede solche Lobby in der Lobby-Liste. Falls das stört: Regeln beim Laden ohne Suche rekonstruieren (sie waren beim Anlegen gültig) oder die Lobby-Liste ohne Domänenobjekte bauen.

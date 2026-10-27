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

```
api/          REST-Ressourcen, SSE-Endpunkte, DTOs
service/      Anwendungslogik (Lobby, Spiel, Chat, KI-Orchestrierung)
domain/       Reine Spiellogik ohne Framework-Abhängigkeiten
persistence/  Panache-Entities, Repositories
```

Die `domain`-Schicht enthält die Regeln und ist ohne Quarkus und DB testbar.

### 3.2 Domänenmodell

- **RuleSet**: `boardWidth`, `boardHeight`, `fleet` (Liste von Schiffslängen), `shootAgainOnHit`, `shipsMayTouch`. Standard: 10x10, Flotte 5/4/3/3/2, kein Nochmal-Schießen, Schiffe dürfen sich nicht berühren (konfigurierbar).
- **Board**: Raster, platzierte `Ship`s, empfangene Schüsse.
- **Ship**: Startkoordinate, Länge, Ausrichtung, Trefferzustand.
- **Game**: Spieler A/B, jeweils ein `Board`, `phase` (`WAITING`, `PLACEMENT`, `RUNNING`, `FINISHED`, `ABANDONED`), `currentTurn`, `winner`.
- **Shot-Ergebnis**: `MISS`, `HIT`, `SUNK` (mit Schiff), `WIN`.

Validierungen (alle serverseitig):
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

Migrationen per Flyway.

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
- Nicht beendete Spiele ohne Aktivität werden nach einer konfigurierbaren Frist als `ABANDONED` markiert (geplanter Job).

## 5. Frontend (Angular, TypeScript)

- Standalone Components, Signals, Angular Router, `HttpClient`.
- **Seiten:** Startseite (Nickname), Lobby-Liste, Lobby erstellen (Regeln), Flotte platzieren, Spiel, Meine Spiele.
- **Services:** `ApiService` (REST), `EventService` (SSE über `EventSource`), `PlayerService` (Token), `GameStore` (Signals).
- **Spielfeld:** CSS-Grid, Größe aus dem `RuleSet`. Flotte per Klick/Drag platzieren, Drehen per Taste oder Button.
- **Testbarkeit:** Stabile `data-testid`-Attribute (zum Beispiel `cell-{x}-{y}`, `own-board`, `enemy-board`).
- Nach Reload oder Reconnect wird der Zustand immer vom Server neu geladen.

## 6. Tests

| Ebene | Werkzeug | Umfang |
|---|---|---|
| Domäne | JUnit 5 | Platzierung, Schussauswertung, Siegbedingung, `RuleSet`-Varianten, KI-Strategie |
| Backend-Integration | `@QuarkusTest`, REST Assured, Testcontainers (Postgres) | REST, SSE, Persistenz, Berechtigungen |
| Frontend-Unit | Jest oder Karma/Jasmine | Komponenten und Services |
| E2E | Playwright (`frontend/e2e/`) | Gegen den Docker-Compose-Stack |

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

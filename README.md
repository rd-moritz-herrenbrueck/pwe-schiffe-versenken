# PWE-SchiffeVersenken

Online-Multiplayer-Version von „Schiffe versenken“ als Webanwendung.
Mehrere Spiele laufen parallel, Spielstände werden gespeichert und können jederzeit fortgesetzt werden.

## Features

### Muss
- Client-Server-Architektur
- Webapp (Angular)
- Spielstände speichern und wieder aufnehmen
- Standardregeln auf 10x10-Feld (Flotte: je ein Schiff der Länge 5, 4, 3 und 2)
- Mehrere Spiele gleichzeitig

### Optional (geplant)
- Individuelle Regeln und Spielfeldgrößen (`RuleSet`)
- Open- und Private-Lobbys (Einladungscode)
- Chat pro Spiel
- KI-Gegner

## Tech-Stack

| Bereich | Technologie |
|---|---|
| Frontend | TypeScript, Angular, PrimeNG (Hell-/Dunkelmodus) |
| Backend | Java, Quarkus (REST + Server-Sent Events) |
| Datenbank | PostgreSQL (Flyway-Migrationen, JSONB für Spielzustand) |
| Build | Maven (Backend), npm (Frontend) |
| Tests | JUnit 5, `@QuarkusTest`, Testcontainers, Vitest, Playwright (E2E) |
| Betrieb | Docker / Docker Compose |

## Projektstruktur

```
backend/              Quarkus-Anwendung (Maven, Java 25, Quarkus 3.40 LTS)
  src/main/java/de/pwe/schiffeversenken/
    api/              REST-Ressourcen, SSE, DTOs
    service/          Anwendungslogik
    domain/           Reine Spiellogik (ohne Framework)
    persistence/      Panache-Entities, Repositories
  src/main/resources/db/migration/   Flyway-Migrationen
frontend/             Angular-Anwendung (Angular 22, Vitest)
  e2e/                Playwright-Tests
docs/
  Architektur.md      Architekturbeschreibung
  todo.md             Aufgabenliste / Roadmap
  ai-journal.md       Dokumentation der KI-gestützten Arbeit
docker-compose.yml    Gesamter Stack (Postgres, Backend, Frontend)
docker-compose.e2e.yml   Testprofil für E2E (leere Datenbank im RAM, fester KI-Seed)
docker-compose.prod.yml  Produktions-Stack auf dem VPS
.github/workflows/    CI (ci.yml) und Deployment (deploy.yml)
```

## Entwicklung

Voraussetzungen: JDK 25, Node.js 22+, Docker.

```bash
# Datenbank (PostgreSQL 17, Zugang schiffe/schiffe, überschreibbar per .env)
docker compose up -d db

# Backend: http://localhost:8080 (Health: /q/health)
cd backend && ./mvnw quarkus:dev

# Frontend: http://localhost:4200 (leitet /api an das Backend weiter)
cd frontend && npm install && npm start
```

## Tests

```bash
cd backend && ./mvnw verify                          # Unit- und Integrationstests (Docker nötig, Dev Services/Testcontainers)
cd backend && ./mvnw test -Dtest='!*IntegrationTest'  # Nur Unit-Tests (ohne Docker)
cd frontend && npm test -- --watch=false  # Unit-Tests (Vitest)
```

Gesamter Stack (Frontend: http://localhost:8081, Port über `APP_PORT` änderbar):

```bash
docker compose up --build
```

E2E-Tests (Playwright, Docker nötig). Der Stack läuft mit dem Testprofil `docker-compose.e2e.yml`: Die Datenbank liegt im RAM und ist bei jedem Start leer.

```bash
cd frontend
npm install && npx playwright install chromium    # einmalig
npm run e2e:stack:up                              # Stack mit leerer Datenbank starten
npm run e2e                                       # Tests gegen http://localhost:8081 (anderes Ziel: E2E_BASE_URL)
npm run e2e:stack:down                            # Stack stoppen und Daten verwerfen
```

## Deployment

Pull Requests und Pushes auf `main` laufen durch die CI. Ein Tag `v*` baut die Images, pusht sie nach GHCR und rollt sie auf dem IONOS-VPS aus (siehe [Deployment](docs/Deployment.md)):

```bash
git tag v0.1.0 && git push origin v0.1.0
```

## Dokumentation

- [Architektur](docs/Architektur.md)
- [Aufgabenliste](docs/todo.md)
- [CI/CD und Deployment](docs/Deployment.md)

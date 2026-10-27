# PWE-SchiffeVersenken

Online-Multiplayer-Version von „Schiffe versenken“ als Webanwendung.
Mehrere Spiele laufen parallel, Spielstände werden gespeichert und können jederzeit fortgesetzt werden.

## Features

### Muss
- Client-Server-Architektur
- Webapp (Angular)
- Spielstände speichern und wieder aufnehmen
- Standardregeln auf 10x10-Feld (Flotte: 5/4/3/3/2)
- Mehrere Spiele gleichzeitig

### Optional (geplant)
- Individuelle Regeln und Spielfeldgrößen (`RuleSet`)
- Open- und Private-Lobbys (Einladungscode)
- Chat pro Spiel
- KI-Gegner

## Tech-Stack

| Bereich | Technologie |
|---|---|
| Frontend | TypeScript, Angular |
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
  e2e/                Playwright-Tests (ab Phase 7)
docs/
  Architektur.md      Architekturbeschreibung
  todo.md             Aufgabenliste / Roadmap
  ai-journal.md       Dokumentation der KI-gestützten Arbeit
docker-compose.yml    Postgres (Backend + Frontend ab Phase 7)
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
cd backend && ./mvnw verify               # Unit- und Integrationstests (Docker nötig, Dev Services/Testcontainers)
cd frontend && npm test -- --watch=false  # Unit-Tests (Vitest)
```

Zielzustand (ab Phase 7):

```bash
docker compose up --build            # Gesamter Stack, Frontend: http://localhost:4200
cd frontend && npx playwright test   # E2E-Tests gegen den Docker-Compose-Stack
```

## Dokumentation

- [Architektur](docs/Architektur.md)
- [Aufgabenliste](docs/todo.md)

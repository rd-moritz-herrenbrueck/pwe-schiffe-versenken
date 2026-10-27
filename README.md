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
| Tests | JUnit 5, `@QuarkusTest`, Testcontainers, Playwright (E2E) |
| Betrieb | Docker / Docker Compose |

## Projektstruktur (geplant)

```
backend/              Quarkus-Anwendung (Maven)
frontend/             Angular-Anwendung
  e2e/                Playwright-Tests
docs/
  Architektur.md      Architekturbeschreibung
  todo.md             Aufgabenliste / Roadmap
docker-compose.yml    Postgres + Backend + Frontend
```

## Schnellstart (Zielzustand)

```bash
docker compose up --build
# Frontend: http://localhost:4200
# Backend:  http://localhost:8080
```

Entwicklung:

```bash
# Datenbank
docker compose up -d db

# Backend
cd backend && ./mvnw quarkus:dev

# Frontend
cd frontend && npm install && npm start
```

Tests:

```bash
cd backend && ./mvnw verify          # Unit- und Integrationstests
cd frontend && npm test              # Unit-Tests
cd frontend && npx playwright test   # E2E-Tests (gegen Docker-Compose-Stack)
```

> Hinweis: Das Projekt befindet sich in der Konzeptphase, die Befehle gelten für den Zielzustand.

## Dokumentation

- [Architektur](docs/Architektur.md)
- [Aufgabenliste](docs/todo.md)

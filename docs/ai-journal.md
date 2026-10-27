# AI-Journal

Dokumentation der KI-gestützten Arbeit an **PWE-SchiffeVersenken**.

| Datum | Ausgangsfrage | Kern der Antwort | Verfeinerungen | Kritische Reflexion |
|---|---|---|---|---|
| 27.10.2026 | Konzept für ein Online-Multiplayer-„Schiffe versenken“ mit Angular, Quarkus und PostgreSQL diskutieren. Muss: Client-Server, Webapp, Spielstände speichern/fortsetzen, 10x10-Standardregeln, parallele Spiele. Optional: eigene Regeln, Open/Private-Lobby. | Stack bestätigt. Spiellogik nur im Server (Anti-Cheat). Zustandsloses Backend, Spielzustand als JSONB in PostgreSQL. REST für Aktionen, SSE für Echtzeit-Events. Anonymes Token im localStorage. Konfigurierbares `RuleSet` und Lobby mit Einladungscode. | Extras ergänzt: Chat, KI-Gegner, Docker Compose, Maven, Tests. E2E-Tests mit Playwright. Dokumentation erstellt: `README.md`, `docs/Architektur.md`, `docs/todo.md`. | Die Antwort war eher ein Vorschlag als eine Diskussion: Alternativen und Nachteile wurden kaum abgewogen. Einige Annahmen wurden nicht abgestimmt: Flotte 5/4/3/3/2 statt der deutschen Variante, JSONB statt relationalem Modell, Berührungsregel. Die README enthält Befehle, die noch nicht lauffähig sind. |

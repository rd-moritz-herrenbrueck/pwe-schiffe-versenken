# CI/CD und Deployment

Ziel ist ein IONOS-VPS (Linux, Docker installiert). Dort belegt bereits Supabase die Ports 80/443 über einen eigenen Reverse-Proxy (Domain `supabase.warenwelle.com`). Die App läuft daneben unter `moritzherrenbrueck.de`.

## Überblick

```
PR / Push auf main ──► ci.yml ──► Backend: ./mvnw verify (inkl. Testcontainers)
                                  Frontend: npm ci, npm test, npm run build

Tag v* ──► deploy.yml ──► ci.yml (wiederverwendet)
                      ──► Images backend/frontend bauen, nach GHCR pushen (:vX.Y.Z, :latest)
                      ──► SSH auf den VPS: docker-compose.yml + .env schreiben,
                          docker compose pull && up -d --wait
                      ──► Smoke-Test https://APP_DOMAIN/

Manuell (workflow_dispatch, Eingabe tag) ──► nur Deploy-Job: vorhandenes Image-Tag ausrollen (Rollback)
```

Auf dem VPS:

```
Internet ──443──► vorhandener Reverse-Proxy (TLS) ──► 127.0.0.1:APP_PORT
                                                       └► frontend (nginx, SPA + /api-Proxy) ──► backend:8080 ──► db
```

Postgres und Backend sind nur im Compose-Netz erreichbar. Nach außen öffnet der Stack nur `127.0.0.1:APP_PORT`.

## Dateien

| Datei | Zweck |
|---|---|
| `.github/workflows/ci.yml` | Build und Tests bei PR und Push auf `main`, als Reusable Workflow auch für das Deployment |
| `.github/workflows/deploy.yml` | Images bauen/pushen und ausrollen bei Tag `v*`, Rollback per `workflow_dispatch` |
| `backend/Dockerfile` | Multi-Stage: Maven-Build (JDK 25), Laufzeit im JRE-25-Image |
| `frontend/Dockerfile`, `frontend/nginx.conf` | Angular-Build, Auslieferung mit nginx, `/api` an das Backend (SSE ohne Buffering) |
| `docker-compose.prod.yml` | Produktions-Stack (`db`, `backend`, `frontend`), wird auf dem VPS zu `docker-compose.yml` |

## GitHub-Konfiguration

Unter *Settings → Environments* das Environment **`production`** anlegen und dort Folgendes hinterlegen. Unter *Deployment branches and tags* sollten nur Tags `v*` erlaubt sein.

Variables (Klartext, keine Zugangsdaten):

| Name | Beispiel | Pflicht |
|---|---|---|
| `VPS_HOST` | IP oder Hostname des VPS | ja |
| `VPS_USER` | `deploy` | ja |
| `VPS_PORT` | `22` | nein (Standard 22) |
| `VPS_DEPLOY_PATH` | `/opt/schiffeversenken` | nein (Standard) |
| `APP_DOMAIN` | `moritzherrenbrueck.de` | ja |
| `APP_PORT` | `8081` | nein (Standard 8081) |

Secrets (verschlüsselt, in Logs maskiert):

| Name | Inhalt |
|---|---|
| `VPS_SSH_KEY` | Privater SSH-Schlüssel des Deploy-Users (nur für dieses Deployment erzeugt) |
| `VPS_KNOWN_HOSTS` | Ausgabe von `ssh-keyscan -p <port> <host>`, schützt vor Man-in-the-Middle |
| `POSTGRES_PASSWORD` | Datenbank-Passwort, nur `[A-Za-z0-9]` (z. B. `openssl rand -hex 32`), weil Compose `$` in `.env` interpoliert |

Für GHCR ist kein eigenes Token nötig. Der Workflow pusht mit `GITHUB_TOKEN`, und auch der VPS meldet sich für den Pull nur kurzzeitig mit `GITHUB_TOKEN` an und danach wieder ab.

## Einmalige Einrichtung des VPS

```bash
# 1. Deploy-User mit Docker-Rechten (als root)
adduser --disabled-password deploy
usermod -aG docker deploy
mkdir -p /opt/schiffeversenken && chown deploy: /opt/schiffeversenken

# 2. SSH-Schlüssel (lokal erzeugen, öffentlichen Teil auf den VPS)
ssh-keygen -t ed25519 -f schiffe_deploy -C "github-actions-deploy" -N ""
#    schiffe_deploy.pub  → /home/deploy/.ssh/authorized_keys auf dem VPS
#    schiffe_deploy      → GitHub-Secret VPS_SSH_KEY, danach lokal löschen
ssh-keyscan -p 22 <VPS_HOST>   # → GitHub-Secret VPS_KNOWN_HOSTS

# 3. Prüfen: aktuelles Docker Compose v2 (muss `up --wait --wait-timeout` kennen)
docker compose version
```

Hinweis: Mitglieder der Gruppe `docker` haben faktisch Root-Rechte. Der Schlüssel gehört deshalb ausschließlich in das GitHub-Secret.

### Reverse-Proxy (vorhanden, Ports 80/443)

Im vorhandenen Proxy einen zusätzlichen Host für `moritzherrenbrueck.de` anlegen, der auf `127.0.0.1:8081` (`APP_PORT`) zeigt. Der DNS-A-Record der Domain muss auf den VPS zeigen. Für SSE dürfen Antworten nicht gepuffert werden.

nginx:

```nginx
server {
    listen 443 ssl;
    server_name moritzherrenbrueck.de;
    # ssl_certificate … (z. B. certbot --nginx -d moritzherrenbrueck.de)

    location / {
        proxy_pass http://127.0.0.1:8081;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_buffering off;
        proxy_read_timeout 1h;
    }
}
```

Caddy:

```caddyfile
moritzherrenbrueck.de {
    reverse_proxy 127.0.0.1:8081 {
        flush_interval -1
    }
}
```

Läuft der Proxy selbst in Docker (z. B. Traefik oder ein nginx-Container), erreicht er `127.0.0.1` des Hosts nicht. Dann ist `host.docker.internal` bzw. die Docker-Bridge-IP nötig, oder der Proxy kommt mit dem `frontend`-Container in ein gemeinsames Netz.

### Datensicherung

Die Daten liegen im Volume `schiffeversenken_db-data`. Ein tägliches Backup lässt sich z. B. per Cron einrichten:

```bash
0 3 * * * cd /opt/schiffeversenken && docker compose exec -T db pg_dump -U schiffe schiffe | gzip > /var/backups/schiffe-$(date +\%F).sql.gz
```

## Release und Rollback

```bash
git tag v0.1.0 && git push origin v0.1.0   # baut, testet und rollt aus
```

Für einen Rollback *Actions → Deploy → Run workflow* mit einem früheren Tag (z. B. `v0.0.9`) starten. Dabei werden keine Images gebaut, sondern nur das vorhandene Tag ausgerollt. Flyway-Migrationen werden nicht zurückgedreht, deshalb sollten Migrationen abwärtskompatibel sein.

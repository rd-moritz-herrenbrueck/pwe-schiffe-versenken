# CI/CD und Deployment

Ziel ist ein IONOS-VPS (Linux, Docker installiert). Dort belegt bereits Supabase die Ports 80/443 über einen eigenen Reverse-Proxy (Domain `supabase.warenwelle.com`). Die App läuft daneben unter `moritzherrenbrueck.de`.

## Überblick

```
PR / Push auf main ──► ci.yml ──► Backend: ./mvnw verify (inkl. Testcontainers)
                                  Frontend: npm ci, npm test, npm run build
                                  E2E: Docker-Compose-Stack mit Testprofil, Playwright-Tests

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
| `.github/workflows/ci.yml` | Build, Tests und E2E-Tests bei PR und Push auf `main`, als Reusable Workflow auch für das Deployment (ein Release-Tag wird erst nach grünen E2E-Tests ausgerollt) |
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
| `VPS_USER` | `deploy-schiffe` | ja |
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

Alle Befehle als `root` auf dem VPS. Pfade sind absolut, damit Schlüssel und `authorized_keys` unabhängig vom aktuellen Verzeichnis beim Deploy-User landen.

```bash
# 1. Deploy-User mit Docker-Rechten
adduser --disabled-password --gecos "" deploy-schiffe     # alternativ: useradd -m -s /bin/bash deploy-schiffe
usermod -aG docker deploy-schiffe
install -d -o deploy-schiffe -g deploy-schiffe /opt/schiffeversenken

# 2. SSH-Schlüssel nur für das Deployment
install -d -m 700 -o deploy-schiffe -g deploy-schiffe /home/deploy-schiffe/.ssh
ssh-keygen -t ed25519 -f /root/schiffe_deploy -C "github-actions-deploy" -N ""
cat /root/schiffe_deploy.pub >> /home/deploy-schiffe/.ssh/authorized_keys
chown deploy-schiffe:deploy-schiffe /home/deploy-schiffe/.ssh/authorized_keys
chmod 600 /home/deploy-schiffe/.ssh/authorized_keys

# 3. Zugang testen (muss "OK" und die Docker-Version ausgeben, ohne Passwortabfrage)
ssh -i /root/schiffe_deploy -o StrictHostKeyChecking=accept-new deploy-schiffe@localhost 'echo OK && docker compose version'

# 4. Werte für die GitHub-Secrets ausgeben
cat /root/schiffe_deploy                # → VPS_SSH_KEY (inkl. BEGIN-/END-Zeile)
ssh-keyscan -p 22 <VPS_HOST>            # → VPS_KNOWN_HOSTS (<VPS_HOST> exakt wie in der Variable)

# 5. Privaten Schlüssel vom Server löschen, sobald er in GitHub hinterlegt ist
rm /root/schiffe_deploy /root/schiffe_deploy.pub
```

Falls Schritt 3 nach einem Passwort fragt: In `/etc/ssh/sshd_config` darf `AllowUsers` bzw. `AllowGroups` den User `deploy-schiffe` nicht ausschließen. `docker compose version` muss v2 mit `up --wait --wait-timeout` sein.

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

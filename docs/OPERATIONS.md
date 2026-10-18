# Operations: secrets, database safety, image pinning, secure backend

## 1. Secrets and demo access

- `.env` is git-ignored (`.env`, `.env.*`; only `.env.example` is tracked) and `.env.example`
  contains **no values**. Compose refuses to start until `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD` and
  `JWT_SECRET` are set.
- `application.properties` has **no fallback credentials**. The backend will not start without
  `DB_USER`, `DB_PASSWORD` and `JWT_SECRET`, and `JwtService` rejects a secret shorter than
  32 bytes or one that still starts with `change-this`.
- Demo seeding is off by default (`DEMO_SEED=false`). When a local demo turns it on, the password
  for the three demo accounts comes from `DEMO_PASSWORD` (12+ characters). No demo password is in
  the source, README or report.
- Generate values: `openssl rand -base64 48`, or in PowerShell
  `[Convert]::ToBase64String((1..48 | % { Get-Random -Max 256 }))`.

**Existing database volume:** MySQL sets the `servicedesk` user's password only when the volume is
first created. If your volume was created with the old default (`servicedesk`), either put that
value in `DB_PASSWORD` for now, or change the password first:

```powershell
docker compose exec db mysql -uroot -p          # enter MYSQL_ROOT_PASSWORD when prompted
```

```sql
ALTER USER 'servicedesk'@'%' IDENTIFIED BY 'NEW_VALUE';
```

Then set the same `NEW_VALUE` as `DB_PASSWORD` in `.env` and restart the backend.

## 2. Database schema safety

- `spring.jpa.hibernate.ddl-auto` now comes from `JPA_DDL_AUTO` and defaults to **validate**,
  which never changes existing tables. If the entities and the schema disagree, the backend
  stops with an error instead of changing the schema.
- Use `JPA_DDL_AUTO=update` **only** for the first start against a new, empty database
  (`.env.example` ships with `update` for that first local run). Afterwards switch to `validate`.
- `compose.secure.yaml` forces `validate`, so moving to the Spring Boot 4 backend can never alter
  the schema. `verify-all` tests this upgrade path on a throwaway database and compares
  `mysqldump --no-data` output before and after.
- **Before any upgrade, `down -v` or change of `JPA_DDL_AUTO`, take a backup:**
  `scripts\backup-db.ps1` (or `scripts/backup-db.sh`) writes `backups/servicedesk-<time>.sql`.
  The script prints the restore command.
- For a production deployment, schema changes should go through versioned migration scripts
  (for example Flyway) that are reviewed and applied separately. Flyway is **not** wired in yet.
  That is a deliberate next step: it can only be added and tested where Maven dependencies can
  be downloaded.

## 3. Reproducible base images

All base images are build arguments with the previous tags as defaults (`compose.yaml` passes
`MYSQL_IMAGE`, `MAVEN_IMAGE`, `BACKEND_RUNTIME_IMAGE`, `SECURE_RUNTIME_IMAGE`, `NODE_IMAGE`,
`NGINX_IMAGE`). To pin them:

```powershell
.\scripts\pin-images.ps1        # prints e.g. SECURE_RUNTIME_IMAGE=cgr.dev/chainguard/jre@sha256:...
```

Run `verify-all` with those lines in `.env`. If it passes, keep the lines and record them in the
report. The mutable `cgr.dev/chainguard/jre:latest` is then no longer used.

The frontend's npm dependencies are pinned to exact versions, with `package-lock.json` committed,
and `npm ci` is used in the image build.

## 4. Spring Boot 3 vs Spring Boot 4 backend

| | `backend/` (default) | `backend-spring4-test/` (optional) |
|---|---|---|
| Spring Boot | 3.5.16 | 4.1.1 (Tomcat 11.0.26, Jackson 2.22.3 / 3.2.3 BOMs) |
| Runtime image | `gcr.io/distroless/java17-debian13:nonroot` | `cgr.dev/chainguard/jre` |
| Selected by | `compose.yaml` | `compose.yaml` + `compose.secure.yaml` |
| Last full test run | Not yet run on the 18 Oct 2026 source; run `verify-all` | 16 Oct 2026 (archived, before the fixes): 8/8 pass, 0 SpotBugs |

Application source is identical in both, so a fix is made once and copied to both.

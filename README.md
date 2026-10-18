# Enterprise Service Desk & Incident Management Platform

**CCA Phase-2 | Building Enterprise Applications (BCS703B) | ISE, BMSIT&M**
Authors: B. Veena (1BY23IS047), C. Rachana (1BY23IS055) · Guide: Mrs. Malini M

Full-stack implementation of the Phase-1 design: **React 18 + Bootstrap 5** single-page client,
**Spring Boot + Spring Security (JWT, BCrypt, RBAC)** REST API, **Spring Data JPA / Hibernate**
and **MySQL 8.4**, packaged with **Docker Compose** behind an **Nginx** reverse proxy.

## Repository layout

```
service-desk-phase2/
├── frontend/                    React/Vite client (src/main.jsx, src/style.css)
├── backend/                     Spring Boot 3.5.16 API — default deployment (compose.yaml)
│   ├── src/main/java/com/bea/servicedesk/
│   │   ├── controller/          REST endpoints (ApiController)
│   │   ├── service/             Workflow, access rules, metrics
│   │   ├── repo/                Spring Data JPA repositories
│   │   ├── model/               JPA entities and enums
│   │   ├── dto/                 Validated request records
│   │   └── config/              Security, JWT, optional demo seed
│   └── src/test/java/...        JUnit 5 unit + MockMvc API tests (H2)
├── backend-spring4-test/        Same application on Spring Boot 4.1.1 — hardened, optional
├── compose.yaml                 MySQL + backend + frontend
├── compose.secure.yaml          Override: use backend-spring4-test, schema changes blocked
├── Dockerfile, nginx.conf       Frontend image and /api reverse proxy
├── scripts/                     verify-all, smoke-test, backup-db, pin-images
├── docs/                        Operations, test cases, review response
└── evidence/                    Archived and fresh analysis/test output
```

`backend/` and `backend-spring4-test/` contain **identical application code**; they differ only
in the Spring Boot version (pom.xml), two test imports (Jackson 2 vs 3 package names) and the
runtime base image. The default stack uses `backend-spring4-test/` (Spring Boot 4.1, Chainguard JRE), which scans with 0 HIGH/CRITICAL in Trivy.

## Run with Docker

```powershell
Copy-Item .env.example .env      # bash: cp .env.example .env
# Edit .env: set DB_PASSWORD, MYSQL_ROOT_PASSWORD and JWT_SECRET to private random values.
docker compose up --build -d
```

Open http://localhost:8088 and register an account. For a **local demonstration only**, set
`DEMO_SEED=true` and a `DEMO_PASSWORD` of 12+ characters in `.env` to create
`admin@demo.local`, `engineer@demo.local` and `employee@demo.local` (all use DEMO_PASSWORD).
No credentials are stored in the source code. Keep `DEMO_SEED=false` anywhere else.

Hardened Spring Boot 4 backend (back up the database first — see `docs/OPERATIONS.md`):

```powershell
.\scripts\backup-db.ps1
docker compose -f compose.yaml -f compose.secure.yaml up --build -d
```

## Run without Docker

Requires Java 17+, Maven 3.9+, Node 20.19+ and MySQL 8.

```bash
cd backend
export DB_URL=jdbc:mysql://localhost:3306/servicedesk DB_USER=servicedesk DB_PASSWORD='<password>'
export JWT_SECRET='<random value, 32+ bytes>' JPA_DDL_AUTO=update   # update only for a new database
mvn spring-boot:run
cd ../frontend && npm ci && npm run dev                               # http://localhost:5173
```

## Verify (tests, static analysis, image scan, end-to-end)

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verify-all.ps1     # bash: scripts/verify-all.sh
```

Only Docker is needed. Results go to `evidence/runs/<timestamp>/summary.md`. The end-to-end part
runs in a separate throwaway Compose project on port 8089 and never touches your database.
Individual commands are listed in `docs/TEST_EVIDENCE.md`.

## API

| Method | Path | Access |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | Public |
| GET | `/api/me` | Authenticated |
| GET, POST | `/api/incidents` | Role-scoped list; create: employee/admin |
| GET, PATCH | `/api/incidents/{id}` | Requester, assigned engineer, admin; update: engineer/admin |
| GET | `/api/incidents/{id}/history` | As above |
| POST | `/api/incidents/{id}/comments` | As above |
| GET | `/api/notifications`; PATCH `/api/notifications/{id}/read` | Owner |
| GET | `/api/users`, `/api/engineers`; PATCH `/api/users/{id}/role` | Admin |
| GET | `/api/reports/summary` | Admin |

## Scope

Implemented: registration/login, JWT + RBAC (END_USER, ENGINEER, ADMIN), incident creation with
category and priority, role-scoped listing and search, admin assignment, engineer progress,
escalation, resolution and closure (resolution text mandatory), audit timeline, comments,
in-app notifications, user-role administration and status/priority reporting.

Not implemented (Phase-1 future scope): email/SMS delivery, file attachments, category CRUD,
automatic SLA escalation, database replication, monitoring and cloud deployment.

## Git workflow

`main` holds stable, verified code; `dev` integrates work; `feature/<name>` branches are merged
into `dev` through pull requests, and `dev` into `main` after `verify-all` passes. Never commit
`.env`, database backups or tokens.

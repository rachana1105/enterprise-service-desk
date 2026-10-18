# Response to "Source Comparison, Fixes & Remaining Verification" (18 Oct 2026)

Status key: **FIXED** means changed in source and checked here. **READY** means the tooling is in
place but it has to run on a machine that can reach Maven Central and Docker Hub (run
`scripts/verify-all`). **OPEN** means not done.

The 18 Oct workspace that made these changes could reach npm, GitHub and Ubuntu packages, but
not Maven Central or the Docker registries (blocked by policy). Checkstyle, the frontend build,
`npm audit` and the SQL scripts were therefore run here. The Java compile, JUnit, SpotBugs,
image builds and Trivy were not.

| Priority | Review item | What was done | Status |
|---|---|---|---|
| HIGH | Rebuild and rerun tests on delivered source | `scripts/verify-all.ps1/.sh` runs `mvn clean test`, Checkstyle and SpotBugs for both backends in the Maven container and saves dated logs and XML. | READY |
| HIGH | Validate secure Compose workflow | `verify-all` starts a throwaway project (`servicedesk-verify`, port 8089, own volume). It runs Spring Boot 3 on a new DB, then a 20-step API smoke test (login, create, list, assign, progress, resolve, notifications, dashboard, five permission denials), and dumps the DB rows. It then switches to `compose.secure.yaml` on the same DB, reruns the smoke test and checks the schema is unchanged. | READY |
| HIGH | Re-scan rebuilt container | `verify-all` builds `servicedesk-backend:local`, `servicedesk-backend-sb4:local` and `servicedesk-frontend:local` and runs Trivy (HIGH/CRITICAL) on each, logging the Trivy version. | READY |
| HIGH | Protect secrets and demo access | Removed every fallback secret from `application.properties`; Compose requires `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD` and `JWT_SECRET` (`:?`); `.env.example` has empty values; `JwtService` refuses secrets under 32 bytes or starting `change-this`; `DEMO_SEED` defaults to false; demo password now comes from `DEMO_PASSWORD` (hard-coded passwords removed from `DemoData.java`, README and UI text); `.gitignore` covers `.env.*`, `backups/` and `evidence/runs/`. | FIXED |
| MEDIUM | Pin reproducible base images | All base images are build args / env variables. `scripts/pin-images` resolves the digests (`RepoDigests`) to put in `.env`. Digests can't be looked up here because the registries are blocked. Frontend npm versions pinned exactly, `package-lock.json` added, image build uses `npm ci`. | READY |
| MEDIUM | Database schema safety | `ddl-auto` comes from `JPA_DDL_AUTO` and defaults to `validate`. `update` is only for the first run on an empty database, and `compose.secure.yaml` forces `validate`. Backup scripts added (`mysqldump --single-transaction` via `--result-file` and `docker compose cp`; dump form tested on MySQL 8.0 here). Versioned migrations (Flyway) not added yet: they need Maven dependencies to test. | FIXED (migrations OPEN) |
| MEDIUM | Publish repository and capture evidence | Local git repository prepared with `main` and `dev` branches. No GitHub account was linked in this session, so nothing was pushed and no URL is claimed. Push commands are in the report. | OPEN |
| MEDIUM | Correct report contradictions | New report uses 116 Checkstyle warnings (archived) → 0 (fresh, after fixes); 8 tests with their real run status; Spring Boot 3 default vs Spring Boot 4 optional stated explicitly. | FIXED |
| MEDIUM | Replace inaccurate test-case table | Report and `TEST_CASES_VERIFIED_FROM_SOURCE.md` list the eight real methods and their assertions; login/update behaviour is attributed to the smoke test, not invented unit tests. | FIXED |
| LOW | Screenshots, captions, references, page numbers | Report has separate numbered figure slots (dashboard, incident list, creation form, API, DB), IEEE references and page-number footer. Only figures backed by real output are filled in; the others are clearly marked. | FIXED (screenshots OPEN) |

## Additional defects found and fixed during the review

| File | Defect | Fix |
|---|---|---|
| `backend/src/test/.../ApiIntegrationTest.java` | Imported `tools.jackson.databind.ObjectMapper` and `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`, which exist only in Spring Boot 4. So the Spring Boot 3.5 module's tests could not compile. | Spring Boot 3 imports (`com.fasterxml.jackson…`, `org.springframework.boot.test.autoconfigure.web.servlet…`). |
| `SecurityConfig.java` | `/error` was not permitted. In a real servlet container, a controller's 400/401/404 is forwarded to `/error`, which then failed authorization and reached the client as 403. MockMvc does not forward, so the tests could not detect this. | Permit `/error`; enable `spring.mvc.problemdetails.enabled` so errors return `{status, detail}` without a forward, and the React client shows the `detail` message. Smoke-test steps S02, S05 and S13 assert 401/400/400 against the real stack. |
| `frontend/package.json` | Vite 5.4 / esbuild ≤ 0.24.2: `npm audit` reported 1 high and 1 moderate advisory (GHSA-67mh-4wv8-2f99). | Vite 7.3.7, exact versions, build tools moved to devDependencies. `npm audit`: 0 vulnerabilities. |
| `backend-spring4-test/pom.xml` | Blank line before the XML declaration (invalid XML; Maven tolerated it). | Removed; both POMs pass `xmllint`. |
| All Java sources | 116 Checkstyle (Google style) warnings: missing Javadoc (52), star imports (21), missing braces (20), indentation (16), import order (4), declaration distance (2), blank line (1). | Explicit imports, braces, 2-space indentation, Javadoc on public types and methods. `IncidentService.update` split into `assign` and `changeStatus` without behaviour change. Checkstyle 9.3: 0 warnings in both modules. |
| `nginx.conf` | No security headers; version disclosed. | `server_tokens off`, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`. |

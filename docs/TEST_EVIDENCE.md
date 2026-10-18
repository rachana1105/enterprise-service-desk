# Verification and evidence

`scripts/verify-all.ps1` (or `.sh`) runs everything below and writes dated output to
`evidence/runs/<timestamp>/`. Take report screenshots from those logs and from the running stack.

| # | What | Command (inside `maven:3.9.9-eclipse-temurin-17` or locally) | Output file |
|---|---|---|---|
| 1 | Unit + API tests | `mvn -B clean test` | `target/surefire-reports/` |
| 2 | Style analysis | `mvn -B checkstyle:checkstyle` | `target/checkstyle-result.xml` |
| 3 | Bug analysis | `mvn -B compile com.github.spotbugs:spotbugs-maven-plugin:4.9.8.5:spotbugs` | `target/spotbugsXml.xml` |
| 4 | Frontend | `npm ci && npm run build && npm audit` | console |
| 5 | Image scan | `trivy image --severity HIGH,CRITICAL servicedesk-backend-sb4:local` | console |
| 6 | End-to-end API | `node scripts/smoke-test.mjs --base http://localhost:8089/api` | `smoke-*.json` |
| 7 | Database rows | `scripts/evidence-queries.sql` through `docker compose exec db mysql -t` | `stack-*-db-rows.log` |

Screenshots for the report (run `verify-all.ps1 -KeepRunning`, open http://localhost:8089):

1. Terminal: `Tests run: 8, Failures: 0, Errors: 0` and `BUILD SUCCESS` for each backend.
2. Terminal or XML: SpotBugs `total_bugs='0'`; Checkstyle `0` warnings.
3. Trivy summary table for the backend image.
4. Smoke test console: `20/20 checks passed`.
5. UI: overview dashboard (admin), incident list, new-incident form, ticket detail with
   activity timeline, reporting page. **Caption each one separately.**
6. Database: `incidents` and `incident_events` rows from `stack-b-sb4-db-rows.log`.
7. GitHub repository page with the `main` and `dev` branches.

Never mark a check as PASS unless it is backed by output from an actual run.

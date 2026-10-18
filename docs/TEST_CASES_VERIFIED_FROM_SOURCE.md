# Automated test cases (exact methods in the source)

Eight JUnit 5 methods exist in both backends. Last recorded run: Surefire XML from
16 Oct 2026, Spring Boot 4 module, Java 17.0.15, 8 run, 0 failures, 0 errors (archived in
`evidence/archived-2026-10-16-spring4-run/`). The 18 Oct 2026 changes did not alter any
assertion. Re-run with `verify-all` to get results for the current source.

| ID | Level | Class.method | Input / action | Asserted result |
|---|---|---|---|---|
| TC-01 | Unit | `IncidentMetricsTest.awaitingActionCountsOnlyOpenAndAssigned` | OPEN, ASSIGNED, RESOLVED incidents | `awaitingAction` returns 2 |
| TC-02 | Unit | `IncidentMetricsTest.completedCountsResolvedAndClosed` | OPEN, RESOLVED, CLOSED incidents | `completed` returns 2 |
| TC-03 | API | `ApiIntegrationTest.invalidEmailRejected` | POST `/api/auth/register` with email `invalid` | HTTP 400 |
| TC-04 | API | `ApiIntegrationTest.incidentCreated` | Authenticated POST `/api/incidents`, valid body | HTTP 201, `status` = OPEN |
| TC-05 | API | `ApiIntegrationTest.incidentRequiresTitle` | POST `/api/incidents` with blank title | HTTP 400 |
| TC-06 | API | `ApiIntegrationTest.unauthenticatedListRejected` | GET `/api/incidents` without token | HTTP 403 |
| TC-07 | API | `ApiIntegrationTest.userCannotAccessAdminReport` | End-user GET `/api/reports/summary` | HTTP 403 |
| TC-08 | API | `ApiIntegrationTest.userCanListOwnIncidents` | Authenticated GET `/api/incidents` | HTTP 200, JSON array |

Every API test first registers a fresh user through POST `/api/auth/register` (expects 201)
in `@BeforeEach`. There is no separate login or update unit test. Login, assignment, status
changes, resolution rules, notifications and cross-role permissions are covered by the 20-step
end-to-end smoke test (`scripts/smoke-test.mjs`), which runs against the real stack and MySQL.

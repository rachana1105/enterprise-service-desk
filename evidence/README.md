# Evidence

| Folder | What it is | Produced where |
|---|---|---|
| `archived-2026-10-16-spring4-run/` | Surefire XML (8 tests, 0 failures), SpotBugs XML (0 bugs) and Checkstyle XML (116 warnings) for `backend-spring4-test`, **before** the 18 Oct fixes | Student machine, WSL2, Java 17.0.15, 16 Oct 2026 |
| `fresh-2026-10-18-this-environment/` | Checkstyle 9.3 on the fixed source (0 warnings in both backends), the same run on the original source (116), and the frontend `npm ci`/build/audit log (0 vulnerabilities) | Claude cloud workspace, 18 Oct 2026; Maven Central and Docker registries were not reachable there, so no Java build or tests |
| `runs/<timestamp>/` | Output of `scripts/verify-all` (git-ignored; copy what the report needs) | Your machine |

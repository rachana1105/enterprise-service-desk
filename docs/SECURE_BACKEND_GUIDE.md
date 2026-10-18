# Spring Boot 4 hardened backend

Moved to [OPERATIONS.md](OPERATIONS.md) §2–§4. Short version:

```powershell
.\scripts\backup-db.ps1
docker compose -f compose.yaml -f compose.secure.yaml up --build -d
docker compose -f compose.yaml -f compose.secure.yaml logs backend --tail 100
```

The override keeps service names and the `mysql_data` volume and forces `JPA_DDL_AUTO=validate`.
Before you use it on real data, run `scripts\verify-all.ps1`. It tests this exact switch on a
throwaway database.

# Dumps the servicedesk database from the running Compose "db" service to backups\.
# Run this BEFORE switching backends, changing JPA_DDL_AUTO or running "docker compose down -v".
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
New-Item -ItemType Directory -Force backups | Out-Null
$out = "backups/servicedesk-$(Get-Date -Format yyyyMMdd-HHmmss).sql"
docker compose exec -T db sh -c 'exec mysqldump -uroot -p$MYSQL_ROOT_PASSWORD --single-transaction --routines --triggers --result-file=/tmp/servicedesk.sql servicedesk'
if ($LASTEXITCODE -ne 0) { throw 'mysqldump failed - check that the db service is running' }
docker compose cp db:/tmp/servicedesk.sql $out
if ($LASTEXITCODE -ne 0 -or (Get-Item $out).Length -eq 0) { throw 'Backup copy failed' }
Write-Host "Backup written to $out"
Write-Host "Restore: docker compose cp $out db:/tmp/restore.sql; docker compose exec -T db sh -c 'mysql -uroot -p`$MYSQL_ROOT_PASSWORD servicedesk < /tmp/restore.sql'"

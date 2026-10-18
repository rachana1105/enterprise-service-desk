#!/usr/bin/env bash
# Dumps the servicedesk database from the running Compose "db" service to backups/.
# Run this BEFORE switching backends, changing JPA_DDL_AUTO or running "docker compose down -v".
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p backups
out="backups/servicedesk-$(date +%Y%m%d-%H%M%S).sql"
docker compose exec -T db sh -c 'exec mysqldump -uroot -p$MYSQL_ROOT_PASSWORD --single-transaction --routines --triggers --result-file=/tmp/servicedesk.sql servicedesk'
docker compose cp db:/tmp/servicedesk.sql "$out"
test -s "$out" || { echo "Backup is empty - check that the db service is running" >&2; exit 1; }
echo "Backup written to $out"
echo "Restore: docker compose cp $out db:/tmp/restore.sql && docker compose exec -T db sh -c 'mysql -uroot -p\$MYSQL_ROOT_PASSWORD servicedesk < /tmp/restore.sql'"

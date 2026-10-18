#!/usr/bin/env bash
# Rebuilds and verifies the whole project with Docker only, saving dated evidence to
# evidence/runs/<timestamp>/. Same steps as scripts/verify-all.ps1 (see that file for details):
# Maven tests + Checkstyle + SpotBugs for both backends, frontend build + npm audit, image builds,
# Trivy scans, and a THROWAWAY Compose project "servicedesk-verify" on port 8089 (own database
# volume) that runs the API smoke test on Spring Boot 3, then switches to Spring Boot 4 with
# JPA_DDL_AUTO=validate on the same database and checks the schema is unchanged.
#
# Usage: scripts/verify-all.sh [--skip-stack] [--keep-running]
set -uo pipefail

SKIP_STACK=0; KEEP=0
for a in "$@"; do
  case $a in
    --skip-stack) SKIP_STACK=1 ;;
    --keep-running) KEEP=1 ;;
    *) echo "unknown option $a" >&2; exit 2 ;;
  esac
done
MAVEN_IMAGE=${MAVEN_IMAGE:-maven:3.9.9-eclipse-temurin-17}
NODE_IMAGE_TOOL=${NODE_IMAGE_TOOL:-node:22-alpine}
TRIVY_IMAGE=${TRIVY_IMAGE:-aquasec/trivy:latest}

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
STAMP=$(date +%Y%m%d-%H%M%S)
RUN="$ROOT/evidence/runs/$STAMP"
mkdir -p "$RUN"
STATUS_FILE="$RUN/.status"
: > "$STATUS_FILE"

step() { # step <name> <command...>
  local name=$1; shift
  echo; echo "=== $name ==="
  { echo "# $name  started $(date -Iseconds)"; } > "$RUN/$name.log"
  "$@" 2>&1 | tee -a "$RUN/$name.log"
  local code=${PIPESTATUS[0]}
  echo "# exit code $code  finished $(date -Iseconds)" >> "$RUN/$name.log"
  if [ "$code" -eq 0 ]; then echo "$name|OK" >> "$STATUS_FILE"; else echo "$name|FAILED (exit $code)" >> "$STATUS_FILE"; fi
}
copy_if() { [ -e "$1" ] && mkdir -p "$2" && cp -r "$1" "$2"/; return 0; }
secret() { head -c 36 /dev/urandom | base64 | tr '+/=' 'xxx'; }

envinfo() { date -Iseconds; docker version; docker compose version; git rev-parse HEAD 2>/dev/null; git status --short 2>/dev/null; return 0; }
step environment envinfo

for m in backend backend-spring4-test; do
  mvn_run() { docker run --rm -v "$ROOT/$m:/app" -v servicedesk-maven-cache:/root/.m2 -w /app "$MAVEN_IMAGE" "$@"; }
  step "$m-01-mvn-test" mvn_run mvn -B clean test
  copy_if "$ROOT/$m/target/surefire-reports" "$RUN/$m"
  step "$m-02-checkstyle" mvn_run mvn -B checkstyle:checkstyle
  copy_if "$ROOT/$m/target/checkstyle-result.xml" "$RUN/$m"
  step "$m-03-spotbugs" mvn_run mvn -B compile com.github.spotbugs:spotbugs-maven-plugin:4.9.8.5:spotbugs
  copy_if "$ROOT/$m/target/spotbugsXml.xml" "$RUN/$m"
done

step frontend-build-audit docker run --rm -v "$ROOT/frontend:/app" -w /app "$NODE_IMAGE_TOOL" \
  sh -c 'npm ci --no-fund && npm run build && npm audit'

export DB_PASSWORD=$(secret) MYSQL_ROOT_PASSWORD=$(secret) JWT_SECRET="$(secret)$(secret)"
export DEMO_PASSWORD="Demo-$(secret | cut -c1-16)" HTTP_PORT=8089
P=(docker compose -p servicedesk-verify)
PS=(docker compose -p servicedesk-verify -f compose.yaml -f compose.secure.yaml)
build_images() { "${P[@]}" build && "${PS[@]}" build backend; }
step images-build build_images

for img in servicedesk-backend:local servicedesk-backend-sb4:local servicedesk-frontend:local; do
  trivy() { docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v servicedesk-trivy-cache:/root/.cache "$TRIVY_IMAGE" "$@"; }
  scan() { trivy --version && trivy image --scanners vuln --severity HIGH,CRITICAL --no-progress "$img"; }
  step "trivy-${img//[:\/]/_}" scan
done

if [ $SKIP_STACK -eq 0 ]; then
  DBQ='exec mysql -t -uroot -p$MYSQL_ROOT_PASSWORD servicedesk'
  SCHEMAQ='exec mysqldump -uroot -p$MYSQL_ROOT_PASSWORD --no-data --skip-comments servicedesk'
  wait_api() {
    for _ in $(seq 60); do
      code=$(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' -d '{}' http://localhost:8089/api/auth/login)
      case $code in 4??) return 0 ;; esac
      sleep 5
    done
    return 1
  }
  smoke() {
    docker run --rm --network servicedesk-verify_default -e DEMO_PASSWORD="$DEMO_PASSWORD" \
      -v "$ROOT/scripts:/s:ro" -v "$RUN:/out" "$NODE_IMAGE_TOOL" \
      node /s/smoke-test.mjs --base http://frontend/api --out "/out/smoke-$1.json"
  }

  export DEMO_SEED=true JPA_DDL_AUTO=update
  "${P[@]}" down -v > /dev/null 2>&1
  up_a() { "${P[@]}" up -d && { wait_api || { "${P[@]}" logs backend --tail 200; return 1; }; }; }
  step stack-a-sb3-up up_a
  step stack-a-sb3-smoke-test smoke sb3
  rows_a() { "${P[@]}" exec -T db sh -c "$DBQ" < scripts/evidence-queries.sql; }
  step stack-a-sb3-db-rows rows_a
  "${P[@]}" exec -T db sh -c "$SCHEMAQ" 2>/dev/null > "$RUN/schema-before.sql"

  unset JPA_DDL_AUTO
  up_b() { "${PS[@]}" up -d && "${PS[@]}" restart frontend && { wait_api || { "${PS[@]}" logs backend --tail 200; return 1; }; }; }
  step stack-b-sb4-up up_b
  step stack-b-sb4-smoke-test smoke sb4
  rows_b() { "${PS[@]}" exec -T db sh -c "$DBQ" < scripts/evidence-queries.sql; }
  step stack-b-sb4-db-rows rows_b
  "${PS[@]}" exec -T db sh -c "$SCHEMAQ" 2>/dev/null > "$RUN/schema-after.sql"
  if cmp -s "$RUN/schema-before.sql" "$RUN/schema-after.sql"; then
    echo "schema-unchanged-after-sb4-switch|OK" >> "$STATUS_FILE"
  else
    echo "schema-unchanged-after-sb4-switch|CHANGED - see schema-*.sql" >> "$STATUS_FILE"
  fi
  "${PS[@]}" logs backend > "$RUN/stack-b-sb4-backend.log" 2>&1

  if [ $KEEP -eq 1 ]; then
    echo; echo "Stack left running at http://localhost:8089 (Spring Boot 4 backend)."
    echo "Demo accounts admin@/engineer@/employee@demo.local, password: $DEMO_PASSWORD"
    echo "Remove it afterwards with: docker compose -p servicedesk-verify down -v"
  else
    "${PS[@]}" down -v > /dev/null 2>&1
  fi
fi

python3 - "$RUN" <<'EOF' || echo "(python3 not found: see $RUN for raw results)"
import glob, json, os, sys, xml.etree.ElementTree as ET
run = sys.argv[1]
md = [f"# Verification run {os.path.basename(run)}", "", "| Step | Result |", "|---|---|"]
for line in open(os.path.join(run, ".status")):
    k, v = line.rstrip("\n").split("|", 1)
    md.append(f"| {k} | {v} |")
md.append("")
for m in ("backend", "backend-spring4-test"):
    t = f = e = s = 0
    for x in glob.glob(os.path.join(run, m, "**", "TEST-*.xml"), recursive=True):
        r = ET.parse(x).getroot()
        t += int(r.get("tests")); f += int(r.get("failures")); e += int(r.get("errors")); s += int(r.get("skipped"))
    md += [f"## {m}", f"- JUnit: {t} run, {f} failures, {e} errors, {s} skipped"]
    cs = os.path.join(run, m, "checkstyle-result.xml")
    if os.path.exists(cs):
        md.append(f"- Checkstyle warnings: {len(ET.parse(cs).getroot().findall('.//error'))}")
    sb = os.path.join(run, m, "spotbugsXml.xml")
    if os.path.exists(sb):
        md.append(f"- SpotBugs bug instances: {len(ET.parse(sb).getroot().findall('.//BugInstance'))}")
    md.append("")
for label in ("sb3", "sb4"):
    j = os.path.join(run, f"smoke-{label}.json")
    if os.path.exists(j):
        o = json.load(open(j))
        md.append(f"- Smoke test ({label}): {o['passed']}/{o['total']} passed")
open(os.path.join(run, "summary.md"), "w").write("\n".join(md) + "\n")
print("\n".join(md))
EOF
echo; echo "Evidence folder: $RUN"

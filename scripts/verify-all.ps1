<#
.SYNOPSIS
  Rebuilds and verifies the whole project with Docker only, saving dated evidence.

.DESCRIPTION
  Writes everything to evidence\runs\<timestamp>\ :
    1. JUnit tests, Checkstyle and SpotBugs for backend\ (Spring Boot 3.5) and
       backend-spring4-test\ (Spring Boot 4.1), run in the official Maven container.
    2. Frontend clean install, production build and npm audit.
    3. Docker image builds for both backends and the frontend.
    4. Trivy vulnerability scans of the built images (HIGH/CRITICAL).
    5. Unless -SkipStack: a THROWAWAY Compose project "servicedesk-verify" on port 8089 with its
       own database volume (your real data is never touched):
         a. Spring Boot 3 backend on a new database (schema created once with JPA_DDL_AUTO=update),
            20-step API smoke test, database rows dumped as evidence.
         b. Switch to the Spring Boot 4 backend with compose.secure.yaml (JPA_DDL_AUTO=validate)
            on the SAME database, smoke test again, and confirm the schema did not change.
       The throwaway project is deleted at the end unless -KeepRunning is given (then open
       http://localhost:8089 to take UI screenshots; log in with the printed demo password).
    6. summary.md with all counts.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\verify-all.ps1
  powershell -ExecutionPolicy Bypass -File scripts\verify-all.ps1 -KeepRunning
#>
param(
  [switch]$SkipStack,
  [switch]$KeepRunning,
  [string]$MavenImage = 'maven:3.9.9-eclipse-temurin-17',
  [string]$NodeImage = 'node:22-alpine',
  [string]$TrivyImage = 'aquasec/trivy:latest'
)

$ErrorActionPreference = 'Continue'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $root
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$run = Join-Path $root "evidence\runs\$stamp"
New-Item -ItemType Directory -Force $run | Out-Null
$status = [ordered]@{}

function Step([string]$name, [scriptblock]$cmd) {
  Write-Host "`n=== $name ===" -ForegroundColor Cyan
  $log = Join-Path $run "$name.log"
  "# $name  started $(Get-Date -Format o)" | Out-File -Encoding utf8 $log
  & $cmd 2>&1 | ForEach-Object { "$_" } | Tee-Object -Variable lines | Write-Host
  $code = $LASTEXITCODE
  $lines | Out-File -Encoding utf8 -Append $log
  "# exit code $code  finished $(Get-Date -Format o)" | Out-File -Encoding utf8 -Append $log
  $script:status[$name] = $(if ($code -eq 0) { 'OK' } else { "FAILED (exit $code)" })
  return $code
}

function Copy-IfExists([string]$from, [string]$to) {
  if (Test-Path $from) {
    New-Item -ItemType Directory -Force $to | Out-Null
    Copy-Item -Recurse -Force $from $to
  }
}

function New-Secret {
  $bytes = New-Object byte[] 36
  [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
  return ([Convert]::ToBase64String($bytes) -replace '[+/=]', 'x')
}

# ---------- environment ----------
Step 'environment' {
  Get-Date -Format o
  docker version
  docker compose version
  git rev-parse HEAD
  git status --short
  $global:LASTEXITCODE = 0   # git is optional; never fail this step
} | Out-Null

# ---------- 1. backend modules ----------
foreach ($m in @('backend', 'backend-spring4-test')) {
  $mount = "${root}\${m}:/app"
  $mvn = @('run', '--rm', '-v', $mount, '-v', 'servicedesk-maven-cache:/root/.m2', '-w', '/app', $MavenImage)
  Step "$m-01-mvn-test" { docker @mvn mvn -B clean test } | Out-Null
  Copy-IfExists "$root\$m\target\surefire-reports" "$run\$m"
  Step "$m-02-checkstyle" { docker @mvn mvn -B checkstyle:checkstyle } | Out-Null
  Copy-IfExists "$root\$m\target\checkstyle-result.xml" "$run\$m"
  Step "$m-03-spotbugs" { docker @mvn mvn -B compile com.github.spotbugs:spotbugs-maven-plugin:4.9.8.5:spotbugs } | Out-Null
  Copy-IfExists "$root\$m\target\spotbugsXml.xml" "$run\$m"
}

# ---------- 2. frontend ----------
Step 'frontend-build-audit' {
  docker run --rm -v "${root}\frontend:/app" -w /app $NodeImage sh -c 'npm ci --no-fund && npm run build && npm audit'
} | Out-Null

# ---------- 3. images ----------
$env:DB_PASSWORD = New-Secret
$env:MYSQL_ROOT_PASSWORD = New-Secret
$env:JWT_SECRET = (New-Secret) + (New-Secret)
$env:DEMO_PASSWORD = 'Demo-' + (New-Secret).Substring(0, 16)
$env:HTTP_PORT = '8089'
Step 'images-build' {
  docker compose -p servicedesk-verify build
  docker compose -p servicedesk-verify -f compose.yaml -f compose.secure.yaml build backend
} | Out-Null

# ---------- 4. Trivy ----------
foreach ($img in @('servicedesk-backend:local', 'servicedesk-backend-sb4:local', 'servicedesk-frontend:local')) {
  $safe = $img -replace '[:/]', '_'
  Step "trivy-$safe" {
    docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v servicedesk-trivy-cache:/root/.cache $TrivyImage --version
    docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v servicedesk-trivy-cache:/root/.cache $TrivyImage image --scanners vuln --severity HIGH,CRITICAL --no-progress $img
  } | Out-Null
}

# ---------- 5. throwaway stack ----------
if (-not $SkipStack) {
  $p = @('compose', '-p', 'servicedesk-verify')
  $dbq = 'exec mysql -t -uroot -p$MYSQL_ROOT_PASSWORD servicedesk'
  $queries = Get-Content "$root\scripts\evidence-queries.sql"
  $schemaq = 'exec mysqldump -uroot -p$MYSQL_ROOT_PASSWORD --no-data --skip-comments servicedesk'

  function Wait-Api {
    for ($i = 0; $i -lt 60; $i++) {
      try {
        Invoke-WebRequest -UseBasicParsing -Method Post -ContentType 'application/json' -Body '{}' "http://localhost:8089/api/auth/login" | Out-Null
        return $true
      } catch {
        # nginx answers 502 until the backend is up; any 4xx means the API is answering.
        if ($_.Exception.Response) {
          $code = [int]$_.Exception.Response.StatusCode
          if ($code -ge 400 -and $code -lt 500) { return $true }
        }
      }
      Start-Sleep -Seconds 5
    }
    return $false
  }

  function Smoke([string]$label) {
    docker run --rm --network servicedesk-verify_default -e DEMO_PASSWORD=$env:DEMO_PASSWORD `
      -v "${root}\scripts:/s:ro" -v "${run}:/out" $NodeImage `
      node /s/smoke-test.mjs --base http://frontend/api --out "/out/smoke-$label.json"
  }

  # a. Spring Boot 3 on a fresh database
  $env:DEMO_SEED = 'true'
  $env:JPA_DDL_AUTO = 'update'
  docker @p down -v 2>&1 | Out-Null
  Step 'stack-a-sb3-up' { docker @p up -d; if (-not (Wait-Api)) { docker @p logs backend --tail 200; $global:LASTEXITCODE = 1 } } | Out-Null
  Step 'stack-a-sb3-smoke-test' { Smoke 'sb3' } | Out-Null
  Step 'stack-a-sb3-db-rows' { $queries | docker @p exec -T db sh -c $dbq } | Out-Null
  docker @p exec -T db sh -c $schemaq 2>$null | Out-File -Encoding utf8 "$run\schema-before.sql"

  # b. Spring Boot 4 on the same database, schema changes blocked (validate)
  Remove-Item Env:JPA_DDL_AUTO
  $ps = $p + @('-f', 'compose.yaml', '-f', 'compose.secure.yaml')
  # Recreate the backend, then restart nginx so it re-resolves the new backend container.
  Step 'stack-b-sb4-up' {
    docker @ps up -d
    docker @ps restart frontend
    if (-not (Wait-Api)) { docker @ps logs backend --tail 200; $global:LASTEXITCODE = 1 }
  } | Out-Null
  Step 'stack-b-sb4-smoke-test' { Smoke 'sb4' } | Out-Null
  Step 'stack-b-sb4-db-rows' { $queries | docker @ps exec -T db sh -c $dbq } | Out-Null
  docker @ps exec -T db sh -c $schemaq 2>$null | Out-File -Encoding utf8 "$run\schema-after.sql"
  $same = (Get-Content "$run\schema-before.sql" -Raw) -eq (Get-Content "$run\schema-after.sql" -Raw)
  $status['schema-unchanged-after-sb4-switch'] = $(if ($same) { 'OK' } else { 'CHANGED - see schema-*.sql' })
  docker @ps logs backend 2>&1 | Out-File -Encoding utf8 "$run\stack-b-sb4-backend.log"

  if ($KeepRunning) {
    Write-Host "`nStack left running at http://localhost:8089 (Spring Boot 4 backend)." -ForegroundColor Yellow
    Write-Host "Demo accounts admin@demo.local / engineer@demo.local / employee@demo.local, password: $env:DEMO_PASSWORD"
    Write-Host "Remove it afterwards with: docker compose -p servicedesk-verify down -v"
  } else {
    docker @ps down -v 2>&1 | Out-Null
  }
}

# ---------- 6. summary ----------
function Count-Tests([string]$dir) {
  $t = 0; $f = 0; $e = 0; $s = 0
  Get-ChildItem -Path $dir -Filter 'TEST-*.xml' -Recurse -ErrorAction SilentlyContinue | ForEach-Object {
    $x = [xml](Get-Content $_.FullName -Raw)
    $t += [int]$x.testsuite.tests; $f += [int]$x.testsuite.failures; $e += [int]$x.testsuite.errors; $s += [int]$x.testsuite.skipped
  }
  return "$t run, $f failures, $e errors, $s skipped"
}
$md = @("# Verification run $stamp", '', "Started from: $root", '', '| Step | Result |', '|---|---|')
foreach ($k in $status.Keys) { $md += "| $k | $($status[$k]) |" }
$md += ''
foreach ($m in @('backend', 'backend-spring4-test')) {
  $md += "## $m"
  $md += "- JUnit: $(Count-Tests "$run\$m")"
  $cs = "$run\$m\checkstyle-result.xml"
  if (Test-Path $cs) { $md += "- Checkstyle warnings: $(([xml](Get-Content $cs -Raw)).SelectNodes('//error').Count)" }
  $sb = "$run\$m\spotbugsXml.xml"
  if (Test-Path $sb) { $md += "- SpotBugs bug instances: $(([xml](Get-Content $sb -Raw)).SelectNodes('//BugInstance').Count)" }
  $md += ''
}
foreach ($label in @('sb3', 'sb4')) {
  $j = "$run\smoke-$label.json"
  if (Test-Path $j) { $o = Get-Content $j -Raw | ConvertFrom-Json; $md += "- Smoke test ($label): $($o.passed)/$($o.total) passed" }
}
$md | Out-File -Encoding utf8 "$run\summary.md"
Write-Host "`n$($md -join "`n")"
Write-Host "`nEvidence folder: $run" -ForegroundColor Green

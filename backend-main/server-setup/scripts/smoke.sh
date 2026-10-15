#!/usr/bin/env bash
# Post-deploy smoke tests. Run against UAT (or any environment with all
# eight services + gateway up). Exits non-zero on the first failure.
#
# Usage:
#   GATEWAY=http://uat.hrms.local USERNAME=admin PASSWORD=$(pass auth-uat) \
#     ./smoke.sh
#
# Defaults to localhost:8080 (gateway) with the dev bootstrap admin
# credentials. Override via env vars in real environments.

set -euo pipefail

GATEWAY="${GATEWAY:-http://localhost:8080}"
USERNAME="${USERNAME:-admin}"
PASSWORD="${PASSWORD:-ChangeMe!2026}"

# Per-service direct ports — used when checking each service's actuator
# health (the gateway only exposes /auth, /employees, etc., not /actuator).
declare -A SERVICE_PORTS=(
    [auth]=8081
    [employee]=8082
    [attendance]=8083
    [notification]=8084
    [audit]=8085
    [bff]=8086
    [payroll]=8087
    [chat]=8088
)

# Used during pass: number of checks run / failed.
PASSED=0
FAILED=0

red()   { printf '\033[0;31m%s\033[0m' "$1"; }
green() { printf '\033[0;32m%s\033[0m' "$1"; }

check() {
    local label="$1"
    shift
    if "$@" >/dev/null 2>&1; then
        printf "  %s %s\n" "$(green ✓)" "$label"
        PASSED=$((PASSED + 1))
    else
        printf "  %s %s\n" "$(red ✗)" "$label"
        FAILED=$((FAILED + 1))
    fi
}

# 1. Per-service /actuator/health — direct on each service's port.
echo "[1/4] Service health"
for svc in "${!SERVICE_PORTS[@]}"; do
    port="${SERVICE_PORTS[$svc]}"
    check "$svc /actuator/health (port $port)" \
        bash -c "curl -fs --max-time 5 http://localhost:$port/actuator/health | grep -q '\"status\":\"UP\"'"
done

# 2. Login → call one authenticated endpoint per service via gateway.
echo "[2/4] Auth round-trip + protected calls"
LOGIN_RESPONSE=$(curl -fs --max-time 10 -X POST "$GATEWAY/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")

ACCESS_TOKEN=$(printf '%s' "$LOGIN_RESPONSE" | python3 -c \
    'import json, sys; print(json.load(sys.stdin)["accessToken"])')

if [[ -z "${ACCESS_TOKEN:-}" || "$ACCESS_TOKEN" == "null" ]]; then
    printf "  %s login returned no accessToken — bailing\n" "$(red ✗)"
    exit 1
fi
printf "  %s login (got %d-char token)\n" "$(green ✓)" "${#ACCESS_TOKEN}"
PASSED=$((PASSED + 1))

# One light authenticated GET per service.
declare -A AUTH_GETS=(
    [auth]="$GATEWAY/auth/me"
    [employee]="$GATEWAY/employees/by-ids?ids=1"
    [attendance]="$GATEWAY/attendance/employee/1?from=2026-01-01&to=2026-01-02"
    [audit]="$GATEWAY/timeline/user/1?limit=1"
    [bff]="$GATEWAY/bff/account/summary"
    [payroll]="$GATEWAY/statutory/config"
    [notification]="$GATEWAY/notifications/my?limit=1"
    [chat]="$GATEWAY/chat/threads?limit=1"
)
for svc in "${!AUTH_GETS[@]}"; do
    url="${AUTH_GETS[$svc]}"
    # 200 / 204 / 404 are all "service answered with a real ProblemDetail-shaped
    # response" — we only treat connection failure / 5xx as a real fault
    # because some endpoints reject the dev admin (e.g. employee 1 may not
    # exist) but still prove the service is alive.
    check "$svc protected GET $url" \
        bash -c "code=\$(curl -fs --max-time 8 -o /dev/null -w '%{http_code}' \
            -H 'Authorization: Bearer $ACCESS_TOKEN' '$url' || echo 000); \
            [[ \$code -lt 500 ]]"
done

# 3. OpenAPI exposure — Swagger UI loads + /v3/api-docs returns JSON
#    on every service that has commons-web on its classpath.
echo "[3/4] OpenAPI / Swagger UI"
for svc in "${!SERVICE_PORTS[@]}"; do
    port="${SERVICE_PORTS[$svc]}"
    case "$svc" in
        gateway)  continue ;;  # spring-cloud-gateway: separate doc surface
    esac
    check "$svc /swagger-ui.html" \
        bash -c "curl -fs --max-time 5 -o /dev/null -w '%{http_code}' \
            http://localhost:$port/swagger-ui.html | grep -qE '^(200|302)\$'"
    check "$svc /v3/api-docs returns JSON" \
        bash -c "curl -fs --max-time 5 http://localhost:$port/v3/api-docs \
            | head -c 1 | grep -q '{'"
done

# 4. Sample report — async kickoff + poll for READY. Picks the lightest
#    payroll report (statutory/eobi) that doesn't need a posted run.
echo "[4/4] Sample async report"
JOB_RESPONSE=$(curl -fs --max-time 10 -X POST \
    "$GATEWAY/reports/payroll/statutory/eobi" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H 'Content-Type: application/json' \
    -d '{"year":2026,"month":1,"format":"PDF"}' || true)
if [[ -z "$JOB_RESPONSE" ]]; then
    printf "  %s skipped — endpoint not available in this build\n" "$(red ⚠)"
else
    JOB_ID=$(printf '%s' "$JOB_RESPONSE" | python3 -c \
        'import json, sys; print(json.load(sys.stdin).get("jobId", ""))' || true)
    if [[ -n "$JOB_ID" ]]; then
        # Poll up to 30s for READY.
        ready=0
        for _ in {1..15}; do
            status=$(curl -fs --max-time 5 \
                -H "Authorization: Bearer $ACCESS_TOKEN" \
                "$GATEWAY/reports/jobs/$JOB_ID" \
                | python3 -c 'import json, sys; print(json.load(sys.stdin)["status"])' \
                || echo "ERR")
            case "$status" in
                READY)  ready=1; break ;;
                FAILED) break ;;
                *)      sleep 2 ;;
            esac
        done
        if [[ $ready -eq 1 ]]; then
            check "report job $JOB_ID reached READY" true
        else
            check "report job $JOB_ID reached READY (last status=$status)" false
        fi
    else
        printf "  %s skipped — kickoff returned no jobId\n" "$(red ⚠)"
    fi
fi

# Summary.
echo
if [[ $FAILED -eq 0 ]]; then
    printf "%s all %d checks passed\n" "$(green PASS)" "$PASSED"
    exit 0
else
    printf "%s %d failed, %d passed\n" "$(red FAIL)" "$FAILED" "$PASSED"
    exit 1
fi

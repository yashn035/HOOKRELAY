#!/usr/bin/env bash
set -eo pipefail

BASE_URL="${1:-http://localhost:8080}"
BASE_URL="${BASE_URL%/}" # Strip trailing slash

echo "=================================================================="
echo " HookRelay Production Smoke Test"
echo " Target Base URL: $BASE_URL"
echo " Timestamp: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo "=================================================================="

PASS_COUNT=0
FAIL_COUNT=0

record_result() {
    local check_name="$1"
    local status="$2"
    local detail="$3"
    if [ "$status" = "PASS" ]; then
        PASS_COUNT=$((PASS_COUNT + 1))
        printf " [PASS] %-40s | %s\n" "$check_name" "$detail"
    else
        FAIL_COUNT=$((FAIL_COUNT + 1))
        printf " [FAIL] %-40s | %s\n" "$check_name" "$detail"
    fi
}

# Check 1: GET /actuator/health
HTTP_CODE=$(curl -s -o /tmp/hr_health.json -w "%{http_code}" "$BASE_URL/actuator/health" || echo "000")
if [ "$HTTP_CODE" = "200" ] && grep -q '"status":"UP"' /tmp/hr_health.json; then
    record_result "1. GET /actuator/health" "PASS" "HTTP 200 (status=UP)"
else
    record_result "1. GET /actuator/health" "FAIL" "Expected HTTP 200 with status UP, got HTTP $HTTP_CODE"
fi

# Check 2: GET /actuator/prometheus
HTTP_CODE=$(curl -s -o /tmp/hr_prom.txt -w "%{http_code}" "$BASE_URL/actuator/prometheus" || echo "000")
if [ "$HTTP_CODE" = "200" ] && grep -q "hookrelay_deliveries_queue_depth" /tmp/hr_prom.txt; then
    record_result "2. GET /actuator/prometheus" "PASS" "HTTP 200 (metric found)"
else
    record_result "2. GET /actuator/prometheus" "FAIL" "Expected hookrelay_deliveries_queue_depth, got HTTP $HTTP_CODE"
fi

# Check 3: POST /api/v1/auth/login
LOGIN_PAYLOAD='{"email":"admin@hookrelay.dev","password":"password123!"}'
HTTP_CODE=$(curl -s -o /tmp/hr_login.json -w "%{http_code}" -X POST "$BASE_URL/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "$LOGIN_PAYLOAD" || echo "000")

JWT_TOKEN=""
PROJECT_ID=""
if [ "$HTTP_CODE" = "200" ]; then
    JWT_TOKEN=$(grep -o '"token":"[^"]*' /tmp/hr_login.json | cut -d'"' -f4 || true)
    PROJECT_ID=$(grep -o '"defaultProjectId":"[^"]*' /tmp/hr_login.json | cut -d'"' -f4 || true)
    if [ -n "$JWT_TOKEN" ]; then
        record_result "3. POST /api/v1/auth/login" "PASS" "HTTP 200 (token acquired)"
    else
        record_result "3. POST /api/v1/auth/login" "FAIL" "HTTP 200 but token missing"
    fi
else
    record_result "3. POST /api/v1/auth/login" "FAIL" "Expected HTTP 200, got HTTP $HTTP_CODE"
fi

# Check 4: GET /api/v1/projects
if [ -n "$JWT_TOKEN" ]; then
    HTTP_CODE=$(curl -s -o /tmp/hr_projects.json -w "%{http_code}" "$BASE_URL/api/v1/projects" \
        -H "Authorization: Bearer $JWT_TOKEN" || echo "000")
    if [ "$HTTP_CODE" = "200" ] && grep -q '"id"' /tmp/hr_projects.json; then
        record_result "4. GET /api/v1/projects" "PASS" "HTTP 200 (>=1 project)"
    else
        record_result "4. GET /api/v1/projects" "FAIL" "Expected HTTP 200 with projects, got HTTP $HTTP_CODE"
    fi
else
    record_result "4. GET /api/v1/projects" "FAIL" "Skipped due to login failure"
fi

# Check 5: GET /api/v1/projects/{id}/endpoints
if [ -n "$JWT_TOKEN" ] && [ -n "$PROJECT_ID" ]; then
    HTTP_CODE=$(curl -s -o /tmp/hr_endpoints.json -w "%{http_code}" "$BASE_URL/api/v1/projects/$PROJECT_ID/endpoints" \
        -H "Authorization: Bearer $JWT_TOKEN" || echo "000")
    if [ "$HTTP_CODE" = "200" ] && grep -q '"id"' /tmp/hr_endpoints.json; then
        record_result "5. GET /api/v1/projects/{id}/endpoints" "PASS" "HTTP 200 (>=1 endpoint)"
    else
        record_result "5. GET /api/v1/projects/{id}/endpoints" "FAIL" "Expected endpoints, got HTTP $HTTP_CODE"
    fi
else
    record_result "5. GET /api/v1/projects/{id}/endpoints" "FAIL" "Skipped (no project ID)"
fi

# Check 6: POST /api/v1/events with API key
EVENT_PAYLOAD='{"type":"smoke.test.event","data":{"testId":"smoke_'$(date +%s)'"}}'
HTTP_CODE=$(curl -s -o /tmp/hr_event.json -w "%{http_code}" -X POST "$BASE_URL/api/v1/events" \
    -H "Authorization: Bearer hr_live_demo_acme_production_key_001" \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: smoke-key-$(date +%s)" \
    -d "$EVENT_PAYLOAD" || echo "000")

if [ "$HTTP_CODE" = "202" ] || [ "$HTTP_CODE" = "200" ]; then
    record_result "6. POST /api/v1/events" "PASS" "HTTP $HTTP_CODE (event accepted)"
else
    record_result "6. POST /api/v1/events" "FAIL" "Expected HTTP 202, got HTTP $HTTP_CODE"
fi

# Check 7: GET /swagger-ui/index.html
HTTP_CODE=$(curl -s -L -o /dev/null -w "%{http_code}" "$BASE_URL/swagger-ui/index.html" || echo "000")
if [ "$HTTP_CODE" = "200" ]; then
    record_result "7. GET /swagger-ui/index.html" "PASS" "HTTP 200 (docs rendered)"
else
    # fallback to swagger-ui.html redirect
    HTTP_CODE=$(curl -s -L -o /dev/null -w "%{http_code}" "$BASE_URL/swagger-ui.html" || echo "000")
    if [ "$HTTP_CODE" = "200" ]; then
        record_result "7. GET /swagger-ui/index.html" "PASS" "HTTP 200 via swagger-ui.html"
    else
        record_result "7. GET /swagger-ui/index.html" "FAIL" "Expected HTTP 200, got HTTP $HTTP_CODE"
    fi
fi

echo "=================================================================="
echo " Smoke Test Summary: $PASS_COUNT PASSED, $FAIL_COUNT FAILED"
echo "=================================================================="

rm -f /tmp/hr_health.json /tmp/hr_prom.txt /tmp/hr_login.json /tmp/hr_projects.json /tmp/hr_endpoints.json /tmp/hr_event.json

if [ "$FAIL_COUNT" -gt 0 ]; then
    exit 1
fi
exit 0

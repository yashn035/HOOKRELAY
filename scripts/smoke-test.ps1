param (
    [string]$BaseUrl = "http://localhost:8080"
)

$BaseUrl = $BaseUrl.TrimEnd('/')

Write-Host "=================================================================="
Write-Host " HookRelay Production Smoke Test (PowerShell)"
Write-Host " Target Base URL: $BaseUrl"
Write-Host " Timestamp: $(Get-Date -Format 'yyyy-MM-ddTHH:mm:ssZ')"
Write-Host "=================================================================="

$passCount = 0
$failCount = 0

function Record-Result {
    param(
        [string]$CheckName,
        [string]$Status,
        [string]$Detail
    )
    if ($Status -eq "PASS") {
        $script:passCount++
        Write-Host " [PASS] $($CheckName.PadRight(40)) | $Detail" -ForegroundColor Green
    } else {
        $script:failCount++
        Write-Host " [FAIL] $($CheckName.PadRight(40)) | $Detail" -ForegroundColor Red
    }
}

# 1. GET /actuator/health
try {
    $resp = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -Method Get -TimeoutSec 10 -ErrorAction Stop
    if ($resp.status -eq "UP") {
        Record-Result "1. GET /actuator/health" "PASS" "HTTP 200 (status=UP)"
    } else {
        Record-Result "1. GET /actuator/health" "FAIL" "Status is $($resp.status)"
    }
} catch {
    Record-Result "1. GET /actuator/health" "FAIL" $_.Exception.Message
}

# 2. GET /actuator/prometheus
try {
    $prom = (Invoke-WebRequest -Uri "$BaseUrl/actuator/prometheus" -Method Get -UseBasicParsing -TimeoutSec 10 -ErrorAction Stop).Content
    if ($prom -match "hookrelay_deliveries_queue_depth") {
        Record-Result "2. GET /actuator/prometheus" "PASS" "HTTP 200 (metric found)"
    } else {
        Record-Result "2. GET /actuator/prometheus" "FAIL" "Metric not found in scrape output"
    }
} catch {
    Record-Result "2. GET /actuator/prometheus" "FAIL" $_.Exception.Message
}

# 3. POST /api/v1/auth/login
$token = ""
$defaultProjectId = ""
try {
    $loginBody = '{"email":"admin@hookrelay.dev","password":"password123!"}'
    $loginResp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -ContentType "application/json" -Body $loginBody -TimeoutSec 10 -ErrorAction Stop
    if ($loginResp.token) {
        $token = $loginResp.token
        $defaultProjectId = $loginResp.defaultProjectId
        Record-Result "3. POST /api/v1/auth/login" "PASS" "HTTP 200 (token acquired)"
    } else {
        Record-Result "3. POST /api/v1/auth/login" "FAIL" "Login succeeded but token absent"
    }
} catch {
    Record-Result "3. POST /api/v1/auth/login" "FAIL" $_.Exception.Message
}

# 4. GET /api/v1/projects
if ($token) {
    try {
        $headers = @{ Authorization = "Bearer $token" }
        $projects = Invoke-RestMethod -Uri "$BaseUrl/api/v1/projects" -Method Get -Headers $headers -TimeoutSec 10 -ErrorAction Stop
        if ($projects.Count -ge 1) {
            Record-Result "4. GET /api/v1/projects" "PASS" "HTTP 200 ($($projects.Count) projects)"
        } else {
            Record-Result "4. GET /api/v1/projects" "FAIL" "No projects returned"
        }
    } catch {
        Record-Result "4. GET /api/v1/projects" "FAIL" $_.Exception.Message
    }
} else {
    Record-Result "4. GET /api/v1/projects" "FAIL" "Skipped (no token)"
}

# 5. GET /api/v1/projects/{id}/endpoints
if ($token -and $defaultProjectId) {
    try {
        $headers = @{ Authorization = "Bearer $token" }
        $endpoints = Invoke-RestMethod -Uri "$BaseUrl/api/v1/projects/$defaultProjectId/endpoints" -Method Get -Headers $headers -TimeoutSec 10 -ErrorAction Stop
        if ($endpoints.Count -ge 1) {
            Record-Result "5. GET /api/v1/projects/{id}/endpoints" "PASS" "HTTP 200 ($($endpoints.Count) endpoints)"
        } else {
            Record-Result "5. GET /api/v1/projects/{id}/endpoints" "FAIL" "Zero endpoints returned"
        }
    } catch {
        Record-Result "5. GET /api/v1/projects/{id}/endpoints" "FAIL" $_.Exception.Message
    }
} else {
    Record-Result "5. GET /api/v1/projects/{id}/endpoints" "FAIL" "Skipped (no project ID)"
}

# 6. POST /api/v1/events with API Key
try {
    $apiKey = "hr_live_demo_acme_production_key_001"
    $headers = @{
        Authorization = "Bearer $apiKey"
        "Content-Type" = "application/json"
        "Idempotency-Key" = "smoke-key-$(Get-Date -UFormat %s)"
    }
    $eventPayload = '{"type":"smoke.test.event","data":{"testId":"ps_smoke"}}'
    $eventResp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/events" -Method Post -Headers $headers -Body $eventPayload -TimeoutSec 10 -ErrorAction Stop
    if ($eventResp.eventId) {
        Record-Result "6. POST /api/v1/events" "PASS" "HTTP 202 (eventId: $($eventResp.eventId))"
    } else {
        Record-Result "6. POST /api/v1/events" "FAIL" "No eventId in response"
    }
} catch {
    Record-Result "6. POST /api/v1/events" "FAIL" $_.Exception.Message
}

# 7. GET /swagger-ui/index.html
try {
    $swaggerResp = Invoke-WebRequest -Uri "$BaseUrl/swagger-ui/index.html" -Method Get -UseBasicParsing -MaximumRedirection 5 -TimeoutSec 10 -ErrorAction Stop
    if ($swaggerResp.StatusCode -eq 200) {
        Record-Result "7. GET /swagger-ui/index.html" "PASS" "HTTP 200 (docs rendered)"
    } else {
        Record-Result "7. GET /swagger-ui/index.html" "FAIL" "HTTP Status $($swaggerResp.StatusCode)"
    }
} catch {
    try {
        $swaggerAlt = Invoke-WebRequest -Uri "$BaseUrl/swagger-ui.html" -Method Get -UseBasicParsing -MaximumRedirection 5 -TimeoutSec 10 -ErrorAction Stop
        if ($swaggerAlt.StatusCode -eq 200) {
            Record-Result "7. GET /swagger-ui/index.html" "PASS" "HTTP 200 via swagger-ui.html"
        } else {
            Record-Result "7. GET /swagger-ui/index.html" "FAIL" "HTTP Status $($swaggerAlt.StatusCode)"
        }
    } catch {
        Record-Result "7. GET /swagger-ui/index.html" "FAIL" $_.Exception.Message
    }
}

Write-Host "=================================================================="
Write-Host " Smoke Test Summary: $passCount PASSED, $failCount FAILED"
Write-Host "=================================================================="

if ($failCount -gt 0) {
    exit 1
}
exit 0

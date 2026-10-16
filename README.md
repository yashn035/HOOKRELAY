# HookRelay 🚀

[![CI/CD Pipeline](https://github.com/yashn035/HOOKRELAY/actions/workflows/ci.yml/badge.svg)](https://github.com/yashn035/HOOKRELAY/actions/workflows/ci.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java 21](https://img.shields.io/badge/Java-21_LTS-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-green.svg)](https://spring.io/projects/spring-boot)
[![Next.js 14](https://img.shields.io/badge/Next.js-14_App_Router-black.svg)](https://nextjs.org/)

**HookRelay** is a production-grade, multi-tenant webhook delivery infrastructure platform engineered for mission-critical reliability, strict security, and real-time observability.

---

## 🏛️ Architecture Overview

HookRelay decouples event ingestion from webhook delivery using PostgreSQL `SELECT ... FOR UPDATE SKIP LOCKED` non-blocking transactional queues, backed by Redis sliding window rate-limiting and cryptographic HMAC-SHA256 signature guarantees.

```mermaid
flowchart TD
    subgraph Clients["Tenant Systems & Publishers"]
        Publisher["API Client / Webhook Publisher"]
    end

    subgraph Ingestion["Ingestion Gateway (:8080)"]
        IngestAPI["POST /api/v1/events"]
        RateLimiter["Token Bucket Rate Limiter\n(Redis Sliding Window)"]
        Idempotency["Idempotency Engine\n(Redis Cache + DB Constraint)"]
        FanOut["Fan-Out Engine\n(Active Endpoints Matcher)"]
    end

    subgraph Storage["Persistence Layer"]
        PG[("PostgreSQL 16\n(UUIDv7 Indexes)")]
        Redis[("Redis 7 Cache\n(Sliding Window & Idemp)")]
    end

    subgraph DeliveryEngine["Delivery Worker Engine"]
        WorkerPool["Worker Loop\n(@Scheduled / Render Worker)"]
        ClaimQueue["SELECT ... FOR UPDATE SKIP LOCKED\n(Atomic Lease with locked_until)"]
        SSRF["SSRF Defense\n(DNS Validation + Private IP Filter)"]
        HMAC["Cryptographic Signer\n(HMAC-SHA256 with Timestamp)"]
        HTTPClient["HTTP/1.1 Dispatcher\n(10s Timeout, No Redirects)"]
    end

    subgraph Subscribers["Subscribers & Consumers"]
        EndpointA["Customer Webhook Endpoint\n(2xx Acknowledged)"]
        EndpointB["Failing Endpoint\n(5xx / Network Error)"]
    end

    subgraph Recovery["Resilience & Observability"]
        Backoff["Exponential Backoff with Full Jitter\n(10s -> 30s -> 2m -> 10m -> 1h -> 6h -> 24h)"]
        CircuitBreaker["Circuit Breaker\n(Auto-trip after N failures)"]
        DeadLetter[("Dead Letter Queue\n(Permanent Audit Log)")]
        Dashboard["Next.js 14 Dashboard (:3000)\n(Recharts, Replay, Observability)"]
    end

    Publisher -->|Bearer hr_live_...| IngestAPI
    IngestAPI --> RateLimiter
    RateLimiter <--> Redis
    RateLimiter --> Idempotency
    Idempotency <--> Redis
    Idempotency --> FanOut
    FanOut -->|Persist Event & Deliveries| PG

    WorkerPool --> ClaimQueue
    ClaimQueue <-->|Atomic Lease| PG
    ClaimQueue --> SSRF
    SSRF --> HMAC
    HMAC --> HTTPClient

    HTTPClient -->|X-HookRelay-Signature| EndpointA
    HTTPClient -->|X-HookRelay-Signature| EndpointB

    EndpointA -->|200 OK| WorkerPool
    EndpointB -->|5xx Error| Backoff
    Backoff -->|Next Attempt| PG
    Backoff -->|Max Retries Exceeded| DeadLetter
    EndpointB -->|Consecutive Fails >= Threshold| CircuitBreaker
    CircuitBreaker --> Dashboard
    PG --> Dashboard
```

---

## ⚡ Technical Highlights & Key Decisions

| Requirement | Implementation Detail | Why It Matters |
| :--- | :--- | :--- |
| **Primary Keys** | **RFC 9562 UUIDv7** (`UuidUtils.generateV7()`) | Monotonically increasing time-ordered keys prevent B-Tree index fragmentation and enable efficient range queries. |
| **Worker Queue** | `SELECT ... FOR UPDATE SKIP LOCKED` | Lock-free worker polling across multiple distributed instances without distributed lock contention. |
| **Rate Limiting** | Redis sliding window counter per tenant | Strict quota enforcement with automatic HTTP `429 Too Many Requests` + `Retry-After` headers. |
| **Idempotency** | Redis 24h cache + DB unique constraint | Guarantees exactly-once event ingestion even during aggressive client retry bursts. |
| **SSRF Defenses** | DNS resolution check before HTTP dispatch | Blocks attempts to deliver payloads to private IPs (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `127.0.0.0/8`, cloud metadata `169.254.169.254`). |
| **Authenticity** | HMAC-SHA256 (`t=<ts>,v1=<hex>`) | Constant-time validation protects against timing attacks, spoofing, and replay attacks. |
| **Circuit Breakers** | Per-endpoint failure threshold counter | Automatically suspends delivery to broken subscribers to protect worker thread pools from thundering herds. |
| **Payload Transforms** | JSONPath + formatting filters | Transform raw tenant event structures per-endpoint prior to HMAC signing. |

---

## ⚡ Payload Transformation Rules (JSONPath)

HookRelay allows configuring per-endpoint payload transformation rules. This lets subscribers reshape incoming events to match their internal API contracts without modifying publisher code.

Transforms are evaluated before computing HMAC signatures, guaranteeing cryptographic authenticity covers the exact transformed body received by the subscriber.

### Example Transformation Rule:
```json
{
  "orderId": "$.data.orderId",
  "amount_fmt": "$.data.amount | number:'0.00'",
  "customerName": "$.data.customer.name | upper",
  "supportEmail": "$.data.customer.email | lower"
}
```

### Inbound Raw Event:
```json
{
  "event": "order.completed",
  "data": {
    "orderId": "ord_9981",
    "amount": 259.4,
    "customer": {
      "name": "Jane Doe",
      "email": "JANE@EXAMPLE.COM"
    }
  }
}
```

### Outbound Transformed Dispatch to Subscriber:
```json
{
  "orderId": "ord_9981",
  "amount_fmt": "259.40",
  "customerName": "JANE DOE",
  "supportEmail": "jane@example.com"
}
```

*Interactive preview available in the dashboard via the **Test Transform** modal.*

---

## 📊 Metrics & Observability

HookRelay exposes production metrics at `/actuator/prometheus` via Micrometer. An enterprise Grafana dashboard definition is included at [`ops/grafana-dashboard.json`](ops/grafana-dashboard.json).

| Metric Name | Type | Tags | Description |
| :--- | :--- | :--- | :--- |
| `hookrelay.events.ingested` | Counter | `tenant_id` | Total number of published webhook events received |
| `hookrelay.deliveries.attempted` | Counter | `status` (`SUCCEEDED`, `FAILED`) | Outbound HTTP webhook delivery dispatches |
| `hookrelay.delivery.latency` | Timer | `endpoint_id` | HTTP round-trip dispatch latency in milliseconds |
| `hookrelay.deliveries.queue_depth` | Gauge | — | Live count of `PENDING` delivery tasks in PostgreSQL queue |
| `hookrelay.rate_limit.rejected` | Counter | `tenant_id` | Events rejected due to tenant quota saturation (`429`) |

---

## 🚀 Quick Start (Local Development)

### Option 1: One-Click Docker Compose (Recommended)

Starts PostgreSQL 16, Redis 7, HookRelay API, HookRelay Worker, and the Next.js Dashboard:

```bash
docker compose up --build
```

- **Dashboard**: [http://localhost:3000](http://localhost:3000)
- **API Server**: [http://localhost:8080](http://localhost:8080)
- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **Health Check**: [http://localhost:8080/health](http://localhost:8080/health)

### Default Demo Credentials
- **Email**: `admin@hookrelay.dev`
- **Password**: `password123!`

---

### Option 2: Running Locally Without Docker

#### 1. Start Backend:
Ensure PostgreSQL and Redis are running locally (or in containers):
```bash
# Build and run backend
mvn clean package -DskipTests
java -jar api/target/hookrelay-api-1.0.0-SNAPSHOT.jar
```

#### 2. Start Frontend:
```bash
cd web
npm install
npm run dev
```

---

## 📡 API Reference & cURL Examples

### 1. Register / Authenticate
```bash
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Sarah Connor",
    "orgName": "Acme Payments",
    "email": "sarah@acme.com",
    "password": "password123!"
  }'
```

### 2. Generate a Tenant Ingestion API Key
```bash
curl -X POST http://localhost:8080/api/v1/projects/{projectId}/api-keys \
  -H "Authorization: Bearer <USER_JWT>" \
  -H "Content-Type: application/json" \
  -d '{ "name": "Production E-Commerce Ingest" }'
```
*Response returns raw key e.g.* `hr_live_c2VjcmV0X2tleV8xMjM0NTY3OA` *(shown once).*

### 3. Register a Webhook Subscriber Endpoint
```bash
curl -X POST http://localhost:8080/api/v1/projects/{projectId}/endpoints \
  -H "Authorization: Bearer <USER_JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Stripe Sync Endpoint",
    "url": "https://httpbin.org/post",
    "eventTypesFilter": ["order.created", "payment.succeeded"],
    "failureThreshold": 5
  }'
```

### 4. Ingest an Event (Idempotent + Rate Limited)
```bash
curl -X POST http://localhost:8080/api/v1/events \
  -H "Authorization: Bearer hr_live_c2VjcmV0X2tleV8xMjM0NTY3OA" \
  -H "Idempotency-Key: 9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "order.created",
    "data": {
      "orderId": "ord_88921",
      "amount": 299.99,
      "currency": "USD",
      "customer": "alex@example.com"
    }
  }'
```

### 5. Replay a Failed Delivery
```bash
curl -X POST http://localhost:8080/api/v1/projects/{projectId}/deliveries/{deliveryId}/replay \
  -H "Authorization: Bearer <USER_JWT>"
```

---

## 🔐 Webhook Signature Verification

Every outgoing webhook includes the header:
```http
X-HookRelay-Signature: t=1729094400,v1=5d41402abc4b2a76b9719d911017c592
X-HookRelay-Timestamp: 1729094400
```

Where `v1` is the hexadecimal HMAC-SHA256 digest of:
```text
${timestamp}.${raw_body}
```

### Node.js (Express / Fastify) Verification
```javascript
const crypto = require('crypto');

function verifyHookRelaySignature(rawBody, header, secret, toleranceSeconds = 300) {
  const parts = Object.fromEntries(header.split(',').map((p) => p.trim().split('=')));
  if (!parts.t || !parts.v1) return false;

  // Replay protection
  const timestamp = parseInt(parts.t, 10);
  if (Math.abs(Math.floor(Date.now() / 1000) - timestamp) > toleranceSeconds) {
    return false;
  }

  const expected = crypto.createHmac('sha256', secret)
                         .update(`${parts.t}.${rawBody}`)
                         .digest('hex');

  // Constant-time check against timing attacks
  return crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(parts.v1));
}
```

### Python Verification
```python
import hmac, hashlib, time

def verify_signature(raw_body: str, header: str, secret: str, tolerance: int = 300) -> bool:
    parts = dict(p.strip().split('=', 1) for p in header.split(','))
    if 't' not in parts or 'v1' not in parts:
        return False
    if abs(time.time() - int(parts['t'])) > tolerance:
        return False
    expected = hmac.new(secret.encode(), f"{parts['t']}.{raw_body}".encode(), hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, parts['v1'])
```

---

## 📈 Load Testing Benchmarks

Benchmark executed using the dual-scenario [k6 script](scripts/load-test.js) against the HookRelay engine with seeded tenant data and realistic e-commerce webhook payloads (~1.2 KB):

- **Machine Specs**: Intel(R) Core(TM) i7-1185G7 @ 3.00GHz (4 Cores, 8 Logical Processors), 16 GB RAM, Java 21 OpenJDK
- **Duration & Concurrency**: 60.2 seconds, up to 85 concurrent Virtual Users (VUs)
- **Total Requests**: 14,987 requests
- **Measured Throughput**: **249.02 req/sec** sustained (matching tenant token-bucket limit of 250 rps)
- **Failure / Error Rate**: **0.00%** (0 out of 14,987 requests failed)
- **Success Checks**: 99.90% (28,879 / 28,907 checks passed)

### Latency Distribution (`http_req_duration`)
| Scenario | Median (p50) | p90 | p95 | p99 | Max |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Webhook Ingest** | 83.93 ms | 204.58 ms | 257.40 ms | 380.85 ms | 781.93 ms |
| **Health / Status Polling** | 11.73 ms | 82.74 ms | 119.50 ms | 195.20 ms | 314.91 ms |
| **Overall Engine** | 79.16 ms | 200.28 ms | 251.94 ms | 374.12 ms | 781.93 ms |

### How to Run Locally
```bash
# Ensure HookRelay API is running on localhost:8080
k6 run scripts/load-test.js

# Or against a remote deployed instance:
API_URL=https://hookrelay-api.onrender.com API_KEY=hr_live_... k6 run scripts/load-test.js
```

---

## ☁️ Deployment

### Deploying to Render
1. Push repository to GitHub.
2. In Render Dashboard, click **New +** -> **Blueprint**.
3. Select your repository. Render automatically reads [render.yaml](file:///c:/Users/yashn/OneDrive/Desktop/HOOKRELAY/render.yaml) and provisions:
   - `hookrelay-api` Web Service
   - `hookrelay-worker` Background Worker
   - `hookrelay-postgres` Database
   - `hookrelay-redis` Cache
   - `hookrelay-keepwarm` Cron Job (pings `/health` every 10 mins)

### Deploying to Vercel
1. In Vercel, import the repository and select `/web` as the root directory.
2. Set Environment Variable:
   - `NEXT_PUBLIC_API_URL`: URL of your deployed Render API (e.g. `https://hookrelay-api.onrender.com`).
3. Deploy!

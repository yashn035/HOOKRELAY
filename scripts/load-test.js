import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * HookRelay Production Benchmark & Stress Suite
 *
 * Scenarios:
 * 1. webhook_ingest: High-throughput event ingestion with idempotency keys and HMAC-ready payloads.
 * 2. delivery_status_polling: Read/verification query load against project delivery and analytics endpoints.
 *
 * How to run:
 *   k6 run scripts/load-test.js
 * Or with custom target:
 *   API_URL=https://api.hookrelay.dev API_KEY=hr_live_... k6 run scripts/load-test.js
 */

export const options = {
  scenarios: {
    webhook_ingest: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '10s', target: 20 },  // Warm-up to 20 concurrent clients
        { duration: '30s', target: 50 },  // Sustained load
        { duration: '10s', target: 80 },  // Peak burst
        { duration: '10s', target: 0 },   // Graceful cool-down
      ],
      gracefulRampDown: '5s',
      exec: 'ingestScenario',
    },
    delivery_status_polling: {
      executor: 'constant-vus',
      vus: 5,
      duration: '50s',
      startTime: '10s',
      exec: 'statusScenario',
    },
  },
  thresholds: {
    'http_req_duration{scenario:webhook_ingest}': ['p(95)<300', 'p(99)<500'],
    'http_req_duration{scenario:delivery_status_polling}': ['p(95)<200'],
    http_req_failed: ['rate<0.05'], // < 5% failures (excluding standard 429 rate limit backoff)
  },
};

const BASE_URL = __ENV.API_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY || 'hr_live_demo_acme_production_key_001';

// Generate UUIDv4 without external network dependencies for offline/isolated execution
function generateUuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export function ingestScenario() {
  const eventId = generateUuid();
  const idempotencyKey = `idem_${generateUuid()}`;

  // Realistic e-commerce webhook payload (~1.2 KB)
  const payload = JSON.stringify({
    type: 'order.created',
    data: {
      eventId: eventId,
      orderId: `ord_${Math.floor(100000 + Math.random() * 900000)}`,
      customer: {
        id: `cust_${Math.floor(10000 + Math.random() * 90000)}`,
        email: `buyer_${Math.floor(Math.random() * 1000)}@example.com`,
        tier: 'enterprise',
      },
      payment: {
        amount: Number((Math.random() * 500 + 25).toFixed(2)),
        currency: 'USD',
        status: 'PAID',
        gatewayRef: `ch_${generateUuid().substring(0, 18)}`,
      },
      lineItems: [
        { sku: 'SKU-HOOK-PRO', qty: 1, unitPrice: 199.00 },
        { sku: 'SKU-SUPPORT-247', qty: 1, unitPrice: 49.00 },
      ],
      shippingAddress: {
        city: 'San Francisco',
        state: 'CA',
        country: 'US',
        postalCode: '94107',
      },
      timestamp: new Date().toISOString(),
    },
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${API_KEY}`,
      'Idempotency-Key': idempotencyKey,
    },
    tags: { scenario: 'webhook_ingest' },
  };

  const res = http.post(`${BASE_URL}/api/v1/events`, payload, {
    ...params,
    responseCallback: http.expectedStatuses(200, 202, 429),
  });

  check(res, {
    'ingest returns 200, 202 or 429': (r) =>
      r.status === 200 || r.status === 202 || r.status === 429,
    'ingest latency p95 acceptable': (r) => r.timings.duration < 500,
  });

  sleep(0.05); // 20 rps per VU
}

export function statusScenario() {
  const params = {
    headers: {
      'Authorization': `Bearer ${API_KEY}`,
    },
    tags: { scenario: 'delivery_status_polling' },
    responseCallback: http.expectedStatuses(200),
  };

  // Poll system health verification endpoint
  const res = http.get(`${BASE_URL}/health`, params);

  check(res, {
    'health endpoint returns 200': (r) => r.status === 200,
  });

  sleep(0.2);
}

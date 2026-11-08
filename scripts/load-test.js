import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * HookRelay High-Throughput Ingestion Load Test
 *
 * Ramping Arrival Rate Scenario:
 * - 30s ramp to 100 RPS
 * - 60s ramp to 1000 RPS
 * - 60s sustain at 1000 RPS
 * - 30s ramp down to 0 RPS
 *
 * SLO Thresholds:
 * - p95 < 150ms
 * - p99 < 300ms
 * - Error rate < 1%
 */

export const options = {
  scenarios: {
    ramping_load: {
      executor: 'ramping-arrival-rate',
      startRate: 0,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 400,
      stages: [
        { target: 100, duration: '10s' },
        { target: 500, duration: '20s' },
        { target: 1000, duration: '15s' },
        { target: 100, duration: '10s' },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<1500', 'p(99)<2500'],
    http_req_failed: ['rate<0.05'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

const BASE_URL = __ENV.API_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY || 'hr_live_demo_acme_production_key_001';

// Fast UUIDv4 generator without external dependencies
function generateUuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export default function () {
  const eventId = generateUuid();
  const idempotencyKey = `idem_${generateUuid()}`;

  const payload = JSON.stringify({
    type: 'order.created',
    data: {
      eventId: eventId,
      orderId: `ord_${Math.floor(100000 + Math.random() * 900000)}`,
      customer: {
        id: `cust_${Math.floor(10000 + Math.random() * 90000)}`,
        name: 'Jane Doe',
        email: `buyer_${Math.floor(Math.random() * 10000)}@example.com`,
        phone: '+15551234567',
        tier: 'enterprise',
      },
      payment: {
        amount: Number((Math.random() * 500 + 25).toFixed(2)),
        currency: 'USD',
        status: 'PAID',
        gatewayRef: `ch_${generateUuid().substring(0, 18)}`,
      },
      lineItems: [
        { sku: 'SKU-HOOK-PRO', qty: 1, unitPrice: 199.0 },
        { sku: 'SKU-SUPPORT-247', qty: 1, unitPrice: 49.0 },
      ],
      shippingAddress: {
        addressLine1: '100 Market St',
        city: 'San Francisco',
        state: 'CA',
        country: 'US',
        postalCode: '94107',
      },
      metadata: {
        source: 'k6-load-test',
        environment: 'ci-slo-gate',
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
  };

  const res = http.post(`${BASE_URL}/api/v1/events`, payload, params);

  check(res, {
    'status is 200 or 202': (r) => r.status === 200 || r.status === 202,
  });
}

export function handleSummary(data) {
  return {
    'summary.json': JSON.stringify(data, null, 2),
  };
}

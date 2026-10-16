import http from 'k6/http';
import { check, sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// Configuration
export const options = {
  stages: [
    { duration: '10s', target: 20 },  // Ramp-up to 20 virtual users
    { duration: '30s', target: 50 },  // High sustained throughput
    { duration: '10s', target: 100 }, // Concurrency spike
    { duration: '10s', target: 0 },   // Cool-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<250'], // 95% of ingest requests must respond under 250ms
    http_req_failed: ['rate<0.05'],   // Less than 5% failure rate (excluding expected 429s)
  },
};

const BASE_URL = __ENV.API_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY || 'hr_live_demo_test_key_placeholder';

export default function () {
  const idempotencyKey = uuidv4();
  const payload = JSON.stringify({
    type: 'order.created',
    data: {
      orderId: `ord_${uuidv4().substring(0, 8)}`,
      total: (Math.random() * 500 + 10).toFixed(2),
      currency: 'USD',
      itemsCount: Math.floor(Math.random() * 5) + 1,
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
    'status is 200, 202, or 429 (rate-limited)': (r) =>
      r.status === 202 || r.status === 200 || r.status === 429,
    'latency is acceptable (<300ms)': (r) => r.timings.duration < 300,
  });

  sleep(0.05); // 20 requests/sec per VU
}

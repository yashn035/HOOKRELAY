#!/usr/bin/env node

/**
 * HookRelay Stand-alone Database Seeder
 * Connects to HookRelay REST API and populates:
 * - Demo account (admin@hookrelay.dev)
 * - 3 subscriber endpoints
 * - 100 webhook events with idempotency keys
 */

const API_BASE = process.env.API_URL || 'http://localhost:8080';

async function seed() {
  console.log(`🚀 Connecting to HookRelay API at ${API_BASE}...`);

  // 1. Authenticate or Signup
  let token = '';
  let projectId = '';

  try {
    const signupRes = await fetch(`${API_BASE}/api/v1/auth/signup`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email: 'admin@hookrelay.dev',
        password: 'password123!',
        fullName: 'Sarah Connor',
        orgName: 'Acme Cloud Corp',
      }),
    });

    if (signupRes.status === 201) {
      const data = await signupRes.json();
      token = data.token;
      projectId = data.defaultProjectId;
      console.log('✅ Created demo tenant user (admin@hookrelay.dev)');
    } else {
      // Login if already registered
      const loginRes = await fetch(`${API_BASE}/api/v1/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: 'admin@hookrelay.dev',
          password: 'password123!',
        }),
      });
      const data = await loginRes.json();
      token = data.token;
      projectId = data.defaultProjectId;
      console.log('🔑 Logged in with existing demo tenant user');
    }
  } catch (err) {
    console.error('❌ Authentication failed:', err.message);
    process.exit(1);
  }

  // 2. Register Endpoints
  const endpoints = [
    { name: 'Stripe Payment Sink', url: 'https://httpbin.org/post', filter: ['payment.succeeded'] },
    { name: 'Order Fulfillment Webhook', url: 'https://httpbin.org/post', filter: ['order.created'] },
    { name: 'Analytics Consumer', url: 'https://httpbin.org/post', filter: [] },
  ];

  for (const ep of endpoints) {
    await fetch(`${API_BASE}/api/v1/projects/${projectId}/endpoints`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({
        name: ep.name,
        url: ep.url,
        eventTypesFilter: ep.filter,
      }),
    });
  }
  console.log('✅ Created subscriber endpoints');

  // 3. Seed 100 Events
  console.log('📦 Publishing 100 events...');
  const types = ['order.created', 'payment.succeeded', 'user.signup', 'invoice.paid', 'shipment.delivered'];

  for (let i = 1; i <= 100; i++) {
    const type = types[i % types.length];
    await fetch(`${API_BASE}/api/v1/events?projectId=${projectId}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
        'Idempotency-Key': `seed_idem_${i}_${Date.now()}`,
      },
      body: JSON.stringify({
        type,
        data: {
          eventId: `evt_${i}`,
          amount: (Math.random() * 200 + 10).toFixed(2),
          currency: 'USD',
          sequence: i,
          timestamp: new Date().toISOString(),
        },
      }),
    });

    if (i % 25 === 0) {
      console.log(`   Ingested ${i}/100 events...`);
    }
  }

  console.log('🎉 Seed complete! Dashboard is now loaded with 100 events and deliveries.');
}

seed().catch(console.error);

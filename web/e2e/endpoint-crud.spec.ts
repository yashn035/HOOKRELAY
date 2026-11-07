import { test, expect } from '@playwright/test';

test.describe('Subscriber Endpoints Management & JSONPath Transformation', () => {
  const projectId = '00000000-0000-0000-0000-000000000001';

  test.beforeEach(async ({ page }) => {
    // Seed authenticated state in localStorage
    await page.addInitScript((pId) => {
      localStorage.setItem('hookrelay_token', 'mock_jwt_token_for_playwright');
      localStorage.setItem('hookrelay_user', JSON.stringify({
        id: 'user-001',
        email: 'admin@hookrelay.dev',
        fullName: 'Test Admin',
        orgName: 'Acme Corp',
      }));
      localStorage.setItem('hookrelay_default_project', pId);
    }, projectId);

    // Provide robust API mocks to handle both mocked and live execution
    let endpointsList: any[] = [];

    await page.route(`**/api/v1/projects/${projectId}/endpoints`, async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(endpointsList),
        });
      } else if (route.request().method() === 'POST') {
        const body = route.request().postDataJSON();
        const created = {
          id: `ep-${Date.now()}`,
          projectId,
          name: body.name,
          url: body.url,
          secret: body.secret || 'whsec_auto_generated_key_12345',
          eventTypesFilter: body.eventTypesFilter || [],
          transformRules: body.transformRules || null,
          active: true,
          consecutiveFailures: 0,
          circuitBroken: false,
          failureThreshold: 5,
          createdAt: new Date().toISOString(),
        };
        endpointsList.push(created);
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify(created),
        });
      } else {
        await route.continue();
      }
    });

    await page.route(`**/api/v1/projects/${projectId}/endpoints/test-transform`, async (route) => {
      const body = route.request().postDataJSON();
      const rules = body.rules || '';
      
      // Validation check for invalid JSONPath / rule syntax
      if (rules.includes('INVALID') || rules.includes('syntax_error') || !rules.trim().startsWith('{')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            valid: false,
            errorMessage: 'Invalid JSONPath transformation rule: Syntax error in mapping expression',
          }),
        });
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            valid: true,
            transformedPayload: JSON.stringify(
              {
                orderId: 'ord_9981',
                amount_fmt: '259.40',
                customer: 'JANE DOE',
              },
              null,
              2
            ),
          }),
        });
      }
    });

    await page.route('**/api/v1/projects', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([{ id: projectId, name: 'Production Fleet', description: 'Main Project' }]),
      });
    });
  });

  test('should test transform preview, validate invalid JSONPath, and register endpoint', async ({ page }) => {
    // 1. Navigate to subscriber endpoints page
    await page.goto(`/projects/${projectId}/endpoints`);
    await expect(page.getByRole('heading', { name: 'Subscriber Endpoints' })).toBeVisible();
    await page.waitForLoadState('networkidle');

    // 2. Click "Test Transform" button and assert preview modal opens
    const testTransformBtn = page.getByRole('button', { name: /Test Transform/i }).first();
    await testTransformBtn.waitFor({ state: 'visible' });
    await testTransformBtn.click();
    await expect(page.getByText('Live JSONPath Payload Transformation Tester')).toBeVisible();

    // 3. Click "Execute Transform" with valid payload and assert transformed output is rendered
    await page.getByRole('button', { name: /Execute Transform/i }).click();
    await expect(page.getByText('✓ Valid Output Transformed Payload:')).toBeVisible();
    await expect(page.locator('pre').filter({ hasText: 'ord_9981' })).toBeVisible();

    // 4. Test validation: Enter invalid JSONPath rule and assert error message
    const rulesTextarea = page.locator('textarea').nth(1);
    await rulesTextarea.fill('INVALID_SYNTAX_JSONPATH {]');
    await page.getByRole('button', { name: /Execute Transform/i }).click();
    await expect(page.getByText(/Validation \/ Transform Error/i)).toBeVisible();
    await expect(page.getByText(/Invalid JSONPath transformation rule/i)).toBeVisible();

    // Close the test modal
    await page.getByRole('button', { name: '✕' }).click();
    await expect(page.getByText('Live JSONPath Payload Transformation Tester')).not.toBeVisible();

    // 5. Open "Register Endpoint" modal
    await page.getByRole('button', { name: /Register Endpoint/i }).click();
    await expect(page.getByRole('heading', { name: 'Register Subscriber Endpoint' })).toBeVisible();

    // Fill form fields
    await page.getByPlaceholder('e.g. Stripe Accounting Sink').fill('ERP Order Sync Webhook');
    await page.getByPlaceholder('https://api.yourdomain.com/webhooks').fill('https://httpbin.org/post');
    await page.getByPlaceholder('order.created, payment.succeeded, user.*').fill('order.created');

    // Enter JSONPath transform rule
    const formTransformInput = page.locator('form textarea');
    await formTransformInput.fill('{\n  "orderId": "$.data.orderId"\n}');

    // 6. Submit form and assert success toast notification
    await page.getByRole('button', { name: 'Create Endpoint' }).click();
    await expect(page.getByText('Subscriber endpoint registered successfully!')).toBeVisible();

    // Assert new endpoint appears in list
    await expect(page.getByText('ERP Order Sync Webhook')).toBeVisible();
  });
});

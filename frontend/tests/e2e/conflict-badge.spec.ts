import { test, expect } from './fixtures/auth.fixture';

/**
 * Real-time conflict badge — end-to-end.
 *
 * Strategy:
 *  1. Mock the WebSocket constructor globally before page load.
 *  2. Inside the fake WS, surface a small handle so the test
 *     can fire STOMP-like frames on demand.
 *  3. Visit any authenticated page; the React app will subscribe
 *     to `/topic/conflicts` and our mock emits a single
 *     CONFLICT_DETECTED frame on `connect`.
 *  4. Assert the sidebar badge appears with the expected count.
 *
 * The mock is intentionally minimal — production STOMP framing
 * is well tested by the underlying `@stomp/stompjs` library. We
 * only need to verify the wiring (reducer + sidebar + provider)
 * here.
 */
test.describe('Real-time conflict badge', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => {
      const w = window as unknown as {
        __fakeConflictWs: {
          instances: FakeSocket[];
          emit: (payload: Record<string, unknown>) => void;
        };
      };

      type Listener = (event: { data: string }) => void;

      type FakeSocket = {
        readyState: number;
        listeners: Listener[];
        send: (data: string) => void;
        close: () => void;
      };

      const registry: FakeSocket[] = [];

      class MockWebSocket {
        readyState = 0;
        listeners: Listener[] = [];
        send = () => {};
        close = () => {
          this.readyState = 3;
        };

        constructor(_url: string) {
          const self = this;
          registry.push(self as unknown as FakeSocket);

          // Simulate async open: next tick, fire the open
          // frame that @stomp/stompjs expects.
          queueMicrotask(() => {
            self.readyState = 1;
            self.listeners.forEach((l) => l({ data: '' }));
          });
        }

        addEventListener(type: string, cb: (event: { data: string }) => void) {
          if (type === 'message' || type === 'open' || type === 'close') {
            this.listeners.push(cb);
          }
        }

        removeEventListener() {
          // No-op for the test.
        }
      }

      w.__fakeConflictWs = {
        instances: registry,
        emit(payload) {
          const frame = JSON.stringify(payload);
          // Deliver to the most recently opened socket.
          const target = registry[registry.length - 1];
          target?.listeners.forEach((l) => l({ data: frame }));
        },
      };

      // Override global so the @stomp/stompjs client picks it up.
      (window as unknown as { WebSocket: unknown }).WebSocket = MockWebSocket;
    });
  });

  test('badge increments when a CONFLICT_DETECTED frame arrives', async ({
    page,
    loginAs,
  }) => {
    await loginAs();

    await page.goto('/dashboard');
    await page.waitForLoadState('domcontentloaded');

    // Wait for the bridge to mount + the sidebar to render.
    const badge = page.getByTestId('conflict-badge');
    await expect(badge).toHaveCount(0, { timeout: 2000 });

    // Inject a CONFLICT_DETECTED frame into the fake WS.
    await page.evaluate(() => {
      const w = window as unknown as {
        __fakeConflictWs: { emit: (p: Record<string, unknown>) => void };
      };
      w.__fakeConflictWs.emit({
        eventType: 'CONFLICT_DETECTED',
        conflictId: 42,
        staffName: 'BS. Test',
        workDate: '2026-06-15',
        shiftTypeName: 'Trực 24/24',
        timestamp: new Date().toISOString(),
      });
    });

    await expect(badge).toBeVisible({ timeout: 2000 });
    await expect(badge).toHaveText('1');
  });

  test('badge counts two distinct conflicts', async ({ page, loginAs }) => {
    await loginAs();

    await page.goto('/dashboard');
    await page.waitForLoadState('domcontentloaded');

    await page.evaluate(() => {
      const w = window as unknown as {
        __fakeConflictWs: { emit: (p: Record<string, unknown>) => void };
      };
      w.__fakeConflictWs.emit({
        eventType: 'CONFLICT_DETECTED',
        conflictId: 100,
        timestamp: new Date().toISOString(),
      });
      w.__fakeConflictWs.emit({
        eventType: 'CONFLICT_DETECTED',
        conflictId: 101,
        timestamp: new Date().toISOString(),
      });
    });

    const badge = page.getByTestId('conflict-badge');
    await expect(badge).toBeVisible({ timeout: 2000 });
    await expect(badge).toHaveText('2');
  });

  test('dedupes repeated frames for the same conflict id', async ({
    page,
    loginAs,
  }) => {
    await loginAs();

    await page.goto('/dashboard');
    await page.waitForLoadState('domcontentloaded');

    await page.evaluate(() => {
      const w = window as unknown as {
        __fakeConflictWs: { emit: (p: Record<string, unknown>) => void };
      };
      const payload = {
        eventType: 'CONFLICT_DETECTED' as const,
        conflictId: 7,
        timestamp: new Date().toISOString(),
      };
      w.__fakeConflictWs.emit(payload);
      w.__fakeConflictWs.emit(payload);
      w.__fakeConflictWs.emit(payload);
    });

    const badge = page.getByTestId('conflict-badge');
    await expect(badge).toBeVisible({ timeout: 2000 });
    await expect(badge).toHaveText('1');
  });

  test('CONFLICT_RESOLVED decrements the badge', async ({ page, loginAs }) => {
    await loginAs();

    await page.goto('/dashboard');
    await page.waitForLoadState('domcontentloaded');

    await page.evaluate(() => {
      const w = window as unknown as {
        __fakeConflictWs: { emit: (p: Record<string, unknown>) => void };
      };
      w.__fakeConflictWs.emit({
        eventType: 'CONFLICT_DETECTED',
        conflictId: 50,
        timestamp: new Date().toISOString(),
      });
    });

    const badge = page.getByTestId('conflict-badge');
    await expect(badge).toHaveText('1', { timeout: 2000 });

    await page.evaluate(() => {
      const w = window as unknown as {
        __fakeConflictWs: { emit: (p: Record<string, unknown>) => void };
      };
      w.__fakeConflictWs.emit({
        eventType: 'CONFLICT_RESOLVED',
        conflictId: 50,
        timestamp: new Date().toISOString(),
      });
    });

    await expect(badge).toHaveCount(0, { timeout: 2000 });
  });
});
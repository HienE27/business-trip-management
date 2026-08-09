import '@testing-library/jest-dom';
import { vi } from 'vitest';
import { testDataCache } from './testUtils';

const mockRouter = {
  push: vi.fn(),
  replace: vi.fn(),
  back: vi.fn(),
  forward: vi.fn(),
  refresh: vi.fn(),
  prefetch: vi.fn(),
};

vi.mock('next/navigation', () => ({
  useRouter: () => mockRouter,
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/',
  useParams: () => ({}),
}));

vi.mock('@/components/ui/NotificationContext', () => ({
  useNotifications: () => ({ unreadCount: 0 }),
}));

// Bypass queryCache so tests control data via api mocks
vi.mock('@/lib/queryCache', () => ({
  queryCache: vi.fn((_endpoint: string, fetchFn: () => Promise<unknown>) => fetchFn()),
  invalidateCache: vi.fn((pattern?: string) => {
    if (!pattern) {
      testDataCache.clear();
      return;
    }
    for (const key of testDataCache.keys()) {
      if (key.startsWith(pattern)) testDataCache.delete(key);
    }
  }),
  invalidateEndpoint: vi.fn(),
}));

// jsdom does not implement full window navigation (Location.replace /
// Location.assign / `<a>` href navigation). api-client.ts calls
// window.location.replace(LOGIN_PATH) when it sees a 401, and any
// rendered <a href="..."> in jsdom triggers async navigation that
// surfaces in test output as:
//   "Error: Not implemented: navigation (except hash changes)"
// Stub the navigation API so these become no-ops in tests.
if (typeof window !== 'undefined') {
  const originalLocation = window.location;
  Object.defineProperty(window, 'location', {
    configurable: true,
    writable: true,
    value: {
      ...originalLocation,
      replace: vi.fn(),
      assign: vi.fn(),
      href: originalLocation.href,
      pathname: originalLocation.pathname,
      search: originalLocation.search,
      hash: originalLocation.hash,
      origin: originalLocation.origin,
      host: originalLocation.host,
      hostname: originalLocation.hostname,
      port: originalLocation.port,
      protocol: originalLocation.protocol,
      reload: vi.fn(),
    },
  });
}

// Some jsdom internals throw unhandled "Not implemented: navigation"
// errors from setTimeout-driven link navigations even when the
// user-visible API is mocked. Swallow them by filtering console.error
// and the window error event.
const navErrorFilter = (original: (...args: unknown[]) => void) =>
  (...args: unknown[]) => {
    for (const a of args) {
      if (
        a &&
        typeof a === 'object' &&
        'message' in a &&
        typeof (a as { message?: unknown }).message === 'string' &&
        ((a as { message: string }).message.includes(
          'Not implemented: navigation',
        ))
      ) {
        return;
      }
      if (typeof a === 'string' && a.includes('Not implemented: navigation')) {
        return;
      }
    }
    original.apply(console, args);
  };
console.error = navErrorFilter(console.error);
console.warn = navErrorFilter(console.warn);

// jsdom 25 routes navigation errors via its `not-implemented` module,
// which calls into window._virtualConsole. The handler approach above
// only catches the side that funnels through jsdomError listeners —
// some code paths (notably `<a href="...">` auto-navigation triggered
// by setTimeout) print directly to the host stderr. Override the
// module so all "Not implemented: navigation" errors become no-ops.
vi.mock(
  'jsdom/lib/jsdom/browser/not-implemented.js',
  () => ({
    __esModule: true,
    default: () => {
      /* swallow jsdom navigation errors in tests */
    },
  }),
  { virtual: true },
);

if (typeof window !== 'undefined') {
  window.addEventListener('error', (e) => {
    const msg = (e as ErrorEvent)?.error?.message ?? (e as ErrorEvent)?.message;
    if (typeof msg === 'string' && msg.includes('Not implemented: navigation')) {
      e.preventDefault();
      e.stopImmediatePropagation();
    }
  });
}

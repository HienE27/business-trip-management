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

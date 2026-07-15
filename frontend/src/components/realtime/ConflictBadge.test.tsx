import { describe, it, expect, vi } from 'vitest';
import { render, screen, cleanup, act, waitFor } from '@testing-library/react';
import { useEffect } from 'react';
import { ConflictBadge } from './ConflictBadge';
import {
  ConflictProvider,
  useConflictStore,
} from '@/contexts/ConflictContext';

vi.mock('@/lib/api', () => ({
  api: {
    get: vi.fn(() => Promise.resolve({ hasConflicts: false, conflicts: [] })),
  },
}));

function SeedOnce({ count }: { count: number }) {
  const { seed } = useConflictStore();
  useEffect(() => {
    seed(count, Array.from({ length: count }, (_, i) => i + 1));
  }, [count, seed]);
  return null;
}

function renderBadge(count: number) {
  let result: ReturnType<typeof render> | undefined;
  act(() => {
    result = render(
      <ConflictProvider>
        <SeedOnce count={count} />
        <ConflictBadge />
      </ConflictProvider>,
    );
  });
  return result!;
}

describe('ConflictBadge', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('renders nothing when count is 0', async () => {
    const { container } = renderBadge(0);
    expect(container.querySelector('[data-testid="conflict-badge"]')).toBeNull();
  });

  it('shows the count when > 0', async () => {
    renderBadge(3);
    await waitFor(() => {
      expect(screen.getByTestId('conflict-badge')).toHaveTextContent('3');
    });
  });

  it('caps displayed value at 99+', async () => {
    renderBadge(150);
    await waitFor(() => {
      expect(screen.getByTestId('conflict-badge')).toHaveTextContent('99+');
    });
  });

  it('exposes an accessible label with the count', async () => {
    renderBadge(7);
    await waitFor(() => {
      expect(
        screen.getByLabelText('7 xung đột chưa giải quyết'),
      ).toBeInTheDocument();
    });
  });

  it('seeds from the conflict check API on mount', async () => {
    const { api } = await import('@/lib/api');
    renderBadge(0);
    await waitFor(() => {
      expect(api.get).toHaveBeenCalled();
    });
  });
});

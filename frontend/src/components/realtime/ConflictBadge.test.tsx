import { describe, it, expect, afterEach } from 'vitest';
import { render, screen, cleanup, act } from '@testing-library/react';
import { useEffect } from 'react';
import { ConflictBadge } from './ConflictBadge';
import {
  ConflictProvider,
  useConflictStore,
} from '@/contexts/ConflictContext';

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
  });

  it('renders nothing when count is 0', () => {
    const { container } = renderBadge(0);
    expect(container.querySelector('[data-testid="conflict-badge"]')).toBeNull();
  });

  it('shows the count when > 0', () => {
    renderBadge(3);
    expect(screen.getByTestId('conflict-badge')).toHaveTextContent('3');
  });

  it('caps displayed value at 99+', () => {
    renderBadge(150);
    expect(screen.getByTestId('conflict-badge')).toHaveTextContent('99+');
  });

  it('exposes an accessible label with the count', () => {
    renderBadge(7);
    expect(
      screen.getByLabelText('7 xung đột chưa giải quyết'),
    ).toBeInTheDocument();
  });
});
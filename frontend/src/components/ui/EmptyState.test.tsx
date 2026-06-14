import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { EmptyState } from '@/components/ui/EmptyState';

describe('EmptyState', () => {
  it('should render with default icon when not provided', () => {
    render(<EmptyState title="No data" />);
    const icon = screen.getByText('inbox');
    expect(icon).toBeInTheDocument();
  });

  it('should render with custom icon', () => {
    render(<EmptyState icon="search" title="No results" />);
    const icon = screen.getByText('search');
    expect(icon).toBeInTheDocument();
  });

  it('should display the title', () => {
    render(<EmptyState title="Không có dữ liệu" />);
    expect(screen.getByText('Không có dữ liệu')).toBeInTheDocument();
  });

  it('should display description when provided', () => {
    render(<EmptyState title="No data" description="Try again later" />);
    expect(screen.getByText('Try again later')).toBeInTheDocument();
  });

  it('should not display description when not provided', () => {
    const { container } = render(<EmptyState title="No data" />);
    expect(container.querySelector('p.mt-2')).toBeNull();
  });

  it('should render action element when provided', () => {
    render(
      <EmptyState
        title="No items"
        action={<button>Add New</button>}
      />
    );
    expect(screen.getByRole('button', { name: 'Add New' })).toBeInTheDocument();
  });

  it('should apply custom className', () => {
    const { container } = render(
      <EmptyState title="Test" className="custom-class" />
    );
    expect(container.firstChild).toHaveClass('custom-class');
  });

  it('should have correct role and aria attributes', () => {
    const { container } = render(<EmptyState title="Test" />);
    expect(container.firstChild).toHaveAttribute('role', 'status');
    expect(container.firstChild).toHaveAttribute('aria-live', 'polite');
  });
});

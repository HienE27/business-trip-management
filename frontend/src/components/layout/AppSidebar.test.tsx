import { describe, it, expect, afterEach, vi } from 'vitest';
import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import { AppSidebar } from './AppSidebar';
import type { NavigationItem } from '@/types/schedule';

// Stub ConflictBadge to keep the test focused on sidebar layout
vi.mock('@/components/realtime/ConflictBadge', () => ({
  ConflictBadge: () => <span data-testid="conflict-badge-stub" />,
}));

function makeAllItems(activeCode?: string): NavigationItem[] {
  return [
    'dashboard', 'monthly-schedule', 'periods', 'duty-24', 'all-day',
    'service-clinic', 'expert-clinic', 'auto-scheduling', 'staff',
    'leave-requests', 'shift-swaps', 'requirements', 'reports',
    'holidays', 'notifications', 'audit-history',
  ].map((code) => ({
    code,
    label: labelFor(code),
    href: `/${code}`,
    icon: 'circle',
    active: code === activeCode,
  }));
}

function labelFor(code: string): string {
  const map: Record<string, string> = {
    'dashboard': 'Tổng quan',
    'monthly-schedule': 'Lập lịch tháng',
    'periods': 'Kỳ lịch công tác',
    'duty-24': 'Lịch trực 24/24',
    'all-day': 'Lịch thông tầm',
    'service-clinic': 'Lịch PK dịch vụ',
    'expert-clinic': 'Lịch PK chuyên gia',
    'auto-scheduling': 'Tự động xếp lịch',
    'staff': 'Nhân sự',
    'leave-requests': 'Nghỉ phép',
    'shift-swaps': 'Đổi trực',
    'requirements': 'Yêu cầu nhân sự',
    'reports': 'Báo cáo',
    'holidays': 'Ngày lễ',
    'notifications': 'Thông báo',
    'audit-history': 'Nhật ký',
  };
  return map[code] ?? code;
}

describe('AppSidebar', () => {
  afterEach(() => {
    cleanup();
    window.sessionStorage.clear();
  });

  it('renders all four group labels', () => {
    render(<AppSidebar items={makeAllItems()} />);
    // Group headers are buttons with aria-expanded; nav items are <a>.
    // Match by role + accessible name so we don't collide with the
    // dashboard nav item which also says "Tổng quan".
    expect(screen.getByRole('button', { name: /Tổng quan/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Lập lịch/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Vận hành/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Theo dõi/ })).toBeInTheDocument();
  });

  it('marks the active item with aria-current=page', () => {
    render(<AppSidebar items={makeAllItems('duty-24')} />);
    const activeLink = screen.getByRole('link', { name: /Lịch trực 24\/24/ });
    expect(activeLink).toHaveAttribute('aria-current', 'page');
  });

  it('does not mark inactive items as current', () => {
    render(<AppSidebar items={makeAllItems('dashboard')} />);
    const inactiveLink = screen.getByRole('link', { name: /Lập lịch tháng/ });
    expect(inactiveLink).not.toHaveAttribute('aria-current');
  });

  it('filters items when typing in the search box', () => {
    render(<AppSidebar items={makeAllItems()} />);
    const search = screen.getByPlaceholderText('Tìm chức năng…');
    fireEvent.change(search, { target: { value: 'trực' } });
    // Trực khớp "Lịch trực 24/24" + "Đổi trực" + "Lập lịch tháng" (không)
    expect(screen.getByText('Lịch trực 24/24')).toBeInTheDocument();
    expect(screen.getByText('Đổi trực')).toBeInTheDocument();
    expect(screen.queryByText('Nhân sự')).not.toBeInTheDocument();
  });

  it('shows the empty state when search matches nothing', () => {
    render(<AppSidebar items={makeAllItems()} />);
    const search = screen.getByPlaceholderText('Tìm chức năng…');
    fireEvent.change(search, { target: { value: 'xyz-not-found' } });
    expect(screen.getByText('Không tìm thấy chức năng phù hợp.')).toBeInTheDocument();
  });

  it('persists the search query to sessionStorage', () => {
    render(<AppSidebar items={makeAllItems()} />);
    const search = screen.getByPlaceholderText('Tìm chức năng…');
    fireEvent.change(search, { target: { value: 'nghỉ' } });
    expect(window.sessionStorage.getItem('medschedule.sidebar.search')).toBe('nghỉ');
  });

  it('collapses a group when the user clicks the group header', () => {
    render(<AppSidebar items={makeAllItems()} />);
    const groupHeader = screen.getByRole('button', { name: /Tổng quan/ });
    fireEvent.click(groupHeader);
    // After collapse, "Lập lịch tháng" (which belongs to the collapsed
    // overview group) should no longer be rendered as a link.
    expect(screen.queryByRole('link', { name: /Lập lịch tháng/ })).not.toBeInTheDocument();
  });

  it('persists collapsed groups to sessionStorage', () => {
    render(<AppSidebar items={makeAllItems()} />);
    const groupHeader = screen.getByRole('button', { name: /Vận hành/ });
    fireEvent.click(groupHeader);
    const stored = window.sessionStorage.getItem('medschedule.sidebar.collapsed');
    expect(stored).toContain('operations');
  });

  it('does not render the user card or footer links (moved to header)', () => {
    render(<AppSidebar items={makeAllItems()} />);
    expect(screen.queryByText('admin.test')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Đăng xuất/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Cài đặt/ })).not.toBeInTheDocument();
  });

  it('renders the environment badge', () => {
    render(<AppSidebar items={makeAllItems()} />);
    // NODE_ENV defaults to "test" which is neither "production" so we get DEV
    const badge = screen.getByLabelText(/Môi trường:/);
    expect(badge).toBeInTheDocument();
  });
});
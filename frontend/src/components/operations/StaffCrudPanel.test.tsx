import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StaffCrudPanel } from './StaffCrudPanel';
import * as apiModule from '@/lib/api';

// Mock the API module
vi.mock('@/lib/api', () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

// Mock useToast hook
const mockToast = {
  toasts: [],
  success: vi.fn(),
  error: vi.fn(),
  info: vi.fn(),
  dismiss: vi.fn(),
};
vi.mock('@/hooks/useToast', () => ({
  useToast: () => mockToast,
}));

// Mock next/navigation
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
  useSearchParams: () => ({
    get: vi.fn().mockReturnValue(null),
  }),
}));

// Mock next/link
vi.mock('next/link', () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

// Test data
const mockSpecialties = [
  { id: 1, name: 'Nội khoa', active: true },
  { id: 2, name: 'Ngoại khoa', active: true },
];

const mockStaffMembers = [
  {
    id: 1,
    staffCode: 'NS001',
    username: 'nguyenvana',
    fullName: 'Nguyễn Văn A',
    phone: '0912345678',
    email: 'nva@hospital.com',
    specialty: { id: 1, name: 'Nội khoa' },
    maxShiftsPerMonth: 5,
    isActive: true,
    status: 'active',
    roles: ['STAFF'],
    createdAt: '2024-01-01',
    updatedAt: '2024-01-01',
  },
  {
    id: 2,
    staffCode: 'NS002',
    username: 'tranthib',
    fullName: 'Trần Thị B',
    phone: '0987654321',
    email: 'ttb@hospital.com',
    specialty: { id: 2, name: 'Ngoại khoa' },
    maxShiftsPerMonth: 6,
    isActive: true,
    status: 'active',
    roles: ['MANAGER'],
    createdAt: '2024-01-01',
    updatedAt: '2024-01-01',
  },
];

describe('StaffCrudPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (apiModule.api.get as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
      if (url === '/specialties/active') {
        return Promise.resolve(mockSpecialties);
      }
      if (url === '/staff') {
        return Promise.resolve(mockStaffMembers);
      }
      return Promise.resolve([]);
    });
  });

  const renderPanel = () => act(() => { render(<StaffCrudPanel />); });

  it('should render the panel header', async () => {
    await renderPanel();
    expect(screen.getByText('Nhân sự')).toBeInTheDocument();
  });

  it('should render search input', async () => {
    await renderPanel();
    const searchInput = screen.getByPlaceholderText(/Tìm kiếm tên, email hoặc mã nhân viên/);
    expect(searchInput).toBeInTheDocument();
  });

  it('should render filter dropdowns', async () => {
    await renderPanel();
    expect(screen.getByLabelText(/Lọc theo vai trò/)).toBeInTheDocument();
    expect(screen.getByLabelText(/Lọc theo trạng thái/)).toBeInTheDocument();
  });

  it('should render action buttons', async () => {
    await renderPanel();
    expect(screen.getByText('Xuất Excel')).toBeInTheDocument();
    expect(screen.getByText('Thêm nhân viên')).toBeInTheDocument();
  });

  it('should update searchKeyword when typing in search input', async () => {
    const user = userEvent.setup();
    await renderPanel();

    const searchInput = screen.getByPlaceholderText(/Tìm kiếm tên, email hoặc mã nhân viên/);
    await user.type(searchInput, 'Nguyễn');

    expect(searchInput).toHaveValue('Nguyễn');
  });

  it('should call fetchStaff on mount', async () => {
    await renderPanel();

    await waitFor(() => {
      expect(apiModule.api.get).toHaveBeenCalledWith('/staff');
    });
  });

  it('should display KPI card for total staff', async () => {
    await renderPanel();

    await waitFor(() => {
      expect(screen.getByText('Tổng nhân sự')).toBeInTheDocument();
    });
  });

  it('should show loading state initially', async () => {
    (apiModule.api.get as ReturnType<typeof vi.fn>).mockImplementation(() =>
      new Promise(() => {}) // Never resolves to keep loading state
    );

    await renderPanel();
    expect(document.querySelector('.animate-spin')).toBeInTheDocument();
  });

  it('should open add form when clicking "Thêm nhân viên"', async () => {
    const user = userEvent.setup();
    await renderPanel();

    const addButton = screen.getByText('Thêm nhân viên');
    await act(async () => { await user.click(addButton); });

    await waitFor(() => {
      expect(screen.getByText('Thêm nhân sự')).toBeInTheDocument();
    });
  });

  it('should call fetchStaff when search keyword changes', async () => {
    const user = userEvent.setup();
    await renderPanel();

    // Wait for initial fetch
    await waitFor(() => {
      expect(apiModule.api.get).toHaveBeenCalled();
    });

    const initialCalls = (apiModule.api.get as ReturnType<typeof vi.fn>).mock.calls.length;

    const searchInput = screen.getByPlaceholderText(/Tìm kiếm tên, email hoặc mã nhân viên/);
    await act(async () => {
      await user.clear(searchInput);
      await user.type(searchInput, 'test');
    });

    // Wait for debounce
    await act(async () => {
      await new Promise(resolve => setTimeout(resolve, 150));
    });

    // Should have called fetchStaff again with search params
    expect((apiModule.api.get as ReturnType<typeof vi.fn>).mock.calls.length).toBeGreaterThan(initialCalls);
  });

  it('should have search input with correct attributes', async () => {
    await renderPanel();

    const searchInput = screen.getByPlaceholderText(/Tìm kiếm tên, email hoặc mã nhân viên/);
    expect(searchInput).toHaveAttribute('aria-label', 'Tìm kiếm nhân sự');
    expect(searchInput).toHaveAttribute('name', 'staffSearch');
  });

  it('should render specialty filter dropdown', async () => {
    await renderPanel();

    await waitFor(() => {
      expect(screen.getByLabelText(/Lọc theo khoa phòng/)).toBeInTheDocument();
    });
  });
});

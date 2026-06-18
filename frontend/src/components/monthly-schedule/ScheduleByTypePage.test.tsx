import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ScheduleByTypePage, type ScheduleTypeConfig } from './ScheduleByTypePage';
import * as apiModule from '@/lib/api';

vi.mock('@/lib/api', () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock('@/hooks/useToast', () => ({
  useToast: () => ({
    toasts: [],
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
    dismiss: vi.fn(),
  }),
}));

vi.mock('@/components/layout/DashboardShell', () => ({
  DashboardShell: ({
    children,
    title,
  }: {
    children: React.ReactNode;
    title: string;
  }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));

vi.mock('@/components/monthly-schedule/ScheduleCalendarSection', () => ({
  ScheduleCalendarSection: ({ schedules }: { schedules: unknown[] }) => (
    <div data-testid="calendar" data-count={schedules.length} />
  ),
}));

vi.mock('@/components/monthly-schedule/QuickAddModal', () => ({
  QuickAddModal: () => null,
}));

vi.mock('@/components/monthly-schedule/ShiftDetailModal', () => ({
  ShiftDetailModal: () => null,
}));

vi.mock('@/hooks/useRole', () => ({
  useRole: () => 'MANAGER',
  canManage: () => true,
  canEditSchedule: () => true,
}));

const mockPeriods = [
  { id: 1, periodName: 'Tháng 6/2026', status: 'DRAFT' },
  { id: 2, periodName: 'Tháng 7/2026', status: 'PUBLISHED' },
];

const mockStaff = [
  { id: 1, fullName: 'BS. Nguyễn Văn A' },
  { id: 2, fullName: 'BS. Trần Thị B' },
];

const mockL01Schedules = [
  {
    id: 100,
    workDate: '2026-06-10',
    shiftType: { id: 'L01', name: 'Trực 24/24' },
    staff: { id: 1, fullName: 'BS. Nguyễn Văn A' },
    hasConflict: false,
  },
  {
    id: 101,
    workDate: '2026-06-11',
    shiftType: { id: 'L01', name: 'Trực 24/24' },
    staff: { id: 2, fullName: 'BS. Trần Thị B' },
    hasConflict: true,
  },
];

const mockL02Schedules = [
  {
    id: 200,
    workDate: '2026-06-10',
    shiftType: { id: 'L02', name: 'Thông tầm' },
    staff: { id: 1, fullName: 'BS. Nguyễn Văn A' },
    hasConflict: false,
  },
];

const mockCompensationDays = [
  { id: 1, staffName: 'BS. Nguyễn Văn A', compensationDate: '2026-06-11T00:00:00' },
];

const duty24Config: ScheduleTypeConfig = {
  activeSection: 'duty-24',
  shiftTypeId: 'L01',
  title: 'Lịch trực 24/24',
  description: 'desc',
  emptyMessage: 'Chọn một kỳ lịch để xem lịch trực 24/24.',
  emptyIcon: 'emergency',
  ctaIcon: 'add',
  ctaLabel: 'Thêm ca trực',
  totalShiftLabel: 'Tổng ca trực 24/24',
  totalShiftAccent: 'bg-shift-24/30',
  staffAccent: 'bg-shift-all-day/20',
  fetchErrorMessage: 'Không thể tải lịch trực 24/24.',
  compDescription: 'Ngày nghỉ bù sau ca trực',
};

const allDayConfig: ScheduleTypeConfig = {
  ...duty24Config,
  activeSection: 'all-day',
  shiftTypeId: 'L02',
  title: 'Lịch thông tầm',
  emptyMessage: 'Chọn một kỳ lịch để xem lịch thông tầm.',
  ctaLabel: 'Thêm ca thông tầm',
  totalShiftLabel: 'Tổng ca thông tầm',
  fetchErrorMessage: 'Không thể tải lịch thông tầm.',
};

const setupApiMock = (schedules: unknown[], compensationDays = mockCompensationDays) => {
  (apiModule.api.get as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
    if (url === '/periods') return Promise.resolve(mockPeriods);
    if (url === '/staff/active') return Promise.resolve(mockStaff);
    if (url.startsWith('/schedules/period/')) return Promise.resolve(schedules);
    if (url.startsWith('/schedules/compensation-days/'))
      return Promise.resolve(compensationDays);
    return Promise.resolve([]);
  });
};

describe('ScheduleByTypePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders the title from config', async () => {
    setupApiMock(mockL01Schedules);
    await act(async () => {
      render(<ScheduleByTypePage config={duty24Config} />);
    });
    expect(screen.getByRole('heading', { level: 1, name: 'Lịch trực 24/24' })).toBeInTheDocument();
  });

  it('fetches periods and active staff on mount', async () => {
    setupApiMock(mockL01Schedules);
    await act(async () => {
      render(<ScheduleByTypePage config={duty24Config} />);
    });
    await waitFor(() => {
      expect(apiModule.api.get).toHaveBeenCalledWith('/periods');
      expect(apiModule.api.get).toHaveBeenCalledWith('/staff/active');
    });
  });

  it('selects the first DRAFT period by default', async () => {
    setupApiMock(mockL01Schedules);
    await act(async () => {
      render(<ScheduleByTypePage config={duty24Config} />);
    });
    await waitFor(() => {
      const select = screen.getByLabelText(/Kỳ lịch/) as HTMLSelectElement;
      expect(select.value).toBe('1');
    });
  });

  it('fetches schedules for the selected period', async () => {
    setupApiMock(mockL01Schedules);
    await act(async () => {
      render(<ScheduleByTypePage config={duty24Config} />);
    });
    await waitFor(() => {
      expect(apiModule.api.get).toHaveBeenCalledWith('/schedules/period/1');
      expect(apiModule.api.get).toHaveBeenCalledWith('/schedules/compensation-days/1');
    });
  });

  it('filters schedules by the configured shift type id', async () => {
    const mixed = [
      ...mockL01Schedules,
      {
        id: 999,
        workDate: '2026-06-12',
        shiftType: { id: 'L02', name: 'Thông tầm' },
        staff: { id: 3, fullName: 'BS. Lê Văn C' },
        hasConflict: false,
      },
    ];
    setupApiMock(mixed);
    await act(async () => {
      render(<ScheduleByTypePage config={duty24Config} />);
    });
    await waitFor(() => {
      const calendar = screen.getByTestId('calendar');
      expect(calendar.getAttribute('data-count')).toBe('2');
    });
  });

  it('uses a different shift type id for the all-day page', async () => {
    const mixed = [...mockL01Schedules, ...mockL02Schedules];
    setupApiMock(mixed);
    await act(async () => {
      render(<ScheduleByTypePage config={allDayConfig} />);
    });
    await waitFor(() => {
      const calendar = screen.getByTestId('calendar');
      expect(calendar.getAttribute('data-count')).toBe('1');
    });
  });

  it('renders the CTA button with the configured label when user is a manager', async () => {
    setupApiMock(mockL01Schedules);
    await act(async () => {
      render(<ScheduleByTypePage config={duty24Config} />);
    });
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Thêm ca trực/ })).toBeInTheDocument();
    });
  });

  it('shows the empty state when no period is selected', async () => {
    setupApiMock([]);
    (apiModule.api.get as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
      if (url === '/periods') return Promise.resolve([]);
      if (url === '/staff/active') return Promise.resolve(mockStaff);
      return Promise.resolve([]);
    });
    await act(async () => {
      render(<ScheduleByTypePage config={duty24Config} />);
    });
    await waitFor(() => {
      expect(screen.getByText(/Chọn một kỳ lịch để xem lịch trực 24\/24/)).toBeInTheDocument();
    });
  });

  it('shows the configured error message when schedule fetch fails', async () => {
    (apiModule.api.get as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
      if (url === '/periods') return Promise.resolve(mockPeriods);
      if (url === '/staff/active') return Promise.resolve(mockStaff);
      if (url.startsWith('/schedules/')) return Promise.reject(new Error('boom'));
      return Promise.resolve([]);
    });
    await act(async () => {
      render(<ScheduleByTypePage config={duty24Config} />);
    });
    await waitFor(() => {
      expect(screen.getByText('Không thể tải lịch trực 24/24.')).toBeInTheDocument();
    });
  });

  it('switches period when the user picks a different one', async () => {
    setupApiMock(mockL01Schedules);
    const user = userEvent.setup();
    await act(async () => {
      render(<ScheduleByTypePage config={duty24Config} />);
    });
    const select = await screen.findByLabelText(/Kỳ lịch/);
    await act(async () => {
      await user.selectOptions(select, '2');
    });
    await waitFor(() => {
      expect(apiModule.api.get).toHaveBeenCalledWith('/schedules/period/2');
    });
  });
});

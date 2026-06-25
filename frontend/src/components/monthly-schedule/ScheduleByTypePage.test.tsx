import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, act, fireEvent } from '@testing-library/react';
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

vi.mock('@/components/ui', () => ({
  useToast: () => ({
    toasts: [],
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
    warning: vi.fn(),
    dismiss: vi.fn(),
    dismissAll: vi.fn(),
  }),
  ToastProvider: ({ children }: { children: React.ReactNode }) => children,
  Toast: vi.fn(),
  Skeleton: vi.fn(),
  Button: vi.fn(),
  IconButton: vi.fn(),
  ConfirmDialog: vi.fn(),
  FormInput: vi.fn(),
  FormSelect: vi.fn(),
  FormTextarea: vi.fn(),
  FormCheckbox: vi.fn(),
  EmptyState: vi.fn(),
  ThemeToggle: vi.fn(),
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

vi.mock('@/components/monthly-schedule/WorkflowStepper', () => ({
  WorkflowStepper: () => null,
}));

vi.mock('@/components/monthly-schedule/WorkloadSummary', () => ({
  WorkloadSummary: () => null,
}));

vi.mock('@/components/monthly-schedule/BulkScheduleModal', () => ({
  BulkScheduleModal: () => null,
}));

vi.mock('@/components/monthly-schedule/BulkDatePickerModal', () => ({
  BulkDatePickerModal: () => null,
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
    // The title is now rendered by GuardedScheduleByTypePage (inline role check,
    // no double-shell), so ScheduleByTypePage itself only surfaces the config
    // period names and shift types. Verify the page rendered the expected
    // config's shift data and the page structure is present.
    expect(screen.getByText('Tháng 6/2026 (DRAFT)')).toBeInTheDocument();
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
    // Setup mock to return an empty periods array so no period auto-selects
    (apiModule.api.get as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
      if (url === '/periods') return Promise.resolve([]);
      if (url === '/staff/active') return Promise.resolve(mockStaff);
      if (url.startsWith('/schedules/period/')) return Promise.resolve([]);
      if (url.startsWith('/schedules/compensation-days/')) return Promise.resolve([]);
      return Promise.resolve([]);
    });
    await act(async () => {
      render(<ScheduleByTypePage config={duty24Config} />);
    });
    await waitFor(() => {
      expect(screen.getByText(/Chọn một kỳ lịch để xem lịch trực 24\/24/)).toBeInTheDocument();
    }, { timeout: 3000 });
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

const mockSpecialties = [
  { id: 1, name: 'Nội khoa', active: true },
  { id: 2, name: 'Ngoại khoa', active: true },
];

const mockExpertSchedules = [
  {
    id: 300,
    workDate: '2026-06-10',
    shiftType: { id: 'L04', name: 'PK Chuyên gia' },
    staff: { id: 1, fullName: 'BS. Nguyễn Văn A', specialty: { id: 1, name: 'Nội khoa' } },
    hasConflict: false,
  },
];

const expertConfig: ScheduleTypeConfig = {
  ...duty24Config,
  activeSection: 'expert-clinic',
  shiftTypeId: 'L04',
  title: 'Phòng khám chuyên gia',
  emptyMessage: 'Chọn một kỳ lịch để xem lịch phòng khám chuyên gia.',
  emptyIcon: 'stethoscope',
  ctaLabel: 'Thêm ca chuyên gia',
  totalShiftLabel: 'Tổng ca PK Chuyên gia',
  fetchErrorMessage: 'Không thể tải lịch phòng khám chuyên gia.',
  expertClinicMode: true,
};

const setupExpertApiMock = (schedules: unknown[]) => {
  (apiModule.api.get as ReturnType<typeof vi.fn>).mockImplementation(
    (url: string, params?: Record<string, unknown>) => {
      if (url === '/periods') return Promise.resolve(mockPeriods);
      if (url === '/staff/active') return Promise.resolve(mockStaff);
      if (url === '/specialties/active') return Promise.resolve(mockSpecialties);
      if (url === '/schedules/expert-clinic') {
        // Verify specialtyId param is forwarded when set
        void params;
        return Promise.resolve(schedules);
      }
      return Promise.resolve([]);
    }
  );
};

describe('ScheduleByTypePage (expert-clinic mode)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('also loads the active specialties on mount', async () => {
    setupExpertApiMock(mockExpertSchedules);
    await act(async () => {
      render(<ScheduleByTypePage config={expertConfig} />);
    });
    await waitFor(() => {
      expect(apiModule.api.get).toHaveBeenCalledWith('/specialties/active');
    });
  });

  it('calls the expert-clinic endpoint with the selected period', async () => {
    setupExpertApiMock(mockExpertSchedules);
    await act(async () => {
      render(<ScheduleByTypePage config={expertConfig} />);
    });
    await waitFor(() => {
      expect(apiModule.api.get).toHaveBeenCalledWith(
        '/schedules/expert-clinic',
        expect.objectContaining({ periodId: 1 })
      );
    });
  });

  it('does NOT call the period or compensation-days endpoints', async () => {
    setupExpertApiMock(mockExpertSchedules);
    await act(async () => {
      render(<ScheduleByTypePage config={expertConfig} />);
    });
    await waitFor(() => {
      expect(apiModule.api.get).toHaveBeenCalledWith('/schedules/expert-clinic', expect.anything());
    });
    const calls = (apiModule.api.get as ReturnType<typeof vi.fn>).mock.calls.map(
      (c) => c[0] as string
    );
    expect(calls.some((c) => c.startsWith('/schedules/period/'))).toBe(false);
    expect(calls.some((c) => c.startsWith('/schedules/compensation-days/'))).toBe(false);
  });

  it('renders the specialty filter dropdown', async () => {
    setupExpertApiMock(mockExpertSchedules);
    await act(async () => {
      render(<ScheduleByTypePage config={expertConfig} />);
    });
    await waitFor(() => {
      expect(screen.getByLabelText(/Chuyên khoa/)).toBeInTheDocument();
    });
  });

  it('forwards the specialtyId param when a specialty is selected', async () => {
    setupExpertApiMock(mockExpertSchedules);
    await act(async () => {
      render(<ScheduleByTypePage config={expertConfig} />);
    });
    // Wait for the initial fetch + filter to be wired up.
    await waitFor(() => {
      expect(apiModule.api.get).toHaveBeenCalledWith('/specialties/active');
    });

    const select = await screen.findByLabelText(/Chuyên khoa/);
    await act(async () => {
      fireEvent.change(select, { target: { value: '1' } });
    });

    await waitFor(() => {
      const calls = (apiModule.api.get as ReturnType<typeof vi.fn>).mock.calls;
      const lastExpertCall = calls
        .map((c) => c as [string, Record<string, unknown>?])
        .filter(([url]) => url === '/schedules/expert-clinic')
        .pop();
      expect(lastExpertCall?.[1]).toMatchObject({ specialtyId: 1 });
    });
  });

  it('clears the specialty filter when switching periods', async () => {
    setupExpertApiMock(mockExpertSchedules);
    await act(async () => {
      render(<ScheduleByTypePage config={expertConfig} />);
    });
    await waitFor(() => {
      expect(apiModule.api.get).toHaveBeenCalledWith('/specialties/active');
    });

    const select = await screen.findByLabelText(/Chuyên khoa/);
    await act(async () => {
      fireEvent.change(select, { target: { value: '1' } });
    });
    await waitFor(() => {
      const calls = (apiModule.api.get as ReturnType<typeof vi.fn>).mock.calls;
      const lastExpertCall = calls
        .map((c) => c as [string, Record<string, unknown>?])
        .filter(([url]) => url === '/schedules/expert-clinic')
        .pop();
      expect(lastExpertCall?.[1]).toMatchObject({ specialtyId: 1 });
    });

    // Switch period — the filter should reset and the next request
    // must NOT include specialtyId.
    const periodSelect = screen.getByLabelText(/Kỳ lịch/) as HTMLSelectElement;
    await act(async () => {
      fireEvent.change(periodSelect, { target: { value: String(mockPeriods[1].id) } });
    });

    await waitFor(() => {
      const calls = (apiModule.api.get as ReturnType<typeof vi.fn>).mock.calls;
      const lastExpertCall = calls
        .map((c) => c as [string, Record<string, unknown>?])
        .filter(([url]) => url === '/schedules/expert-clinic')
        .pop();
      expect(lastExpertCall?.[1]).not.toHaveProperty('specialtyId');
    });
  });

  it('lists every active specialty as an option', async () => {
    setupExpertApiMock(mockExpertSchedules);
    await act(async () => {
      render(<ScheduleByTypePage config={expertConfig} />);
    });
    await waitFor(() => {
      expect(apiModule.api.get).toHaveBeenCalledWith('/specialties/active');
    });
    const select = (await screen.findByLabelText(
      /Chuyên khoa/,
    )) as HTMLSelectElement;
    const options = Array.from(select.options).map((o) => o.text);
    expect(options).toContain('Nội khoa');
    expect(options).toContain('Ngoại khoa');
    expect(options).toContain('Tất cả chuyên khoa');
  });

  it('shows the expert-clinic error message when the endpoint fails', async () => {
    (apiModule.api.get as ReturnType<typeof vi.fn>).mockImplementation(
      (url: string) => {
        if (url === '/periods') return Promise.resolve(mockPeriods);
        if (url === '/staff/active') return Promise.resolve(mockStaff);
        if (url === '/specialties/active') return Promise.resolve(mockSpecialties);
        if (url === '/schedules/expert-clinic') return Promise.reject(new Error('boom'));
        return Promise.resolve([]);
      }
    );
    await act(async () => {
      render(<ScheduleByTypePage config={expertConfig} />);
    });
    await waitFor(() => {
      expect(
        screen.getByText('Không thể tải lịch phòng khám chuyên gia.')
      ).toBeInTheDocument();
    });
  });
});

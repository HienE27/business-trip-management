import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QuickAddModal } from './QuickAddModal';
import * as apiModule from '@/lib/api';

vi.mock('@/lib/api', () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

const staff = [
  {
    id: 1,
    username: 'nguyenvana',
    fullName: 'BS. Nguyễn Văn A',
    staffCode: 'NV001',
    maxShiftsPerMonth: 5,
    isActive: true,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    roles: ['STAFF'],
  },
  {
    id: 2,
    username: 'tranthib',
    fullName: 'BS. Trần Thị B',
    staffCode: 'NV002',
    maxShiftsPerMonth: 6,
    isActive: true,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    roles: ['STAFF'],
  },
];

const baseProps = {
  date: new Date('2026-06-15T12:00:00Z'),
  periodId: 5,
  defaultShiftTypeId: 'L01' as const,
  staffList: staff,
  onSuccess: vi.fn(),
  onClose: vi.fn(),
};

async function openStaffDropdown(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('combobox', { name: 'Nhân sự' }));
}

describe('QuickAddModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (apiModule.api.post as ReturnType<typeof vi.fn>).mockReset();
  });

  it('does not render the form when date is null', () => {
    render(<QuickAddModal {...baseProps} date={null} />);
    expect(screen.queryByLabelText(/Loại lịch/)).not.toBeInTheDocument();
  });

  it('renders staff options from the staffList prop', async () => {
    const user = userEvent.setup();
    render(<QuickAddModal {...baseProps} />);
    await openStaffDropdown(user);
    expect(screen.getByRole('option', { name: /BS\. Nguyễn Văn A/ })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: /BS\. Trần Thị B/ })).toBeInTheDocument();
  });

  it('submits the form with the picked staff id and shift type', async () => {
    (apiModule.api.post as ReturnType<typeof vi.fn>).mockResolvedValue({
      id: 999,
      workDate: '2026-06-15',
      staff: staff[0],
      shiftType: { id: 'L01', name: 'Trực 24/24', isOvernight: false },
      hasConflict: false,
    });
    const onSuccess = vi.fn();
    const user = userEvent.setup();
    render(<QuickAddModal {...baseProps} onSuccess={onSuccess} />);

    await openStaffDropdown(user);
    await user.click(screen.getByRole('option', { name: /BS\. Nguyễn Văn A/ }));
    await user.click(screen.getByRole('button', { name: /Tạo lịch/ }));

    await waitFor(() => {
      expect(apiModule.api.post).toHaveBeenCalledWith(
        '/schedules',
        expect.objectContaining({
          periodId: 5,
          workDate: '2026-06-15',
          staffId: 1,
          shiftTypeId: 'L01',
        })
      );
    });
    expect(onSuccess).toHaveBeenCalled();
  });

  describe('optimistic flow', () => {
    it('calls onOptimisticAdd BEFORE posting when the optimistic callbacks are wired', async () => {
      const onOptimisticAdd = vi.fn();
      const onCommit = vi.fn();
      const onRollback = vi.fn();
      const onSuccess = vi.fn();

      let resolvePost: (value: unknown) => void = () => {};
      (apiModule.api.post as ReturnType<typeof vi.fn>).mockReturnValue(
        new Promise((resolve) => {
          resolvePost = resolve;
        })
      );

      const user = userEvent.setup();
      render(
        <QuickAddModal
          {...baseProps}
          onOptimisticAdd={onOptimisticAdd}
          onCommit={onCommit}
          onRollback={onRollback}
          onSuccess={onSuccess}
        />
      );

      await openStaffDropdown(user);
      await user.click(screen.getByRole('option', { name: /BS\. Nguyễn Văn A/ }));
      await user.click(screen.getByRole('button', { name: /Tạo lịch/ }));

      expect(onOptimisticAdd).toHaveBeenCalledTimes(1);
      const tempSchedule = onOptimisticAdd.mock.calls[0][0];
      expect(tempSchedule.id).toBeLessThan(0);
      expect(tempSchedule.staff.id).toBe(1);
      expect(tempSchedule.shiftType.id).toBe('L01');

      resolvePost({
        id: 1234,
        workDate: '2026-06-15',
        staff: staff[0],
        shiftType: { id: 'L01', name: 'Trực 24/24', isOvernight: false },
        hasConflict: false,
      });

      await waitFor(() => {
        expect(onCommit).toHaveBeenCalledTimes(1);
      });
      const [tempId, realSchedule] = onCommit.mock.calls[0];
      expect(tempId).toBe(tempSchedule.id);
      expect(realSchedule.id).toBe(1234);
      expect(onSuccess).not.toHaveBeenCalled();
    });

    it('rolls the optimistic insert back when the POST fails', async () => {
      const onOptimisticAdd = vi.fn();
      const onCommit = vi.fn();
      const onRollback = vi.fn();

      (apiModule.api.post as ReturnType<typeof vi.fn>).mockRejectedValue(new Error('boom'));

      const user = userEvent.setup();
      render(
        <QuickAddModal
          {...baseProps}
          onOptimisticAdd={onOptimisticAdd}
          onCommit={onCommit}
          onRollback={onRollback}
        />
      );

      await openStaffDropdown(user);
      await user.click(screen.getByRole('option', { name: /BS\. Trần Thị B/ }));
      await user.click(screen.getByRole('button', { name: /Tạo lịch/ }));

      await waitFor(() => {
        expect(onRollback).toHaveBeenCalledTimes(1);
      });
      expect(onRollback.mock.calls[0][0]).toBe(onOptimisticAdd.mock.calls[0][0].id);
      expect(onCommit).not.toHaveBeenCalled();
      expect(await screen.findByRole('alert')).toHaveTextContent(/boom/);
    });

    it('does not invoke optimistic callbacks when they are not supplied', async () => {
      (apiModule.api.post as ReturnType<typeof vi.fn>).mockResolvedValue({ id: 1 });
      const onSuccess = vi.fn();
      const user = userEvent.setup();
      render(<QuickAddModal {...baseProps} onSuccess={onSuccess} />);

      await openStaffDropdown(user);
      await user.click(screen.getByRole('option', { name: /BS\. Nguyễn Văn A/ }));
      await user.click(screen.getByRole('button', { name: /Tạo lịch/ }));

      await waitFor(() => {
        expect(onSuccess).toHaveBeenCalled();
      });
    });
  });

  describe('client-side guard', () => {
    it('shows error when date is outside the schedule period', async () => {
      // The date-out-of-range guard triggers when periodStart/periodEnd are provided.
      const dateOutOfRangeProps = {
        ...baseProps,
        date: new Date('2025-01-01T12:00:00Z'),
        periodStart: '2026-06-01',
        periodEnd: '2026-06-30',
      };
      render(<QuickAddModal {...dateOutOfRangeProps} />);
      await waitFor(() => {
        expect(screen.getByRole('alert')).toHaveTextContent(/kỳ lịch/);
      });
    });
  });
});

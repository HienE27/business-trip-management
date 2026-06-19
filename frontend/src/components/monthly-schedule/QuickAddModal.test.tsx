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
    maxShiftsPerMonth: 5,
    isActive: true,
  },
  {
    id: 2,
    username: 'tranthib',
    fullName: 'BS. Trần Thị B',
    maxShiftsPerMonth: 6,
    isActive: true,
  },
];

const baseProps = {
  // Build the date via the same path the production code uses:
  // toISOString().slice(0, 10). The local-time constructor here
  // means the date string rounds-trips correctly in CI regardless
  // of the runner's timezone.
  date: new Date('2026-06-15T12:00:00Z'),
  periodId: 5,
  defaultShiftTypeId: 'L01' as const,
  staffList: staff,
  onSuccess: vi.fn(),
  onClose: vi.fn(),
};

describe('QuickAddModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (apiModule.api.post as ReturnType<typeof vi.fn>).mockReset();
  });

  it('does not render the form when date is null', () => {
    render(<QuickAddModal {...baseProps} date={null} />);
    expect(screen.queryByLabelText(/Loại lịch/)).not.toBeInTheDocument();
  });

  it('renders staff options from the staffList prop', () => {
    render(<QuickAddModal {...baseProps} />);
    expect(screen.getByRole('option', { name: 'BS. Nguyễn Văn A' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'BS. Trần Thị B' })).toBeInTheDocument();
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

    await user.selectOptions(screen.getByLabelText(/Nhân sự/), '1');
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

      await user.selectOptions(screen.getByLabelText(/Nhân sự/), '1');
      await user.click(screen.getByRole('button', { name: /Tạo lịch/ }));

      // Optimistic insert fires synchronously, before the network
      // round-trip has a chance to resolve.
      expect(onOptimisticAdd).toHaveBeenCalledTimes(1);
      const tempSchedule = onOptimisticAdd.mock.calls[0][0];
      expect(tempSchedule.id).toBeLessThan(0);
      expect(tempSchedule.staff.id).toBe(1);
      expect(tempSchedule.shiftType.id).toBe('L01');

      // Now resolve the POST and verify the commit flow.
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
      // onSuccess is NOT called when the optimistic path commits.
      expect(onSuccess).not.toHaveBeenCalled();
    });

    it('rolls the optimistic insert back when the POST fails', async () => {
      const onOptimisticAdd = vi.fn();
      const onCommit = vi.fn();
      const onRollback = vi.fn();

      (apiModule.api.post as ReturnType<typeof vi.fn>).mockRejectedValue(
        new Error('boom')
      );

      const user = userEvent.setup();
      render(
        <QuickAddModal
          {...baseProps}
          onOptimisticAdd={onOptimisticAdd}
          onCommit={onCommit}
          onRollback={onRollback}
        />
      );

      await user.selectOptions(screen.getByLabelText(/Nhân sự/), '2');
      await user.click(screen.getByRole('button', { name: /Tạo lịch/ }));

      await waitFor(() => {
        expect(onRollback).toHaveBeenCalledTimes(1);
      });
      // Rollback receives the same temp id that was optimistically inserted.
      expect(onRollback.mock.calls[0][0]).toBe(onOptimisticAdd.mock.calls[0][0].id);
      expect(onCommit).not.toHaveBeenCalled();
      // The error surfaces in the form-level alert region; the
      // exact wording comes from getErrorMessage, which uses the
      // Error's own message when present.
      expect(await screen.findByRole('alert')).toHaveTextContent(/boom/);
    });

    it('does not invoke optimistic callbacks when they are not supplied', async () => {
      (apiModule.api.post as ReturnType<typeof vi.fn>).mockResolvedValue({ id: 1 });
      const onSuccess = vi.fn();
      const user = userEvent.setup();
      render(<QuickAddModal {...baseProps} onSuccess={onSuccess} />);

      await user.selectOptions(screen.getByLabelText(/Nhân sự/), '1');
      await user.click(screen.getByRole('button', { name: /Tạo lịch/ }));

      await waitFor(() => {
        expect(onSuccess).toHaveBeenCalled();
      });
    });
  });

  describe('client-side guard', () => {
    it('refuses to submit when the picked staff is on a compensation day', async () => {
      const compensationDays = [
        { id: 1, staffId: 1, staffName: 'BS. Nguyễn Văn A', compensationDate: '2026-06-15' },
      ];
      const user = userEvent.setup();
      render(<QuickAddModal {...baseProps} compensationDays={compensationDays} />);

      await user.selectOptions(screen.getByLabelText(/Nhân sự/), '1');
      await user.click(screen.getByRole('button', { name: /Tạo lịch/ }));

      expect(apiModule.api.post).not.toHaveBeenCalled();
      expect(await screen.findByRole('alert')).toHaveTextContent(/nghỉ bù/);
    });
  });
});
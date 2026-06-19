import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup, waitFor } from '@testing-library/react';

vi.mock('@/lib/api', () => ({
  api: {
    getAllStaff: vi.fn(),
    getAllShiftTypes: vi.fn(),
    exportScheduleExcel: vi.fn(),
    exportSchedulePdf: vi.fn(),
    exportWorkloadExcel: vi.fn(),
  },
}));

import { api } from '@/lib/api';
import { ExportControls } from './ExportControls';
import { ToastProvider } from '@/components/ui';

const apiMock = api as unknown as {
  getAllStaff: ReturnType<typeof vi.fn>;
  getAllShiftTypes: ReturnType<typeof vi.fn>;
  exportScheduleExcel: ReturnType<typeof vi.fn>;
  exportSchedulePdf: ReturnType<typeof vi.fn>;
  exportWorkloadExcel: ReturnType<typeof vi.fn>;
};

const STAFF_OPTIONS = [
  { id: 1, fullName: 'BS. Nguyễn Văn A' },
  { id: 2, fullName: 'BS. Trần Thị B' },
];

const SHIFT_OPTIONS = [
  { id: 'L01', name: 'Trực 24/24' },
  { id: 'L02', name: 'Thông tầm' },
];

function renderControls(props: Partial<React.ComponentProps<typeof ExportControls>> = {}) {
  const onSuccess = vi.fn();
  const onError = vi.fn();
  const utils = render(
    <ToastProvider>
      <ExportControls
        periodId={42}
        onSuccess={onSuccess}
        onError={onError}
        {...props}
      />
    </ToastProvider>,
  );
  return { ...utils, onSuccess, onError };
}

describe('ExportControls', () => {
  beforeEach(() => {
    apiMock.getAllStaff.mockReset();
    apiMock.getAllShiftTypes.mockReset();
    apiMock.exportScheduleExcel.mockReset();
    apiMock.exportSchedulePdf.mockReset();
    apiMock.exportWorkloadExcel.mockReset();

    apiMock.getAllStaff.mockResolvedValue({ data: STAFF_OPTIONS });
    apiMock.getAllShiftTypes.mockResolvedValue({ data: SHIFT_OPTIONS });
    apiMock.exportScheduleExcel.mockResolvedValue(
      new Blob(['excel'], { type: 'application/octet-stream' }),
    );
    apiMock.exportSchedulePdf.mockResolvedValue(
      new Blob(['pdf'], { type: 'application/pdf' }),
    );
    apiMock.exportWorkloadExcel.mockResolvedValue(
      new Blob(['workload'], { type: 'application/octet-stream' }),
    );

    if (typeof URL.createObjectURL !== 'function') {
      Object.defineProperty(URL, 'createObjectURL', {
        configurable: true,
        value: () => 'blob:test',
      });
      Object.defineProperty(URL, 'revokeObjectURL', {
        configurable: true,
        value: () => {},
      });
    }
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it('renders the export trigger button', async () => {
    renderControls();
    expect(screen.getByTestId('export-trigger')).toBeInTheDocument();
  });

  it('exposes shift type and staff filters from the dropdowns', async () => {
    renderControls();
    await waitFor(() => {
      expect(apiMock.getAllStaff).toHaveBeenCalled();
      expect(apiMock.getAllShiftTypes).toHaveBeenCalled();
    });
    const selects = screen.getAllByRole('combobox');
    expect(selects.length).toBeGreaterThanOrEqual(2);
  });

  it('calls exportScheduleExcel with the period id and chosen filters', async () => {
    renderControls();
    await waitFor(() => expect(apiMock.getAllStaff).toHaveBeenCalled());

    const selects = screen.getAllByRole('combobox') as HTMLSelectElement[];
    fireEvent.change(selects[0], { target: { value: 'L02' } });
    fireEvent.change(selects[1], { target: { value: '2' } });

    fireEvent.click(screen.getByTestId('export-trigger'));

    await waitFor(() => {
      expect(apiMock.exportScheduleExcel).toHaveBeenCalledWith(
        42,
        expect.objectContaining({
          shiftTypeId: 'L02',
          staffId: 2,
        }),
      );
    });
  });

  it('calls exportSchedulePdf when the format is set to PDF', async () => {
    renderControls({ allowedFormats: ['excel-schedule', 'pdf-schedule'] });
    await waitFor(() => expect(apiMock.getAllStaff).toHaveBeenCalled());

    const selects = screen.getAllByRole('combobox') as HTMLSelectElement[];
    const formatSelect = selects[selects.length - 1];
    fireEvent.change(formatSelect, { target: { value: 'pdf-schedule' } });

    fireEvent.click(screen.getByTestId('export-trigger'));

    await waitFor(() =>
      expect(apiMock.exportSchedulePdf).toHaveBeenCalledWith(42, expect.any(Object)),
    );
  });

  it('calls exportWorkloadExcel when the format is set to workload', async () => {
    renderControls({ showWorkload: true });
    await waitFor(() => expect(apiMock.getAllStaff).toHaveBeenCalled());

    const selects = screen.getAllByRole('combobox') as HTMLSelectElement[];
    const formatSelect = selects[selects.length - 1];
    fireEvent.change(formatSelect, { target: { value: 'excel-workload' } });

    fireEvent.click(screen.getByTestId('export-trigger'));

    await waitFor(() =>
      expect(apiMock.exportWorkloadExcel).toHaveBeenCalledWith(42, expect.any(Object)),
    );
  });

  it('pinned format hides the format selector', async () => {
    renderControls({ pinFormat: 'excel-workload' });
    await waitFor(() => expect(apiMock.getAllStaff).toHaveBeenCalled());

    const selects = screen.getAllByRole('combobox');
    expect(selects).toHaveLength(2);

    fireEvent.click(screen.getByTestId('export-trigger'));
    await waitFor(() =>
      expect(apiMock.exportWorkloadExcel).toHaveBeenCalledWith(42, {}),
    );
  });

  it('reports success through onSuccess callback', async () => {
    const { onSuccess } = renderControls();
    await waitFor(() => expect(apiMock.getAllStaff).toHaveBeenCalled());

    fireEvent.click(screen.getByTestId('export-trigger'));
    await waitFor(() => expect(onSuccess).toHaveBeenCalledWith('Đã tạo file báo cáo.'));
  });

  it('reports errors through onError callback', async () => {
    apiMock.exportScheduleExcel.mockRejectedValue(new Error('boom'));

    const { onError } = renderControls();
    await waitFor(() => expect(apiMock.getAllStaff).toHaveBeenCalled());

    fireEvent.click(screen.getByTestId('export-trigger'));
    await waitFor(() => expect(onError).toHaveBeenCalled());
    expect(onError.mock.calls[0][0]).toMatch(/boom|thất bại/i);
  });
});
'use client';

import { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react';
import type { ShiftType, Staff } from '@/types/api';
import { api } from '@/lib/api';
import { getErrorMessage } from '@/lib/errors';
import { Button } from '@/components/ui/Button';

export type ExportFormat = 'excel-schedule' | 'pdf-schedule' | 'excel-workload';

export interface ExportControlsFilters {
  shiftTypeId?: string;
  staffId?: number;
  startDate?: string;
  endDate?: string;
}

/**
 * Shared export toolbar used by every reports page.
 *
 * Lets the manager pick:
 *   - a shift type (L01..L04) to focus on a single schedule type,
 *     per §M06-F04 ("theo từng loại lịch hoặc toàn phòng"),
 *   - a staff member to scope to one person,
 *   - a date range for partial-month exports.
 *
 * Then calls one of three export endpoints:
 *   - excel-schedule  -> GET /dashboard/export/schedule/{id}[?filters]
 *   - pdf-schedule    -> GET /dashboard/export/schedule/{id}/pdf[?filters]
 *   - excel-workload  -> GET /dashboard/export/workload/{id}[?filters]
 */
export interface ExportControlsProps {
  periodId: number;
  variant?: 'inline' | 'block';
  defaultFormat?: ExportFormat;
  /** Restrict formats available in the UI. */
  allowedFormats?: ExportFormat[];
  /** Hide the format selector and pin to this format. */
  pinFormat?: ExportFormat;
  /** Show the workload option (false for legacy reports). */
  showWorkload?: boolean;
  onError?: (message: string) => void;
  onSuccess?: (message: string) => void;
}

const FORMAT_LABEL: Record<ExportFormat, string> = {
  'excel-schedule': 'Xuất Excel',
  'pdf-schedule': 'Xuất PDF',
  'excel-workload': 'Xuất thống kê tải',
};

const FORMAT_FILENAME: Record<ExportFormat, string> = {
  'excel-schedule': 'lich-cong-tac.xlsx',
  'pdf-schedule': 'lich-cong-tac.pdf',
  'excel-workload': 'thong-ke-tai-nhan-su.xlsx',
};

const FORMAT_MIME: Record<ExportFormat, string> = {
  'excel-schedule':
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'pdf-schedule': 'application/pdf',
  'excel-workload':
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
};

export function ExportControls({
  periodId,
  variant = 'inline',
  defaultFormat = 'excel-schedule',
  allowedFormats,
  pinFormat,
  showWorkload = false,
  onError,
  onSuccess,
}: ExportControlsProps) {
  const uid = useId();
  const [staffOptions, setStaffOptions] = useState<Staff[]>([]);
  const [shiftTypes, setShiftTypes] = useState<ShiftType[]>([]);
  const [shiftTypeId, setShiftTypeId] = useState('');
  const [staffId, setStaffId] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [format, setFormat] = useState<ExportFormat>(pinFormat ?? defaultFormat);
  const [loading, setLoading] = useState(false);
  const [optionsLoaded, setOptionsLoaded] = useState(false);
  const lastPeriodId = useRef<number | null>(null);

  const formats = useMemo<ExportFormat[]>(() => {
    if (pinFormat) return [pinFormat];
    const all = showWorkload
      ? (['excel-schedule', 'pdf-schedule', 'excel-workload'] as ExportFormat[])
      : (['excel-schedule', 'pdf-schedule'] as ExportFormat[]);
    return allowedFormats ? all.filter((f) => allowedFormats.includes(f)) : all;
  }, [allowedFormats, pinFormat, showWorkload]);

  // Reset filters when the user navigates between periods so a stale
  // selection from the previous month never leaks into the export.
  useEffect(() => {
    if (lastPeriodId.current === periodId) return;
    lastPeriodId.current = periodId;
    setShiftTypeId('');
    setStaffId('');
    setStartDate('');
    setEndDate('');
  }, [periodId]);

  // Lazy-load dropdown options once per page mount; safe to call
  // multiple times since the response is small.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [staff, shifts] = await Promise.all([
          api.getAllStaff(),
          api.getAllShiftTypes(),
        ]);
        if (cancelled) return;
        setStaffOptions(staff?.data ?? []);
        setShiftTypes(shifts?.data ?? []);
        setOptionsLoaded(true);
      } catch (err) {
        if (cancelled) return;
        onError?.(getErrorMessage(err, 'Không tải được bộ lọc.'));
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [onError]);

  const handleExport = useCallback(async () => {
    if (!periodId || loading) return;
    setLoading(true);
    try {
      const filters: ExportControlsFilters = {};
      if (shiftTypeId) filters.shiftTypeId = shiftTypeId;
      if (staffId) filters.staffId = Number(staffId);
      if (startDate) filters.startDate = startDate;
      if (endDate) filters.endDate = endDate;

      let blob: Blob;
      switch (format) {
        case 'excel-schedule':
          blob = await api.exportScheduleExcel(periodId, filters);
          break;
        case 'pdf-schedule':
          blob = await api.exportSchedulePdf(periodId, filters);
          break;
        case 'excel-workload':
          blob = await api.exportWorkloadExcel(periodId, filters);
          break;
        default: {
          const _exhaustive: never = format;
          throw new Error(`Unsupported format: ${String(_exhaustive)}`);
        }
      }

      const url = URL.createObjectURL(
        new Blob([blob], { type: FORMAT_MIME[format] }),
      );
      const a = document.createElement('a');
      a.href = url;
      a.download = FORMAT_FILENAME[format];
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
      onSuccess?.('Đã tạo file báo cáo.');
    } catch (err) {
      onError?.(getErrorMessage(err, 'Xuất báo cáo thất bại.'));
    } finally {
      setLoading(false);
    }
  }, [
    periodId,
    loading,
    shiftTypeId,
    staffId,
    startDate,
    endDate,
    format,
    onError,
    onSuccess,
  ]);

  const hasFilters = Boolean(shiftTypeId || staffId || startDate || endDate);

  const wrapperClass =
    variant === 'block'
      ? 'flex flex-col gap-3 rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm'
      : 'flex flex-wrap items-end gap-2';

  return (
    <div className={wrapperClass} data-testid="export-controls">
      <div className="flex flex-wrap items-end gap-2 flex-1">
        {/* Shift type */}
        <label
          htmlFor={`${uid}-shift`}
          className="flex flex-col gap-1 text-[12px] font-medium text-on-surface-variant"
        >
          Loại lịch
          <select
            id={`${uid}-shift`}
            value={shiftTypeId}
            onChange={(e) => setShiftTypeId(e.target.value)}
            disabled={loading || !optionsLoaded}
            className="h-9 min-w-[140px] rounded-lg border border-outline-variant bg-surface-container-lowest px-2 text-[13px] text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
          >
            <option value="">Tất cả</option>
            {shiftTypes.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        </label>

        {/* Staff */}
        <label
          htmlFor={`${uid}-staff`}
          className="flex flex-col gap-1 text-[12px] font-medium text-on-surface-variant"
        >
          Nhân sự
          <select
            id={`${uid}-staff`}
            value={staffId}
            onChange={(e) => setStaffId(e.target.value)}
            disabled={loading || !optionsLoaded}
            className="h-9 min-w-[160px] rounded-lg border border-outline-variant bg-surface-container-lowest px-2 text-[13px] text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
          >
            <option value="">Tất cả</option>
            {staffOptions.map((s) => (
              <option key={s.id} value={s.id}>
                {s.fullName}
              </option>
            ))}
          </select>
        </label>

        {/* Date range */}
        <label
          htmlFor={`${uid}-start`}
          className="flex flex-col gap-1 text-[12px] font-medium text-on-surface-variant"
        >
          Từ ngày
          <input
            id={`${uid}-start`}
            type="date"
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
            disabled={loading}
            className="h-9 rounded-lg border border-outline-variant bg-surface-container-lowest px-2 text-[13px] text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
          />
        </label>
        <label
          htmlFor={`${uid}-end`}
          className="flex flex-col gap-1 text-[12px] font-medium text-on-surface-variant"
        >
          Đến ngày
          <input
            id={`${uid}-end`}
            type="date"
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
            disabled={loading}
            className="h-9 rounded-lg border border-outline-variant bg-surface-container-lowest px-2 text-[13px] text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
          />
        </label>

        {hasFilters && (
          <button
            type="button"
            onClick={() => {
              setShiftTypeId('');
              setStaffId('');
              setStartDate('');
              setEndDate('');
            }}
            disabled={loading}
            className="h-9 rounded-lg px-3 text-[12px] font-medium text-on-surface-variant underline-offset-2 hover:underline disabled:opacity-60"
          >
            Xoá lọc
          </button>
        )}
      </div>

      <div className="flex flex-wrap items-center gap-2">
        {formats.length > 1 && !pinFormat && (
          <label
            htmlFor={`${uid}-format`}
            className="flex items-center gap-2 text-[12px] font-medium text-on-surface-variant"
          >
            Định dạng:
            <select
              id={`${uid}-format`}
              value={format}
              onChange={(e) => setFormat(e.target.value as ExportFormat)}
              disabled={loading}
              className="h-9 rounded-lg border border-outline-variant bg-surface-container-lowest px-2 text-[13px] text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
            >
              {formats.map((f) => (
                <option key={f} value={f}>
                  {FORMAT_LABEL[f]}
                </option>
              ))}
            </select>
          </label>
        )}

        <Button
          variant="primary"
          size="sm"
          onClick={handleExport}
          disabled={loading || !periodId}
          icon={
            <span className="material-symbols-outlined text-[16px]">
              download
            </span>
          }
          data-testid="export-trigger"
        >
          {loading ? 'Đang xuất...' : pinFormat ? FORMAT_LABEL[pinFormat] : 'Xuất báo cáo'}
        </Button>
      </div>
    </div>
  );
}
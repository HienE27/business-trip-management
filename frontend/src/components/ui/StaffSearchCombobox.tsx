"use client";

import { memo, useCallback, useEffect, useRef, useState } from "react";
import type { LeaveRequest, Staff } from "@/types/api";

type GroupedStaff = {
  available: Staff[];
  unavailableCompDay: Staff[];
  unavailableExisting: Staff[];
  unavailableConflict: Staff[];
  unavailableOnLeave: Staff[];
};

function groupStaff(
  list: Staff[],
  workDate: string,
  existingScheduleStaffIds: Set<number>,
  compensationDates: Set<string>,
  existingConflictStaffIds: Set<number>,
  onLeaveStaffIds: Set<number>,
): GroupedStaff {
  const available: Staff[] = [];
  const unavailableCompDay: Staff[] = [];
  const unavailableExisting: Staff[] = [];
  const unavailableConflict: Staff[] = [];
  const unavailableOnLeave: Staff[] = [];

  for (const s of list) {
    if (onLeaveStaffIds.has(s.id)) {
      unavailableOnLeave.push(s);
    } else if (compensationDates.has(`${s.id}|${workDate}`)) {
      unavailableCompDay.push(s);
    } else if (existingScheduleStaffIds.has(s.id)) {
      unavailableExisting.push(s);
    } else if (existingConflictStaffIds.has(s.id)) {
      unavailableConflict.push(s);
    } else {
      available.push(s);
    }
  }

  return { available, unavailableCompDay, unavailableExisting, unavailableConflict, unavailableOnLeave };
}

export type StaffSearchComboboxProps = {
  value: number | "";
  onChange: (id: number | "") => void;
  staffList: Staff[];
  workDate: string;
  compensationDays?: Array<{ staffId: number; compensationDate: string }>;
  existingScheduleStaffIds?: number[];
  /** Staff IDs that have a conflicting schedule of the opposite type (e.g., L02 when selecting L01). */
  existingConflictStaffIds?: number[];
  /** Approved leave requests — staff on approved leave for workDate are grouped separately. */
  leaveRequests?: LeaveRequest[];
  placeholder?: string;
  disabled?: boolean;
  error?: string;
  required?: boolean;
  loading?: boolean;
};

export const StaffSearchCombobox = memo(function StaffSearchCombobox({
  value,
  onChange,
  staffList,
  workDate,
  compensationDays = [],
  existingScheduleStaffIds = [],
  existingConflictStaffIds = [],
  leaveRequests = [],
  placeholder = "Tìm kiếm hoặc chọn nhân sự…",
  disabled,
  error,
  required,
  loading,
}: StaffSearchComboboxProps) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [activeIndex, setActiveIndex] = useState(-1);

  const inputRef = useRef<HTMLInputElement>(null);
  const triggerRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const listRootRef = useRef<HTMLDivElement>(null);

  // Track whether a mousedown is in progress — used to defer the
  // click-outside close decision until the full click cycle completes.
  // Without this flag, clicking the trigger closes the dropdown because
  // the trigger's onClick fires and calls closeDropdown() synchronously,
  // then the document's click listener also sees the event and re-evaluates
  // open=true, closing it again before the next animation frame.
  const isMousedown = useRef(false);

  const selectedStaff = staffList.find((s) => s.id === Number(value));

  const existingSet = new Set(existingScheduleStaffIds);
  const conflictSet = new Set(existingConflictStaffIds);
  const compDates = new Set(compensationDays.map((c) => `${c.staffId}|${c.compensationDate}`));

  // Derive staff IDs that are on approved leave for the given workDate
  const onLeaveStaffIds = new Set<number>();
  if (workDate) {
    for (const lr of leaveRequests) {
      if (lr.staffId != null && workDate >= lr.startDate && workDate <= lr.endDate) {
        onLeaveStaffIds.add(lr.staffId);
      }
    }
  }

  const grouped = groupStaff(staffList, workDate, existingSet, compDates, conflictSet, onLeaveStaffIds);

  const filtered = query.trim()
    ? {
        available: grouped.available.filter(
          (s) =>
            s.fullName.toLowerCase().includes(query.toLowerCase()) ||
            s.username.toLowerCase().includes(query.toLowerCase()) ||
            s.specialty?.name.toLowerCase().includes(query.toLowerCase()) ||
            s.position?.toLowerCase().includes(query.toLowerCase()),
        ),
        unavailableCompDay: grouped.unavailableCompDay.filter(
          (s) =>
            s.fullName.toLowerCase().includes(query.toLowerCase()) ||
            s.username.toLowerCase().includes(query.toLowerCase()),
        ),
        unavailableExisting: grouped.unavailableExisting.filter(
          (s) =>
            s.fullName.toLowerCase().includes(query.toLowerCase()) ||
            s.username.toLowerCase().includes(query.toLowerCase()),
        ),
        unavailableConflict: grouped.unavailableConflict.filter(
          (s) =>
            s.fullName.toLowerCase().includes(query.toLowerCase()) ||
            s.username.toLowerCase().includes(query.toLowerCase()),
        ),
        unavailableOnLeave: grouped.unavailableOnLeave.filter(
          (s) =>
            s.fullName.toLowerCase().includes(query.toLowerCase()) ||
            s.username.toLowerCase().includes(query.toLowerCase()),
        ),
      }
    : grouped;

  const totalAvailable = filtered.available.length;
  const totalUnavailable =
    filtered.unavailableCompDay.length +
    filtered.unavailableExisting.length +
    filtered.unavailableConflict.length +
    filtered.unavailableOnLeave.length;
  const hasResults = totalAvailable + totalUnavailable > 0;

  const openDropdown = useCallback(() => {
    if (disabled) return;
    setOpen(true);
    setActiveIndex(-1);
  }, [disabled]);

  const closeDropdown = useCallback(() => {
    setOpen(false);
    setQuery("");
    setActiveIndex(-1);
  }, []);

  // Focus the search input whenever the dropdown opens.
  useEffect(() => {
    if (open) {
      // Small delay ensures the input is mounted and visible.
      const raf = requestAnimationFrame(() => {
        inputRef.current?.focus();
      });
      return () => cancelAnimationFrame(raf);
    }
  }, [open]);

  // Reset active index whenever the search query changes.
  useEffect(() => {
    setActiveIndex(-1);
  }, [query]);

  // Scroll the active item into view.
  useEffect(() => {
    if (!open || activeIndex < 0) return;
    const item = listRef.current?.children[activeIndex] as HTMLElement | undefined;
    (item as HTMLElement | undefined)?.scrollIntoView?.({ block: "nearest" });
  }, [activeIndex, open]);

  // Click-outside: close the dropdown when the user clicks anywhere outside
  // the trigger or dropdown. We use explicit refs (not closest()) so
  // hovering into the dropdown from outside never triggers the close.
  //
  // The isMousedown flag is critical: when the trigger is clicked, React's
  // onClick fires synchronously and calls closeDropdown(), which sets open=false.
  // If the document click listener also fires during the same event cycle,
  // it would re-evaluate open=true (react state update is async) and might
  // close an already-closed dropdown. By checking isMousedown.current, the
  // listener defers its decision until after the click cycle completes.
  const closeOnOutsideClick = useCallback((e: MouseEvent) => {
    if (isMousedown.current) {
      // This is the click that opened the dropdown — skip.
      isMousedown.current = false;
      return;
    }
    const target = e.target as Node;
    if (triggerRef.current?.contains(target)) return;
    if (listRootRef.current?.contains(target)) return;
    closeDropdown();
  }, [closeDropdown]);

  useEffect(() => {
    if (!open) return;
    document.addEventListener("click", closeOnOutsideClick);
    return () => document.removeEventListener("click", closeOnOutsideClick);
  }, [open, closeOnOutsideClick]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent<HTMLInputElement>) => {
      switch (e.key) {
        case "ArrowDown": {
          e.preventDefault();
          if (!open) { openDropdown(); return; }
          setActiveIndex((i) => Math.min(i + 1, totalAvailable - 1));
          break;
        }
        case "ArrowUp": {
          e.preventDefault();
          setActiveIndex((i) => Math.max(i - 1, -1));
          break;
        }
        case "Enter": {
          e.preventDefault();
          if (activeIndex >= 0 && activeIndex < totalAvailable) {
            onChange(filtered.available[activeIndex].id);
            closeDropdown();
          }
          break;
        }
        case "Escape": {
          e.preventDefault();
          closeDropdown();
          break;
        }
        case "Tab": {
          closeDropdown();
          break;
        }
        default:
          if (!open) openDropdown();
          break;
      }
    },
    [open, openDropdown, activeIndex, totalAvailable, filtered, onChange, closeDropdown],
  );

  const handleSelect = (staff: Staff, blocked: boolean) => {
    if (blocked) return;
    onChange(staff.id);
    closeDropdown();
  };

  const handleClear = (e: React.MouseEvent) => {
    e.stopPropagation();
    onChange("");
    setQuery("");
  };

  // Capture phase: set flag BEFORE any React event handlers run.
  // This is the key to fixing the hover-inside-dropdown bug.
  const handleTriggerMouseDown = () => {
    isMousedown.current = true;
  };

  const borderColor = error
    ? "border-error"
    : open && hasResults
    ? "border-primary ring-1 ring-primary/20"
    : "border-outline-variant focus-within:border-primary focus-within:ring-1 focus-within:ring-primary/20";

  const triggerContent = selectedStaff ? (
    <div className="flex items-center gap-2.5 px-3 py-2 min-w-0 flex-1">
      <div className="w-8 h-8 rounded-full bg-blue-100 flex items-center justify-center shrink-0" aria-hidden="true">
        <span className="text-[13px] font-bold text-blue-800 leading-none">
          {selectedStaff.fullName.split(" ").slice(-2).map((n) => n[0]).join("").toUpperCase()}
        </span>
      </div>
      <div className="min-w-0 flex-1">
        <p className="text-body-sm font-semibold text-on-surface truncate leading-tight">{selectedStaff.fullName}</p>
        <p className="text-label-sm text-on-surface-variant truncate leading-tight">
          {[selectedStaff.specialty?.name, selectedStaff.position].filter(Boolean).join(" · ") || "Nhân sự"}
        </p>
      </div>
    </div>
  ) : (
    <div className="flex items-center gap-2 px-3 py-2.5 flex-1">
      <span className="material-symbols-outlined text-[20px] text-outline shrink-0" aria-hidden="true">person</span>
      <span className="text-body-sm text-outline truncate">{placeholder}</span>
    </div>
  );

  return (
    <div className="flex flex-col gap-1" data-combobox-root>
      {/* Trigger */}
      <div
        ref={triggerRef}
        className={[
          "relative flex items-center rounded-lg border bg-surface-container-low transition-all",
          disabled ? "opacity-60 cursor-not-allowed" : "cursor-pointer",
          borderColor,
        ].join(" ")}
        onClick={openDropdown}
        onMouseDown={handleTriggerMouseDown}
        role="combobox"
        aria-expanded={open}
        aria-haspopup="listbox"
        aria-controls="staff-listbox"
        aria-label="Nhân sự"
        aria-invalid={error ? "true" : undefined}
        tabIndex={disabled ? -1 : 0}
        onKeyDown={(e) => {
          if (e.key === "Enter" || e.key === " ") { e.preventDefault(); openDropdown(); }
          if (e.key === "ArrowDown") { e.preventDefault(); openDropdown(); }
        }}
      >
        {triggerContent}

        <div className="flex items-center gap-1 pr-3 shrink-0">
          {selectedStaff && !disabled && (
            <button
              type="button"
              className="p-1 rounded hover:bg-surface-container-high transition-colors"
              onClick={handleClear}
              aria-label="Xóa lựa chọn"
            >
              <span className="material-symbols-outlined text-[16px] text-outline hover:text-on-surface transition-colors" aria-hidden="true">close</span>
            </button>
          )}
          <span className="material-symbols-outlined text-[20px] text-outline shrink-0" aria-hidden="true">
            {open ? "expand_less" : "expand_more"}
          </span>
        </div>

        {/* Accessible hidden input */}
        <input
          type="text"
          role="option"
          disabled={disabled}
          required={required}
          value={selectedStaff ? `${selectedStaff.id}` : ""}
          onChange={() => {}}
          className="sr-only"
          tabIndex={-1}
          aria-hidden="true"
          readOnly
        />
      </div>

      {/* Dropdown */}
      {open && (
        <div
          className="relative z-10 mt-1"
          data-combobox-dropdown
          ref={listRootRef}
        >
          <div
            className="absolute left-0 right-0 z-50 flex flex-col bg-surface-container-lowest border border-outline-variant rounded-xl shadow-2xl overflow-hidden animate-scale-in"
            style={{ transformOrigin: "top" }}
          >
            {/* Search bar */}
            <div className="p-2 border-b border-outline-variant shrink-0">
              <div className="relative">
                <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[20px]" aria-hidden="true">
                  search
                </span>
                <input
                  type="text"
                  value={query}
                  onChange={(e) => { setQuery(e.target.value); }}
                  onKeyDown={handleKeyDown}
                  ref={inputRef}
                  placeholder="Tìm theo tên, khoa, chức vụ…"
                  className="w-full pl-10 pr-4 py-2.5 bg-surface-container-low text-body-sm text-on-surface rounded-lg border border-transparent focus:border-blue-300 focus:bg-surface-container-lowest focus:outline-none focus:ring-1 focus:ring-blue-300 transition-all placeholder:text-outline"
                  aria-label="Tìm kiếm nhân sự"
                  autoComplete="off"
                />
              </div>
            </div>

            {/* Results */}
            <div
              className="max-h-72 overflow-y-auto py-1"
              ref={listRef}
              role="listbox"
              id="staff-listbox"
              aria-label="Danh sách nhân sự"
            >
              {loading ? (
                <div className="flex items-center gap-3 px-4 py-5">
                  <div className="w-5 h-5 rounded-full border-2 border-primary border-t-transparent animate-spin shrink-0" />
                  <span className="text-body-sm text-on-surface-variant">Đang tải nhân sự…</span>
                </div>
              ) : !hasResults ? (
                <div className="flex flex-col items-center gap-2 px-4 py-8 text-center">
                  <span className="material-symbols-outlined text-[36px] text-outline" aria-hidden="true">search_off</span>
                  <p className="text-body-sm text-on-surface-variant">
                    {query ? "Không tìm thấy nhân sự phù hợp" : "Không có nhân sự nào"}
                  </p>
                </div>
              ) : (
                <>
                  {filtered.available.length > 0 && (
                    <div>
                      <p className="px-4 pt-2 pb-1 text-label-sm text-on-surface-variant uppercase tracking-wide font-semibold">
                        Có thể chọn
                      </p>
                      {filtered.available.map((staff, i) => (
                        <StaffOption
                          key={staff.id}
                          staff={staff}
                          isActive={activeIndex === i}
                          isSelected={value === staff.id}
                          onSelect={() => handleSelect(staff, false)}
                          onHover={() => setActiveIndex(i)}
                        />
                      ))}
                    </div>
                  )}
                  {filtered.unavailableCompDay.length > 0 && (
                    <div>
                      <p className="px-4 pt-2 pb-1 text-label-sm text-tertiary uppercase tracking-wide font-semibold">
                        Nghỉ bù trong ngày
                      </p>
                      {filtered.unavailableCompDay.map((staff) => (
                        <StaffOption
                          key={staff.id}
                          staff={staff}
                          isActive={false}
                          isSelected={value === staff.id}
                          isUnavailable
                          unavailableIcon="event_busy"
                          unavailableText="Nghỉ bù"
                          onSelect={() => handleSelect(staff, true)}
                          onHover={() => {}}
                        />
                      ))}
                    </div>
                  )}
                  {filtered.unavailableExisting.length > 0 && (
                    <div>
                      <p className="px-4 pt-2 pb-1 text-label-sm text-tertiary uppercase tracking-wide font-semibold">
                        Đã có lịch ngày này
                      </p>
                      {filtered.unavailableExisting.map((staff) => (
                        <StaffOption
                          key={staff.id}
                          staff={staff}
                          isActive={false}
                          isSelected={value === staff.id}
                          isUnavailable
                          unavailableIcon="event_available"
                          unavailableText="Đã xếp"
                          onSelect={() => handleSelect(staff, true)}
                          onHover={() => {}}
                        />
                      ))}
                    </div>
                  )}
                  {filtered.unavailableOnLeave.length > 0 && (
                    <div>
                      <p className="px-4 pt-2 pb-1 text-label-sm text-tertiary uppercase tracking-wide font-semibold">
                        Nghỉ phép
                      </p>
                      {filtered.unavailableOnLeave.map((staff) => (
                        <StaffOption
                          key={staff.id}
                          staff={staff}
                          isActive={false}
                          isSelected={value === staff.id}
                          isUnavailable
                          unavailableIcon="event_busy"
                          unavailableText="Nghỉ phép"
                          onSelect={() => handleSelect(staff, true)}
                          onHover={() => {}}
                        />
                      ))}
                    </div>
                  )}
                  {filtered.unavailableConflict.length > 0 && (
                    <div>
                      <p className="px-4 pt-2 pb-1 text-label-sm text-red-800 uppercase tracking-wide font-semibold">
                        Xung đột loại lịch
                      </p>
                      {filtered.unavailableConflict.map((staff) => (
                        <StaffOption
                          key={staff.id}
                          staff={staff}
                          isActive={false}
                          isSelected={value === staff.id}
                          isUnavailable
                          unavailableIcon="warning"
                          unavailableText="Xung đột"
                          onSelect={() => handleSelect(staff, true)}
                          onHover={() => {}}
                        />
                      ))}
                    </div>
                  )}
                </>
              )}
            </div>

            {/* Keyboard hint */}
            <div className="px-4 py-2 border-t border-outline-variant flex items-center gap-4 shrink-0 bg-surface-container-low">
              <span className="flex items-center gap-1.5 text-label-sm text-on-surface-variant">
                <kbd className="px-1.5 py-0.5 rounded bg-surface-container-highest text-label-sm font-mono border border-outline-variant">↑↓</kbd> di chuyển
              </span>
              <span className="flex items-center gap-1.5 text-label-sm text-on-surface-variant">
                <kbd className="px-1.5 py-0.5 rounded bg-surface-container-highest text-label-sm font-mono border border-outline-variant">↵</kbd> chọn
              </span>
              <span className="flex items-center gap-1.5 text-label-sm text-on-surface-variant">
                <kbd className="px-1.5 py-0.5 rounded bg-surface-container-highest text-label-sm font-mono border border-outline-variant">Esc</kbd> đóng
              </span>
            </div>
          </div>
        </div>
      )}

      {/* Error */}
      {error && (
        <p className="flex items-center gap-1 text-label-sm text-red-800" role="alert">
          <span className="material-symbols-outlined text-[14px]" aria-hidden="true">error</span>
          {error}
        </p>
      )}
    </div>
  );
});

type StaffOptionProps = {
  staff: Staff;
  isActive: boolean;
  isSelected: boolean;
  isUnavailable?: boolean;
  unavailableIcon?: string;
  unavailableText?: string;
  onSelect: () => void;
  onHover: () => void;
};

function StaffOption({
  staff,
  isActive,
  isSelected,
  isUnavailable,
  unavailableIcon,
  unavailableText,
  onSelect,
  onHover,
}: StaffOptionProps) {
  return (
    <div
      role="option"
      aria-selected={isSelected}
      aria-disabled={isUnavailable}
      className={[
        "flex items-center gap-3 px-4 py-2.5 transition-colors",
        isUnavailable
          ? "opacity-60 cursor-not-allowed"
          : isActive
          ? "bg-blue-100 cursor-pointer"
          : isSelected
          ? "bg-blue-100 cursor-pointer"
          : "hover:bg-surface-container-low cursor-pointer",
      ].join(" ")}
      onClick={onSelect}
      onMouseEnter={onHover}
    >
      <div
        className={[
          "w-8 h-8 rounded-full flex items-center justify-center shrink-0 text-[12px] font-bold leading-none",
          isUnavailable ? "bg-surface-container-high text-outline" : "bg-blue-100 text-blue-800",
        ].join(" ")}
        aria-hidden="true"
      >
        {staff.fullName.split(" ").slice(-2).map((n) => n[0]).join("").toUpperCase()}
      </div>

      <div className="min-w-0 flex-1">
        <p className="text-body-sm font-semibold text-on-surface truncate leading-tight">{staff.fullName}</p>
        <p className="text-label-sm text-on-surface-variant truncate leading-tight">
          {[staff.specialty?.name, staff.position].filter(Boolean).join(" · ") || "Nhân sự"}
        </p>
      </div>

      {isSelected && !isUnavailable && (
        <span className="material-symbols-outlined text-blue-800 shrink-0" aria-label="Đã chọn" style={{ fontVariationSettings: "'FILL' 1" }}>
          check
        </span>
      )}

      {isUnavailable && unavailableIcon && (
        <span className="material-symbols-outlined text-tertiary shrink-0 text-[16px]" title={unavailableText}>
          {unavailableIcon}
        </span>
      )}
    </div>
  );
}

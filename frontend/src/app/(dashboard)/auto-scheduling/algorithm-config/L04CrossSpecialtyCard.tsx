"use client";

import { useState, useEffect } from "react";

type L04SpecialtyConfigProps = {
  enabled: boolean;
  ratio: number;
  allowedSpecialties: string[];
  allSpecialties: string[];
  editing: boolean;
  onChange: (enabled: boolean, ratio: number, allowedSpecialties: string[]) => void;
};

export function L04SpecialtyConfig({ 
  enabled, 
  ratio, 
  allowedSpecialties, 
  allSpecialties, 
  editing, 
  onChange 
}: L04SpecialtyConfigProps) {
  const [localRatio, setLocalRatio] = useState(ratio);
  const [localAllowed, setLocalAllowed] = useState<string[]>(allowedSpecialties);
  // Initial mode based on data:
  // - [] (empty) = "all" (backend default)
  // - [...allSpecialties] = "all" (explicit)
  // - partial = "partial"
  // - length 0 with no allSpecialties = "none"
  const [selectionMode, setSelectionMode] = useState<"all" | "partial" | "none">(() => {
    if (allowedSpecialties.length === 0 || allowedSpecialties.length === allSpecialties.length) {
      return "all";
    } else if (allowedSpecialties.length > 0) {
      return "partial";
    }
    return "none";
  });

  useEffect(() => {
    setLocalAllowed(allowedSpecialties);
    // Sync selection mode
    if (allowedSpecialties.length === 0 || allowedSpecialties.length === allSpecialties.length) {
      setSelectionMode("all");
    } else if (allowedSpecialties.length > 0) {
      setSelectionMode("partial");
    } else {
      setSelectionMode("none");
    }
  }, [allowedSpecialties, allSpecialties]);

  // Check if a specialty is selected based on current mode
  function isSpecialtySelected(specialty: string): boolean {
    if (selectionMode === "all") return true;
    if (selectionMode === "none") return false;
    return localAllowed.includes(specialty);
  }

  function handleToggle() {
    const newEnabled = !enabled;
    onChange(newEnabled, localRatio, localAllowed);
  }

  function handleRatioChange(value: number) {
    setLocalRatio(value);
    onChange(enabled, value, localAllowed);
  }

  function handleSpecialtyToggle(specialty: string) {
    let newAllowed: string[];
    if (selectionMode === "all") {
      // Clicking from "all" mode → deselect all except this one
      newAllowed = allSpecialties.filter(s => s !== specialty);
      setSelectionMode("partial");
    } else if (selectionMode === "none") {
      // Clicking from "none" mode → select this one only
      newAllowed = [specialty];
      setSelectionMode("partial");
    } else {
      // Partial mode → toggle normally
      if (localAllowed.includes(specialty)) {
        newAllowed = localAllowed.filter(s => s !== specialty);
        if (newAllowed.length === 0) setSelectionMode("none");
      } else {
        newAllowed = [...localAllowed, specialty];
      }
    }
    setLocalAllowed(newAllowed);
    onChange(enabled, localRatio, newAllowed);
  }

  function handleSelectAll() {
    // Select all → empty array (backend: "all specialties")
    setLocalAllowed([]);
    setSelectionMode("all");
    onChange(enabled, localRatio, []);
  }

  function handleClearAll() {
    // Clear all → ["__NONE__"] (backend: no specialties allowed)
    const noneMarker = ["__NONE__"];
    setLocalAllowed(noneMarker);
    setSelectionMode("none");
    onChange(enabled, localRatio, noneMarker);
  }

  return (
    <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant overflow-hidden hover:shadow-sm transition-shadow duration-200 border-l-4 border-l-tertiary">
      <div className="px-5 py-4 bg-surface-container-low flex items-center gap-3">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-surface-container text-tertiary">
          <span className="material-symbols-outlined text-[18px]" aria-hidden="true">medical_services</span>
        </div>
        <div className="flex-1">
          <p className="text-label-md font-semibold text-on-surface tracking-tight">Chuyên khoa cho L04</p>
          <p className="text-[11px] text-on-surface-variant">Chọn chuyên khoa được phép gán PK Chuyên gia</p>
        </div>
      </div>
      <div className="p-5 space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-label-sm text-on-surface font-medium">Bật giới hạn chuyên khoa</p>
            <p className="text-[11px] text-on-surface-variant mt-0.5">Chỉ gán nhân sự thuộc các chuyên khoa được chọn</p>
          </div>
          {editing ? (
            <button
              type="button"
              role="switch"
              aria-checked={enabled}
              onClick={handleToggle}
              className={`relative inline-flex h-7 w-12 shrink-0 items-center rounded-full border-2 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 ${
                enabled ? "bg-tertiary border-tertiary" : "bg-surface-container-high border-outline"
              }`}
            >
              <span className={`inline-block h-5 w-5 transform rounded-full bg-white shadow-sm transition-transform ${enabled ? "translate-x-6" : "translate-x-1"}`} />
            </button>
          ) : (
            <span className={`flex items-center gap-2 px-3 py-1.5 rounded-full text-label-sm font-semibold ${enabled ? "bg-surface-container text-tertiary border border-outline-variant" : "bg-surface-container-high text-outline"}`}>
              <span className={`h-2 w-2 rounded-full ${enabled ? "bg-tertiary" : "bg-outline"}`} />
              {enabled ? "Giới hạn" : "Tất cả"}
            </span>
          )}
        </div>

        {enabled && (
          <div className="pt-2 border-t border-outline-variant space-y-3">
            {/* Quick actions */}
            <div className="flex items-center justify-between">
              <p className="text-label-sm text-on-surface font-medium">
                {selectionMode === "all" ? "Tất cả chuyên khoa" : selectionMode === "none" ? "Không có chuyên khoa nào" : `${localAllowed.length}/${allSpecialties.length} chuyên khoa`}
              </p>
              {editing && (
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={handleSelectAll}
                    className="text-[11px] text-primary hover:underline"
                  >
                    Chọn tất cả
                  </button>
                  <span className="text-outline">|</span>
                  <button
                    type="button"
                    onClick={handleClearAll}
                    className="text-[11px] text-tertiary hover:underline"
                  >
                    Bỏ chọn tất cả
                  </button>
                </div>
              )}
            </div>

            {/* Specialty chips */}
            {editing ? (
              <div className="flex flex-wrap gap-2">
                {allSpecialties.map((specialty) => {
                  const isSelected = isSpecialtySelected(specialty);
                  return (
                    <button
                      key={specialty}
                      type="button"
                      onClick={() => handleSpecialtyToggle(specialty)}
                      className={`px-3 py-1.5 rounded-lg text-label-sm font-medium transition-all ${
                        isSelected
                          ? "bg-tertiary text-white border border-tertiary"
                          : "bg-surface-container text-on-surface-variant border border-outline hover:border-tertiary hover:text-tertiary"
                      }`}
                    >
                      {specialty}
                    </button>
                  );
                })}
              </div>
            ) : (
              <div className="flex flex-wrap gap-2">
                {selectionMode === "all" ? (
                  <span className="text-[12px] text-on-surface-variant">
                    Tất cả chuyên khoa được phép
                  </span>
                ) : selectionMode === "none" ? (
                  <span className="text-[12px] text-error">
                    Không có chuyên khoa nào được phép
                  </span>
                ) : (
                  localAllowed.map((specialty) => (
                    <span
                      key={specialty}
                      className="px-2.5 py-1 rounded-lg text-[12px] font-medium bg-tertiary-container text-on-tertiary-container"
                    >
                      {specialty}
                    </span>
                  ))
                )}
              </div>
            )}

            {/* Cross-specialty ratio */}
            <div className="flex items-center justify-between mt-3 pt-3 border-t border-outline-variant/50">
              <div>
                <p className="text-label-sm text-on-surface font-medium">Cross-specialty ratio</p>
                <p className="text-[11px] text-on-surface-variant mt-0.5">Tỷ lệ staff ngoài danh sách cho mỗi ca</p>
              </div>
              <span className="font-mono text-lg font-bold text-tertiary tabular-nums">
                {Math.round(ratio * 100)}%
              </span>
            </div>
            {editing && (
              <input
                type="range"
                min="0"
                max="100"
                step="5"
                value={Math.round(localRatio * 100)}
                onChange={(e) => handleRatioChange(parseInt(e.target.value) / 100)}
                className="w-full h-2 bg-surface-variant rounded-full appearance-none cursor-pointer accent-tertiary"
              />
            )}
          </div>
        )}

        {!enabled && (
          <div className="flex items-start gap-2 p-3 rounded-lg bg-surface-container-low">
            <span className="material-symbols-outlined text-[16px] text-tertiary shrink-0 mt-0.5" aria-hidden="true">info</span>
            <p className="text-[12px] text-on-surface-variant leading-relaxed">
              Tất cả nhân sự đều có thể được gán vào L04 (PK Chuyên gia). Bật lên để giới hạn theo chuyên khoa cụ thể.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}

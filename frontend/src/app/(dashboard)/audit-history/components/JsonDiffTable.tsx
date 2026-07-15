"use client";

import { parseJson, isMetaKey, prettyKey, fmtVal } from "./auditUtils";

// ─── JSON Diff Table ───────────────────────────────────────────────────────────

export function JsonDiffTable({ oldJson, newJson }: { oldJson?: string; newJson?: string }) {
  const m1 = parseJson(oldJson ?? "") ?? {};
  const m2 = parseJson(newJson ?? "") ?? {};
  const allKeys = Array.from(new Set([...Object.keys(m1), ...Object.keys(m2)])).filter((k) => !isMetaKey(k));
  const changed = allKeys.filter((k) => JSON.stringify(m1[k]) !== JSON.stringify(m2[k]));
  const added = allKeys.filter((k) => !(k in m1) && k in m2);
  const removed = allKeys.filter((k) => k in m1 && !(k in m2));

  if (!changed.length && !added.length && !removed.length) {
    return (
      <div className="flex items-center gap-2 text-[13px] text-secondary py-3">
        <span className="material-symbols-outlined text-[16px]">check_circle</span>
        Không có thay đổi dữ liệu.
      </div>
    );
  }

  return (
    <div className="space-y-1.5">
      {changed.length > 0 && (
        <>
          <p className="text-[11px] font-semibold text-on-surface-variant uppercase tracking-wide mb-1.5">
            {changed.length} thay đổi
          </p>
          {changed.map((k) => (
            <div key={k} className="grid grid-cols-2 rounded-lg overflow-hidden border border-outline-variant text-[12px]">
              <div className="bg-surface-container-low border-r border-outline-variant px-3 py-2">
                <p className="text-[10px] text-error font-medium mb-0.5 leading-none uppercase tracking-wide">{prettyKey(k)}</p>
                <p className="text-on-surface font-medium leading-snug mt-0.5">{fmtVal(m1[k])}</p>
              </div>
              <div className="bg-surface-container-lowest px-3 py-2">
                <p className="text-[10px] text-secondary font-medium mb-0.5 leading-none uppercase tracking-wide">{prettyKey(k)}</p>
                <p className="text-on-surface font-medium leading-snug mt-0.5">{fmtVal(m2[k])}</p>
              </div>
            </div>
          ))}
        </>
      )}
      {added.length > 0 && (
        <>
          <p className="text-[11px] font-semibold text-secondary mt-3 mb-1.5 uppercase tracking-wide">{added.length} mới thêm</p>
          {added.map((k) => (
            <div key={k} className="flex items-center gap-3 px-3 py-2 rounded-lg bg-secondary-container border border-secondary/20 text-[12px]">
              <span className="material-symbols-outlined text-[14px] text-secondary shrink-0">add</span>
              <span className="text-secondary font-medium w-36 shrink-0">{prettyKey(k)}</span>
              <span className="text-on-surface font-medium">{fmtVal(m2[k])}</span>
            </div>
          ))}
        </>
      )}
      {removed.length > 0 && (
        <>
          <p className="text-[11px] font-semibold text-error mt-3 mb-1.5 uppercase tracking-wide">{removed.length} đã xóa</p>
          {removed.map((k) => (
            <div key={k} className="flex items-center gap-3 px-3 py-2 rounded-lg bg-error-container border border-error/20 text-[12px]">
              <span className="material-symbols-outlined text-[14px] text-error shrink-0">remove</span>
              <span className="text-error font-medium w-36 shrink-0">{prettyKey(k)}</span>
              <span className="text-on-surface font-medium">{fmtVal(m1[k])}</span>
            </div>
          ))}
        </>
      )}
    </div>
  );
}

export type ActionFilter = "" | "CREATE" | "UPDATE" | "DELETE";
export type DateRange = "today" | "yesterday" | "7d" | "30d" | "custom";

// ─── Style constants ───────────────────────────────────────────────────────────

export const ACTION_STYLE: Record<string, {
  label: string;
  icon: string;
  iconBg: string;
  chipBg: string;
  chipColor: string;
}> = {
  CREATE: {
    label: "Tạo mới",
    icon: "add_circle",
    iconBg: "bg-secondary-container text-secondary",
    chipBg: "bg-secondary-container text-secondary",
    chipColor: "text-secondary",
  },
  UPDATE: {
    label: "Cập nhật",
    icon: "edit",
    iconBg: "bg-primary-fixed text-primary",
    chipBg: "bg-primary-fixed text-primary",
    chipColor: "text-primary",
  },
  DELETE: {
    label: "Xóa",
    icon: "delete",
    iconBg: "bg-error-container text-error",
    chipBg: "bg-error-container text-error",
    chipColor: "text-error",
  },
};

export function getAction(action: string) {
  return ACTION_STYLE[action] ?? {
    label: action,
    icon: "info",
    iconBg: "bg-surface-container-high text-on-surface-variant",
    chipBg: "bg-surface-container-high text-on-surface-variant",
    chipColor: "text-on-surface-variant",
  };
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

export function fmtTime(dateStr: string) {
  try {
    return new Date(dateStr).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" });
  } catch { return dateStr; }
}

const VI_DAY_SHORT = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"];

export function fmtDateShort(dateKey: string) {
  const d = new Date(dateKey + "T12:00:00");
  return `${VI_DAY_SHORT[d.getDay()]}, ${d.toLocaleDateString("vi-VN")}`;
}

export function getDateRange(range: DateRange): { from?: string; to?: string } {
  // Use local date (not UTC) so it matches the backend's Asia/Ho_Chi_Minh timezone
  // that audit_history.created_at is stored in.
  const now = new Date();
  const yyyy = now.getFullYear();
  const mm = String(now.getMonth() + 1).padStart(2, "0");
  const dd = String(now.getDate()).padStart(2, "0");
  const to = `${yyyy}-${mm}-${dd}`;
  switch (range) {
    case "today":    return { from: to, to };
    case "yesterday": {
      const y = new Date(now);
      y.setDate(y.getDate() - 1);
      const yy = y.getFullYear();
      const ym = String(y.getMonth() + 1).padStart(2, "0");
      const yd = String(y.getDate()).padStart(2, "0");
      return { from: `${yy}-${ym}-${yd}`, to: `${yy}-${ym}-${yd}` };
    }
    case "7d":  {
      const s = new Date(now);
      s.setDate(s.getDate() - 7);
      const sy = s.getFullYear();
      const sm = String(s.getMonth() + 1).padStart(2, "0");
      const sd = String(s.getDate()).padStart(2, "0");
      return { from: `${sy}-${sm}-${sd}`, to };
    }
    case "30d": {
      const s = new Date(now);
      s.setDate(s.getDate() - 30);
      const sy = s.getFullYear();
      const sm = String(s.getMonth() + 1).padStart(2, "0");
      const sd = String(s.getDate()).padStart(2, "0");
      return { from: `${sy}-${sm}-${sd}`, to };
    }
    default: return {};
  }
}

export function subDateStr(days: number) {
  const d = new Date();
  d.setDate(d.getDate() - days);
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
}

export const todayStr = (() => {
  const d = new Date();
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
})();

export function isToday(dateKey: string) {
  const d = new Date();
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  const todayLocal = `${yyyy}-${mm}-${dd}`;
  return dateKey === todayLocal;
}

export function isYesterday(dateKey: string) {
  const d = new Date();
  d.setDate(d.getDate() - 1);
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  const yesterdayLocal = `${yyyy}-${mm}-${dd}`;
  return dateKey === yesterdayLocal;
}

// ─── JSON utils ───────────────────────────────────────────────────────────────

export const META_KEYS = new Set([
  "id", "Id", "ID",
  "createdAt", "created_at", "createAt", "create_at", "createDate", "createdDate",
  "updatedAt", "updated_at", "modifiedAt", "modified_at",
  "deletedAt", "deleted_at", "lastModified", "lastModifiedAt",
  "createdBy", "created_by", "updatedBy", "updated_by",
  "version", "uuid", "Uuid", "UID",
  "notificationType", "isRead", "readAt",
  "oldData", "newData",
]);

export function isMetaKey(k: string): boolean {
  return META_KEYS.has(k) ||
    /^(id|_id|.*[Ii]d$|.*[Tt]imestamp$|.*[Dd]ate$|.*[Bb]y$|.*[Uu]ser|.*[Uu]serId|.*[Uu]ser_Id)/.test(k) ||
    /(content|message|description|details|metadata|payload|params|data)$/i.test(k);
}

export function parseJson(raw?: string): Record<string, unknown> | null {
  if (!raw) return null;
  try {
    const p = JSON.parse(raw);
    return typeof p === "object" && p !== null ? p as Record<string, unknown> : null;
  } catch { return null; }
}

export function prettyKey(k: string) {
  return k.replace(/_/g, " ").replace(/([a-z])([A-Z])/g, "$1 $2").replace(/^\w/, (c) => c.toUpperCase());
}

export function fmtVal(v: unknown): string {
  if (v == null) return "\u2014";
  if (typeof v === "boolean") return v ? "Có" : "Không";
  if (typeof v === "number") return v.toLocaleString("vi");
  if (typeof v === "object") {
    const e = Object.entries(v as Record<string, unknown>);
    if (!e.length) return "\u2014";
    const n = e.find(([k]) => /(name|title|label)/i.test(k));
    return n ? fmtVal(n[1]) : `${e.length} trường`;
  }
  if (typeof v === "string") return v.length > 120 ? v.slice(0, 120) + "\u2026" : v;
  return String(v);
}

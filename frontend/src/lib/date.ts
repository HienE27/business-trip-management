const LOCALE = "vi-VN" as const;

// Cache formatters at module level for better performance
const DATE_FORMATTER = new Intl.DateTimeFormat(LOCALE, { day: "2-digit", month: "2-digit", year: "numeric" });
const DATE_FORMATTER_SHORT = new Intl.DateTimeFormat(LOCALE, { day: "2-digit", month: "2-digit" });
const DATE_FORMATTER_MEDIUM = new Intl.DateTimeFormat(LOCALE, { dateStyle: "medium" });
const DATE_FORMATTER_FULL = new Intl.DateTimeFormat(LOCALE, { dateStyle: "long" });
const DATE_TIME_FORMATTER = new Intl.DateTimeFormat(LOCALE, {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
});
const TIME_FORMATTER = new Intl.DateTimeFormat(LOCALE, { hour: "2-digit", minute: "2-digit" });

/** Short date: 15/06/2026 */
export const formatDate = (dateStr: string): string => {
  try {
    return DATE_FORMATTER.format(new Date(dateStr));
  } catch {
    return dateStr;
  }
};

/** Short date no year: 15/06 */
export const formatDateShortNoYear = (dateStr: string): string => {
  try {
    return DATE_FORMATTER_SHORT.format(new Date(dateStr));
  } catch {
    return dateStr;
  }
};

/** Medium date: 15 tháng 6, 2026 */
export const formatDateMedium = (dateStr: string): string => {
  try {
    return DATE_FORMATTER_MEDIUM.format(new Date(dateStr));
  } catch {
    return dateStr;
  }
};

/** Full date: thứ Hai, 15 tháng 6, 2026 */
export const formatDateFull = (dateStr: string): string => {
  try {
    return DATE_FORMATTER_FULL.format(new Date(dateStr));
  } catch {
    return dateStr;
  }
};

/** Date + time: 15/06/2026 14:30 */
export const formatDateTime = (dateStr: string): string => {
  try {
    return DATE_TIME_FORMATTER.format(new Date(dateStr));
  } catch {
    return dateStr;
  }
};

/** Time only: 14:30 */
export const formatTime = (dateStr: string): string => {
  try {
    return TIME_FORMATTER.format(new Date(dateStr));
  } catch {
    return dateStr;
  }
};

/** Relative time: "5 phút trước", "2 giờ trước", "3 ngày trước" */
export const formatRelativeTime = (dateStr: string): string => {
  try {
    const date = new Date(dateStr);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    if (diffMins < 1) return "Vừa xong";
    if (diffMins < 60) return `${diffMins} phút trước`;
    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `${diffHours} giờ trước`;
    const diffDays = Math.floor(diffHours / 24);
    if (diffDays < 7) return `${diffDays} ngày trước`;
    return formatDate(dateStr);
  } catch {
    return dateStr;
  }
};

/** Date range: "15/06/2026 – 20/06/2026" or single date if same */
export const formatDateRange = (startDate: string, endDate: string): string => {
  const start = formatDate(startDate);
  const end = formatDate(endDate);
  return start === end ? start : `${start} – ${end}`;
};

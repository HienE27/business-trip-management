/**
 * Safe number parsing utilities for handling BigDecimal from Java backend.
 * Java BigDecimal serializes to {value: [...], scale: n} format in JSON.
 */

/**
 * Parse a value that may be a number, string, or BigDecimal-like object.
 * Handles Java BigDecimal serialization: {value: number[], scale: number}
 */
export function parseNumber(val: unknown): number {
  if (val === null || val === undefined) return 0;
  if (typeof val === 'number' && !isNaN(val)) return val;
  if (typeof val === 'string') return parseFloat(val) || 0;
  
  // Handle BigDecimal-like objects: {value: [...], scale: number}
  if (val && typeof val === 'object' && !Array.isArray(val)) {
    const obj = val as { value?: number[]; scale?: number; _?: unknown };
    if (Array.isArray(obj.value) && typeof obj.scale === 'number') {
      const unscaled = obj.value[0] || 0;
      return unscaled / Math.pow(10, obj.scale);
    }
    // Also check for plain {value: number, scale: number} format
    if (typeof obj.value === 'number' && typeof obj.scale === 'number') {
      return obj.value / Math.pow(10, obj.scale);
    }
  }
  
  return 0;
}

/**
 * Format a number for display, handling BigDecimal inputs.
 */
export function formatPercent(val: unknown, decimals = 1): string {
  const num = parseNumber(val);
  if (isNaN(num)) return '—';
  return `${num.toFixed(decimals)}%`;
}

/**
 * Format coverage rate for display (rounded to integer).
 */
export function formatCoverageRate(val: unknown): string {
  const num = Math.round(parseNumber(val));
  return `${num}%`;
}

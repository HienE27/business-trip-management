/**
 * Sentinel values that the algorithm-config UI historically used to encode
 * "no specialties selected" in the l04AllowedSpecialties list.
 *
 * The backend has no notion of a "none" state for cross-specialty: an empty
 * list means "all specialties are eligible", and a non-empty list is an
 * explicit allowlist. Any string that isn't a real specialty name leaks into
 * the eligibility filter and either gets silently ignored or, worse, surfaces
 * as a chip in the UI labelled "__NONE__".
 *
 * Strip these sentinels everywhere we read from / write to form state so the
 * persisted config is always a valid allowlist.
 */
export const NONE_SENTINELS = new Set(["__NONE__", ""]);

export function sanitizeAllowedSpecialties(input: readonly string[] | null | undefined): string[] {
  if (!input) return [];
  const cleaned = input.filter((s) => typeof s === "string" && !NONE_SENTINELS.has(s));
  // Deduplicate while preserving order — defence in depth against legacy data.
  return Array.from(new Set(cleaned));
}

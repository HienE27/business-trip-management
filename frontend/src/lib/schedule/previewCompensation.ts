/**
 * Frontend port of backend `CompensationDateCalculator` — pure date math,
 * no holiday awareness. Computes the default compensation date for an L01
 * (24/24) shift per spec rules:
 *
 *   Mon duty → Tue (next day)
 *   Tue duty → Wed (next day)
 *   Wed duty → Thu (next day)
 *   Thu duty → Fri (next day)
 *   Fri duty → Tue next week (skip Mon, skip Fri)
 *   Sat duty → Tue next week (skip Mon, skip Fri)
 *   Sun duty → Mon (next day)
 *
 * The frontend does not have access to the holiday repository, so we skip
 * the holiday-avoidance pass; the backend still enforces it on apply.
 *
 * Used by the auto-scheduling preview grid so users can see "🌙 Nghỉ bù"
 * indicators next to L01 schedules *before* clicking Apply.
 */

/** Find next occurrence of `target` from `start` inclusive (forward). */
function findNextDayOfWeek(start: Date, target: number): Date {
  const d = new Date(start);
  for (let i = 0; i < 7; i++) {
    if (d.getDay() === target) return d;
    d.setDate(d.getDate() + 1);
  }
  return d;
}

/**
 * Default compensation date for a single L01 shift date.
 * Returns YYYY-MM-DD string (local time) suitable for matrix lookup.
 */
export function calculateCompensationDate(shiftDate: string): string {
  // Parse YYYY-MM-DD into local Date at midnight to avoid TZ drift.
  const [y, m, d] = shiftDate.split("T")[0].split("-").map(Number);
  const duty = new Date(y, m - 1, d);
  const dow = duty.getDay(); // 0=Sun, 1=Mon, ..., 6=Sat

  let comp: Date;
  switch (dow) {
    case 1: // Mon → Tue
    case 2: // Tue → Wed
    case 3: // Wed → Thu
    case 4: // Thu → Fri
      comp = new Date(duty);
      comp.setDate(duty.getDate() + 1);
      break;
    case 5: // Fri → Tue next week (Fri + 4 days = Tue next week)
      comp = findNextDayOfWeek(
        new Date(duty.getFullYear(), duty.getMonth(), duty.getDate() + 4),
        2, // Tue
      );
      break;
    case 6: // Sat → Tue next week (Sat + 3 days = Tue next week)
      comp = findNextDayOfWeek(
        new Date(duty.getFullYear(), duty.getMonth(), duty.getDate() + 3),
        2, // Tue
      );
      break;
    case 0: // Sun → Mon
      comp = findNextDayOfWeek(
        new Date(duty.getFullYear(), duty.getMonth(), duty.getDate() + 1),
        1, // Mon
      );
      break;
    default:
      comp = new Date(duty);
  }

  const yy = comp.getFullYear();
  const mm = String(comp.getMonth() + 1).padStart(2, "0");
  const dd = String(comp.getDate()).padStart(2, "0");
  return `${yy}-${mm}-${dd}`;
}

/**
 * For a list of L01 shifts, return synthetic CompensationDay entries with
 * a stable `id` (negative) so the matrix grid can render comp-day cells
 * without colliding with real DB ids.
 *
 * Each entry has:
 *   - id: -staffId * 100000 + dayIndex (negative, stable per staff+date)
 *   - staffId
 *   - compensationDate (YYYY-MM-DD)
 *   - shiftDate (YYYY-MM-DD, source L01 workDate)
 */
export function deriveCompensationDaysFromPreview<
  T extends { staffId: number; workDate: string; shiftTypeId: string },
>(previewSchedules: T[]): Array<{
  id: number;
  staffId: number;
  compensationDate: string;
  shiftDate: string;
}> {
  const seen = new Set<string>();
  const result: Array<{
    id: number;
    staffId: number;
    compensationDate: string;
    shiftDate: string;
  }> = [];

  for (const s of previewSchedules) {
    if (s.shiftTypeId !== "L01") continue;
    const workDate = s.workDate.split("T")[0];
    const compDate = calculateCompensationDate(workDate);
    const key = `${s.staffId}|${compDate}`;
    if (seen.has(key)) continue; // de-dup: same staff can only have one comp day per date
    seen.add(key);

    // Stable negative id derived from staffId + day index.
    const dayIdx = Math.floor((Date.parse(workDate) - Date.parse("2020-01-01")) / 86400000);
    result.push({
      id: -(s.staffId * 100000 + dayIdx),
      staffId: s.staffId,
      compensationDate: compDate,
      shiftDate: workDate,
    });
  }
  return result;
}
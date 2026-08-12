'use client';

import { useEffect, useRef, useState } from 'react';
import { useConflictCount, useConflictStore } from '@/contexts/ConflictContext';
import { api } from '@/lib/api';

/**
 * Red badge that sits inside a nav item to surface the current
 * unresolved conflict count. Animates briefly when the number
 * grows so the change is noticeable even from peripheral vision.
 *
 * Self-hides when the count is 0 (returns null).
 */
export function ConflictBadge() {
  const count = useConflictCount();
  const { seed } = useConflictStore();
  const [pulse, setPulse] = useState(false);
  const previous = useRef(count);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const seeded = useRef(false);

  // Seed initial count from the conflict-check API on first mount.
  // Avoids showing "0" immediately after a page reload when conflicts
  // already exist on the server.
  useEffect(() => {
    if (seeded.current) return;
    seeded.current = true;

    let cancelled = false;
    (async () => {
      try {
        const now = new Date();
        const year = now.getFullYear();
        const month = now.getMonth() + 1;
        const res = await api.get<{ hasConflicts: boolean; conflicts?: Array<{ scheduleId?: number }> }>(
          `/schedules/conflicts/check/${year}-${String(month).padStart(2, '0')}`
        );
        if (cancelled) return;
        const { hasConflicts, conflicts = [] } = res;
        seed(hasConflicts ? conflicts.length : 0, conflicts.map((c) => c.scheduleId ?? 0));
      } catch {
        // Seed silently on failure — WS will correct the count when events arrive.
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [seed]);

  useEffect(() => {
    if (count > previous.current) {
      setPulse(true);
      if (timer.current) clearTimeout(timer.current);
      timer.current = setTimeout(() => setPulse(false), 600);
    }
    previous.current = count;
    return () => {
      if (timer.current) clearTimeout(timer.current);
    };
  }, [count]);

  if (count <= 0) return null;

  return (
    <span
      data-testid="conflict-badge"
      aria-label={`${count} xung đột chưa giải quyết`}
      className={`
        ml-auto inline-flex items-center justify-center
        min-w-[20px] h-5 px-1.5 rounded-full
        bg-error text-white text-[11px] font-bold
        leading-none
        ${pulse ? 'animate-pulse-soft' : ''}
      `}
    >
      {count > 99 ? '99+' : count}
    </span>
  );
}
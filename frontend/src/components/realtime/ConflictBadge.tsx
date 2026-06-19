'use client';

import { useEffect, useRef, useState } from 'react';
import { useConflictCount } from '@/contexts/ConflictContext';

/**
 * Red badge that sits inside a nav item to surface the current
 * unresolved conflict count. Animates briefly when the number
 * grows so the change is noticeable even from peripheral vision.
 *
 * Self-hides when the count is 0 (returns null).
 */
export function ConflictBadge() {
  const count = useConflictCount();
  const [pulse, setPulse] = useState(false);
  const previous = useRef(count);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

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
        bg-error text-on-error text-[11px] font-bold
        leading-none
        ${pulse ? 'animate-pulse-soft' : ''}
      `}
    >
      {count > 99 ? '99+' : count}
    </span>
  );
}
"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { Schedule } from "@/types/api";

export function useScheduleDetailModal(scheduleId: number | null, onCloseRoute: () => void) {
  const [detailSchedule, setDetailSchedule] = useState<Schedule | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  useEffect(() => {
    if (scheduleId === null) return;

    let active = true;
    queueMicrotask(() => {
      if (!active) return;
      setDetailSchedule(null);
      setDetailLoading(true);
    });

    void api.getScheduleById(scheduleId)
      .then((res) => {
        if (active) setDetailSchedule(res.data);
      })
      .catch(() => {
        if (active) setDetailSchedule(null);
      })
      .finally(() => {
        if (active) setDetailLoading(false);
      });

    return () => {
      active = false;
    };
  }, [scheduleId]);

  const closeDetail = () => {
    setDetailSchedule(null);
    onCloseRoute();
  };

  return {
    detailScheduleId: scheduleId,
    detailSchedule,
    detailLoading,
    closeDetail,
  };
}

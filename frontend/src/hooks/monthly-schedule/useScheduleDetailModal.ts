"use client";

import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import type { Schedule } from "@/types/api";

export function useScheduleDetailModal(scheduleId: number | null, onCloseRoute: () => void) {
  const [detailSchedule, setDetailSchedule] = useState<Schedule | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);
  const ignore = useRef(false);

  useEffect(() => {
    if (scheduleId === null) {
      setDetailSchedule(null);
      setDetailError(null);
      return;
    }

    ignore.current = false;
    setDetailSchedule(null);
    setDetailError(null);
    setDetailLoading(true);

    void api.getScheduleById(scheduleId)
      .then((res) => {
        if (ignore.current) return;
        setDetailSchedule(res.data);
      })
      .catch(() => {
        if (ignore.current) return;
        setDetailError("Không thể tải chi tiết ca trực.");
      })
      .finally(() => {
        if (ignore.current) return;
        setDetailLoading(false);
      });

    return () => {
      ignore.current = true;
    };
  }, [scheduleId]);

  const closeDetail = () => {
    setDetailSchedule(null);
    setDetailError(null);
    onCloseRoute();
  };

  return {
    detailScheduleId: scheduleId,
    detailSchedule,
    detailLoading,
    detailError,
    closeDetail,
  };
}

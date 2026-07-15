"use client";

import { useState } from "react";
import { BackButton } from "@/components/ui/BackButton";
import { useRole } from "@/hooks/useRole";
import { TabBar } from "./TabBar";
import { RuntimeConfigEditor } from "./RuntimeConfigEditor";
import { MetricsHistory } from "./MetricsHistory";
import { AccessDeniedCard } from "./AccessDeniedCard";
import type { TabKey } from "./types";

export default function AlgorithmConfigPage() {
  const role = useRole();
  const isAdmin = role === "ADMIN";
  const [activeTab, setActiveTab] = useState<TabKey>("config");

  if (!isAdmin) return <AccessDeniedCard />;

  return (
    <div className="space-y-5">
      <BackButton href="/auto-scheduling" variant="full" label="Quay lai" className="mb-1" />

      <div className="flex items-center justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-headline-lg font-bold text-on-surface tracking-tight">Cau hinh thuat toan</h1>
          <p className="text-label-sm text-on-surface-variant mt-0.5">Thiet lap thong so van hanh cho thuat toan xep lich</p>
        </div>
        <TabBar active={activeTab} onChange={setActiveTab} />
      </div>

      {activeTab === "config" && (
        <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant overflow-hidden">
          <div className="px-5 py-3.5 border-b border-outline-variant bg-surface-container-low flex items-center gap-2.5">
            <span className="material-symbols-outlined text-primary text-[18px]" aria-hidden="true">tune</span>
            <h2 className="text-title-sm font-semibold text-on-surface">Thong so runtime</h2>
          </div>
          <div className="p-5">
            <RuntimeConfigEditor />
          </div>
        </div>
      )}

      {activeTab === "history" && <MetricsHistory />}
    </div>
  );
}

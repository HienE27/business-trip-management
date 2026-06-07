"use client";

import { DashboardShell } from "@/components/layout/DashboardShell";
import { SectionCard } from "@/components/ui/SectionCard";
import { StaffExclusionTable } from "@/components/auto-scheduling/StaffExclusionTable";
import { BusinessRulesPanel } from "@/components/auto-scheduling/BusinessRulesPanel";
import { AlgorithmTip } from "@/components/auto-scheduling/AlgorithmTip";

export default function AutoSchedulingPage() {
  return (
    <DashboardShell
      activeCode="M07"
      description="Tu dong phan cong lich theo thuat toan, kiem tra rang buoc va xem truoc truoc khi ap dung."
      title="Cau hinh Tu dong xep lich"
    >
      {/* Header actions */}
      <div className="flex justify-end gap-3">
        <button
          className="px-4 py-2 border border-primary text-primary font-label-md rounded-lg hover:bg-primary/5 transition-colors flex items-center gap-2"
          type="button"
        >
          <span className="material-symbols-outlined text-[18px]">restart_alt</span>
          Dat lai
        </button>
        <button
          className="px-6 py-2 bg-primary text-white font-label-md rounded-lg shadow-md hover:bg-primary/90 transition-all flex items-center gap-2"
          type="button"
        >
          <span className="material-symbols-outlined text-[18px]">play_arrow</span>
          Tao ban nhap
        </button>
      </div>

      {/* Main Grid */}
      <div className="grid grid-cols-1 xl:grid-cols-12 gap-6">

        {/* Left Column */}
        <div className="xl:col-span-8 flex flex-col gap-6">

          {/* Time & Basic Config */}
          <SectionCard
            title={
              <span className="flex items-center gap-2">
                <span className="material-symbols-outlined text-primary">event</span>
                Thoi gian &amp; Chi tieu co ban
              </span>
            }
          >
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6 p-6">
              <div>
                <label className="block font-label-md text-on-surface-variant mb-2">Thang ap dung</label>
                <div className="relative">
                  <select className="w-full border border-outline-variant rounded-lg px-4 py-2.5 bg-surface-container-lowest font-body-sm text-body-sm focus:border-primary focus:ring-1 focus:ring-primary outline-none transition-all appearance-none cursor-pointer">
                    <option>Thang 11 / 2023</option>
                    <option selected>Thang 12 / 2023</option>
                    <option>Thang 01 / 2024</option>
                  </select>
                  <span className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-outline-variant pointer-events-none">expand_more</span>
                </div>
              </div>
              <div>
                <label className="block font-label-md text-on-surface-variant mb-2">Khoa / Phong ban</label>
                <div className="relative">
                  <select className="w-full border border-outline-variant rounded-lg px-4 py-2.5 bg-surface-container-lowest font-body-sm text-body-sm focus:border-primary focus:ring-1 focus:ring-primary outline-none transition-all appearance-none cursor-pointer">
                    <option>Khoa Noi tong hop</option>
                    <option selected>Khoa Cap cuu</option>
                    <option>Khoa Nhi</option>
                  </select>
                  <span className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-outline-variant pointer-events-none">expand_more</span>
                </div>
              </div>
              <div>
                <label className="block font-label-md text-on-surface-variant mb-2">So ca truc Toi thieu / nguoi</label>
                <input
                  className="w-full border border-outline-variant rounded-lg px-4 py-2.5 bg-surface-container-lowest font-body-sm text-body-sm focus:border-primary focus:ring-1 focus:ring-primary outline-none transition-all"
                  type="number"
                  defaultValue="4"
                />
              </div>
              <div>
                <label className="block font-label-md text-on-surface-variant mb-2">So ca truc Toi da / nguoi</label>
                <input
                  className="w-full border border-outline-variant rounded-lg px-4 py-2.5 bg-surface-container-lowest font-body-sm text-body-sm focus:border-primary focus:ring-1 focus:ring-primary outline-none transition-all"
                  type="number"
                  defaultValue="8"
                />
              </div>
            </div>
          </SectionCard>

          {/* Personnel Exclusion */}
          <SectionCard
            title={
              <span className="flex items-center gap-2">
                <span className="material-symbols-outlined text-tertiary-container">person_off</span>
                Loai tru &amp; Ngay nghi du kien
              </span>
            }
          >
            <div className="p-6 pt-0">
              <StaffExclusionTable />
            </div>
          </SectionCard>

        </div>

        {/* Right Column */}
        <div className="xl:col-span-4 flex flex-col gap-6">
          <div className="overflow-hidden rounded-lg border border-outline-variant bg-surface-container-lowest shadow-sm">
            <div className="p-6">
              <BusinessRulesPanel />
            </div>
          </div>
          <AlgorithmTip />
        </div>

      </div>
    </DashboardShell>
  );
}

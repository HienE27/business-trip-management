"use client";

import { useState } from "react";
import Link from "next/link";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { ProfileCard } from "@/components/staff/ProfileCard";
import { ProfileTabs } from "@/components/staff/ProfileTabs";
import { ProfileInfoSection } from "@/components/staff/ProfileInfoSection";

const MOCK_STAFF = {
  id: "NV-2023-084",
  name: "Nguyen Van A",
  namePrefix: "BS.",
  specialty: "Truong khoa Noi tim mach",
  department: "Khoa Noi",
  role: "Trưởng khoa",
  avatarUrl: "https://lh3.googleusercontent.com/aida-public/AB6AXuB-A762m1pSq9vwRmREbAQDBj4qPGDh3k-1PdvpJ2s5zlGq32jc24jGq_VDIn0VRgCxAd1mN65-GIdUbjeaJxfbNB-HG4_cmeFyWO7ksEHF-DoFqrx6yLOxfdbrGVkRE0Ah8Sptn9q6g3s57jvv7wFex2NqwTWBTqymvFE70NPqLoITbQaqXM0jj6988g3kN-bShqJvaa5Jf2FXR6Sx7o4wXWktVFtdY7RV9MBLAztjmVReMOieIUO7ZYfj_bIo3gHmvqzDeSGJXJL3",
  email: "nguyen.vana@hospital.vn",
  phone: "0912 345 678",
  address: "123 Duong Ton Duc Thang, Phuong Ben Nghe, Quan 1, TP. Ho Chi Minh",
  joinDate: "15/03/2015",
  systemRoles: ["Bac si", "Truong khoa"],
  status: "active" as const,
};

export default function StaffProfilePage() {
  const [activeTab, setActiveTab] = useState("info");

  const personalInfo = [
    { label: "Email", value: MOCK_STAFF.email },
    { label: "So dien thoai", value: MOCK_STAFF.phone },
    { label: "Dia chi lien he", value: MOCK_STAFF.address },
  ];

  const workInfo = [
    { label: "Ngay tiep nhan", value: MOCK_STAFF.joinDate },
    { label: "Chuyen khoa", value: "Noi tim mach" },
    { label: "Khoa/Phong", value: MOCK_STAFF.department },
  ];

  return (
    <DashboardShell
      activeCode="M01"
      description="Quan ly ho so nhan su"
      title="Chi tiet nhan vien"
    >
      {/* Breadcrumbs */}
      <div className="flex items-center gap-2 text-sm text-on-surface-variant mb-4">
        <Link className="hover:text-primary transition-colors" href="/staff">
          Quan ly nhan su
        </Link>
        <span className="material-symbols-outlined text-sm">chevron_right</span>
        <span className="text-on-surface font-medium">Chi tiet nhan vien</span>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left: Profile Card */}
        <div className="lg:col-span-4">
          <ProfileCard
            avatarUrl={MOCK_STAFF.avatarUrl}
            id={MOCK_STAFF.id}
            name={MOCK_STAFF.name}
            namePrefix={MOCK_STAFF.namePrefix}
            role={MOCK_STAFF.role}
            specialty={MOCK_STAFF.specialty}
          />
        </div>

        {/* Right: Tabs */}
        <div className="lg:col-span-8 flex flex-col">
          <ProfileTabs activeTab={activeTab} onTabChange={setActiveTab}>
            {activeTab === "info" && (
              <ProfileInfoSection
                personalInfo={personalInfo}
                systemRoles={MOCK_STAFF.systemRoles}
                workInfo={workInfo}
              />
            )}
            {activeTab === "schedule" && (
              <div className="text-center py-12 text-on-surface-variant">
                <span className="material-symbols-outlined text-5xl text-outline">calendar_month</span>
                <p className="mt-4 font-body-md">Lich cong tac gan day cua {MOCK_STAFF.namePrefix} {MOCK_STAFF.name}</p>
              </div>
            )}
            {activeTab === "stats" && (
              <div className="text-center py-12 text-on-surface-variant">
                <span className="material-symbols-outlined text-5xl text-outline">bar_chart</span>
                <p className="mt-4 font-body-md">Thong ke workload cua {MOCK_STAFF.namePrefix} {MOCK_STAFF.name}</p>
              </div>
            )}
            {activeTab === "history" && (
              <div className="text-center py-12 text-on-surface-variant">
                <span className="material-symbols-outlined text-5xl text-outline">history</span>
                <p className="mt-4 font-body-md">Nhat ky thay doi cua {MOCK_STAFF.namePrefix} {MOCK_STAFF.name}</p>
              </div>
            )}
          </ProfileTabs>
        </div>
      </div>
    </DashboardShell>
  );
}

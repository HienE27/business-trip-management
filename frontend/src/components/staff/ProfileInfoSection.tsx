"use client";

import { translateRoleListToDisplay } from "@/lib/roleLabels";

type InfoField = {
  label: string;
  value: string;
  icon?: string;
};

type ProfileInfoSectionProps = {
  personalInfo: InfoField[];
  workInfo: InfoField[];
  systemRoles?: string[];
};

export function ProfileInfoSection({ personalInfo, workInfo, systemRoles }: ProfileInfoSectionProps) {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
      {/* Personal Info */}
      <section className="bg-surface-container-lowest rounded-xl border border-outline-variant p-5 shadow-sm">
        <h3 className="text-title-sm font-semibold text-on-surface mb-5 flex items-center gap-2">
          <div className="size-9 rounded-lg bg-primary flex items-center justify-center">
            <span className="material-symbols-outlined text-white text-[18px]">person_outline</span>
          </div>
          Thông tin cá nhân
        </h3>
        <div className="space-y-4">
          {personalInfo.map((field) => (
            <div key={field.label} className="flex items-start gap-3">
              <div className="size-9 rounded-lg bg-primary-fixed flex items-center justify-center shrink-0">
                <span className="material-symbols-outlined text-primary text-[18px]">{field.icon || "info"}</span>
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-label-sm text-on-surface-variant">{field.label}</p>
                <p className="text-label-md text-on-surface font-medium truncate">{field.value || "—"}</p>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Work Info */}
      <section className="bg-surface-container-lowest rounded-xl border border-outline-variant p-5 shadow-sm">
        <h3 className="text-title-sm font-semibold text-on-surface mb-5 flex items-center gap-2">
          <div className="size-9 rounded-lg bg-secondary flex items-center justify-center">
            <span className="material-symbols-outlined text-white text-[18px]">work_outline</span>
          </div>
          Thông tin công việc
        </h3>
        <div className="space-y-4">
          {workInfo.map((field) => (
            <div key={field.label} className="flex items-start gap-3">
              <div className="size-9 rounded-lg bg-secondary-container flex items-center justify-center shrink-0">
                <span className="material-symbols-outlined text-secondary text-[18px]">{field.icon || "work"}</span>
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-label-sm text-on-surface-variant">{field.label}</p>
                <p className="text-label-md text-on-surface font-medium truncate">{field.value || "—"}</p>
              </div>
            </div>
          ))}

          {systemRoles && systemRoles.length > 0 && (
            <div>
              <p className="text-label-sm text-on-surface-variant mb-2">Vai trò hệ thống</p>
              <div className="flex flex-wrap gap-2">
                {translateRoleListToDisplay(systemRoles).map((role, idx) => (
                  <span
                    key={role}
                    className={`px-3 py-1.5 rounded-lg text-label-sm font-semibold ${
                      systemRoles[idx] === "ADMIN" || systemRoles[idx] === "MANAGER"
                        ? "bg-primary-fixed text-primary"
                        : "bg-surface-container-low text-on-surface"
                    }`}
                  >
                    {role}
                  </span>
                ))}
              </div>
            </div>
          )}
        </div>
      </section>
    </div>
  );
}

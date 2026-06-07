"use client";

type InfoField = {
  label: string;
  value: string;
};

type InfoSection = {
  title: string;
  icon: string;
  fields: InfoField[];
};

type ProfileInfoProps = {
  personalInfo: InfoField[];
  workInfo: InfoField[];
  systemRoles?: string[];
};

export function ProfileInfoSection({ personalInfo, workInfo, systemRoles }: ProfileInfoProps) {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
      <section>
        <h3 className="font-title-lg text-on-surface mb-6 flex items-center gap-2">
          <span className="material-symbols-outlined text-primary">person_outline</span>
          Thong tin ca nhan
        </h3>
        <div className="space-y-4">
          {personalInfo.map((field) => (
            <div key={field.label}>
              <p className="font-label-sm text-outline uppercase tracking-wider mb-1">{field.label}</p>
              <p className="font-body-md text-on-surface font-medium">{field.value}</p>
            </div>
          ))}
        </div>
      </section>

      <section>
        <h3 className="font-title-lg text-on-surface mb-6 flex items-center gap-2">
          <span className="material-symbols-outlined text-primary">work_outline</span>
          Thong tin cong viec
        </h3>
        <div className="space-y-4">
          {workInfo.map((field) => (
            <div key={field.label}>
              <p className="font-label-sm text-outline uppercase tracking-wider mb-1">{field.label}</p>
              <p className="font-body-md text-on-surface font-medium">{field.value}</p>
            </div>
          ))}

          {systemRoles && systemRoles.length > 0 && (
            <div>
              <p className="font-label-sm text-outline uppercase tracking-wider mb-2">Vai tro he thong</p>
              <div className="flex gap-2 mt-2">
                {systemRoles.map((role) => (
                  <span
                    className={`font-label-sm text-label-sm px-3 py-1 rounded-full ${
                      role.includes("Truong") || role.includes("ADMIN")
                        ? "bg-primary-fixed text-primary font-semibold"
                        : "bg-surface-container-high text-on-surface"
                    }`}
                    key={role}
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

import { DashboardShell } from "@/components/layout/DashboardShell";
import { SectionCard } from "@/components/ui/SectionCard";
import { SimpleDataTable } from "@/components/ui/SimpleDataTable";
import { clinicServiceRows } from "@/data/module-screens";

export default function ServiceClinicPage() {
  return (
    <DashboardShell
      activeCode="M04"
      description="Gán nhân sự phụ trách phòng khám dịch vụ theo ngày và kiểm tra trùng lịch chuyên gia."
      title="Lịch phòng khám dịch vụ"
    >
      <div className="space-y-6">
        <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {[
            ["Ca dich vu", "24", "Thang 05/2026"],
            ["Da gan", "19", "79% ke hoach"],
            ["Canh bao", "01", "Trung lich chuyen gia"],
            ["Phong kham", "03", "Dang hoat dong"],
          ].map((item) => {
            const [label, value, helper] = item;
            return (
              <div
                className="rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm transition-colors hover:bg-surface-container-low"
                key={label}
              >
                <p className="text-label-sm uppercase tracking-wider text-on-surface-variant">{label}</p>
                <p className="mt-3 font-display-lg font-bold text-on-surface">{value}</p>
                <p className="mt-1 font-body-sm text-on-surface-variant">{helper}</p>
              </div>
            );
          })}
        </section>

        <div className="grid gap-6 xl:grid-cols-[1fr_340px]">
          <SectionCard
            description="Ô ngày có lịch chuyên gia hoặc nghỉ bù được tô cảnh báo"
            title="Bảng phân công phòng khám dịch vụ"
          >
            <SimpleDataTable
              headers={["Ngày", "Nhân sự", "Chuyên khoa", "Phòng", "Trạng thái"]}
              rows={clinicServiceRows}
              statusColumn={4}
            />
          </SectionCard>

          <aside className="space-y-6">
            <SectionCard description="M04-F01" title="Tao lich dich vu">
              <div className="space-y-4 px-5 py-4">
                {["Ngay", "Nhan su", "Phong kham", "Ghi chu"].map((label) => (
                  <label className="block" key={label}>
                    <span className="text-label-sm uppercase tracking-wider text-on-surface-variant">{label}</span>
                    <input
                      className="mt-2 h-10 w-full rounded-lg border border-outline-variant bg-surface px-3 text-body-md text-on-surface outline-none transition-colors placeholder:text-on-surface-variant/70 focus-visible:border-primary focus-visible:ring-2 focus-visible:ring-primary/20"
                      placeholder={label}
                    />
                  </label>
                ))}
                <button className="inline-flex h-10 w-full items-center justify-center rounded-lg bg-primary px-4 text-label-md text-on-primary shadow-sm transition-colors hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20">
                  Them ca dich vu
                </button>
              </div>
            </SectionCard>

            <section className="rounded-lg border border-tertiary-container bg-tertiary-fixed/30 p-5 shadow-sm">
              <p className="text-label-sm uppercase tracking-wider text-tertiary">Kiem tra truoc luu</p>
              <p className="mt-3 font-body-sm text-on-surface">
                Dich vu khong duoc trung phong kham chuyen gia va khong duoc xep vao ngay nghi bu.
              </p>
            </section>
          </aside>
        </div>
      </div>
    </DashboardShell>
  );
}

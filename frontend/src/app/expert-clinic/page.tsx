import { DashboardShell } from "@/components/layout/DashboardShell";
import { SectionCard } from "@/components/ui/SectionCard";
import { SimpleDataTable } from "@/components/ui/SimpleDataTable";
import { expertClinicRows } from "@/data/module-screens";

export default function ExpertClinicPage() {
  return (
    <DashboardShell
      activeCode="M05"
      description="Lọc chuyên khoa, gán chuyên gia khám chuyên sâu và tránh trùng lịch dịch vụ."
      title="Lịch phòng khám chuyên gia"
    >
      <div className="grid gap-6 xl:grid-cols-[280px_1fr_1fr]">
        <aside className="space-y-6">
          <SectionCard description="M05-F04" title="Bo loc chuyen khoa">
            <div className="space-y-2 px-5 py-4">
              {["Tat ca", "Ngoai", "Noi", "Nhi", "Mat", "Rang ham mat"].map((specialty) => (
                <button
                  className={`w-full rounded-lg px-4 py-2.5 text-left text-label-md transition-colors ${
                    specialty === "Tat ca"
                      ? "bg-primary text-on-primary shadow-sm"
                      : "border border-outline-variant bg-surface-container-lowest text-on-surface hover:bg-surface-container-low"
                  }`}
                  key={specialty}
                  type="button"
                >
                  {specialty}
                </button>
              ))}
            </div>
          </SectionCard>

          <section className="rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm">
            <p className="text-label-sm uppercase tracking-wider text-on-surface-variant">Rang buoc</p>
            <p className="mt-3 font-body-sm text-on-surface-variant">
              Cung chuyen gia trong cung ngay khong duoc dong thoi co lich phong kham dich vu.
            </p>
          </section>
        </aside>

        <div className="space-y-6">
          <section className="grid gap-4 md:grid-cols-3">
            {[
              ["Chuyen gia", "12"],
              ["Ca chuyen sau", "18"],
              ["Cho phan cong", "01"],
            ].map((item) => {
              const [label, value] = item;
              return (
                <div
                  className="rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm transition-colors hover:bg-surface-container-low"
                  key={label}
                >
                  <p className="text-label-sm uppercase tracking-wider text-on-surface-variant">{label}</p>
                  <p className="mt-3 font-display-lg font-bold text-on-surface">{value}</p>
                </div>
              );
            })}
          </section>

          <SectionCard
            description="Hien thi theo ngay, chuyen khoa va trang thai kiem tra"
            title="Bang lich chuyen gia"
          >
            <SimpleDataTable
              headers={["Ngay", "Chuyen gia", "Chuyen khoa", "Noi dung", "Trang thai"]}
              rows={expertClinicRows}
              statusColumn={4}
            />
          </SectionCard>
        </div>
      </div>
    </DashboardShell>
  );
}

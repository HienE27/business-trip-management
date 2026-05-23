import { DashboardShell } from "@/components/layout/DashboardShell";
import { SectionCard } from "@/components/ui/SectionCard";
import { SimpleDataTable } from "@/components/ui/SimpleDataTable";
import { expertClinicRows } from "@/data/module-screens";

export default function ExpertClinicPage() {
  return (
    <DashboardShell
      activeCode="M05"
      description="Lọc chuyên khoa, gán chuyên gia khám chuyên sâu và tránh trùng lịch dịch vụ."
      primaryAction="Lưu lịch chuyên gia"
      secondaryAction="Lọc chuyên khoa"
      title="M05 - Lịch phòng khám chuyên gia"
    >
      <div className="grid gap-4 p-5 max-sm:p-3 xl:grid-cols-[280px_minmax(0,1fr)]">
        <aside className="space-y-4">
          <SectionCard description="M05-F04" title="Bộ lọc chuyên khoa">
            <div className="space-y-2 p-4">
              {["Tất cả", "Ngoại", "Nội", "Nhi", "Mắt", "Răng hàm mặt"].map((specialty) => (
                <button
                  className={`h-9 w-full rounded-md px-3 text-left text-sm ${
                    specialty === "Tất cả"
                      ? "bg-slate-950 text-white"
                      : "border border-slate-200 bg-white text-slate-700"
                  }`}
                  key={specialty}
                >
                  {specialty}
                </button>
              ))}
            </div>
          </SectionCard>

          <section className="rounded-lg border border-slate-200 bg-white p-4 shadow-[0_1px_2px_rgba(15,23,42,0.05)]">
            <p className="text-xs font-medium uppercase text-slate-500">Ràng buộc</p>
            <p className="mt-2 text-sm leading-6 text-slate-600">
              Cùng chuyên gia trong cùng ngày không được đồng thời có lịch phòng khám dịch vụ.
            </p>
          </section>
        </aside>

        <div className="space-y-4">
          <section className="grid gap-4 md:grid-cols-3">
            {[
              ["Chuyên gia", "12"],
              ["Ca chuyên sâu", "18"],
              ["Chờ phân công", "01"],
            ].map(([label, value]) => (
              <div
                className="rounded-lg border border-slate-200 bg-white p-4 shadow-[0_1px_2px_rgba(15,23,42,0.05)]"
                key={label}
              >
                <p className="text-xs font-medium uppercase text-slate-500">{label}</p>
                <p className="mt-3 text-2xl font-semibold">{value}</p>
              </div>
            ))}
          </section>

          <SectionCard
            description="Hiển thị theo ngày, chuyên khoa và trạng thái kiểm tra"
            title="Bảng lịch chuyên gia"
          >
            <SimpleDataTable
              headers={["Ngày", "Chuyên gia", "Chuyên khoa", "Nội dung", "Trạng thái"]}
              rows={expertClinicRows}
              statusColumn={4}
            />
          </SectionCard>
        </div>
      </div>
    </DashboardShell>
  );
}

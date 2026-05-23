import { DashboardShell } from "@/components/layout/DashboardShell";
import { SectionCard } from "@/components/ui/SectionCard";
import { SimpleDataTable } from "@/components/ui/SimpleDataTable";
import { clinicServiceRows } from "@/data/module-screens";

export default function ServiceClinicPage() {
  return (
    <DashboardShell
      activeCode="M04"
      description="Gán nhân sự phụ trách phòng khám dịch vụ theo ngày và kiểm tra trùng lịch chuyên gia."
      primaryAction="Lưu lịch dịch vụ"
      secondaryAction="Kiểm tra chuyên gia"
      title="M04 - Lịch phòng khám dịch vụ"
    >
      <div className="space-y-4 p-5 max-sm:p-3">
        <section className="grid gap-4 md:grid-cols-4">
          {[
            ["Ca dịch vụ", "24", "Tháng 05/2026"],
            ["Đã gán", "19", "79% kế hoạch"],
            ["Cảnh báo", "01", "Trùng lịch chuyên gia"],
            ["Phòng khám", "03", "Đang hoạt động"],
          ].map(([label, value, helper]) => (
            <div
              className="rounded-lg border border-slate-200 bg-white p-4 shadow-[0_1px_2px_rgba(15,23,42,0.05)]"
              key={label}
            >
              <p className="text-xs font-medium uppercase text-slate-500">{label}</p>
              <p className="mt-3 text-2xl font-semibold">{value}</p>
              <p className="mt-1 text-sm text-slate-500">{helper}</p>
            </div>
          ))}
        </section>

        <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_340px]">
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

          <aside className="space-y-4">
            <SectionCard description="M04-F01" title="Tạo lịch dịch vụ">
              <div className="space-y-3 p-4">
                {["Ngày", "Nhân sự", "Phòng khám", "Ghi chú"].map((label) => (
                  <label className="block" key={label}>
                    <span className="text-xs font-medium text-slate-500">{label}</span>
                    <input
                      className="mt-1 h-9 w-full rounded-md border border-slate-200 px-3 text-sm outline-none focus:border-slate-400"
                      placeholder={label}
                    />
                  </label>
                ))}
                <button className="h-9 w-full rounded-md bg-slate-950 text-sm font-medium text-white">
                  Thêm ca dịch vụ
                </button>
              </div>
            </SectionCard>

            <section className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-amber-800">
              <p className="text-xs font-medium uppercase">Kiểm tra trước lưu</p>
              <p className="mt-2 text-sm leading-6">
                Dịch vụ không được trùng phòng khám chuyên gia và không được xếp vào ngày nghỉ bù.
              </p>
            </section>
          </aside>
        </div>
      </div>
    </DashboardShell>
  );
}

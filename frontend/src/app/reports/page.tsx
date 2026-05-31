import { DashboardShell } from "@/components/layout/DashboardShell";
import { SectionCard } from "@/components/ui/SectionCard";
import { SimpleDataTable } from "@/components/ui/SimpleDataTable";
import { exportFilters, reportRows } from "@/data/operations-dashboard";

export default function ReportsPage() {
  return (
    <DashboardShell
      activeCode="M06-F04"
      description="Xuất báo cáo lịch công tác theo tháng, từng loại lịch hoặc toàn phòng."
      primaryAction="Tạo báo cáo"
      secondaryAction="Tải Excel"
      title="M06-F04 - Xuất báo cáo lịch"
    >
      <div className="grid gap-4 p-5 max-sm:p-3 xl:grid-cols-[320px_minmax(0,1fr)]">
        <aside className="space-y-4">
          <SectionCard description="Cấu hình trước khi xuất file" title="Bộ lọc báo cáo">
            <div className="divide-y divide-[#edf1f5]">
              {exportFilters.map(([label, value]) => (
                <div className="flex h-14 items-center justify-between px-4" key={label}>
                  <span className="text-sm text-[#667085]">{label}</span>
                  <span className="text-sm font-medium text-[#111418]">{value}</span>
                </div>
              ))}
            </div>
          </SectionCard>

          <section className="rounded-lg border border-[#202832] bg-[#15191f] p-4 text-white shadow-[0_1px_2px_rgba(15,23,42,0.08)]">
            <p className="text-xs font-medium uppercase text-white/50">Mẫu xuất</p>
            <h2 className="mt-3 text-lg font-semibold leading-6">Excel cho thao tác, PDF cho ký duyệt</h2>
            <p className="mt-2 text-sm leading-6 text-white/64">
              Mỗi báo cáo giữ nguyên màu lịch để đối chiếu nhanh với dashboard.
            </p>
          </section>
        </aside>

        <div className="space-y-4">
          <section className="grid gap-4 md:grid-cols-4">
            {[
              ["Báo cáo sẵn sàng", "02"],
              ["Đang chạy", "01"],
              ["Chờ tạo", "01"],
              ["Lần xuất gần nhất", "18:40"],
            ].map(([label, value]) => (
              <div
                className="rounded-lg border border-[#dfe4ea] bg-white p-4 shadow-[0_1px_2px_rgba(15,23,42,0.05)]"
                key={label}
              >
                <p className="text-xs font-medium uppercase text-[#667085]">{label}</p>
                <p className="mt-3 text-2xl font-semibold leading-8 text-[#111418]">{value}</p>
              </div>
            ))}
          </section>

          <SectionCard
            description="Các file báo cáo được tạo từ dữ liệu lịch tháng hiện tại"
            title="Danh sách báo cáo"
          >
            <SimpleDataTable
              headers={["Mã báo cáo", "Tên báo cáo", "Định dạng", "Trạng thái"]}
              rows={reportRows}
              statusColumn={3}
            />
          </SectionCard>
        </div>
      </div>
    </DashboardShell>
  );
}

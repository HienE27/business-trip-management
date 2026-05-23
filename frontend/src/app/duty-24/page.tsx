import { ScheduleMatrix } from "@/components/dashboard/ScheduleMatrix";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { SectionCard } from "@/components/ui/SectionCard";
import { SimpleDataTable } from "@/components/ui/SimpleDataTable";
import { dutyRows, ruleCards } from "@/data/module-screens";
import { scheduleRows, staffColumns } from "@/data/schedule-dashboard";

export default function Duty24Page() {
  return (
    <DashboardShell
      activeCode="M02"
      description="Xếp lịch trực cả tháng, tự tính nghỉ bù và kiểm tra xung đột hàng loạt."
      primaryAction="Lưu & công bố"
      secondaryAction="Kiểm tra xung đột"
      title="M02 - Lịch trực 24/24"
    >
      <div className="space-y-4 p-5 max-sm:p-3">
        <section className="grid gap-4 md:grid-cols-3">
          {ruleCards.map((rule) => (
            <article
              className="rounded-lg border border-slate-200 bg-white p-4 shadow-[0_1px_2px_rgba(15,23,42,0.05)]"
              key={rule.title}
            >
              <h2 className="text-sm font-semibold">{rule.title}</h2>
              <p className="mt-2 text-sm leading-6 text-slate-600">{rule.detail}</p>
            </article>
          ))}
        </section>

        <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
          <div className="space-y-4">
            <ScheduleMatrix staff={staffColumns} rows={scheduleRows} />
            <SectionCard
              description="Mỗi dòng là ngày trực và ngày nghỉ bù hệ thống tự sinh"
              title="Bảng trực đã gán"
            >
              <SimpleDataTable
                headers={["Ngày trực", "Thứ", "Nhân sự trực", "Nghỉ bù", "Trạng thái"]}
                rows={dutyRows}
                statusColumn={4}
              />
            </SectionCard>
          </div>

          <aside className="space-y-4">
            <SectionCard description="Áp dụng ngay khi gán ngày trực" title="Quy tắc nghỉ bù">
              <div className="space-y-3 p-4 text-sm text-slate-600">
                <p>
                  Trực Thứ 2 đến Thứ 5: nghỉ bù ngày kế tiếp và khóa ô trên bảng tháng.
                </p>
                <p>
                  Trực Thứ 6 hoặc Thứ 7: chuyển sang tuần sau, bỏ qua Thứ 2 và Thứ 6.
                </p>
                <p>Trực Chủ Nhật: nghỉ bù Thứ 2 ngay hôm sau.</p>
              </div>
            </SectionCard>

            <section className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-rose-800">
              <p className="text-xs font-medium uppercase">Chặn lưu</p>
              <h2 className="mt-2 text-lg font-semibold">2 ô cần xử lý</h2>
              <p className="mt-2 text-sm leading-6">
                Có lịch khác xếp vào ngày nghỉ bù. Sửa hết lỗi trước khi công bố lịch tháng.
              </p>
            </section>
          </aside>
        </div>
      </div>
    </DashboardShell>
  );
}

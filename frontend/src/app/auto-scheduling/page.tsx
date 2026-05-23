import { StaffLoadTable } from "@/components/dashboard/StaffLoadTable";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { SectionCard } from "@/components/ui/SectionCard";
import { SimpleDataTable } from "@/components/ui/SimpleDataTable";
import { autoSchedulingPreview, exceptionStaff } from "@/data/module-screens";
import { scheduleRows, staffColumns, staffLoads } from "@/data/schedule-dashboard";
import { ScheduleMatrix } from "@/components/dashboard/ScheduleMatrix";

export default function AutoSchedulingPage() {
  return (
    <DashboardShell
      activeCode="M07"
      description="Tự động phân công lịch theo thuật toán, kiểm tra ràng buộc và xem trước trước khi áp dụng."
      primaryAction="Xác nhận & áp dụng"
      secondaryAction="Chạy lại"
      title="M07 - Tự động sắp xếp lịch"
    >
      <div className="grid gap-4 p-5 max-sm:p-3 2xl:grid-cols-[minmax(0,1fr)_340px]">
        <div className="space-y-4">
          <section className="grid gap-4 md:grid-cols-4">
            {[
              ["Thuật toán", "Round Robin", "Phân bổ đều"],
              ["Nhân sự xét", "18/20", "Đã loại ngoại lệ"],
              ["Ngày chưa đủ", "02", "Cần xử lý tay"],
              ["Vi phạm", "00", "Sau quét ràng buộc"],
            ].map(([label, value, helper]) => (
              <div
                className="rounded-lg border border-slate-200 bg-white p-4 shadow-[0_1px_2px_rgba(15,23,42,0.05)]"
                key={label}
              >
                <p className="text-xs font-medium uppercase text-slate-500">{label}</p>
                <p className="mt-3 text-xl font-semibold">{value}</p>
                <p className="mt-1 text-sm text-slate-500">{helper}</p>
              </div>
            ))}
          </section>

          <ScheduleMatrix staff={staffColumns} rows={scheduleRows} />

          <SectionCard description="Trạng thái thực thi luồng M07" title="Tiến trình thuật toán">
            <SimpleDataTable
              headers={["Bước", "Xử lý", "Trạng thái"]}
              rows={autoSchedulingPreview}
              statusColumn={2}
            />
          </SectionCard>
        </div>

        <aside className="space-y-4">
          <SectionCard description="Không tham gia hoặc có giới hạn đặc biệt" title="Ngoại lệ đầu vào">
            <SimpleDataTable
              headers={["Nhân sự", "Loại ngoại lệ", "Thời gian"]}
              rows={exceptionStaff}
            />
          </SectionCard>

          <StaffLoadTable loads={staffLoads} />

          <section className="rounded-lg border border-slate-200 bg-[#15191f] p-4 text-white shadow-[0_1px_2px_rgba(15,23,42,0.08)]">
            <p className="text-xs font-medium uppercase text-white/50">Gợi ý</p>
            <h2 className="mt-3 text-lg font-semibold">Dùng Round Robin trước</h2>
            <p className="mt-2 text-sm leading-6 text-white/64">
              Sau khi phân bổ đều, dùng greedy để chọn người có ít ngày công nhất mà không vi phạm.
            </p>
          </section>
        </aside>
      </div>
    </DashboardShell>
  );
}

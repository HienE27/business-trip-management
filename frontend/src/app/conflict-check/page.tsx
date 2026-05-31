import { DashboardShell } from "@/components/layout/DashboardShell";
import { SectionCard } from "@/components/ui/SectionCard";
import { SimpleDataTable } from "@/components/ui/SimpleDataTable";
import { conflictRows, conflictSummary } from "@/data/operations-dashboard";

export default function ConflictCheckPage() {
  return (
    <DashboardShell
      activeCode="M06-F03"
      description="Quét toàn bộ lịch tháng, phát hiện trùng trực 24/24, thông tầm, phòng khám và ngày nghỉ bù."
      primaryAction="Chạy kiểm tra"
      secondaryAction="Xuất lỗi"
      title="M06-F03 - Cảnh báo xung đột thời gian thực"
    >
      <div className="space-y-4 p-5 max-sm:p-3">
        <section className="grid gap-4 md:grid-cols-4">
          {conflictSummary.map(([label, value]) => (
            <div
              className={`rounded-lg border p-4 shadow-[0_1px_2px_rgba(15,23,42,0.05)] ${
                label === "Chặn lưu"
                  ? "border-rose-200 bg-rose-50 text-rose-700"
                  : "border-[#dfe4ea] bg-white text-[#111418]"
              }`}
              key={label}
            >
              <p className="text-xs font-medium uppercase opacity-70">{label}</p>
              <p className="mt-3 text-2xl font-semibold leading-8">{value}</p>
            </div>
          ))}
        </section>

        <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_320px]">
          <SectionCard
            description="Danh sách lỗi tổng hợp sau khi quét toàn bộ các module lịch"
            title="Bảng lỗi xung đột"
          >
            <SimpleDataTable
              headers={["Mã lỗi", "Loại lỗi", "Nhân sự", "Ngày", "Module", "Mức độ"]}
              rows={conflictRows}
              statusColumn={5}
            />
          </SectionCard>

          <aside className="space-y-4">
            <SectionCard description="Service dùng chung cho manual + auto" title="Logic kiểm tra">
              <div className="space-y-3 p-4 text-sm leading-6 text-[#4b5565]">
                <p>1. L01 không được trùng L02 cùng ngày.</p>
                <p>2. L03 không được trùng L04 cùng ngày.</p>
                <p>3. Ngày nghỉ bù bị khóa với mọi loại lịch khác.</p>
                <p>4. Ngoại lệ nghỉ phép được kiểm tra trước khi lưu.</p>
              </div>
            </SectionCard>

            <section className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-rose-800">
              <p className="text-xs font-medium uppercase">Trạng thái lưu</p>
              <h2 className="mt-2 text-lg font-semibold leading-6">Đang bị khóa</h2>
              <p className="mt-2 text-sm leading-6">
                Cần xử lý 2 lỗi chặn lưu trước khi công bố lịch tháng.
              </p>
            </section>
          </aside>
        </div>
      </div>
    </DashboardShell>
  );
}

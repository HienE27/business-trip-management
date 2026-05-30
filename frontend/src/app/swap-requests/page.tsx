import { DashboardShell } from "@/components/layout/DashboardShell";
import { SectionCard } from "@/components/ui/SectionCard";
import { SimpleDataTable } from "@/components/ui/SimpleDataTable";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { swapRequests, swapValidationSteps } from "@/data/operations-dashboard";

export default function SwapRequestsPage() {
  return (
    <DashboardShell
      activeCode="M02-F04"
      description="Nhân viên gửi yêu cầu đổi ngày trực, quản lý duyệt sau khi hệ thống mô phỏng ràng buộc."
      primaryAction="Duyệt yêu cầu"
      secondaryAction="Từ chối"
      title="M02-F04 - Đăng ký đổi ngày trực"
    >
      <div className="grid gap-4 p-5 max-sm:p-3 xl:grid-cols-[minmax(0,1fr)_340px]">
        <div className="space-y-4">
          <section className="grid gap-4 md:grid-cols-4">
            {[
              ["Chờ duyệt", "01"],
              ["Hợp lệ", "01"],
              ["Chặn lưu", "01"],
              ["Đã xử lý", "12"],
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
            description="Mô phỏng lịch sau khi đổi trước khi cho quản lý phê duyệt"
            title="Danh sách yêu cầu đổi trực"
          >
            <SimpleDataTable
              headers={["Mã", "Người gửi", "Ngày cũ", "Người đổi cùng", "Ngày mới", "Trạng thái"]}
              rows={swapRequests}
              statusColumn={5}
            />
          </SectionCard>
        </div>

        <aside className="space-y-4">
          <SectionCard description="Kiểm tra tự động trước khi duyệt" title="Luồng xác minh">
            <div className="space-y-2 p-4">
              {swapValidationSteps.map(([step, title, status]) => (
                <div className="flex min-h-11 items-center justify-between gap-3 rounded-lg bg-[#f8fafc] px-3" key={step}>
                  <div>
                    <p className="text-sm font-medium text-[#111418]">{step}. {title}</p>
                  </div>
                  <StatusBadge tone={status === "Hoàn tất" ? "success" : status === "Đang chạy" ? "warning" : "neutral"}>
                    {status}
                  </StatusBadge>
                </div>
              ))}
            </div>
          </SectionCard>

          <section className="rounded-lg border border-[#202832] bg-[#15191f] p-4 text-white shadow-[0_1px_2px_rgba(15,23,42,0.08)]">
            <p className="text-xs font-medium uppercase text-white/50">Quy tắc duyệt</p>
            <h2 className="mt-3 text-lg font-semibold leading-6">Không duyệt nếu còn lỗi chặn</h2>
            <p className="mt-2 text-sm leading-6 text-white/64">
              Khi đổi ngày trực, hệ thống kiểm tra cả người gửi và người đổi cùng ở cả ngày cũ lẫn ngày mới.
            </p>
          </section>
        </aside>
      </div>
    </DashboardShell>
  );
}

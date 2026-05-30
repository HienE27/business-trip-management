import { DashboardShell } from "@/components/layout/DashboardShell";
import { SectionCard } from "@/components/ui/SectionCard";
import { SimpleDataTable } from "@/components/ui/SimpleDataTable";
import { allDayRows } from "@/data/module-screens";

export default function AllDayPage() {
  return (
    <DashboardShell
      activeCode="M03"
      description="Xếp lịch làm liên tục không nghỉ trưa, chỉ chọn ngày và nhân sự."
      primaryAction="Lưu lịch thông tầm"
      secondaryAction="Quét xung đột"
      title="M03 - Lịch thông tầm"
    >
      <div className="grid gap-4 p-5 max-sm:p-3 xl:grid-cols-[minmax(0,1fr)_340px]">
        <div className="space-y-4">
          <SectionCard
            action={
              <div className="flex rounded-md border border-slate-200 bg-slate-50 p-1 text-xs font-medium">
                <button className="h-7 rounded bg-white px-2 shadow-sm">Tháng</button>
                <button className="h-7 px-2 text-slate-500">Tuần</button>
              </div>
            }
            description="Dữ liệu trực 24/24 và nghỉ bù được tải kèm để tránh xung đột"
            title="Bảng lịch thông tầm"
          >
            <SimpleDataTable
              headers={["Ngày", "Nhân sự", "Kiểm tra", "Trạng thái"]}
              rows={allDayRows}
              statusColumn={3}
            />
          </SectionCard>

          <section className="grid gap-4 md:grid-cols-3">
            {["Chọn tháng", "Gán nhân sự", "Lưu & công bố"].map((title, index) => (
              <div
                className="rounded-lg border border-slate-200 bg-white p-4 shadow-[0_1px_2px_rgba(15,23,42,0.05)]"
                key={title}
              >
                <p className="text-xs font-semibold text-slate-500">B{index + 1}</p>
                <h2 className="mt-1 text-sm font-semibold">{title}</h2>
                <p className="mt-2 text-sm leading-6 text-slate-600">
                  {index === 0
                    ? "Tải lịch trực 24/24 và ngày nghỉ bù của tháng."
                    : index === 1
                      ? "Nhấn ô ngày để chọn người làm thông tầm."
                      : "Chỉ cho phép lưu khi không còn lỗi chặn."}
                </p>
              </div>
            ))}
          </section>
        </div>

        <aside className="space-y-4">
          <SectionCard description="Không chọn giờ, chỉ chọn ngày" title="Form gán nhanh">
            <div className="space-y-3 p-4">
              {["Tháng", "Ngày", "Nhân sự", "Ghi chú"].map((label) => (
                <label className="block" key={label}>
                  <span className="text-xs font-medium text-slate-500">{label}</span>
                  <input
                    className="mt-1 h-9 w-full rounded-md border border-slate-200 px-3 text-sm outline-none focus:border-slate-400"
                    placeholder={label}
                  />
                </label>
              ))}
              <button className="h-9 w-full rounded-md bg-slate-950 text-sm font-medium text-white">
                Gán lịch
              </button>
            </div>
          </SectionCard>

          <section className="rounded-lg border border-slate-200 bg-white p-4 shadow-[0_1px_2px_rgba(15,23,42,0.05)]">
            <p className="text-xs font-medium uppercase text-slate-500">Ràng buộc</p>
            <p className="mt-2 text-sm leading-6 text-slate-600">
              Cùng nhân sự trong cùng ngày không được đồng thời có trực 24/24 hoặc nghỉ bù.
            </p>
          </section>
        </aside>
      </div>
    </DashboardShell>
  );
}

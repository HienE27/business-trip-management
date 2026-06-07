import { DashboardShell } from "@/components/layout/DashboardShell";
import { SectionCard } from "@/components/ui/SectionCard";
import { SimpleDataTable } from "@/components/ui/SimpleDataTable";
import { allDayRows } from "@/data/module-screens";

export default function AllDayPage() {
  return (
    <DashboardShell
      activeCode="M03"
      description="Xếp lịch làm liên tục không nghỉ trưa, chỉ chọn ngày và nhân sự."
      title="Lịch thông tầm"
    >
        <div className="grid gap-6 xl:grid-cols-[1fr_340px]">
        <div className="space-y-6">
          <SectionCard
            action={
              <div className="inline-flex items-center rounded-lg border border-outline-variant bg-surface-container p-1 text-on-surface-variant shadow-sm">
                <button className="rounded-lg bg-surface-container-lowest px-3 py-1.5 text-on-surface shadow-sm" type="button">
                  Tháng
                </button>
                <button className="rounded-lg px-3 py-1.5 transition-colors hover:text-on-surface" type="button">
                  Tuần
                </button>
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
                className="rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm transition-colors hover:bg-surface-container-low"
                key={title}
              >
                <p className="text-label-sm uppercase tracking-wider text-on-surface-variant">B{index + 1}</p>
                <h2 className="mt-2 font-title-lg text-on-surface">{title}</h2>
                <p className="mt-2 font-body-sm text-on-surface-variant">
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

        <aside className="space-y-6">
          <SectionCard description="Khong chon gio, chi chon ngay" title="Form gan nhanh">
            <div className="space-y-4 px-5 py-4">
              {["Thang", "Ngay", "Nhan su", "Ghi chu"].map((label) => (
                <label className="block" key={label}>
                  <span className="text-label-sm uppercase tracking-wider text-on-surface-variant">{label}</span>
                  <input
                    className="mt-2 h-10 w-full rounded-lg border border-outline-variant bg-surface px-3 text-body-md text-on-surface outline-none transition-colors placeholder:text-on-surface-variant/70 focus-visible:border-primary focus-visible:ring-2 focus-visible:ring-primary/20"
                    placeholder={label}
                  />
                </label>
              ))}
              <button className="inline-flex h-10 w-full items-center justify-center rounded-lg bg-primary px-4 text-label-md text-on-primary shadow-sm transition-colors hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20">
                Gan lich
              </button>
            </div>
          </SectionCard>

          <section className="rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm">
            <p className="text-label-sm uppercase tracking-wider text-on-surface-variant">Rang buoc</p>
            <p className="mt-3 font-body-sm text-on-surface-variant">
              Cung nhan su trong cung ngay khong duoc dong thoi co truc 24/24 hoac nghi bu.
            </p>
          </section>
        </aside>
      </div>
    </DashboardShell>
  );
}

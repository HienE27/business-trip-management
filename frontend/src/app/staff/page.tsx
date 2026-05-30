import { DashboardShell } from "@/components/layout/DashboardShell";
import { SectionCard } from "@/components/ui/SectionCard";
import { SimpleDataTable } from "@/components/ui/SimpleDataTable";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { staffMembers, staffSummary } from "@/data/module-screens";

export default function StaffPage() {
  return (
    <DashboardShell
      activeCode="M01"
      description="Quản lý 20 nhân sự, phân quyền và trạng thái hoạt động."
      primaryAction="Thêm nhân sự"
      secondaryAction="Nhập danh sách"
      title="M01 - Quản lý nhân sự"
    >
      <div className="grid gap-4 p-5 max-sm:p-3 xl:grid-cols-[minmax(0,1fr)_320px]">
        <div className="space-y-4">
          <section className="grid gap-4 md:grid-cols-4">
            {staffSummary.map(([label, value]) => (
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
            action={
              <div className="flex rounded-md border border-slate-200 bg-slate-50 p-1 text-xs font-medium">
                <button className="h-7 rounded bg-white px-2 shadow-sm">Tất cả</button>
                <button className="h-7 px-2 text-slate-500">Đang làm</button>
              </div>
            }
            description="Tìm theo tên, mã NV, chức vụ, chuyên khoa và trạng thái"
            title="Danh sách nhân sự"
          >
            <SimpleDataTable
              headers={["Mã NV", "Họ tên", "Vai trò", "Chức vụ", "Chuyên khoa", "Trạng thái"]}
              rows={staffMembers.map((member) => [
                member.code,
                member.name,
                member.role,
                member.position,
                member.specialty,
                member.status,
              ])}
              statusColumn={5}
            />
          </SectionCard>
        </div>

        <aside className="space-y-4">
          <SectionCard description="Kiểm tra trùng mã NV trước khi lưu" title="Form thêm nhanh">
            <div className="space-y-3 p-4">
              {["Họ tên", "Mã nhân viên", "Chức vụ", "Chuyên khoa", "Email"].map((label) => (
                <label className="block" key={label}>
                  <span className="text-xs font-medium text-slate-500">{label}</span>
                  <input
                    className="mt-1 h-9 w-full rounded-md border border-slate-200 bg-white px-3 text-sm outline-none focus:border-slate-400"
                    placeholder={label}
                  />
                </label>
              ))}
              <button className="h-9 w-full rounded-md bg-slate-950 text-sm font-medium text-white">
                Lưu nhân sự
              </button>
            </div>
          </SectionCard>

          <section className="rounded-lg border border-slate-200 bg-white p-4 shadow-[0_1px_2px_rgba(15,23,42,0.05)]">
            <h2 className="text-sm font-semibold">Phân quyền</h2>
            <div className="mt-3 space-y-2">
              <StatusBadge tone="info">Quản lý lịch - toàn quyền</StatusBadge>
              <StatusBadge tone="success">Trưởng phòng - xem và phê duyệt</StatusBadge>
              <StatusBadge>Nhân viên - xem lịch cá nhân</StatusBadge>
            </div>
          </section>
        </aside>
      </div>
    </DashboardShell>
  );
}

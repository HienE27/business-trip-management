import { ScheduleMatrix } from "@/components/dashboard/ScheduleMatrix";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { DutyTable } from "@/components/duty-24/DutyTable";
import { SectionCard } from "@/components/ui/SectionCard";
import { dutyRows, ruleCards } from "@/data/module-screens";
import { scheduleRows, staffColumns } from "@/data/schedule-dashboard";

export default function Duty24Page() {
  return (
    <DashboardShell
      activeCode="M02"
      description="Xếp lịch trực cả tháng, tự tính nghỉ bù và kiểm tra xung đột hàng loạt."
      title="Lịch trực 24/24"
    >
      <div className="space-y-6">
        <section className="grid gap-4 md:grid-cols-3">
          {ruleCards.map((rule) => (
            <article
              className="rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm transition-colors hover:bg-surface-container-low"
              key={rule.title}
            >
              <h2 className="font-title-lg text-on-surface">{rule.title}</h2>
              <p className="mt-2 font-body-sm text-on-surface-variant">{rule.detail}</p>
            </article>
          ))}
        </section>

        <div className="grid gap-6 xl:grid-cols-[1fr_340px]">
          <div className="space-y-6">
            <ScheduleMatrix staff={staffColumns} rows={scheduleRows} />
            <SectionCard
              description="Moi dong la ngay truc va ngay nghi bu he thong tu sinh"
              title="Bang truc da gan"
            >
              <DutyTable rows={dutyRows} />
            </SectionCard>
          </div>

          <aside className="space-y-6">
            <SectionCard description="Áp dụng ngay khi gán ngày trực" title="Quy tắc nghỉ bù">
              <div className="space-y-3 px-5 py-4 text-sm leading-6 text-on-surface-variant">
                <p>Trực Thứ 2 đến Thứ 5: nghỉ bù ngày kế tiếp và khóa ô trên bảng tháng.</p>
                <p>Trực Thứ 6 hoặc Thứ 7: chuyển sang tuần sau, bỏ qua Thứ 2 và Thứ 6.</p>
                <p>Trực Chủ Nhật: nghỉ bù Thứ 2 ngay hôm sau.</p>
              </div>
            </SectionCard>

            <section className="rounded-lg border border-error-container bg-error-container/10 p-5 shadow-sm">
              <p className="text-label-sm uppercase tracking-wider text-error">Chan luu</p>
              <p className="mt-3 font-headline-md text-on-surface">2 o can xu ly</p>
              <p className="mt-2 font-body-sm text-on-surface-variant">
                Co lich khac xep vao ngay nghi bu. Sua het loi truoc khi cong bo lich thang.
              </p>
            </section>
          </aside>
        </div>
      </div>
    </DashboardShell>
  );
}

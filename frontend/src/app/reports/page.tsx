import { DashboardShell } from "@/components/layout/DashboardShell";

const kpiCards = [
  {
    label: "Tổng ca trực",
    value: "124",
    helper: "12%",
    helperTone: "positive",
    icon: "calendar_month",
    iconClass: "bg-primary-fixed text-primary",
  },
  {
    label: "Tổng ngày nghỉ bù",
    value: "42",
    helper: "-3%",
    helperTone: "neutral",
    icon: "event_available",
    iconClass: "bg-secondary-container text-secondary",
  },
  {
    label: "Xung đột lịch",
    value: "3",
    helper: "Cần xử lý",
    helperTone: "alert",
    icon: "warning",
    iconClass: "bg-error-container text-error",
  },
  {
    label: "Độ cân bằng",
    value: "92%",
    helper: "Tốt",
    helperTone: "good",
    icon: "balance",
    iconClass: "bg-tertiary-fixed text-tertiary",
  },
];

const workloadDistribution = [
  {
    label: "Nội khoa",
    values: [75, 40, 20, 10],
  },
  {
    label: "Ngoại khoa",
    values: [60, 85, 30, 5],
  },
  {
    label: "Cấp cứu",
    values: [90, 25, 10, 0],
  },
  {
    label: "Sản nhi",
    values: [40, 60, 80, 45],
  },
];

const chartLegend = [
  { label: "Truc 24/24", colorClass: "bg-primary" },
  { label: "Thong tam", colorClass: "bg-secondary" },
  { label: "Dich vu", colorClass: "bg-tertiary" },
  { label: "Chuyen gia", colorClass: "bg-expert" },
];

const topWorkloads = [
  { name: "BS. Nguyễn Văn A", hours: "128h", percent: 95, tone: "error", helper: "Quá tải 15%" },
  { name: "BS. Lê Thị B", hours: "115h", percent: 85, tone: "tertiary" },
  { name: "ĐD. Trần Văn C", hours: "110h", percent: 80, tone: "primary" },
  { name: "BS. Phạm Thị D", hours: "105h", percent: 75, tone: "primary" },
];

const monthlyDetailRows = [
  {
    initials: "NA",
    name: "Nguyễn Văn A",
    role: "Bác sĩ CK1",
    duty2424: 4,
    allDay: 2,
    service: 0,
    compLeave: 1,
    hours: "128h",
    status: "Quá tải",
    statusTone: "error",
  },
  {
    initials: "LB",
    name: "Lê Thị B",
    role: "Bác sĩ CK2",
    duty2424: 3,
    allDay: 4,
    service: 1,
    compLeave: 1,
    hours: "115h",
    status: "Cao",
    statusTone: "tertiary",
  },
  {
    initials: "TC",
    name: "Trần Văn C",
    role: "Điều dưỡng trưởng",
    duty2424: 2,
    allDay: 5,
    service: 0,
    compLeave: 0,
    hours: "110h",
    status: "Ổn định",
    statusTone: "secondary",
  },
  {
    initials: "PD",
    name: "Phạm Thị D",
    role: "Bác sĩ nội trú",
    duty2424: 3,
    allDay: 3,
    service: 1,
    compLeave: 1,
    hours: "105h",
    status: "Ổn định",
    statusTone: "primary",
  },
];

function getHelperBadgeClass(tone: string) {
  switch (tone) {
    case "positive":
      return "bg-secondary-container text-secondary font-semibold";
    case "alert":
      return "text-error font-semibold";
    case "neutral":
      return "bg-surface-container-high text-on-surface-variant";
    case "good":
      return "text-tertiary font-semibold";
    default:
      return "bg-surface-container-high text-on-surface-variant";
  }
}

function getStatusBadgeClass(tone: string) {
  switch (tone) {
    case "error":
      return "bg-error-container text-error border border-error/20";
    case "tertiary":
      return "bg-tertiary-fixed/30 text-tertiary border border-tertiary/20";
    case "secondary":
      return "bg-secondary-container text-secondary border border-secondary/20";
    case "primary":
      return "bg-primary-fixed/30 text-primary border border-primary/20";
    default:
      return "bg-surface-container-high text-on-surface-variant border border-outline-variant";
  }
}

function getWorkloadBarClass(tone: string) {
  switch (tone) {
    case "error":
      return "bg-error";
    case "tertiary":
      return "bg-tertiary";
    case "primary":
      return "bg-primary";
    default:
      return "bg-primary";
  }
}

export default function ReportsPage() {
  return (
    <DashboardShell
      activeCode="M06-REPORTS"
      description="Tổng quan hoạt động và phân bổ nguồn lực đội ngũ y tế."
      title="Thống kê & Báo cáo"
    >
      <div className="space-y-4 pb-12">
        <section className="mb-2 flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
          <div>
            <p className="text-[14px] text-on-surface-variant">
              Tong quan hoat dong va phan bo nguon luc doi ngu y te
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <div className="relative">
              <select className="appearance-none h-10 rounded-lg border border-outline-variant bg-surface px-4 pr-9 text-label-md text-on-surface shadow-sm transition-colors hover:bg-surface-container-low focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20">
                <option>Thang 10, 2023</option>
                <option>Thang 09, 2023</option>
                <option>Thang 08, 2023</option>
              </select>
              <span className="material-symbols-outlined pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[18px] text-on-surface-variant">
                expand_more
              </span>
            </div>
            <button className="flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-label-md text-on-primary shadow-sm transition-colors hover:opacity-90">
              <span className="material-symbols-outlined text-[18px]">download</span>
              Xuat bao cao
            </button>
          </div>
        </section>

        <section className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {kpiCards.map((card) => (
            <div
              className="flex cursor-default flex-col justify-between rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm transition-colors hover:bg-surface-container-low"
              key={card.label}
            >
              <div className="flex items-start justify-between">
                <h3 className="text-label-sm uppercase tracking-wider text-on-surface-variant">{card.label}</h3>
                <div className={`rounded-lg p-2 ${card.iconClass}`}>
                  <span
                    className="material-symbols-outlined text-[18px]"
                    style={{ fontVariationSettings: "'FILL' 1, 'wght' 400, 'GRAD' 0, 'opsz' 24" }}
                  >
                    {card.icon}
                  </span>
                </div>
              </div>
              <div className="mt-4 flex items-baseline gap-3">
                <span className={`text-display-lg font-bold ${card.helperTone === "alert" ? "text-error" : "text-on-surface"}`}>
                  {card.value}
                </span>
                {card.helperTone === "positive" ? (
                  <span className={`flex items-center gap-0.5 rounded-full px-2 py-0.5 text-[11px] font-semibold ${getHelperBadgeClass(card.helperTone)}`}>
                    <span className="material-symbols-outlined text-[12px]">arrow_upward</span>
                    {card.helper}
                  </span>
                ) : card.helperTone === "neutral" ? (
                  <span className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${getHelperBadgeClass(card.helperTone)}`}>
                    {card.helper}
                  </span>
                ) : card.helperTone === "good" ? (
                    <div className="w-full space-y-1">
                    <div className="flex items-center justify-between">
                      <span className="text-label-sm text-on-surface font-semibold">{card.helper}</span>
                    </div>
                    <div className="h-1.5 w-full overflow-hidden rounded-full bg-surface-variant">
                      <div className="h-full rounded-full bg-primary" style={{ width: card.value }} />
                    </div>
                  </div>
                ) : (
                  <span className={`text-[11px] ${getHelperBadgeClass(card.helperTone)}`}>{card.helper}</span>
                )}
              </div>
            </div>
          ))}
        </section>

        <section className="grid grid-cols-1 gap-4 lg:grid-cols-3">
          <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-6 shadow-sm lg:col-span-2">
            <div className="mb-6 flex items-center justify-between">
              <h3 className="font-title-lg text-on-surface">
                Phan bo cong viec theo khoa phong
              </h3>
              <button className="rounded-lg p-2 text-on-surface-variant transition-colors hover:bg-surface-container-low">
                <span className="material-symbols-outlined">more_vert</span>
              </button>
            </div>

            <div className="relative flex h-64 flex-col justify-between pt-4">
              <div className="pointer-events-none absolute inset-x-0 top-4 bottom-0 z-0 flex flex-col justify-between opacity-10">
                <div className="w-full border-t border-on-surface" />
                <div className="w-full border-t border-on-surface" />
                <div className="w-full border-t border-on-surface" />
                <div className="w-full border-t border-on-surface" />
              </div>

              <div className="z-10 flex flex-1 items-end gap-4 px-2 sm:gap-8">
                {workloadDistribution.map((group) => (
                  <div className="flex flex-1 flex-col items-center gap-3" key={group.label}>
                    <div className="flex h-full w-full items-end justify-center gap-1">
                      <div className="w-2 sm:w-4 bg-primary rounded-t-sm" style={{ height: `${group.values[0]}%` }} />
                      <div className="w-2 sm:w-4 bg-secondary rounded-t-sm" style={{ height: `${group.values[1]}%` }} />
                      <div className="w-2 sm:w-4 bg-tertiary rounded-t-sm" style={{ height: `${group.values[2]}%` }} />
                      <div className="w-2 sm:w-4 bg-expert rounded-t-sm" style={{ height: `${group.values[3]}%` }} />
                    </div>
                    <span className="text-[11px] font-semibold text-on-surface-variant">{group.label}</span>
                  </div>
                ))}
              </div>
            </div>

              <div className="mt-6 flex flex-wrap justify-center gap-x-6 gap-y-2 border-t border-outline-variant pt-4">
              {chartLegend.map((item) => (
                <div className="flex items-center gap-2" key={item.label}>
                  <div className={`h-2.5 w-2.5 rounded-full ${item.colorClass}`} />
                  <span className="text-label-sm text-on-surface-variant">{item.label}</span>
                </div>
              ))}
            </div>
          </div>

          <div className="flex flex-col rounded-lg border border-outline-variant bg-surface-container-lowest p-6 shadow-sm">
            <h3 className="mb-6 font-title-lg text-on-surface">Top khoi luong cao</h3>
            <div className="flex-1 space-y-6 overflow-y-auto pr-1">
              {topWorkloads.map((item) => (
                <div className="space-y-2" key={item.name}>
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-medium text-on-surface">{item.name}</span>
                    <span className={`text-sm font-bold ${item.tone === "error" ? "text-error" : item.tone === "tertiary" ? "text-tertiary" : "text-primary"}`}>
                      {item.hours}
                    </span>
                  </div>
                  <div className="h-2 w-full rounded-full bg-surface-variant">
                    <div className={`h-full rounded-full ${getWorkloadBarClass(item.tone)}`} style={{ width: `${item.percent}%` }} />
                  </div>
                  {item.helper ? (
                    <div className="flex justify-end">
                      <span className="rounded bg-error-container/20 px-1.5 py-0.5 text-[10px] font-medium text-error">
                        {item.helper}
                      </span>
                    </div>
                  ) : null}
                </div>
              ))}
            </div>
            <button className="mt-6 w-full rounded-lg border border-outline-variant px-4 py-2 text-label-md text-primary transition-colors hover:bg-surface-container-low">
              Xem tat ca
            </button>
          </div>
        </section>

        <section className="overflow-hidden rounded-lg border border-outline-variant bg-surface-container-lowest shadow-sm">
          <div className="flex flex-col items-start justify-between gap-4 border-b border-outline-variant bg-surface-container-lowest p-6 sm:flex-row sm:items-center">
            <h3 className="text-[20px] font-semibold leading-[28px] text-on-surface">Chi tiết chỉ tiêu tháng</h3>
            <div className="flex w-full items-center gap-2 sm:w-auto">
              <div className="relative flex-1 sm:flex-none">
                <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">
                  search
                </span>
                <input
                  className="w-full rounded-lg border border-outline-variant bg-surface py-2 pl-10 pr-4 font-body-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 sm:w-56"
                  placeholder="Tìm nhân sự..."
                  type="text"
                />
              </div>
              <button className="flex items-center gap-1 rounded-lg border border-outline-variant p-2 text-on-surface-variant transition-colors hover:bg-surface-container-low">
                <span className="material-symbols-outlined text-[20px]">filter_list</span>
              </button>
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-left">
              <thead>
                <tr className="border-b border-outline-variant bg-surface-container-low">
                  <th className="px-6 py-4 text-[11px] font-semibold uppercase tracking-wider text-on-surface-variant">Nhân sự</th>
                  <th className="px-6 py-4 text-[11px] font-semibold uppercase tracking-wider text-on-surface-variant">Chức vụ</th>
                  <th className="px-6 py-4 text-center text-[11px] font-semibold uppercase tracking-wider text-on-surface-variant">Trực 24/24</th>
                  <th className="px-6 py-4 text-center text-[11px] font-semibold uppercase tracking-wider text-on-surface-variant">Thông tầm</th>
                  <th className="px-6 py-4 text-center text-[11px] font-semibold uppercase tracking-wider text-on-surface-variant">Dịch vụ</th>
                  <th className="px-6 py-4 text-center text-[11px] font-semibold uppercase tracking-wider text-on-surface-variant">Nghỉ bù</th>
                  <th className="px-6 py-4 text-right text-[11px] font-semibold uppercase tracking-wider text-on-surface-variant">Tổng giờ</th>
                  <th className="px-6 py-4 text-center text-[11px] font-semibold uppercase tracking-wider text-on-surface-variant">Trạng thái</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant text-sm">
                {monthlyDetailRows.length === 0 ? (
                  <tr>
                    <td className="px-6 py-10 text-center text-sm text-on-surface-variant" colSpan={7}>
                      Chưa có dữ liệu nhân sự nào.
                    </td>
                  </tr>
                ) : (
                  monthlyDetailRows.map((row) => (
                    <tr className="group transition-colors hover:bg-surface-container" key={row.name}>
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-3">
                          <div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary-container/20 text-xs font-bold text-primary">
                            {row.initials}
                          </div>
                          <span className="text-sm font-medium text-on-surface transition-colors group-hover:text-primary">
                            {row.name}
                          </span>
                        </div>
                      </td>
                      <td className="px-6 py-4 text-on-surface-variant">{row.role}</td>
                      <td className="px-6 py-4 text-center font-medium text-on-surface">{row.duty2424}</td>
                      <td className="px-6 py-4 text-center font-medium text-on-surface">{row.allDay}</td>
                      <td className="px-6 py-4 text-center font-medium text-on-surface">{row.service}</td>
                      <td className="px-6 py-4 text-center text-outline">{row.compLeave}</td>
                      <td className="px-6 py-4 text-right font-bold text-on-surface">{row.hours}</td>
                      <td className="px-6 py-4 text-center">
                        <span className={`inline-flex items-center rounded-full px-2.5 py-1 text-[11px] font-bold ${getStatusBadgeClass(row.statusTone)}`}>
                          {row.status}
                        </span>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </DashboardShell>
  );
}

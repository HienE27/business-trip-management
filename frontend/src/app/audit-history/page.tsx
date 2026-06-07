import { DashboardShell } from "@/components/layout/DashboardShell";
import { auditRows } from "@/data/operations-dashboard";

const auditSummary = [
  ["Tong su kien", "128"],
  ["Cap nhat", "46"],
  ["Tao moi", "32"],
  ["Canh bao", "05"],
];

const auditDetails = [
  {
    time: "15/10/2023 14:30:22",
    actor: "BS. Nguyen Van A",
    action: "Cap nhat",
    actionTone: "update",
    module: "Lich truc 24/24",
    target: "Ca truc Khoa Ngoai",
    description: "Thay doi nguoi truc tu BS. Tran B sang BS. Le C",
  },
  {
    time: "15/10/2023 10:15:05",
    actor: "Admin He thong",
    action: "Tao moi",
    actionTone: "create",
    module: "Nhan su",
    target: "Tai khoan Dieu duong",
    description: "Tao tai khoan moi cho DD. Pham D",
  },
  {
    time: "14/10/2023 16:45:10",
    actor: "BS. Truong Khoa",
    action: "Xoa",
    actionTone: "delete",
    module: "Lich kham chuyen gia",
    target: "Lich kham GS. E",
    description: "Huy lich kham ngay 20/10 do ban cong tac",
  },
  {
    time: "14/10/2023 09:20:00",
    actor: "He thong Auto",
    action: "Tu dong",
    actionTone: "auto",
    module: "Tu dong xep lich",
    target: "Lich tuan 42",
    description: "Chay kich ban phan cong tu dong hoan tat",
  },
  {
    time: "13/10/2023 11:10:45",
    actor: "DD. Nguyen F",
    action: "Yeu cau",
    actionTone: "request",
    module: "Doi truc",
    target: "Phieu yeu cau #1024",
    description: "Gui yeu cau doi ca truc ngay 16/10 sang 17/10",
  },
];

function getActionBadgeClass(tone: string) {
  switch (tone) {
    case "update":
      return "bg-primary-fixed/30 text-primary border border-primary/20";
    case "create":
      return "bg-secondary-container text-secondary border border-secondary/20";
    case "delete":
      return "bg-error-container text-error border border-error/20";
    case "auto":
      return "bg-tertiary-fixed/30 text-tertiary border border-tertiary/20";
    case "request":
      return "bg-surface-container-high text-on-surface-variant border border-outline/10";
    default:
      return "bg-surface-container-high text-on-surface-variant";
  }
}

function getStatusBadgeClass(status: string) {
  if (status === "Hoan tat" || status === "Hop le") {
    return "bg-secondary-container text-secondary border border-secondary/20";
  }
  if (status === "Canh bao") {
    return "bg-error-container text-error border border-error/20";
  }
  return "bg-primary-fixed/30 text-primary border border-primary/20";
}

function getSummaryAccent(label: string) {
  if (label === "Canh bao") return "border-l-4 border-l-error";
  if (label === "Tao moi") return "border-l-4 border-l-secondary";
  if (label === "Cap nhat") return "border-l-4 border-l-primary";
  return "border-l-4 border-l-outline";
}

export default function AuditHistoryPage() {
  return (
    <DashboardShell
      activeCode="M06-AUDIT"
      description="Theo doi lich su thay doi tren toan he thong."
      title="Nhat ky thao tac"
    >
      <div className="space-y-6">
        {/* Header */}
        <section className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
          <div>
            <p className="text-label-sm text-on-surface-variant uppercase tracking-widest">Nhat ky thao tac</p>
            <p className="mt-1 font-body-sm text-on-surface-variant">
              Theo doi lich su thay doi tren toan he thong.
            </p>
          </div>
          <div className="flex items-center gap-3">
            <button className="flex items-center gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest px-4 py-2 text-label-md text-primary shadow-sm transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20">
              <span aria-hidden="true" className="material-symbols-outlined text-[18px]">download</span>
              Xuat bao cao
            </button>
          </div>
        </section>

        {/* Summary Cards */}
        <section className="grid gap-4 md:grid-cols-4">
          {auditSummary.map((item) => {
            const [label, value] = item;
            return (
              <div
                className={`rounded-lg border-t border-r border-b border-outline-variant bg-surface-container-lowest p-5 shadow-sm transition-colors hover:bg-surface-container-low ${getSummaryAccent(label)}`}
                key={label}
              >
                <p className="text-label-sm text-on-surface-variant uppercase tracking-wider">{label}</p>
                <p className="mt-3 text-display-lg text-on-surface">{value}</p>
              </div>
            );
          })}
        </section>

        {/* Filter bar */}
        <section className="rounded-lg border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
            <div className="relative">
              <label className="mb-1 block text-label-sm text-on-surface-variant uppercase tracking-wider" htmlFor="audit-search">
                Tim kiem
              </label>
              <div className="relative">
                <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">
                  search
                </span>
                <input
                  autoComplete="off"
                  className="w-full rounded-lg border border-outline-variant bg-surface py-2 pl-9 pr-3 font-body-sm text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                  id="audit-search"
                  name="auditSearch"
                  placeholder="Tim theo nguoi thao tac, doi tuong..."
                  type="text"
                />
              </div>
            </div>
            <div>
              <label className="mb-1 block text-label-sm text-on-surface-variant uppercase tracking-wider" htmlFor="audit-module">
                Module
              </label>
              <select id="audit-module" name="module" className="w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 py-2 font-body-sm text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20">
                <option value="">Tat ca Module</option>
                <option value="schedule">Lich truc</option>
                <option value="staff">Nhan su</option>
                <option value="system">He thong</option>
              </select>
            </div>
            <div>
              <label className="mb-1 block text-label-sm text-on-surface-variant uppercase tracking-wider" htmlFor="audit-action">
                Hanh dong
              </label>
              <select id="audit-action" name="action" className="w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 py-2 font-body-sm text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20">
                <option value="">Tat ca Hanh dong</option>
                <option value="create">Tao moi</option>
                <option value="update">Cap nhat</option>
                <option value="delete">Xoa</option>
              </select>
            </div>
            <div>
              <label className="mb-1 block text-label-sm text-on-surface-variant uppercase tracking-wider" htmlFor="audit-date">
                Thoi gian
              </label>
              <input
                className="w-full rounded-lg border border-outline-variant bg-surface px-3 py-2 font-body-sm text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                id="audit-date"
                name="date"
                type="date"
              />
            </div>
          </div>
        </section>

        <div className="grid gap-6 xl:grid-cols-[1fr_320px]">
          {/* Audit Table */}
          <section className="overflow-hidden rounded-lg border border-outline-variant bg-surface-container-lowest shadow-sm">
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-left">
                <thead>
                  <tr className="border-b border-outline-variant bg-surface-container-low">
                    <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Thoi gian</th>
                    <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Nguoi thao tac</th>
                    <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Hanh dong</th>
                    <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Module</th>
                    <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Doi tuong</th>
                    <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Mo ta thay doi</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-variant font-body-sm">
                  {auditDetails.length === 0 ? (
                    <tr>
                      <td className="px-5 py-10 text-center font-body-sm text-on-surface-variant" colSpan={6}>
                        Chua co nhat ky thao tac nao.
                      </td>
                    </tr>
                  ) : (
                    auditDetails.map((row) => (
                      <tr className="transition-colors hover:bg-surface-container-low group" key={`${row.time}-${row.actor}`}>
                        <td className="px-5 py-3 text-on-surface">{row.time}</td>
                        <td className="px-5 py-3 font-medium text-on-surface">{row.actor}</td>
                        <td className="px-5 py-3">
                          <span className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-[11px] font-bold ${getActionBadgeClass(row.actionTone)}`}>
                            {row.action}
                          </span>
                        </td>
                        <td className="px-5 py-3 text-on-surface-variant">{row.module}</td>
                        <td className="px-5 py-3 text-on-surface">{row.target}</td>
                        <td className="max-w-xs truncate px-5 py-3 text-on-surface-variant" title={row.description}>
                          {row.description}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </section>

          <aside className="space-y-4">
            {/* Recent Activity */}
            <section className="rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm">
              <h2 className="font-title-lg text-on-surface">Hoat dong gan day</h2>
              <div className="mt-4 space-y-3">
                {auditRows.map((row) => {
                  const [time, actor, action, module, status] = row;
                  return (
                    <div className="rounded-lg bg-surface-container-low p-3" key={`${time}-${actor}-${action}`}>
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <p className="font-label-md text-on-surface">{action}</p>
                          <p className="mt-1 font-body-sm text-on-surface-variant">
                            {actor} • {module}
                          </p>
                          <p className="mt-1 text-label-sm text-outline">{time}</p>
                        </div>
                        <span className={`inline-flex whitespace-nowrap rounded-full px-3 py-1 text-[11px] font-bold ${getStatusBadgeClass(status)}`}>
                          {status}
                        </span>
                      </div>
                    </div>
                  );
                })}
              </div>
            </section>

            {/* Control Note */}
            <section className="rounded-lg border border-error-container bg-error-container/10 p-5 shadow-sm">
              <p className="text-label-sm text-error uppercase tracking-wider">Luu y kiem soat</p>
              <h2 className="mt-3 font-headline-md text-on-surface">Theo doi thay doi truoc khi cong bo lich</h2>
              <p className="mt-2 font-body-sm leading-relaxed text-on-surface-variant">
                Moi thao tac tao, sua, xoa va duyet lien quan toi lich cong tac deu can co dau vet de truy vet va doi soat.
              </p>
            </section>
          </aside>
        </div>
      </div>
    </DashboardShell>
  );
}

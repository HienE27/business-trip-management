import { DashboardShell } from "@/components/layout/DashboardShell";
import { conflictRows, conflictSummary } from "@/data/operations-dashboard";

const conflictDetails = [
  {
    code: "CF-101",
    type: "Truc 24/24 trung thong tam",
    staff: "Nguyen Minh Anh",
    date: "31/05/2026",
    module: "M02 / M03",
    severity: "Chan luu",
    description: "Nhan su da co lich thong tam cung ngay voi ca truc 24/24 duoc de xuat.",
  },
  {
    code: "CF-102",
    type: "Xep lich vao ngay nghi bu",
    staff: "Tran Duc Huy",
    date: "28/05/2026",
    module: "M02 / M04",
    severity: "Chan luu",
    description: "Ngay nghi bu sau truc dem dang bi su dung lai cho phong kham dich vu.",
  },
  {
    code: "CF-103",
    type: "Dich vu trung chuyen gia",
    staff: "Le Bao Chau",
    date: "29/05/2026",
    module: "M04 / M05",
    severity: "Canh bao",
    description: "Nhan su dang duoc de xuat cho ca lich kham dich vu va kham chuyen gia cung ngay.",
  },
  {
    code: "CF-104",
    type: "Ngoai le nghi phep",
    staff: "Do Lan Phuong",
    date: "30/05/2026",
    module: "M07",
    severity: "Canh bao",
    description: "Ban du thao tu dong dang phan bo vao ngay co yeu cau nghi phep cho duyet.",
  },
];

const affectedScopes = [
  ["Lich truc 24/24", "02 loi chan luu"],
  ["Phong kham dich vu", "01 canh bao"],
  ["Phong kham chuyen gia", "01 canh bao"],
  ["Tu dong xep lich", "01 dau vao can xac minh"],
];

function getSeverityClass(severity: string) {
  if (severity === "Chan luu") {
    return "bg-error-container text-error border border-error/20";
  }
  if (severity === "Canh bao") {
    return "bg-tertiary-fixed text-on-tertiary-fixed border border-on-tertiary-fixed/10";
  }
  return "bg-surface-container-high text-on-surface-variant border border-outline/10";
}

function getSummaryAccent(label: string) {
  if (label === "Chan luu") return "border-l-4 border-l-error";
  if (label === "Da xu ly") return "border-l-4 border-l-secondary";
  return "border-l-4 border-l-outline";
}

export default function ConflictCheckPage() {
  return (
    <DashboardShell
      activeCode="M06-CONFLICT"
      description="Quet toan bo lich thang, phat hien trung truc 24/24, thong tam, phong kham va ngay nghi bu."
      title="Canh bao xung dot thoi gian thuc"
    >
      <div className="space-y-6">
        {/* Header */}
        <section className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
          <div>
            <p className="text-label-sm text-on-surface-variant uppercase tracking-widest">Canh bao xung dot</p>
            <p className="mt-1 font-body-sm text-on-surface-variant">
              Quet toan bo lich thang va gom cac loi chan luu truoc khi cong bo lich chinh thuc.
            </p>
          </div>
          <div className="flex shrink-0 items-center gap-3">
            <button className="flex items-center gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest px-4 py-2 text-label-md text-on-surface shadow-sm transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20">
              <span aria-hidden="true" className="material-symbols-outlined text-[18px]">tune</span>
              Bo loc nang cao
            </button>
            <button className="flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-label-md text-on-primary shadow-sm transition-colors hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20">
              <span aria-hidden="true" className="material-symbols-outlined text-[18px]">play_circle</span>
              Chay kiem tra
            </button>
          </div>
        </section>

        {/* Summary Cards */}
        <section className="grid gap-4 md:grid-cols-4">
          {conflictSummary.map((item) => {
            const [label, value] = item;
            return (
              <div
                className={`rounded-lg border-t border-r border-b border-outline-variant bg-surface-container-lowest p-5 shadow-sm transition-colors hover:bg-surface-container-low ${getSummaryAccent(label)}`}
                key={label}
              >
                <p className="text-label-sm text-on-surface-variant uppercase tracking-wider opacity-80">{label}</p>
                <p className="mt-3 text-display-lg text-on-surface">{value}</p>
              </div>
            );
          })}
        </section>

        {/* Filter bar */}
        <section className="rounded-lg border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
            <div className="relative">
              <label className="mb-1 block text-label-sm text-on-surface-variant uppercase tracking-wider" htmlFor="conflict-search">
                Tim kiem
              </label>
              <div className="relative">
                <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">
                  search
                </span>
                <input
                  autoComplete="off"
                  className="w-full rounded-lg border border-outline-variant bg-surface py-2 pl-9 pr-3 font-body-sm text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                  id="conflict-search"
                  name="conflictSearch"
                  placeholder="Tim theo ma loi, nhan su..."
                  type="text"
                />
              </div>
            </div>
            <div>
              <label className="mb-1 block text-label-sm text-on-surface-variant uppercase tracking-wider" htmlFor="conflict-module">
                Module
              </label>
              <select id="conflict-module" name="module" className="w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 py-2 font-body-sm text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20">
                <option value="">Tat ca Module</option>
                <option value="m02">Lich truc 24/24</option>
                <option value="m03">Thong tam</option>
                <option value="m04">Phong kham dich vu</option>
                <option value="m05">Phong kham chuyen gia</option>
                <option value="m07">Tu dong xep lich</option>
              </select>
            </div>
            <div>
              <label className="mb-1 block text-label-sm text-on-surface-variant uppercase tracking-wider" htmlFor="conflict-severity">
                Muc do
              </label>
              <select id="conflict-severity" name="severity" className="w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 py-2 font-body-sm text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20">
                <option value="">Tat ca muc do</option>
                <option value="block">Chan luu</option>
                <option value="warning">Canh bao</option>
              </select>
            </div>
            <div>
              <label className="mb-1 block text-label-sm text-on-surface-variant uppercase tracking-wider" htmlFor="conflict-period">
                Ky kiem tra
              </label>
              <input
                className="w-full rounded-lg border border-outline-variant bg-surface px-3 py-2 font-body-sm text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                id="conflict-period"
                name="period"
                type="month"
              />
            </div>
          </div>
        </section>

        <div className="grid gap-6 xl:grid-cols-[1fr_320px]">
          <div className="space-y-6">
            {/* Error Table */}
            <section className="overflow-hidden rounded-lg border border-outline-variant bg-surface-container-lowest shadow-sm">
              <div className="flex flex-col gap-4 border-b border-outline-variant bg-surface-container-low p-4 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <h2 className="font-title-lg text-on-surface">Bang loi xung dot</h2>
                  <p className="mt-1 font-body-sm text-on-surface-variant">
                    Danh sach loi tong hop sau khi quet toan bo cac module lich.
                  </p>
                </div>
                <button className="flex items-center gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 py-2 text-label-md text-on-surface transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20">
                  <span aria-hidden="true" className="material-symbols-outlined text-[18px]">download</span>
                  Xuat danh sach
                </button>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-left">
                  <thead>
                    <tr className="border-b border-outline-variant bg-surface-container-low">
                      <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Ma loi</th>
                      <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Loai loi</th>
                      <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Nhan su</th>
                      <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Ngay</th>
                      <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Module</th>
                      <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Muc do</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-variant font-body-sm">
                    {conflictRows.length === 0 ? (
                      <tr>
                        <td className="px-5 py-10 text-center font-body-sm text-on-surface-variant" colSpan={6}>
                          Khong co loi xung dot nao.
                        </td>
                      </tr>
                    ) : (
                      conflictRows.map((row) => (
                        <tr className="transition-colors hover:bg-surface-container-low group" key={row[0]}>
                          <td className="px-5 py-3 font-semibold text-primary">{row[0]}</td>
                          <td className="px-5 py-3 text-on-surface">{row[1]}</td>
                          <td className="px-5 py-3 text-on-surface">{row[2]}</td>
                          <td className="px-5 py-3 text-on-surface-variant">{row[3]}</td>
                          <td className="px-5 py-3 text-on-surface-variant">{row[4]}</td>
                          <td className="px-5 py-3">
                            <span className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-[11px] font-bold ${getSeverityClass(row[5])}`}>
                              {row[5]}
                            </span>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </section>

            {/* Conflict Detail Cards */}
            <section className="grid gap-4">
              {conflictDetails.map((item) => (
                <article
                  className="rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm transition-colors hover:bg-surface-container-low"
                  key={item.code}
                >
                  <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                    <div>
                      <div className="flex flex-wrap items-center gap-2">
                        <h3 className="font-title-lg text-on-surface font-semibold">{item.code}</h3>
                        <span className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-[11px] font-bold ${getSeverityClass(item.severity)}`}>
                          {item.severity}
                        </span>
                      </div>
                      <p className="mt-2 font-label-md text-on-surface">{item.type}</p>
                      <p className="mt-1 font-body-sm text-on-surface-variant">{item.description}</p>
                    </div>
                    <button className="rounded-lg border border-outline-variant bg-surface-container-lowest px-3 py-2 text-label-md text-on-surface transition-colors hover:bg-surface-container shrink-0">
                      Xem nguyen nhan
                    </button>
                  </div>

                  <div className="mt-5 grid gap-4 md:grid-cols-4">
                    <div className="flex flex-col gap-1">
                      <span className="text-label-sm text-on-surface-variant uppercase tracking-wider">Nhan su</span>
                      <span className="font-label-md text-on-surface">{item.staff}</span>
                    </div>
                    <div className="flex flex-col gap-1">
                      <span className="text-label-sm text-on-surface-variant uppercase tracking-wider">Ngay</span>
                      <span className="font-label-md text-on-surface">{item.date}</span>
                    </div>
                    <div className="flex flex-col gap-1">
                      <span className="text-label-sm text-on-surface-variant uppercase tracking-wider">Module</span>
                      <span className="font-label-md text-on-surface">{item.module}</span>
                    </div>
                    <div className="flex flex-col gap-1">
                      <span className="text-label-sm text-on-surface-variant uppercase tracking-wider">Khuyen nghi</span>
                      <span className="font-label-md text-on-surface">
                        {item.severity === "Chan luu" ? "Sua truoc khi cong bo lich" : "Cho phep luu ban nhap de ra lai"}
                      </span>
                    </div>
                  </div>
                </article>
              ))}
            </section>
          </div>

          <aside className="space-y-4">
            {/* Affected Scopes */}
            <section className="rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm">
              <h2 className="font-title-lg text-on-surface">Pham vi bi anh huong</h2>
              <div className="mt-4 space-y-3">
                {affectedScopes.map((item) => {
                  const [name, detail] = item;
                  return (
                    <div className="rounded-lg bg-surface-container-low p-3" key={name}>
                      <p className="font-label-md text-on-surface">{name}</p>
                      <p className="mt-1 font-body-sm text-on-surface-variant">{detail}</p>
                    </div>
                  );
                })}
              </div>
            </section>

            {/* Logic Panel */}
            <section className="rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm">
              <h2 className="font-title-lg text-on-surface">Logic kiem tra</h2>
              <div className="mt-4 space-y-3 font-body-sm leading-relaxed text-on-surface-variant">
                <p>1. L01 khong duoc trung L02 cung ngay.</p>
                <p>2. L03 khong duoc trung L04 cung ngay.</p>
                <p>3. Ngay nghi bu bi khoa voi moi loai lich khac.</p>
                <p>4. Ngoai le nghi phep duoc kiem tra truoc khi luu.</p>
              </div>
            </section>

            {/* Lock Status Panel */}
            <section className="rounded-lg border border-error-container bg-error-container/10 p-5 shadow-sm">
              <p className="text-label-sm text-error uppercase tracking-wider opacity-80">Trang thai luu</p>
              <h2 className="mt-2 font-headline-md text-on-surface">Dang bi khoa</h2>
              <p className="mt-2 font-body-sm leading-relaxed text-on-surface-variant">
                Can xu ly 2 loi chan luu truoc khi cong bo lich thang. Cac canh bao con lai co the giu o ban nhap de ran soat them.
              </p>
            </section>
          </aside>
        </div>
      </div>
    </DashboardShell>
  );
}

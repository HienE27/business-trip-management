"use client";

import { useState } from "react";
import { DashboardShell } from "@/components/layout/DashboardShell";

type SwapRequest = {
  id: string;
  requesterName: string;
  requesterDept: string;
  partnerName: string;
  partnerDept: string;
  oldDate: string;
  oldShift: string;
  newDate: string;
  newShift: string;
  status: "pending" | "approved" | "rejected";
  approvedBy?: string;
  approvedAt?: string;
};

const MOCK_REQUESTS: SwapRequest[] = [
  {
    id: "REQ-001",
    requesterName: "BS. Nguyen Van An",
    requesterDept: "Khoa Cap cuu",
    partnerName: "BS. Tran Thi Bich",
    partnerDept: "Khoa Cap cuu",
    oldDate: "15/10/2023",
    oldShift: "Truc dem (22h - 06h)",
    newDate: "17/10/2023",
    newShift: "Truc ngay (06h - 14h)",
    status: "pending",
  },
  {
    id: "REQ-002",
    requesterName: "DD. Le Hoang Nam",
    requesterDept: "Khoa Noi tong hop",
    partnerName: "DD. Pham Thu Ha",
    partnerDept: "Khoa Noi tong hop",
    oldDate: "16/10/2023",
    oldShift: "Hanh chinh (08h - 17h)",
    newDate: "18/10/2023",
    newShift: "Truc dem (22h - 06h)",
    status: "pending",
  },
  {
    id: "REQ-003",
    requesterName: "BS. Vu Duc Cuong",
    requesterDept: "Khoa Ngoai khoa",
    partnerName: "BS. Dang Mai Linh",
    partnerDept: "Khoa Ngoai khoa",
    oldDate: "12/10/2023",
    oldShift: "Truc chieu (14h - 22h)",
    newDate: "14/10/2023",
    newShift: "Truc sang (06h - 14h)",
    status: "approved",
    approvedBy: "Admin",
    approvedAt: "09:30",
  },
];

function getStatusBadge(status: SwapRequest["status"]) {
  if (status === "pending") return "bg-tertiary-fixed text-on-tertiary-fixed";
  if (status === "approved") return "bg-secondary-fixed text-on-secondary-fixed";
  return "bg-error-container text-on-error-container";
}

function getStatusLabel(status: SwapRequest["status"]) {
  if (status === "pending") return "Cho duyet";
  if (status === "approved") return "Da duyet";
  return "Tu choi";
}

function getOldBorderColor(request: SwapRequest) {
  if (request.status === "rejected") return "border-outline-variant";
  if (request.oldShift.includes("dem")) return "border-error";
  if (request.oldShift.includes("ngay")) return "border-secondary";
  if (request.oldShift.includes("chieu")) return "border-outline";
  if (request.oldShift.includes("sang")) return "border-primary";
  return "border-outline-variant";
}

function getNewBorderColor(request: SwapRequest) {
  if (request.newShift.includes("dem")) return "border-error";
  if (request.newShift.includes("ngay")) return "border-secondary";
  if (request.newShift.includes("chieu")) return "border-outline";
  if (request.newShift.includes("sang")) return "border-primary";
  return "border-outline-variant";
}

export default function SwapRequestsPage() {
  const [statusFilter, setStatusFilter] = useState("pending");
  const [deptFilter, setDeptFilter] = useState("");

  const filtered = MOCK_REQUESTS.filter((r) => {
    if (statusFilter && r.status !== statusFilter) return false;
    if (deptFilter && r.requesterDept !== deptFilter) return false;
    return true;
  });

  const total = MOCK_REQUESTS.length;
  const pending = MOCK_REQUESTS.filter((r) => r.status === "pending").length;
  const approved = MOCK_REQUESTS.filter((r) => r.status === "approved").length;
  const rejected = MOCK_REQUESTS.filter((r) => r.status === "rejected").length;

  function handleApprove(id: string) {
    console.log("Approve", id);
  }

  function handleReject(id: string) {
    console.log("Reject", id);
  }

  return (
    <DashboardShell
      activeCode="M09"
      description="Quan ly va xet duyet cac de xuat thay doi lich truc tu nhan su cac khoa phong."
      title="Phe duyet Yeu cau Doi ca"
    >
      <div className="space-y-6">
        {/* Header */}
        <div>
          <h1 className="text-2xl font-bold text-on-surface">Phe duyet Yeu cau Doi ca</h1>
          <p className="mt-1 text-body-sm text-on-surface-variant">
            Quan ly va xet duyet cac de xuat thay doi lich truc tu nhan su cac khoa phong.
          </p>
        </div>

        {/* KPI Summary Cards */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5 shadow-[0_1px_3px_0_rgba(0,0,0,0.1),0_1px_2px_-1px_rgba(0,0,0,0.1)] relative overflow-hidden">
            <div className="absolute right-0 top-0 w-24 h-24 bg-primary-fixed/50 rounded-bl-full blur-xl -z-10" />
            <div className="flex items-center gap-3 mb-3 text-on-surface-variant">
              <span className="material-symbols-outlined">list_alt</span>
              <span className="font-label-md uppercase tracking-wider">Tong yeu cau</span>
            </div>
            <div className="font-display-lg text-display-lg text-on-surface">{String(total).padStart(2, "0")}</div>
            <div className="font-body-sm text-on-surface-variant mt-1">Trong thang nay</div>
          </div>

          <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5 shadow-[0_1px_3px_0_rgba(0,0,0,0.1),0_1px_2px_-1px_rgba(0,0,0,0.1)] relative overflow-hidden">
            <div className="absolute right-0 top-0 w-24 h-24 bg-tertiary-fixed/50 rounded-bl-full blur-xl -z-10" />
            <div className="flex items-center gap-3 mb-3 text-tertiary">
              <span className="material-symbols-outlined">pending_actions</span>
              <span className="font-label-md uppercase tracking-wider">Cho duyet</span>
            </div>
            <div className="font-display-lg text-display-lg text-on-surface">{String(pending).padStart(2, "0")}</div>
            <div className="font-body-sm text-on-surface-variant mt-1">Can xu ly ngay</div>
          </div>

          <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5 shadow-[0_1px_3px_0_rgba(0,0,0,0.1),0_1px_2px_-1px_rgba(0,0,0,0.1)] relative overflow-hidden">
            <div className="absolute right-0 top-0 w-24 h-24 bg-secondary-fixed/50 rounded-bl-full blur-xl -z-10" />
            <div className="flex items-center gap-3 mb-3 text-secondary">
              <span className="material-symbols-outlined">task_alt</span>
              <span className="font-label-md uppercase tracking-wider">Da duyet</span>
            </div>
            <div className="font-display-lg text-display-lg text-on-surface">{String(approved).padStart(2, "0")}</div>
            <div className="font-body-sm text-on-surface-variant mt-1">Thanh cong</div>
          </div>

          <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5 shadow-[0_1px_3px_0_rgba(0,0,0,0.1),0_1px_2px_-1px_rgba(0,0,0,0.1)] relative overflow-hidden">
            <div className="absolute right-0 top-0 w-24 h-24 bg-error-container/50 rounded-bl-full blur-xl -z-10" />
            <div className="flex items-center gap-3 mb-3 text-error">
              <span className="material-symbols-outlined">cancel</span>
              <span className="font-label-md uppercase tracking-wider">Tu choi</span>
            </div>
            <div className="font-display-lg text-display-lg text-on-surface">{String(rejected).padStart(2, "0")}</div>
            <div className="font-body-sm text-on-surface-variant mt-1">Khong hop le</div>
          </div>
        </div>

        {/* Filter Bar */}
        <div className="flex items-center justify-between bg-surface-container-lowest p-4 rounded-xl border border-outline-variant shadow-[0_1px_3px_0_rgba(0,0,0,0.1),0_1px_2px_-1px_rgba(0,0,0,0.1)]">
          <div className="flex items-center gap-4">
            <div className="flex flex-col gap-1">
              <label className="font-label-sm text-label-sm text-on-surface-variant" htmlFor="status-filter">Trang thai</label>
              <select
                id="status-filter"
                className="bg-surface border border-outline-variant rounded-lg px-3 py-2 font-body-sm text-body-sm focus:ring-2 focus:ring-primary focus:border-primary outline-none"
                onChange={(e) => setStatusFilter(e.target.value)}
                value={statusFilter}
              >
                <option value="">Tat ca trang thai</option>
                <option value="pending">Cho duyet</option>
                <option value="approved">Da duyet</option>
                <option value="rejected">Tu choi</option>
              </select>
            </div>
            <div className="flex flex-col gap-1">
              <label className="font-label-sm text-label-sm text-on-surface-variant" htmlFor="dept-filter">Khoa / Phong</label>
              <select
                id="dept-filter"
                className="bg-surface border border-outline-variant rounded-lg px-3 py-2 font-body-sm text-body-sm focus:ring-2 focus:ring-primary focus:border-primary outline-none"
                onChange={(e) => setDeptFilter(e.target.value)}
                value={deptFilter}
              >
                <option value="">Tat ca khoa phong</option>
                <option value="Khoa Cap cuu">Khoa Cap cuu</option>
                <option value="Khoa Noi tong hop">Khoa Noi tong hop</option>
                <option value="Khoa Ngoai khoa">Khoa Ngoai khoa</option>
                <option value="Khoa Nhi">Khoa Nhi</option>
              </select>
            </div>
          </div>
          <button
            className="bg-surface-container-high hover:bg-surface-variant text-on-surface font-label-md text-label-md px-4 py-2 rounded-lg border border-outline-variant transition-colors flex items-center gap-2 h-[40px]"
            type="button"
          >
            <span className="material-symbols-outlined text-[18px]">download</span>
            Xuat bao cao
          </button>
        </div>

        {/* Data Table */}
        <div className="bg-surface-container-lowest rounded-xl shadow-[0_1px_3px_0_rgba(0,0,0,0.1),0_1px_2px_-1px_rgba(0,0,0,0.1)] border border-outline-variant overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-[#f8fafc] border-b border-outline-variant">
                  <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Nguoi yeu cau</th>
                  <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Nguoi doi cung</th>
                  <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Ca ban dau</th>
                  <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Ca de xuat</th>
                  <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Trang thai</th>
                  <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider text-right">Thao tac</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/50">
                {filtered.length === 0 ? (
                  <tr>
                    <td className="px-5 py-10 text-center text-body-sm text-on-surface-variant" colSpan={6}>
                      Khong co yeu cau nao phu hop.
                    </td>
                  </tr>
                ) : (
                  filtered.map((req) => (
                    <tr
                      className={`hover:bg-[#f1f5f9] transition-colors group ${req.status === "approved" ? "opacity-70" : ""}`}
                      key={req.id}
                    >
                      {/* Requester */}
                      <td className="px-5 py-2">
                        <div className="flex flex-col">
                          <span className="font-body-sm font-medium text-on-surface">{req.requesterName}</span>
                          <span className="font-label-sm text-label-sm text-on-surface-variant">{req.requesterDept}</span>
                        </div>
                      </td>

                      {/* Partner */}
                      <td className="px-5 py-2">
                        <div className="flex flex-col">
                          <span className="font-body-sm text-on-surface">{req.partnerName}</span>
                          <span className="font-label-sm text-label-sm text-on-surface-variant">{req.partnerDept}</span>
                        </div>
                      </td>

                      {/* Old Shift */}
                      <td className="px-5 py-2">
                        <div className={`flex items-center gap-2 border-l-4 ${getOldBorderColor(req)} pl-2`}>
                          <span className="material-symbols-outlined text-outline text-[16px]">calendar_today</span>
                          <div className="flex flex-col">
                            <span className={`font-body-sm text-on-surface ${req.status === "approved" ? "line-through" : ""}`}>{req.oldDate}</span>
                            <span className="font-label-sm text-label-sm text-on-surface-variant">{req.oldShift}</span>
                          </div>
                        </div>
                      </td>

                      {/* New Shift */}
                      <td className="px-5 py-2">
                        <div className={`flex items-center gap-2 border-l-4 ${getNewBorderColor(req)} pl-2`}>
                          <span className="material-symbols-outlined text-outline text-[16px]">event</span>
                          <div className="flex flex-col">
                            <span className="font-body-sm text-on-surface">{req.newDate}</span>
                            <span className="font-label-sm text-label-sm text-on-surface-variant">{req.newShift}</span>
                          </div>
                        </div>
                      </td>

                      {/* Status */}
                      <td className="px-5 py-2">
                        <span className={`inline-flex items-center px-2 py-1 rounded-full text-xs font-medium ${getStatusBadge(req.status)}`}>
                          {getStatusLabel(req.status)}
                        </span>
                      </td>

                      {/* Actions */}
                      <td className="px-5 py-2 text-right">
                        {req.status === "pending" ? (
                          <div className="flex justify-end gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                            <button
                              className="h-8 w-8 flex items-center justify-center rounded bg-secondary-container/20 text-secondary hover:bg-secondary-container transition-colors"
                              onClick={() => handleApprove(req.id)}
                              title="Phe duyet"
                              type="button"
                            >
                              <span className="material-symbols-outlined text-[18px]">check</span>
                            </button>
                            <button
                              className="h-8 w-8 flex items-center justify-center rounded bg-error-container/20 text-error hover:bg-error-container transition-colors"
                              onClick={() => handleReject(req.id)}
                              title="Tu choi"
                              type="button"
                            >
                              <span className="material-symbols-outlined text-[18px]">close</span>
                            </button>
                          </div>
                        ) : (
                          <span className="font-label-sm text-label-sm text-on-surface-variant">
                            Boi {req.approvedBy} luc {req.approvedAt}
                          </span>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          {/* Pagination Footer */}
          <div className="px-5 py-3 border-t border-outline-variant/50 bg-surface-container-lowest flex items-center justify-between">
            <span className="font-body-sm text-body-sm text-on-surface-variant">
              Hien thi 1-{filtered.length} cua {MOCK_REQUESTS.length} yeu cau
            </span>
            <div className="flex gap-1">
              <button
                className="w-8 h-8 rounded border border-outline-variant flex items-center justify-center text-on-surface-variant hover:bg-surface-container transition-colors disabled:opacity-50"
                disabled
                type="button"
              >
                <span className="material-symbols-outlined text-[18px]">chevron_left</span>
              </button>
              <button className="w-8 h-8 rounded border border-primary bg-primary-fixed text-primary flex items-center justify-center font-label-md text-label-md" type="button">1</button>
              <button className="w-8 h-8 rounded border border-outline-variant flex items-center justify-center text-on-surface-variant hover:bg-surface-container transition-colors font-label-md text-label-md" type="button">2</button>
              <button className="w-8 h-8 rounded border border-outline-variant flex items-center justify-center text-on-surface-variant hover:bg-surface-container transition-colors font-label-md text-label-md" type="button">3</button>
              <button
                className="w-8 h-8 rounded border border-outline-variant flex items-center justify-center text-on-surface-variant hover:bg-surface-container transition-colors"
                type="button"
              >
                <span className="material-symbols-outlined text-[18px]">chevron_right</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </DashboardShell>
  );
}

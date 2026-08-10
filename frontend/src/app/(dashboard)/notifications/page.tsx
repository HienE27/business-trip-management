"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import dynamic from "next/dynamic";
import { Button, IconButton } from "@/components/ui";
import { EmptyState } from "@/components/ui/EmptyState";
import { Pagination } from "@/components/ui/Pagination";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { useNotifications } from "@/components/ui/NotificationContext";
import { useAuth } from "@/components/auth/AuthProvider";
import { useToast } from "@/hooks/useToast";
import { formatRelativeTime } from "@/lib/date";
import { BackButton } from "@/components/ui/BackButton";
import type { Notification } from "@/types/api";

// Lazy-load the confirm dialog. Used only when the user clicks
// "Xóa thông báo" — deferring it shaves a small chunk off the
// initial /notifications payload.
const ConfirmDialog = dynamic(
  () => import("@/components/ui/ConfirmDialog").then((m) => m.ConfirmDialog),
  { ssr: false },
);

const DELETE_ALL_CONFIRM_PHRASE = "XÓA TẤT CẢ";

type NotifTab = "all" | "unread" | "conflict" | "exchange" | "published" | "system";

const TABS: { label: string; value: NotifTab }[] = [
  { label: "Tất cả", value: "all" },
  { label: "Chưa đọc", value: "unread" },
  { label: "Cảnh báo xung đột", value: "conflict" },
  { label: "Yêu cầu đổi trực", value: "exchange" },
  { label: "Lịch đã công bố", value: "published" },
  { label: "Hệ thống", value: "system" },
];

function getNotificationIcon(title: string) {
  const lower = title.toLowerCase();
  if (lower.includes("xung đột") || lower.includes("conflict") || lower.includes("cảnh báo"))
    return { icon: "warning", wrapClass: "bg-error-container/40 text-error border border-error/20" };
  if (lower.includes("đổi trực") || lower.includes("swap") || lower.includes("đổi ca"))
    return { icon: "swap_horiz", wrapClass: "bg-primary-fixed text-primary border border-primary/20" };
  if (lower.includes("công bố") || lower.includes("published") || lower.includes("lich"))
    return { icon: "event_available", wrapClass: "bg-secondary-container text-secondary border border-secondary/20" };
  if (lower.includes("tự động") || lower.includes("auto"))
    return { icon: "auto_mode", wrapClass: "bg-surface-container-high text-on-surface-variant border border-outline-variant/30" };
  return { icon: "notifications", wrapClass: "bg-surface-container-high text-on-surface-variant border border-outline-variant" };
}

function getBadge(title: string) {
  const lower = title.toLowerCase();
  if (lower.includes("24/24")) return { badge: "24/24", badgeClass: "bg-primary-fixed text-primary font-bold" };
  if (lower.includes("dịch vụ")) return { badge: "DV", badgeClass: "bg-secondary-container text-secondary font-bold" };
  if (lower.includes("chuyên gia")) return { badge: "CG", badgeClass: "bg-tertiary-fixed text-on-tertiary font-bold" };
  return null;
}

export default function NotificationsPage() {
  return <NotificationsContent />;
}

function NotificationsContent() {
  const { user } = useAuth();
  const userId = user?.userId ?? null;
  const { refreshCount } = useNotifications();
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<NotifTab>("all");
  const [message, setMessage] = useState("");
  const [markingAll, setMarkingAll] = useState(false);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [unreadCount, setUnreadCount] = useState(0);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [deleteTargetId, setDeleteTargetId] = useState<number | null>(null);

  // Bulk delete + typed-confirm state — mirrors the audit-history pattern.
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [deleteDialogType, setDeleteDialogType] = useState<"single" | "bulk" | "date-range" | "all" | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteDateFrom, setDeleteDateFrom] = useState("");
  const [deleteDateTo, setDeleteDateTo] = useState("");
  const [deleteAllConfirmText, setDeleteAllConfirmText] = useState("");
  const toast = useToast();

  // Helper: today's date in YYYY-MM-DD using local timezone (matches the
  // backend storage column). Same shape as audit-history so the two
  // dialogs feel identical.
  const todayStr = useMemo(() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  }, []);
  const subDateStr = (days: number) => {
    const d = new Date();
    d.setDate(d.getDate() - days);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  };

  const fetchNotifications = useCallback(async () => {
    if (!userId) {
      setNotifications([]);
      setUnreadCount(0);
      await refreshCount(0);
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setMessage("");
      const [result, unreadRes] = await Promise.all([
        api.getNotificationsPageWithTab(page, pageSize, activeTab === "all" ? undefined : activeTab),
        api.countMyUnreadNotifications(),
      ]);
      setNotifications(result.content ?? []);
      setTotalPages(result.totalPages ?? 0);
      setTotalElements(result.totalElements ?? 0);
      const serverUnread = unreadRes?.data?.count ?? 0;
      setUnreadCount(serverUnread);
      await refreshCount(serverUnread);
    } catch (err) {
      setNotifications([]);
      setUnreadCount(0);
      await refreshCount(0);
      setMessage(getErrorMessage(err, "Không thể tải thông báo."));
    } finally {
      setLoading(false);
    }
  }, [refreshCount, userId, page, pageSize, activeTab]);

  useEffect(() => {
    void fetchNotifications();
  }, [fetchNotifications]);

  useEffect(() => {
    setPage(0);
  }, [activeTab]);

  // BUGFIX #6: server filters by tab — render the page slice as-is.
  const filtered = notifications;

// unreadCount comes from server aggregate so it stays accurate across all pages.
  // (Was previously derived from `notifications` slice, which only counted unread
  // items on the current page.)

  async function handleMarkAsRead(id: number) {
    try {
      await api.put(`/notifications/${id}/read`, {});
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, isRead: true } : n)),
      );
      const next = Math.max(0, unreadCount - 1);
      setUnreadCount(next);
      await refreshCount(next);
    } catch (err) {
      setMessage(getErrorMessage(err, "Lỗi đánh dấu đã đọc."));
    }
  }

  async function handleMarkAllAsRead() {
    if (!userId) return;
    try {
      setMarkingAll(true);
      await api.put(`/notifications/staff/${userId}/read-all`, {});
      setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
      setUnreadCount(0);
      await refreshCount(0);
      setMessage("Đã đánh dấu tất cả là đã đọc.");
      setTimeout(() => setMessage(""), 3000);
    } catch (err) {
      setMessage(getErrorMessage(err, "Lỗi đánh dấu tất cả."));
    } finally {
      setMarkingAll(false);
    }
  }

  async function handleDelete(id: number) {
    setDeleteTargetId(id);
    setDeleteDialogType("single");
    setConfirmOpen(true);
  }

  async function confirmDelete() {
    if (deleteDialogType === "single" && deleteTargetId !== null) {
      const id = deleteTargetId;
      const wasUnread = notifications.find((n) => n.id === id) && !notifications.find((n) => n.id === id)!.isRead;
      try {
        await api.delete(`/notifications/${id}`);
        setNotifications((prev) => prev.filter((n) => n.id !== id));
        setSelectedIds((prev) => {
          if (!prev.has(id)) return prev;
          const next = new Set(prev);
          next.delete(id);
          return next;
        });
        // BUGFIX (was FE#8): the previous code blindly subtracted 1 from
        // unreadCount locally without re-fetching from the server. That
        // drifts when another notification was marked read/unread between
        // the page load and the delete click. Recompute the count from
        // the server's countMyUnreadNotifications() endpoint so the badge
        // and the count shown to the user stay in sync with the truth.
        try {
          const fresh = await api.countMyUnreadNotifications();
          const serverUnread = fresh?.data?.count ?? 0;
          setUnreadCount(serverUnread);
          await refreshCount(serverUnread);
        } catch {
          // Fallback to the local decrement if the server count endpoint
          // is unavailable for some reason — better than nothing.
          if (wasUnread) {
            const next = Math.max(0, unreadCount - 1);
            setUnreadCount(next);
            await refreshCount(next);
          }
        }
        toast.success("Đã xóa thông báo.");
      } catch (err) {
        toast.error(getErrorMessage(err, "Lỗi xóa thông báo."));
      } finally {
        setConfirmOpen(false);
        setDeleteTargetId(null);
        setDeleteDialogType(null);
      }
      return;
    }

    // Bulk + date-range + all routes share the same dialog state machine.
    if (!deleteDialogType || deleting) return;
    setDeleting(true);
    try {
      if (deleteDialogType === "bulk") {
        const ids = Array.from(selectedIds);
        const count = await api.deleteMultipleNotifications(ids);
        toast.success(`Đã xóa ${count} thông báo.`);
        setSelectedIds(new Set());
        setDeleteDialogType(null);
        setConfirmOpen(false);
        await fetchNotifications();
      } else if (deleteDialogType === "date-range") {
        const count = await api.deleteNotificationsByDateRange(deleteDateFrom, deleteDateTo);
        toast.success(`Đã xóa ${count} thông báo.`);
        setDeleteDialogType(null);
        setDeleteDateFrom("");
        setDeleteDateTo("");
        setConfirmOpen(false);
        await fetchNotifications();
      } else if (deleteDialogType === "all") {
        const count = await api.deleteAllNotifications();
        toast.success(`Đã xóa toàn bộ ${count} thông báo.`);
        setDeleteDialogType(null);
        setDeleteAllConfirmText("");
        setSelectedIds(new Set());
        setConfirmOpen(false);
        await fetchNotifications();
      }
    } catch (err) {
      toast.error(getErrorMessage(err, "Lỗi xóa thông báo."));
    } finally {
      setDeleting(false);
    }
  }

  function toggleSelect(id: number) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }

  function toggleSelectAll() {
    if (selectedIds.size === notifications.length) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(notifications.map((n) => n.id)));
    }
  }

  return (
    <>
      <BackButton href="/dashboard" variant="full" label="Quay lại" className="mb-4" />

      <div className="flex flex-col gap-4 pb-6">
        {/* Header bar */}
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
          <div className="flex items-center gap-3">
            {unreadCount > 0 && (
              <span className="inline-flex items-center gap-1.5 rounded-full bg-error px-3 py-1 text-[12px] font-bold text-on-error">
                <span className="h-2 w-2 rounded-full bg-on-error animate-pulse" />
                {unreadCount} chưa đọc
              </span>
            )}
            {unreadCount === 0 && (
              <span className="inline-flex items-center gap-1.5 rounded-full bg-secondary-container px-3 py-1 text-[12px] font-bold text-secondary">
                <span className="material-symbols-outlined text-[14px]">check</span>
                Tất cả đã đọc
              </span>
            )}
          </div>
          {unreadCount > 0 && (
            <button
              className="flex items-center gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest px-4 py-2 text-[13px] font-medium text-on-surface shadow-sm transition-colors hover:bg-surface-container-low disabled:opacity-50"
              disabled={markingAll}
              onClick={handleMarkAllAsRead}
              type="button"
            >
              {markingAll ? (
                <div className="size-4 animate-spin rounded-full border-2 border-primary border-t-transparent" />
              ) : (
                <span className="material-symbols-outlined text-[18px]">done_all</span>
              )}
              Đánh dấu tất cả đã đọc
            </button>
          )}
        </div>

        {/* Toolbar — bulk select + delete dropdown (mirrors audit-history). */}
        <div className="flex items-center gap-2 flex-wrap rounded-xl border border-outline-variant bg-surface-container-lowest px-3 py-2.5 shadow-sm">
          <label className="flex items-center gap-2 text-[12px] text-on-surface-variant cursor-pointer shrink-0">
            <input
              type="checkbox"
              className="h-4 w-4 rounded border-outline-variant accent-primary cursor-pointer"
              checked={notifications.length > 0 && selectedIds.size === notifications.length}
              onChange={toggleSelectAll}
              disabled={notifications.length === 0}
              aria-label="Chọn tất cả"
            />
            <span>Chọn tất cả</span>
          </label>

          <div className="flex-1" />

          {selectedIds.size > 0 && (
            <>
              <span className="text-[12px] text-primary font-semibold tabular-nums">
                {selectedIds.size} đã chọn
              </span>
              <Button
                variant="danger"
                size="sm"
                onClick={() => { setDeleteDialogType("bulk"); setConfirmOpen(true); }}
                icon={<span className="material-symbols-outlined text-[14px]" aria-hidden="true">delete</span>}
              >
                Xóa ({selectedIds.size})
              </Button>
            </>
          )}

          {notifications.length > 0 && (
            <div className="relative">
              <Button
                variant="secondary"
                size="sm"
                onClick={() => {
                  setDeleteDialogType("date-range");
                  setDeleteDateFrom(subDateStr(29));
                  setDeleteDateTo(todayStr);
                  setConfirmOpen(true);
                }}
                icon={<span className="material-symbols-outlined text-[14px]" aria-hidden="true">delete_sweep</span>}
              >
                Xóa theo ngày
              </Button>
            </div>
          )}

          {notifications.length > 0 && (
            <Button
              variant="danger"
              size="sm"
              onClick={() => { setDeleteDialogType("all"); setDeleteAllConfirmText(""); setConfirmOpen(true); }}
              icon={<span className="material-symbols-outlined text-[14px]" aria-hidden="true">delete_forever</span>}
            >
              Xóa tất cả
            </Button>
          )}
        </div>

        {/* Filter tabs */}
        <div className="flex gap-1.5 overflow-x-auto pb-1 hide-scrollbar">
          {TABS.map((tab) => (
            <button
              className={`flex items-center gap-1.5 whitespace-nowrap rounded-lg px-3 py-1.5 text-[12px] transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20 ${
                activeTab === tab.value
                  ? "bg-primary text-on-primary font-semibold shadow-sm"
                  : "border border-outline-variant/50 bg-surface-container-lowest text-on-surface-variant hover:bg-surface-container-low"
              }`}
              key={tab.value}
              onClick={() => setActiveTab(tab.value)}
              type="button"
            >
              {tab.label}
              {tab.value === "unread" && unreadCount > 0 ? (
                <span className="rounded-full bg-error px-1 py-0.5 text-[9px] font-bold text-on-error leading-tight">
                  {unreadCount}
                </span>
              ) : null}
            </button>
          ))}
        </div>

        {message && (
          <div className="rounded-lg border border-primary/20 bg-primary/5 px-4 py-3 text-sm text-on-surface">
            {message}
          </div>
        )}

        {loading ? (
          <div className="flex flex-col gap-3" aria-busy={true} aria-live="polite">
            {Array.from({ length: 5 }).map((_, i) => (
              <div
                key={i}
                className="flex gap-4 rounded-lg border border-outline-variant bg-surface-container-lowest p-5 animate-pulse"
              >
                <div className="size-10 shrink-0 rounded-lg bg-surface-container" />
                <div className="flex-1 space-y-2">
                  <div className="flex items-center gap-3">
                    <div className="h-4 w-2/5 rounded bg-surface-container" />
                    <div className="h-3 w-12 rounded-full bg-surface-container" />
                  </div>
                  <div className="h-3 w-full rounded bg-surface-container" />
                  <div className="h-3 w-4/5 rounded bg-surface-container" />
                </div>
              </div>
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <EmptyState
              icon="notifications_none"
              title="Không có thông báo nào"
              action={
                <button
                  className="inline-flex items-center gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest px-4 py-2 text-label-md font-medium text-on-surface shadow-sm transition-colors hover:bg-surface-container-low"
                  onClick={() => void fetchNotifications()}
                  type="button"
                >
                  <span className="material-symbols-outlined text-[18px]">sync</span>
                  Tải lại
                </button>
              }
            />
        ) : (
          <div className="flex flex-col gap-2" aria-busy={loading} aria-live="polite">
            {filtered.map((notif) => {
              const { icon, wrapClass } = getNotificationIcon(notif.title);
              const badge = getBadge(notif.title);
              const isSelected = selectedIds.has(notif.id);
              return (
                <div
                  className={`group relative flex gap-3 rounded-lg border bg-surface-container-lowest p-3 transition-all hover:bg-surface-container-low ${
                    !notif.isRead ? "border-primary/20 ring-1 ring-primary/10" : "border-outline-variant"
                  } ${isSelected ? "ring-2 ring-primary/40" : ""}`}
                  key={notif.id}
                >
                  {!notif.isRead && (
                    <div className="absolute left-0 top-0 bottom-0 w-1 bg-primary rounded-l-lg" />
                  )}

                  {/* Bulk-select checkbox */}
                  <div className="flex items-center pt-1">
                    <input
                      type="checkbox"
                      className="h-4 w-4 rounded border-outline-variant accent-primary cursor-pointer"
                      checked={isSelected}
                      onChange={() => toggleSelect(notif.id)}
                      aria-label={`Chọn thông báo ${notif.id}`}
                    />
                  </div>

                  <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg shadow-sm ${wrapClass}`}>
                    <span className={`material-symbols-outlined text-[18px] ${!notif.isRead ? "fill" : ""}`}>
                      {icon}
                    </span>
                  </div>

                  <div className="min-w-0 flex-1 pr-5">
                    <div className="mb-1 flex items-start justify-between gap-2">
                      <div className="flex min-w-0 flex-wrap items-center gap-1.5">
                        <h3 className={`truncate text-[13px] leading-tight ${!notif.isRead ? "text-on-surface font-semibold" : "text-on-surface font-medium"}`}>
                          {notif.title}
                        </h3>
                        {badge && (
                          <span className={`shrink-0 rounded px-1.5 py-0.5 text-[9px] uppercase tracking-wider ${badge.badgeClass}`}>
                            {badge.badge}
                          </span>
                        )}
                      </div>
                      <div className="flex items-center gap-1.5 shrink-0">
                        <span className="text-[10px] font-medium text-on-surface-variant">
                          {formatRelativeTime(notif.createdAt)}
                        </span>
                        {!notif.isRead && (
                          <span className="h-2 w-2 rounded-full bg-primary" />
                        )}
                      </div>
                    </div>

                    <p className="mb-1.5 text-[12px] text-on-surface-variant leading-snug">
                      {notif.message}
                    </p>

                    {/* Actions */}
                    <div className="flex items-center gap-1.5">
                      {!notif.isRead && (
                        <button
                          className="flex items-center gap-1 rounded-md border border-outline-variant bg-surface-container-lowest px-2 py-1 text-[11px] font-medium text-on-surface transition-colors hover:bg-surface-container"
                          onClick={() => handleMarkAsRead(notif.id)}
                          type="button"
                        >
                          <span className="material-symbols-outlined text-[12px]">check</span>
                          Đã đọc
                        </button>
                      )}
                      <button
                        className="flex items-center gap-1 rounded-md border border-outline-variant bg-surface-container-lowest px-2 py-1 text-[11px] font-medium text-error transition-colors hover:bg-error-container"
                        onClick={() => handleDelete(notif.id)}
                        type="button"
                      >
                        <span className="material-symbols-outlined text-[12px]">delete</span>
                        Xóa
                      </button>
                    </div>
                  </div>
                </div>
              );
            })}

            {filtered.length > 0 && (
              <Pagination
                currentPage={page + 1}
                totalPages={totalPages}
                totalItems={totalElements}
                pageSize={pageSize}
                onPageChange={(p) => setPage(p - 1)}
                onPageSizeChange={(s) => { setPageSize(s); setPage(0); }}
              />
            )}
          </div>
        )}

      </div>

      <ConfirmDialog
        open={confirmOpen && deleteDialogType === "single"}
        onClose={() => { setConfirmOpen(false); setDeleteTargetId(null); setDeleteDialogType(null); }}
        onConfirm={confirmDelete}
        title="Xóa thông báo?"
        description="Hành động này không thể hoàn tác."
        confirmLabel="Xóa"
        variant="danger"
      />

      <ConfirmDialog
        open={confirmOpen && deleteDialogType === "bulk"}
        onClose={() => { setConfirmOpen(false); setDeleteDialogType(null); }}
        onConfirm={confirmDelete}
        title={`Xóa ${selectedIds.size} thông báo?`}
        description={`Bạn có chắc muốn xóa ${selectedIds.size} thông báo? Hành động này không thể hoàn tác.`}
        confirmLabel="Xóa"
        variant="danger"
        loading={deleting}
      />

      {confirmOpen && deleteDialogType === "date-range" && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm" onClick={(e) => { if (e.target === e.currentTarget && !deleting) { setConfirmOpen(false); setDeleteDialogType(null); } }}>
          <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl shadow-2xl w-full max-w-sm mx-4 animate-scale-in">
            <div className="flex items-center justify-between px-5 pt-5 pb-4 border-b border-outline-variant">
              <h2 className="text-title-lg font-semibold text-on-surface">Xóa theo khoảng ngày</h2>
              <IconButton label="Đóng" variant="ghost" size="sm" disabled={deleting} onClick={() => { setConfirmOpen(false); setDeleteDialogType(null); }} className="ml-auto text-on-surface-variant">
                <span className="material-symbols-outlined text-[20px]" aria-hidden="true">close</span>
              </IconButton>
            </div>
            <div className="px-5 py-4 flex flex-col gap-3">
              <p className="text-body-sm text-on-surface-variant">Chọn khoảng ngày cần xóa (cả hai đầu đều bao gồm).</p>
              <div className="grid grid-cols-2 gap-3">
                <div className="flex flex-col gap-1.5">
                  <label htmlFor="notif-del-from" className="text-[12px] font-semibold text-on-surface-variant">Từ ngày</label>
                  <input id="notif-del-from" type="date" className="w-full h-10 px-3 rounded-lg border border-outline-variant bg-surface text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all" value={deleteDateFrom} onChange={(e) => setDeleteDateFrom(e.target.value)} disabled={deleting} />
                </div>
                <div className="flex flex-col gap-1.5">
                  <label htmlFor="notif-del-to" className="text-[12px] font-semibold text-on-surface-variant">Đến ngày</label>
                  <input id="notif-del-to" type="date" className="w-full h-10 px-3 rounded-lg border border-outline-variant bg-surface text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all" value={deleteDateTo} onChange={(e) => setDeleteDateTo(e.target.value)} disabled={deleting} />
                </div>
              </div>
              <div className="flex gap-2 pt-1">
                <Button variant="secondary" size="md" fullWidth disabled={deleting} onClick={() => { setConfirmOpen(false); setDeleteDialogType(null); }}>Hủy</Button>
                <Button
                  variant="danger"
                  size="md"
                  fullWidth
                  disabled={!deleteDateFrom || !deleteDateTo || deleteDateFrom > deleteDateTo || deleting}
                  loading={deleting}
                  onClick={confirmDelete}
                >
                  Xóa
                </Button>
              </div>
            </div>
          </div>
        </div>
      )}

      {confirmOpen && deleteDialogType === "all" && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm" role="dialog" aria-modal="true" aria-labelledby="notif-delete-all-title" onClick={(e) => { if (e.target === e.currentTarget && !deleting) { setConfirmOpen(false); setDeleteDialogType(null); setDeleteAllConfirmText(""); } }}>
          <div className="bg-surface-container-lowest border border-error/40 rounded-2xl shadow-2xl w-full max-w-md mx-4 animate-scale-in">
            <div className="flex items-start gap-3 px-5 pt-5 pb-4 border-b border-outline-variant">
              <div className="w-10 h-10 rounded-full bg-error-container flex items-center justify-center shrink-0">
                <span className="material-symbols-outlined text-error" style={{ fontVariationSettings: "'FILL' 1" }}>warning</span>
              </div>
              <div className="flex-1">
                <h2 id="notif-delete-all-title" className="text-title-lg font-semibold text-on-surface">Xóa toàn bộ thông báo?</h2>
                <p className="text-body-sm text-on-surface-variant mt-1">Hành động này sẽ xóa vĩnh viễn <strong className="font-semibold text-error tabular-nums">{totalElements.toLocaleString("vi")}</strong> thông báo của bạn. Không thể hoàn tác.</p>
              </div>
              <IconButton label="Đóng" variant="ghost" size="sm" disabled={deleting} onClick={() => { if (!deleting) { setConfirmOpen(false); setDeleteDialogType(null); setDeleteAllConfirmText(""); } }} className="shrink-0 text-on-surface-variant">
                <span className="material-symbols-outlined text-[16px]" aria-hidden="true">close</span>
              </IconButton>
            </div>
            <div className="px-5 py-4 flex flex-col gap-3">
              <div className="bg-error-container border border-error/20 rounded-lg p-3 flex items-start gap-2">
                <span className="material-symbols-outlined text-error text-[18px] mt-0.5">info</span>
                <p className="text-[13px] text-on-error-container leading-snug">
                  Để xác nhận, hãy gõ chính xác cụm từ{" "}
                  <code className="px-1.5 py-0.5 rounded bg-error/15 text-error font-mono font-bold text-[12px]">{DELETE_ALL_CONFIRM_PHRASE}</code>
                  {" "}vào ô bên dưới.
                </p>
              </div>
              <div className="flex flex-col gap-1.5">
                <label htmlFor="notif-delete-all-confirm" className="text-[12px] font-semibold text-on-surface-variant">Xác nhận xóa</label>
                <input
                  id="notif-delete-all-confirm"
                  type="text"
                  autoComplete="off"
                  spellCheck={false}
                  value={deleteAllConfirmText}
                  onChange={(e) => setDeleteAllConfirmText(e.target.value)}
                  placeholder={DELETE_ALL_CONFIRM_PHRASE}
                  className="w-full h-10 px-3 rounded-lg border border-outline-variant bg-surface text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-error/30 focus:border-error transition-all font-mono"
                  disabled={deleting}
                />
                {deleteAllConfirmText && deleteAllConfirmText !== DELETE_ALL_CONFIRM_PHRASE && (
                  <p className="text-[11px] text-error" role="alert">Cụm từ chưa khớp. Hãy gõ đúng: {DELETE_ALL_CONFIRM_PHRASE}</p>
                )}
              </div>
              <div className="flex gap-2 pt-1">
                <Button variant="secondary" size="md" fullWidth disabled={deleting} onClick={() => { setConfirmOpen(false); setDeleteDialogType(null); setDeleteAllConfirmText(""); }}>Hủy</Button>
                <Button
                  variant="danger"
                  size="md"
                  fullWidth
                  disabled={deleting || deleteAllConfirmText !== DELETE_ALL_CONFIRM_PHRASE || totalElements === 0}
                  loading={deleting}
                  onClick={() => { if (deleteAllConfirmText === DELETE_ALL_CONFIRM_PHRASE) confirmDelete(); }}
                >
                  {deleting ? "Đang xóa…" : `Xóa vĩnh viễn ${totalElements.toLocaleString("vi")} thông báo`}
                </Button>
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

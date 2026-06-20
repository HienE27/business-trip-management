import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import type { NavigationItem } from "@/types/schedule";
import { ConflictBadge } from "@/components/realtime/ConflictBadge";
import {
  groupNavigationItems,
  type SidebarGroupKey,
} from "@/data/sidebarGroups";

const CONFLICTS_HREF = "/reports/conflicts";
const SIDEBAR_COLLAPSED_KEY = "medschedule.sidebar.collapsed";
const SIDEBAR_SEARCH_KEY = "medschedule.sidebar.search";

type AppSidebarProps = {
  items: NavigationItem[];
  mobileOpen?: boolean;
  onClose?: () => void;
};

export function AppSidebar({ items, mobileOpen = false, onClose }: AppSidebarProps) {
  // Group the flat navigation into the 4 buckets. Items that don't
  // match any group are silently filtered out — the user should never
  // see dangling nav rows.
  const grouped = useMemo(() => groupNavigationItems(items), [items]);

  // ── Collapse state (sessionStorage so a reload mid-session remembers;
  //   a brand-new tab starts expanded so first-time users see everything).
  const [collapsedGroups, setCollapsedGroups] = useState<Set<SidebarGroupKey>>(() => loadCollapsed());

  // ── Search filter (sessionStorage so refresh keeps the query; new tab
  //   starts with empty filter).
  const [search, setSearch] = useState<string>(() => loadSearch());

  // Persist whenever the user toggles a group / types in the search box.
  useEffect(() => { persistCollapsed(collapsedGroups); }, [collapsedGroups]);
  useEffect(() => { persistSearch(search); }, [search]);

  // If the user navigates to a page inside a collapsed group, auto-expand
  // that group so the active item is visible. This keeps the surface area
  // tidy without hiding where the user actually is.
  const activeGroupKey = useMemo(() => {
    for (const group of grouped) {
      if (group.items.some((i) => i.active)) return group.key;
    }
    return undefined;
  }, [grouped]);

  useEffect(() => {
    if (!activeGroupKey) return;
    setCollapsedGroups((prev) => {
      if (!prev.has(activeGroupKey)) return prev;
      const next = new Set(prev);
      next.delete(activeGroupKey);
      return next;
    });
  }, [activeGroupKey]);

  // Filter groups by search query — match against label or icon name
  // (case-insensitive). An empty query shows everything.
  const filteredGroups = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return grouped;
    return grouped
      .map((g) => ({
        ...g,
        items: g.items.filter((i) => i.label.toLowerCase().includes(q)),
      }))
      .filter((g) => g.items.length > 0);
  }, [grouped, search]);

  const totalMatches = filteredGroups.reduce((sum, g) => sum + g.items.length, 0);

  function toggleGroup(key: SidebarGroupKey) {
    setCollapsedGroups((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key); else next.add(key);
      return next;
    });
  }

  function isConflictsItem(href: string) {
    return href === CONFLICTS_HREF;
  }

  return (
    <>
      {/* Mobile overlay */}
      {mobileOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/40 backdrop-blur-sm md:hidden"
          onClick={onClose}
          aria-hidden="true"
        />
      )}

      <aside
        aria-label="Điều hướng chính"
        className={`
          fixed left-0 top-0 h-screen w-64 border-r border-outline-variant
          bg-surface-container-low z-50 flex flex-col
          hidden md:flex
          ${mobileOpen ? "translate-x-0" : "-translate-x-full"}
          md:translate-x-0
          transition-all duration-200
        `}
      >
        {/* ── Logo + env badge ── */}
        <div className="px-6 pt-5 pb-4 flex items-center gap-3 shrink-0 border-b border-outline-variant">
          <div className="w-10 h-10 rounded-lg bg-primary text-on-primary flex items-center justify-center shadow-sm shrink-0">
            <span aria-hidden="true" className="material-symbols-outlined text-[20px]">medical_services</span>
          </div>
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2">
              <h1 className="font-bold text-title-md leading-tight text-primary truncate">Quản lý Lịch</h1>
              <EnvBadge />
            </div>
            <p className="text-label-sm text-on-surface-variant truncate">Hệ thống điều phối</p>
          </div>
        </div>

        {/* ── Search ── */}
        <div className="px-3 pt-3 pb-2 shrink-0">
          <div className="relative">
            <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[16px] pointer-events-none">
              search
            </span>
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Tìm chức năng…"
              aria-label="Tìm chức năng trong sidebar"
              className="w-full rounded-lg border border-outline-variant bg-surface h-9 pl-9 pr-8 text-[13px] text-on-surface placeholder:text-outline focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 transition-all"
            />
            {search && (
              <button
                type="button"
                onClick={() => setSearch("")}
                aria-label="Xóa tìm kiếm"
                className="absolute right-2 top-1/2 -translate-y-1/2 p-1 rounded hover:bg-surface-container-low transition-colors"
              >
                <span className="material-symbols-outlined text-[12px] text-outline">close</span>
              </button>
            )}
          </div>
          {search && (
            <p className="mt-1.5 px-1 text-[11px] text-on-surface-variant">
              {totalMatches === 0
                ? "Không có kết quả"
                : `${totalMatches} kết quả`}
            </p>
          )}
        </div>

        {/* ── Nav: scrollable middle ── */}
        <nav className="flex-1 min-h-0 overflow-y-auto px-3 pb-3">
          {filteredGroups.length === 0 ? (
            <EmptySearch />
          ) : (
            <div className="flex flex-col gap-3">
              {filteredGroups.map((group) => {
                const collapsed = collapsedGroups.has(group.key);
                return (
                  <div key={group.key}>
                    <button
                      type="button"
                      onClick={() => toggleGroup(group.key)}
                      aria-expanded={!collapsed}
                      aria-controls={`sidebar-group-${group.key}`}
                      className="w-full flex items-center gap-1.5 px-2 py-1 text-on-surface-variant hover:text-on-surface transition-colors group"
                    >
                      {group.icon && (
                        <span aria-hidden="true" className="material-symbols-outlined text-[14px]">
                          {group.icon}
                        </span>
                      )}
                      <span className="text-[11px] font-bold uppercase tracking-wider">
                        {group.label}
                      </span>
                      <span className="ml-auto inline-flex items-center justify-center min-w-[18px] h-4 px-1 rounded-full bg-surface-container-high text-[10px] font-bold text-on-surface-variant">
                        {group.items.length}
                      </span>
                      <span
                        aria-hidden="true"
                        className={`material-symbols-outlined text-[16px] transition-transform ${collapsed ? "" : "rotate-180"}`}
                      >
                        expand_more
                      </span>
                    </button>

                    {!collapsed && (
                      <ul
                        id={`sidebar-group-${group.key}`}
                        className="flex flex-col gap-0.5 mt-1"
                      >
                        {group.items.map((item) => (
                          <li key={item.code}>
                            <SidebarLink
                              item={item}
                              isConflicts={isConflictsItem(item.href)}
                              onNavigate={onClose}
                            />
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </nav>
      </aside>
    </>
  );
}

// ── Sub-components ──────────────────────────────────────────────────────────

function SidebarLink({
  item,
  isConflicts,
  onNavigate,
}: {
  item: NavigationItem;
  isConflicts: boolean;
  onNavigate?: () => void;
}) {
  const isActive = !!item.active;
  return (
    <Link
      href={item.href}
      onClick={onNavigate}
      aria-current={isActive ? "page" : undefined}
      className={`
        relative flex items-center gap-3 pl-4 pr-3 py-2 rounded-lg
        text-[13px] font-medium transition-all
        ${isActive
          ? "bg-primary-fixed text-primary border-l-2 border-primary font-semibold"
          : "text-on-surface-variant hover:bg-surface-container-high hover:text-on-surface border-l-2 border-transparent"
        }
      `}
    >
      <span
        aria-hidden="true"
        className={`material-symbols-outlined text-[18px] shrink-0 ${
          isActive ? "text-primary" : ""
        } ${isActive ? "font-variation-fill" : ""}`}
        style={isActive ? { fontVariationSettings: "'FILL' 1, 'wght' 500" } : undefined}
      >
        {item.icon || "dashboard"}
      </span>
      <span className="truncate">{item.label}</span>
      {isConflicts ? <ConflictBadge /> : null}
    </Link>
  );
}

function EnvBadge() {
  const env = (process.env.NEXT_PUBLIC_APP_ENV ?? process.env.NODE_ENV ?? "development").toLowerCase();
  const isProd = env === "production";
  return (
    <span
      aria-label={`Môi trường: ${env}`}
      title={`Môi trường: ${env}`}
      className={`
        inline-flex items-center px-1.5 py-0.5 rounded text-[9px] font-bold uppercase tracking-wider shrink-0
        ${isProd
          ? "bg-tertiary-container text-on-tertiary-container"
          : "bg-secondary-container text-secondary"
        }
      `}
    >
      {isProd ? "PROD" : "DEV"}
    </span>
  );
}

function EmptySearch() {
  return (
    <div className="flex flex-col items-center justify-center gap-2 px-3 py-8 text-center">
      <span aria-hidden="true" className="material-symbols-outlined text-[28px] text-outline">search_off</span>
      <p className="text-[12px] text-on-surface-variant">
        Không tìm thấy chức năng phù hợp.
      </p>
      <p className="text-[11px] text-outline">Thử từ khóa khác.</p>
    </div>
  );
}

// ── sessionStorage helpers ──────────────────────────────────────────────────

function loadCollapsed(): Set<SidebarGroupKey> {
  if (typeof window === "undefined") return new Set();
  try {
    const raw = window.sessionStorage.getItem(SIDEBAR_COLLAPSED_KEY);
    if (!raw) return new Set();
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return new Set();
    return new Set(parsed.filter((v): v is SidebarGroupKey => typeof v === "string"));
  } catch {
    return new Set();
  }
}

function persistCollapsed(set: Set<SidebarGroupKey>) {
  if (typeof window === "undefined") return;
  try {
    window.sessionStorage.setItem(SIDEBAR_COLLAPSED_KEY, JSON.stringify(Array.from(set)));
  } catch {
    // storage may be full or disabled — silent best-effort
  }
}

function loadSearch(): string {
  if (typeof window === "undefined") return "";
  try {
    return window.sessionStorage.getItem(SIDEBAR_SEARCH_KEY) ?? "";
  } catch {
    return "";
  }
}

function persistSearch(value: string) {
  if (typeof window === "undefined") return;
  try {
    if (value) {
      window.sessionStorage.setItem(SIDEBAR_SEARCH_KEY, value);
    } else {
      window.sessionStorage.removeItem(SIDEBAR_SEARCH_KEY);
    }
  } catch {
    // silent best-effort
  }
}
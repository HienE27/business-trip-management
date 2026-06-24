"use client";

import Link from "next/link";
import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import type { NavigationItem } from "@/types/schedule";
import { ConflictBadge } from "@/components/realtime/ConflictBadge";
import { useNotifications } from "@/components/ui/NotificationContext";
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
  // Group the flat navigation into the 4 buckets.
  const grouped = useMemo(() => groupNavigationItems(items), [items]);

  const [collapsedGroups, setCollapsedGroups] = useState<Set<SidebarGroupKey>>(
    () => loadCollapsed(),
  );
  const [search, setSearch] = useState<string>(() => loadSearch());

  useEffect(() => {
    persistCollapsed(collapsedGroups);
  }, [collapsedGroups]);
  useEffect(() => {
    persistSearch(search);
  }, [search]);

  // Find the group that owns the currently-active item. Used to
  // auto-expand it on navigation so the user can see where they are.
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

  const totalMatches = filteredGroups.reduce(
    (sum, g) => sum + g.items.length,
    0,
  );

  // ── Scroll preservation ──────────────────────────────────────────────────
  // Why: re-renders caused by group auto-expand, search filtering, or active
  // item change would otherwise reset <nav> scrollTop to 0, making the sidebar
  // "jump" back to the top on every navigation. We snapshot scrollTop right
  // before each render and restore it in useLayoutEffect so the user never
  // sees the jump.
  const navRef = useRef<HTMLElement | null>(null);
  const preservedScrollTop = useRef(0);
  const restoredOnce = useRef(false);

  useLayoutEffect(() => {
    const nav = navRef.current;
    if (!nav) return;
    // Skip the very first paint — there's nothing to restore yet.
    if (!restoredOnce.current) {
      restoredOnce.current = true;
      // First render: if we have an active item, scroll it into view.
      // Guard for jsdom where scrollIntoView is not implemented.
      const activeEl = nav.querySelector<HTMLElement>(
        '[data-sidebar-active="true"]',
      );
      if (activeEl && typeof activeEl.scrollIntoView === "function") {
        activeEl.scrollIntoView({ block: "nearest" });
      }
      return;
    }
    // Restore the scroll position that was current at the moment of the
    // last render. This is a no-op if scrollTop is unchanged.
    if (preservedScrollTop.current !== nav.scrollTop) {
      nav.scrollTop = preservedScrollTop.current;
    }
  });

  // Capture scrollTop synchronously before React commits the next render
  // (MutationObserver fires before useLayoutEffect on the same tick).
  useEffect(() => {
    const nav = navRef.current;
    if (!nav) return;
    const onScroll = () => {
      preservedScrollTop.current = nav.scrollTop;
    };
    nav.addEventListener("scroll", onScroll, { passive: true });
    return () => nav.removeEventListener("scroll", onScroll);
  }, []);

  const toggleGroup = useCallback((key: SidebarGroupKey) => {
    setCollapsedGroups((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }, []);

  const isConflictsItem = useCallback(
    (href: string) => href === CONFLICTS_HREF,
    [],
  );

  return (
    <>
      {mobileOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/40 backdrop-blur-sm md:hidden"
          onClick={onClose}
          aria-hidden="true"
        />
      )}

      <aside
        id="app-sidebar"
        aria-label="Điều hướng chính"
        className={`
          fixed left-0 top-0 h-screen w-64 border-r border-outline-variant
          bg-surface-container-low z-50 flex flex-col
          hidden md:flex
          ${mobileOpen ? "translate-x-0" : "-translate-x-full"}
          md:translate-x-0
          transition-transform duration-200
        `}
      >
        {/* ── Logo + env badge ───────────────────────────────────────────── */}
        <div
          className="relative px-5 pt-5 pb-4 flex items-center gap-3 shrink-0
            bg-gradient-to-b from-surface-container-lowest to-surface-container-low
            border-b border-outline-variant"
        >
          <div
            className="w-10 h-10 rounded-lg bg-primary text-on-primary flex items-center
              justify-center shrink-0 shadow-sm ring-1 ring-inset ring-primary/20"
          >
            <span
              aria-hidden="true"
              className="material-symbols-outlined text-[20px]"
              style={{ fontVariationSettings: "'FILL' 1, 'wght' 500" }}
            >
              medical_services
            </span>
          </div>
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2">
              <h1 className="font-bold text-title-md leading-tight text-primary truncate">
                Quản lý Lịch
              </h1>
              <EnvBadge />
            </div>
            <p className="text-label-sm text-on-surface-variant truncate">
              Hệ thống điều phối
            </p>
          </div>
        </div>

        {/* ── Search ─────────────────────────────────────────────────────── */}
        <div className="px-3 pt-3 pb-2 shrink-0">
          <div className="relative">
            <span
              className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2
                text-outline text-[16px] pointer-events-none"
            >
              search
            </span>
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Tìm chức năng…"
              aria-label="Tìm chức năng trong sidebar"
              className="w-full rounded-lg border border-outline-variant bg-surface
                h-9 pl-9 pr-8 text-[13px] text-on-surface placeholder:text-outline
                focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20
                focus:bg-surface-container-lowest transition-all"
            />
            {search && (
              <button
                type="button"
                onClick={() => setSearch("")}
                aria-label="Xóa tìm kiếm"
                className="absolute right-2 top-1/2 -translate-y-1/2 p-1 rounded
                  hover:bg-surface-container-low transition-colors"
              >
                <span className="material-symbols-outlined text-[12px] text-outline">
                  close
                </span>
              </button>
            )}
          </div>
          {search && (
            <p className="mt-1.5 px-1 text-label-sm text-on-surface-variant">
              {totalMatches === 0 ? "Không có kết quả" : `${totalMatches} kết quả`}
            </p>
          )}
        </div>

        {/* ── Nav: scrollable middle with top/bottom fade masks ──────────── */}
        <div className="relative flex-1 min-h-0">
          {/* Top fade — appears when content is scrolled down */}
          <div
            aria-hidden="true"
            className="pointer-events-none absolute top-0 inset-x-0 h-6 z-10
              bg-gradient-to-b from-surface-container-low to-transparent"
          />
          {/* Bottom fade — appears when content overflows below */}
          <div
            aria-hidden="true"
            className="pointer-events-none absolute bottom-0 inset-x-0 h-6 z-10
              bg-gradient-to-t from-surface-container-low to-transparent"
          />

          <nav
            ref={navRef}
            className="absolute inset-0 overflow-y-auto px-3 py-3
              scroll-smooth [scrollbar-gutter:stable]"
          >
            {filteredGroups.length === 0 ? (
              <EmptySearch />
            ) : (
              <div className="flex flex-col gap-4">
                {filteredGroups.map((group) => {
                  const collapsed = collapsedGroups.has(group.key);
                  return (
                    <div key={group.key}>
                      <button
                        type="button"
                        onClick={() => toggleGroup(group.key)}
                        aria-expanded={!collapsed}
                        aria-controls={`sidebar-group-${group.key}`}
                        className="w-full flex items-center gap-1.5 px-2 py-1
                          text-on-surface-variant hover:text-on-surface
                          transition-colors rounded group/header"
                      >
                        {group.icon && (
                          <span
                            aria-hidden="true"
                            className="material-symbols-outlined text-[14px]"
                          >
                            {group.icon}
                          </span>
                        )}
                        <span className="text-label-sm font-bold uppercase tracking-wider">
                          {group.label}
                        </span>
                        <span
                          className="ml-auto inline-flex items-center justify-center
                            min-w-[20px] h-4 px-1.5 rounded-full
                            bg-primary-fixed text-on-primary-fixed-dim text-[10px] font-bold
                            group-hover/header:bg-primary-fixed-dim
                            transition-colors"
                        >
                          {group.items.length}
                        </span>
                        <span
                          aria-hidden="true"
                          className={`material-symbols-outlined text-[16px]
                            transition-transform duration-200 ${
                              collapsed ? "" : "rotate-180"
                            }`}
                        >
                          expand_more
                        </span>
                      </button>

                      {!collapsed && (
                        <ul
                          id={`sidebar-group-${group.key}`}
                          className="flex flex-col gap-0.5 mt-1 animate-slide-down"
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
        </div>
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
  const { unreadCount } = useNotifications();

  const showNotificationBadge = item.href === "/notifications" && unreadCount > 0;

  return (
    <Link
      href={item.href}
      onClick={onNavigate}
      aria-current={isActive ? "page" : undefined}
      data-sidebar-active={isActive ? "true" : undefined}
      className={`
        relative flex items-center gap-3 pl-4 pr-3 py-2 rounded-lg
        text-[13px] font-medium transition-all
        ${isActive
          ? "bg-primary-fixed text-primary font-semibold border-l-2 border-primary shadow-[0_1px_2px_rgba(0,74,198,0.08)]"
          : "text-on-surface-variant hover:bg-surface-container-highest hover:text-on-surface border-l-2 border-transparent"
        }
      `}
    >
      <span
        aria-hidden="true"
        className="material-symbols-outlined text-[18px] shrink-0"
        style={
          isActive
            ? { fontVariationSettings: "'FILL' 1, 'wght' 500" }
            : undefined
        }
      >
        {item.icon || "dashboard"}
      </span>
      <span className="truncate flex-1">{item.label}</span>
      {isConflicts ? <ConflictBadge /> : null}
      {showNotificationBadge && (
        <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-error px-1 text-[10px] font-bold text-on-error ring-2 ring-surface shrink-0">
          {unreadCount > 99 ? "99+" : unreadCount}
        </span>
      )}
    </Link>
  );
}

function EnvBadge() {
  const env = (
    process.env.NEXT_PUBLIC_APP_ENV ??
    process.env.NODE_ENV ??
    "development"
  ).toLowerCase();
  const isProd = env === "production";
  return (
    <span
      aria-label={`Môi trường: ${env}`}
      title={`Môi trường: ${env}`}
      className={`
        inline-flex items-center px-1.5 py-0.5 rounded text-[9px] font-bold
        uppercase tracking-wider shrink-0 ring-1 ring-inset
        ${isProd
          ? "bg-tertiary-container text-on-tertiary-container ring-tertiary/20"
          : "bg-secondary-container text-secondary ring-secondary/20"
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
      <span
        aria-hidden="true"
        className="material-symbols-outlined text-[28px] text-outline"
      >
        search_off
      </span>
      <p className="text-[12px] text-on-surface-variant">
        Không tìm thấy chức năng phù hợp.
      </p>
      <p className="text-label-sm text-outline">Thử từ khóa khác.</p>
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
    window.sessionStorage.setItem(
      SIDEBAR_COLLAPSED_KEY,
      JSON.stringify(Array.from(set)),
    );
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
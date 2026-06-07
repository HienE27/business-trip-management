import Link from "next/link";
import type { NavigationItem } from "@/types/schedule";

type AppSidebarProps = {
  items: NavigationItem[];
};

const FOOTER_ITEMS = [
  { label: "Thong bao", href: "/notifications", icon: "notifications" },
  { label: "Cai dat", href: "/settings", icon: "settings" },
  { label: "Ho so ca nhan", href: "/profile", icon: "person" },
];

export function AppSidebar({ items }: AppSidebarProps) {
  return (
    <aside
      aria-label="Dieu huong chinh"
      className="hidden md:flex flex-col fixed left-0 top-0 h-screen w-[260px] border-r border-outline-variant bg-surface-container-low py-4 z-50 overflow-y-auto"
    >
      {/* Logo Area */}
      <div className="px-6 mb-8 flex items-center gap-3">
        <div className="w-10 h-10 rounded-lg bg-primary text-white flex items-center justify-center shrink-0 shadow-md">
          <span
            aria-hidden="true"
            className="material-symbols-outlined fill text-[20px]"
          >
            calendar_month
          </span>
        </div>
        <div>
          <p className="font-title-lg text-on-surface font-bold">MedSchedule Pro</p>
          <p className="text-label-sm text-on-surface-variant uppercase tracking-wider">
            Quan ly lich cong tac
          </p>
        </div>
      </div>

      {/* Main Navigation */}
      <nav className="flex-1 flex flex-col gap-1 px-3">
        {items.map((item) => {
          const isActive = item.active;
          return (
            <Link
              className={
                isActive
                  ? "flex items-center gap-3 px-4 py-2.5 rounded-lg bg-primary-container/10 text-primary font-semibold transition-all text-body-sm shadow-sm"
                  : "flex items-center gap-3 px-4 py-2.5 rounded-lg text-on-surface-variant hover:bg-surface-container-high transition-all text-body-sm"
              }
              href={item.href}
              key={item.code}
            >
              <span
                aria-hidden="true"
                className={`material-symbols-outlined text-[20px] shrink-0 ${
                  isActive ? "fill" : ""
                }`}
              >
                {item.icon || "dashboard"}
              </span>
              <span className="truncate">{item.label}</span>
            </Link>
          );
        })}
      </nav>

      {/* Footer Navigation */}
      <div className="mt-auto px-3 pt-4 border-t border-outline-variant flex flex-col gap-1">
        {FOOTER_ITEMS.map((item) => (
          <Link
            className="flex items-center gap-3 px-4 py-2.5 rounded-lg text-on-surface-variant hover:bg-surface-container-high transition-all text-body-sm"
            href={item.href}
            key={item.href}
          >
            <span
              aria-hidden="true"
              className="material-symbols-outlined text-[20px] shrink-0"
            >
              {item.icon}
            </span>
            <span className="truncate">{item.label}</span>
          </Link>
        ))}
      </div>
    </aside>
  );
}

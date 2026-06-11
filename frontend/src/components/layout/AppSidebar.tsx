import Link from "next/link";
import type { NavigationItem } from "@/types/schedule";

type AppSidebarProps = {
  items: NavigationItem[];
  mobileOpen?: boolean;
  onClose?: () => void;
};

export function AppSidebar({ items, mobileOpen = false, onClose }: AppSidebarProps) {
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
        {/* Logo */}
        <div className="px-6 mb-6 flex items-center gap-3 shrink-0">
          <div className="w-10 h-10 rounded-lg bg-primary text-on-primary flex items-center justify-center shadow-sm shrink-0">
            <span aria-hidden="true" className="material-symbols-outlined text-[20px]">medical_services</span>
          </div>
          <div>
            <h1 className="font-title-lg text-primary font-bold leading-tight">Quản lý Lịch</h1>
            <p className="text-label-sm text-on-surface-variant">Hệ thống điều phối</p>
          </div>
        </div>

        {/* Nav + Footer wrapper — grows to fill space */}
        <div className="flex flex-col flex-1 min-h-0">
          {/* Nav — scrollable */}
          <nav className="flex flex-col gap-1 px-3 flex-1 overflow-y-auto">
            {items
              .filter((item) => item.code !== "settings")
              .map((item) => {
                const isActive = item.active;
                return (
                  <Link
                    className={`flex items-center gap-3 px-4 py-2.5 rounded-lg transition-all font-medium text-body-sm ${
                      isActive
                        ? "bg-primary-container text-on-primary-container border-l-4 border-primary font-semibold"
                        : "text-on-surface-variant hover:bg-surface-container-high"
                    }`}
                    href={item.href}
                    key={item.code}
                    onClick={onClose}
                    aria-current={isActive ? "page" : undefined}
                  >
                    <span
                      aria-hidden="true"
                      className="material-symbols-outlined text-[20px] shrink-0"
                    >
                      {item.icon || "dashboard"}
                    </span>
                    <span className="truncate">{item.label}</span>
                  </Link>
                );
              })}
          </nav>

          {/* Footer — always at bottom */}
          <div className="mt-auto px-3 pt-4 shrink-0">
            <div className="border-t border-outline-variant">
              {[
                { label: "Cài đặt", icon: "settings", href: "/settings" },
                { label: "Hồ sơ cá nhân", icon: "person", href: "/staff/profile" },
              ].map((item) => (
                <Link
                  className="flex items-center gap-3 px-4 py-2.5 rounded-lg text-on-surface-variant hover:bg-surface-container-high transition-all font-medium text-body-sm"
                  href={item.href}
                  key={item.href}
                  onClick={onClose}
                >
                  <span aria-hidden="true" className="material-symbols-outlined text-[20px] shrink-0">
                    {item.icon}
                  </span>
                  <span className="truncate">{item.label}</span>
                </Link>
              ))}
            </div>
          </div>
        </div>
      </aside>
    </>
  );
}

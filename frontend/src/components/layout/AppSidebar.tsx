import Link from "next/link";
import type { NavigationItem } from "@/types/schedule";

type AppSidebarProps = {
  items: NavigationItem[];
};

export function AppSidebar({ items }: AppSidebarProps) {
  return (
    <aside className="border-r border-[#202832] bg-[#111418] text-white max-lg:hidden">
      <div className="flex h-16 items-center gap-3 border-b border-white/10 px-4">
        <div className="grid size-9 place-items-center rounded-lg bg-white text-sm font-bold text-[#111418] shadow-[0_1px_2px_rgba(0,0,0,0.16)]">
          MS
        </div>
        <div>
          <p className="text-sm font-semibold leading-5">MedSchedule Pro</p>
          <p className="text-xs leading-4 text-white/48">Clinical operations system</p>
        </div>
      </div>
      <nav className="space-y-1 px-3 py-4 text-sm">
        {items.map((item) => (
          <Link
            className={`flex h-10 items-center justify-between rounded-lg px-3 ${
              item.active
                ? "bg-white text-[#111418] shadow-[0_1px_2px_rgba(0,0,0,0.18)]"
                : "text-white/66 hover:bg-white/8 hover:text-white"
            }`}
            href={item.href}
            key={item.code}
          >
            <span>{item.label}</span>
            <span className={item.active ? "text-slate-500" : "text-white/35"}>
              {item.code}
            </span>
          </Link>
        ))}
      </nav>
    </aside>
  );
}

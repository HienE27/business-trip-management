import { AuthStatus } from "@/components/auth/AuthStatus";

type DashboardHeaderProps = {
  title: string;
  description: string;
  primaryAction?: string;
  secondaryAction?: string;
};

export function DashboardHeader({
  title,
  description,
  primaryAction = "Lưu thay đổi",
  secondaryAction = "Xuất báo cáo",
}: DashboardHeaderProps) {
  return (
    <header className="flex h-16 items-center justify-between gap-4 border-b border-[#dfe4ea] bg-white px-6 max-sm:h-auto max-sm:flex-col max-sm:items-start max-sm:px-4 max-sm:py-4">
      <div className="min-w-0">
        <h1 className="truncate text-lg font-semibold leading-6 tracking-normal text-[#111418]">
          {title}
        </h1>
        <p className="truncate text-sm leading-5 text-[#667085]">{description}</p>
      </div>
      <div className="flex shrink-0 items-center gap-2 max-sm:w-full max-sm:flex-wrap">
        <button className="h-9 rounded-lg border border-[#dfe4ea] bg-white px-3 text-sm font-medium text-[#364152] shadow-[0_1px_2px_rgba(15,23,42,0.05)] max-sm:flex-1">
          {secondaryAction}
        </button>
        <button className="h-9 rounded-lg bg-[#111418] px-3 text-sm font-medium text-white shadow-[0_1px_2px_rgba(15,23,42,0.08)] max-sm:flex-1">
          {primaryAction}
        </button>
        <AuthStatus />
      </div>
    </header>
  );
}

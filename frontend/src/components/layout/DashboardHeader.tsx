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
    <header className="flex h-16 items-center justify-between border-b border-slate-200 bg-white px-6 max-sm:h-auto max-sm:flex-col max-sm:items-start max-sm:gap-3 max-sm:px-4 max-sm:py-4">
      <div>
        <h1 className="text-lg font-semibold tracking-normal">{title}</h1>
        <p className="text-sm text-slate-500">{description}</p>
      </div>
      <div className="flex items-center gap-2 max-sm:w-full">
        <button className="h-9 rounded-md border border-slate-200 bg-white px-3 text-sm font-medium text-slate-700 shadow-sm max-sm:flex-1">
          {secondaryAction}
        </button>
        <button className="h-9 rounded-md bg-slate-950 px-3 text-sm font-medium text-white shadow-sm max-sm:flex-1">
          {primaryAction}
        </button>
      </div>
    </header>
  );
}

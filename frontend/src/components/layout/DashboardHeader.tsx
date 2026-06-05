type DashboardHeaderProps = {
  title: string;
  description: string;
  primaryAction?: string;
  secondaryAction?: string;
  onPrimaryAction?: () => void;
  onSecondaryAction?: () => void;
};

export function DashboardHeader({
  title,
  description,
  primaryAction,
  secondaryAction,
  onPrimaryAction,
  onSecondaryAction,
}: DashboardHeaderProps) {
  return (
    <header className="flex h-16 items-center justify-between border-b border-slate-200 bg-white px-6 max-sm:h-auto max-sm:flex-col max-sm:items-start max-sm:gap-3 max-sm:px-4 max-sm:py-4">
      <div>
        <h1 className="text-lg font-semibold tracking-normal">{title}</h1>
        <p className="text-sm text-slate-500">{description}</p>
      </div>
      <div className="flex items-center gap-2 max-sm:w-full">
        {secondaryAction && (
          <button 
            onClick={onSecondaryAction}
            className="h-9 rounded-md border border-slate-200 bg-white px-3 text-sm font-medium text-slate-700 shadow-sm max-sm:flex-1 transition-colors hover:bg-slate-50"
          >
            {secondaryAction}
          </button>
        )}
        {primaryAction && (
          <button 
            onClick={onPrimaryAction}
            className="h-9 rounded-md bg-slate-950 px-3 text-sm font-medium text-white shadow-sm max-sm:flex-1 transition-colors hover:bg-slate-800"
          >
            {primaryAction}
          </button>
        )}
      </div>
    </header>
  );
}

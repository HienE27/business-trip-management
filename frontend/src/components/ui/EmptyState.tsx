type EmptyStateProps = {
  icon?: string;
  title: string;
  description?: string;
  action?: React.ReactNode;
  className?: string;
};

export function EmptyState({
  icon = "inbox",
  title,
  description,
  action,
  className = "",
}: EmptyStateProps) {
  return (
    <div
      className={`flex flex-col items-center justify-center py-16 text-center ${className}`}
      role="status"
      aria-live="polite"
    >
      <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-xl bg-surface-container-low text-on-surface-variant shadow-sm">
        <span
          aria-hidden="true"
          className="material-symbols-outlined text-[32px]"
        >
          {icon}
        </span>
      </div>
      <p className="text-title-lg text-on-surface">{title}</p>
      {description ? (
        <p className="mt-2 max-w-xs text-body-sm text-on-surface-variant">
          {description}
        </p>
      ) : null}
      {action ? <div className="mt-5">{action}</div> : null}
    </div>
  );
}

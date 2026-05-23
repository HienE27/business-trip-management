import type { ReactNode } from "react";

type SectionCardProps = {
  title: string;
  description?: string;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
};

export function SectionCard({
  title,
  description,
  action,
  children,
  className = "",
}: SectionCardProps) {
  return (
    <section
      className={`rounded-lg border border-slate-200 bg-white shadow-[0_1px_2px_rgba(15,23,42,0.05)] ${className}`}
    >
      <div className="flex min-h-14 items-center justify-between gap-3 border-b border-slate-200 px-4 py-3">
        <div>
          <h2 className="text-sm font-semibold">{title}</h2>
          {description ? <p className="text-xs text-slate-500">{description}</p> : null}
        </div>
        {action}
      </div>
      {children}
    </section>
  );
}

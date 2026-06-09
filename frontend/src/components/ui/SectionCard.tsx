import type { ReactNode } from "react";

type SectionCardProps = {
  title: ReactNode;
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
  className,
}: SectionCardProps) {
  return (
    <section className={`bg-surface-container-lowest rounded-xl border border-outline-variant shadow-[0_1px_3px_0_rgba(0,0,0,0.08),0_1px_2px_-1px_rgba(0,0,0,0.06)] overflow-hidden ${className ?? ""}`}>
      <div className="px-5 py-4 border-b border-outline-variant flex items-start justify-between gap-4">
        <div className="space-y-1">
          <h2 className="text-title-lg text-on-surface">{title}</h2>
          {description && (
            <p className="max-w-3xl font-body-sm text-on-surface-variant">{description}</p>
          )}
        </div>
        {action && <div className="shrink-0">{action}</div>}
      </div>
      {children}
    </section>
  );
}

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
    <section className={`bg-surface-container-lowest rounded-xl border border-outline-variant shadow-sm overflow-hidden hover:bg-surface-container-low transition-colors ${className ?? ""}`}>
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

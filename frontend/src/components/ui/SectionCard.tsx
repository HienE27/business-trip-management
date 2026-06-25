import type { ReactNode } from "react";

type SectionCardProps = {
  title: string | ReactNode;
  description?: string | ReactNode;
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
    <section className={`bg-surface-container-lowest rounded-lg border border-outline-variant shadow-sm overflow-hidden ${className ?? ""}`}>
      <div className="px-4 py-3 border-b border-outline-variant flex items-start justify-between gap-4 hover:border-primary/20 transition-colors">
        <div className="space-y-0.5">
          <h2 className="text-title-lg text-on-surface">{title}</h2>
          {description && (
            <p className="max-w-3xl font-body-sm text-on-surface-variant">{description}</p>
          )}
        </div>
        {action && (
          <div className="shrink-0 transition-transform hover:scale-105">{action}</div>
        )}
      </div>
      {children}
    </section>
  );
}

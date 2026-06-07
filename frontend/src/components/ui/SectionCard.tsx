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
  className,
}: SectionCardProps) {
  return (
    <section
      className={`overflow-hidden rounded-lg border border-outline-variant bg-surface-container-lowest shadow-sm ${className ?? ""}`}
    >
      <div className="flex min-h-[60px] flex-col justify-between gap-3 border-b border-outline-variant px-5 py-4 md:flex-row md:items-start">
        <div className="space-y-1">
          <h2 className="font-title-lg text-on-surface">{title}</h2>
          {description ? (
            <p className="max-w-3xl font-body-sm text-on-surface-variant">{description}</p>
          ) : null}
        </div>
        {action ? <div className="shrink-0 self-start">{action}</div> : null}
      </div>
      {children}
    </section>
  );
}

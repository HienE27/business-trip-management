"use client";

interface BeforeAfterTableProps {
  before: {
    coverage: number;
    fairness: number;
    violations: number;
    changes: number;
  };
  after: {
    coverage: number;
    fairness: number;
    violations: number;
    changes: number;
  };
}

/**
 * Before/After comparison table showing key metrics.
 */
export function BeforeAfterTable({ before, after }: BeforeAfterTableProps) {
  const rows = [
    {
      label: "Coverage",
      before: `${before.coverage.toFixed(1)}%`,
      after: `${after.coverage.toFixed(1)}%`,
      delta: after.coverage - before.coverage,
      unit: "%",
      higher: true,
    },
    {
      label: "Fairness (CV)",
      before: before.fairness.toFixed(3),
      after: after.fairness.toFixed(3),
      delta: after.fairness - before.fairness,
      unit: "",
      higher: false, // Lower is better for CV
    },
    {
      label: "Violations",
      before: before.violations.toString(),
      after: after.violations.toString(),
      delta: after.violations - before.violations,
      unit: "",
      higher: false, // Lower is better
    },
    {
      label: "Total Changes",
      before: before.changes.toString(),
      after: after.changes.toString(),
      delta: after.changes - before.changes,
      unit: "",
      higher: null, // Neutral - changes can go either way
    },
  ];

  const getDeltaClass = (row: typeof rows[0]) => {
    if (row.delta === 0) return "text-on-surface-variant";
    if (row.higher === null) return "text-on-surface-variant";
    if (row.higher) {
      return row.delta > 0 ? "text-secondary" : "text-error";
    }
    return row.delta < 0 ? "text-secondary" : "text-error";
  };

  const formatDelta = (row: typeof rows[0]) => {
    const prefix = row.delta > 0 ? "+" : "";
    return `${prefix}${row.delta.toFixed(row.unit === "%" ? 1 : 3)}${row.unit}`;
  };

  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-xl overflow-hidden">
      <table className="w-full">
        <thead>
          <tr className="bg-surface-container-low border-b border-outline-variant">
            <th className="py-3 px-4 text-left text-label-sm text-on-surface-variant uppercase font-semibold">
              Metric
            </th>
            <th className="py-3 px-4 text-center text-label-sm text-on-surface-variant uppercase font-semibold">
              Trước
            </th>
            <th className="py-3 px-4 text-center text-label-sm text-on-surface-variant uppercase font-semibold">
              Sau
            </th>
            <th className="py-3 px-4 text-right text-label-sm text-on-surface-variant uppercase font-semibold">
              Thay đổi
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-outline-variant">
          {rows.map((row) => (
            <tr key={row.label} className="hover:bg-surface-container-low transition-colors">
              <td className="py-3 px-4">
                <span className="text-body-md text-on-surface">{row.label}</span>
              </td>
              <td className="py-3 px-4 text-center">
                <span className="text-body-md text-on-surface-variant font-mono">
                  {row.before}
                </span>
              </td>
              <td className="py-3 px-4 text-center">
                <span className="text-body-md text-on-surface font-bold font-mono">
                  {row.after}
                </span>
              </td>
              <td className={`py-3 px-4 text-right font-mono ${getDeltaClass(row)}`}>
                <div className="flex items-center justify-end gap-1">
                  {row.delta !== 0 && (
                    <span className={`material-symbols-outlined text-[14px] ${getDeltaClass(row)}`}>
                      {row.higher === null
                        ? "arrow_forward"
                        : row.higher
                        ? row.delta > 0
                          ? "arrow_upward"
                          : "arrow_downward"
                        : row.delta < 0
                        ? "arrow_downward"
                        : "arrow_upward"}
                    </span>
                  )}
                  <span className="text-label-md font-bold">{formatDelta(row)}</span>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

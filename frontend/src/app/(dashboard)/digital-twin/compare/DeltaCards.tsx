"use client";

interface DeltaCardsProps {
  coverageDelta: number;
  fairnessDelta: number;
  violationsDelta: number;
  changes: number;
}

/**
 * Delta cards showing improvement indicators.
 */
export function DeltaCards({ coverageDelta, fairnessDelta, violationsDelta, changes }: DeltaCardsProps) {
  const cards = [
    {
      label: "Coverage",
      delta: coverageDelta,
      unit: "%",
      icon: "verified_user",
      higherIsBetter: true,
      color: coverageDelta >= 0 ? "secondary" : "error",
    },
    {
      label: "Fairness",
      delta: fairnessDelta,
      unit: "%",
      icon: "balance",
      higherIsBetter: false, // Improvement = more negative (lower CV)
      color: fairnessDelta <= 0 ? "secondary" : "error",
    },
    {
      label: "Violations",
      delta: violationsDelta,
      unit: "",
      icon: "warning",
      higherIsBetter: false, // Lower is better
      color: violationsDelta <= 0 ? "secondary" : "error",
    },
    {
      label: "Changes",
      delta: changes,
      unit: "",
      icon: "swap_horiz",
      higherIsBetter: null, // Neutral
      color: "primary",
    },
  ];

  const formatDelta = (card: typeof cards[0]) => {
    const prefix = card.delta > 0 ? "+" : "";
    const unit = card.unit || "";
    return `${prefix}${card.delta.toFixed(card.unit === "%" ? 1 : 0)}${unit}`;
  };

  const getTone = (card: typeof cards[0]): "success" | "warning" | "error" => {
    if (card.delta === 0) return "warning";
    if (card.higherIsBetter === null) return "warning";
    if (card.higherIsBetter) {
      return card.delta > 0 ? "success" : "error";
    }
    return card.delta < 0 ? "success" : "error";
  };

  return (
    <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
      {cards.map((card) => {
        const tone = getTone(card);
        const isPositive = tone === "success";

        const toneConfig = {
          success: {
            bg: "bg-secondary-container",
            text: "text-on-secondary-container",
            iconBg: "bg-secondary",
            iconText: "text-on-secondary",
            deltaBg: "bg-secondary",
            deltaText: "text-on-secondary",
          },
          warning: {
            bg: "bg-tertiary-fixed",
            text: "text-on-tertiary-fixed-variant",
            iconBg: "bg-tertiary",
            iconText: "text-on-tertiary",
            deltaBg: "bg-tertiary",
            deltaText: "text-on-tertiary",
          },
          error: {
            bg: "bg-error-container",
            text: "text-on-error-container",
            iconBg: "bg-error",
            iconText: "text-on-error",
            deltaBg: "bg-error",
            deltaText: "text-on-error",
          },
        }[tone];

        return (
          <div
            key={card.label}
            className={`rounded-xl border border-outline-variant p-4 ${toneConfig.bg} ${toneConfig.text} flex flex-col gap-3`}
          >
            <div className="flex items-start justify-between">
              <span className="text-label-sm font-medium">{card.label}</span>
              <span
                className={`material-symbols-outlined text-[18px] ${toneConfig.iconBg} ${toneConfig.iconText} p-1 rounded-md`}
                style={{ fontVariationSettings: "'FILL' 0" }}
              >
                {card.icon}
              </span>
            </div>

            <div className="flex items-baseline gap-2">
              <span className={`text-display-lg font-bold ${toneConfig.deltaText}`}>
                {formatDelta(card)}
              </span>
              {card.label !== "Changes" && (
                <span className="text-label-sm opacity-70">
                  {isPositive ? "improved" : "degraded"}
                </span>
              )}
            </div>

            {card.label !== "Changes" && (
              <div className={`text-label-xs ${toneConfig.text} opacity-70`}>
                {card.higherIsBetter ? "Higher is better" : "Lower is better"}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}

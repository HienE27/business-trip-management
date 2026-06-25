"use client";

const TABS = [
  { id: "info", label: "Thông tin chung", icon: "person" },
  { id: "schedule", label: "Lịch công tác", icon: "calendar_month" },
  { id: "stats", label: "Thống kê", icon: "bar_chart" },
];

type ProfileTabsProps = {
  activeTab?: string;
  onTabChange?: (tab: string) => void;
  children: React.ReactNode;
};

export function ProfileTabs({ activeTab = "info", onTabChange, children }: ProfileTabsProps) {
  return (
    <div className="bg-surface-container-lowest rounded-xl border border-outline-variant shadow-sm overflow-hidden">
      {/* Tab Header */}
      <div
        role="tablist"
        aria-label="Hồ sơ nhân sự"
        className="flex border-b border-outline-variant bg-surface-container-low"
      >
        {TABS.map((tab) => {
          const isActive = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              role="tab"
              type="button"
              aria-selected={isActive}
              aria-controls={`tabpanel-${tab.id}`}
              id={`tab-${tab.id}`}
              className={`flex-1 flex items-center justify-center gap-2 py-3.5 text-label-md font-medium transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-primary ${
                isActive
                  ? "text-primary bg-surface-container-lowest border-b-2 border-primary"
                  : "text-on-surface-variant hover:text-on-surface hover:bg-surface-container-lowest/50"
              }`}
              onClick={() => onTabChange?.(tab.id)}
            >
              <span className="material-symbols-outlined text-[18px]">{tab.icon}</span>
              {tab.label}
            </button>
          );
        })}
      </div>

      {/* Tab Content */}
      <div className="p-5">
        {children}
      </div>
    </div>
  );
}

"use client";

const TABS = [
  { id: "info", label: "Thông tin chung" },
  { id: "schedule", label: "Lịch công tác gần đây" },
  { id: "stats", label: "Thống kê workload" },
  { id: "history", label: "Nhật ký thay đổi" },
];

type ProfileTabsProps = {
  activeTab?: string;
  onTabChange?: (tab: string) => void;
  children: React.ReactNode;
};

export function ProfileTabs({ activeTab = "info", onTabChange, children }: ProfileTabsProps) {
  return (
    <div className="bg-surface-container-lowest rounded-lg shadow-sm overflow-hidden flex-1 flex flex-col">
      <div
        role="tablist"
        aria-label="Hồ sơ nhân sự"
        className="border-b border-outline-variant flex overflow-x-auto bg-surface-container-low px-4"
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
              className={`px-4 py-3 font-label-md text-label-md border-b-2 whitespace-nowrap transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
                isActive
                  ? "text-primary border-primary"
                  : "text-on-surface-variant border-transparent hover:text-on-surface hover:bg-surface-container-high"
              }`}
              onClick={() => onTabChange?.(tab.id)}
            >
              {tab.label}
            </button>
          );
        })}
      </div>
      <div
        id={`tabpanel-${activeTab}`}
        role="tabpanel"
        aria-labelledby={`tab-${activeTab}`}
        className="p-6"
      >
        {children}
      </div>
    </div>
  );
}

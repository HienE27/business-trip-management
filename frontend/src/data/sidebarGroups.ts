import type { AppSectionKey } from "@/data/navigation";
import type { NavigationItem } from "@/types/schedule";

/**
 * Logical grouping for sidebar items. Items that previously sat as one
 * flat list (17 entries) are split into 4 buckets so users can scan the
 * surface area of the app at a glance:
 *
 *  - overview:    Home + cross-cutting workflows
 *  - scheduling:  Anything that builds / mutates the schedule
 *  - operations:  Live state of staff + requests that affect the schedule
 *  - insights:    Reports, audit, notifications
 *
 * Settings + Profile are intentionally kept OUT of groups because they
 * are surfaced as a footer card (different visual treatment).
 */
export type SidebarGroupKey = "overview" | "scheduling" | "operations" | "insights";

export type SidebarGroup = {
  key: SidebarGroupKey;
  /** Short label rendered above each group (uppercase, label-sm) */
  label: string;
  /** Material Symbol icon shown next to the label, optional */
  icon?: string;
  /** Section keys that belong to this group, in display order */
  sections: AppSectionKey[];
};

export const SIDEBAR_GROUPS: SidebarGroup[] = [
  {
    key: "overview",
    label: "Tổng quan",
    icon: "space_dashboard",
    sections: ["dashboard", "monthly-schedule"],
  },
  {
    key: "scheduling",
    label: "Lập lịch",
    icon: "edit_calendar",
    sections: ["periods", "duty-24", "all-day", "service-clinic", "expert-clinic", "requirements"],
  },
  {
    key: "operations",
    label: "Vận hành",
    icon: "settings_b_roll",
    sections: ["staff", "auto-scheduling", "leave-requests", "shift-swaps", "holidays"],
  },
  {
    key: "insights",
    label: "Theo dõi",
    icon: "monitoring",
    sections: ["reports", "notifications", "audit-history"],
  },
];

/**
 * Lookup: section key → owning group key. Settings / profile are
 * intentionally absent here — they're rendered in the footer card.
 */
const SECTION_TO_GROUP: Record<string, SidebarGroupKey> = SIDEBAR_GROUPS.reduce(
  (acc, group) => {
    group.sections.forEach((sectionKey) => {
      acc[sectionKey] = group.key;
    });
    return acc;
  },
  {} as Record<string, SidebarGroupKey>,
);

export function getGroupForSection(sectionKey: string): SidebarGroupKey | undefined {
  return SECTION_TO_GROUP[sectionKey];
}

/**
 * Group the flat NavigationItem list (filtered to exclude footer-only
 * entries like settings/profile) into the 4 buckets defined above.
 *
 * Items whose section is not represented in any group are silently
 * dropped so the sidebar never shows dangling entries.
 */
export function groupNavigationItems(
  items: NavigationItem[],
): Array<SidebarGroup & { items: NavigationItem[] }> {
  return SIDEBAR_GROUPS.map((group) => ({
    ...group,
    items: items.filter(
      (item) =>
        group.sections.includes(item.code as AppSectionKey) &&
        item.code !== "settings" &&
        item.href !== "/staff/profile",
    ),
  })).filter((group) => group.items.length > 0);
}
import { describe, it, expect } from 'vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';
import {
  groupNavigationItems,
  getGroupForSection,
  SIDEBAR_GROUPS,
} from './sidebarGroups';
import type { NavigationItem } from '@/types/schedule';

function makeItem(code: string, label: string, active = false): NavigationItem {
  return { code, label, href: `/${code}`, icon: 'circle', active };
}

function makeAllItems(activeCode?: string): NavigationItem[] {
  // Replicate APP_SECTIONS order so the test mirrors production
  return [
    'dashboard', 'monthly-schedule', 'periods', 'duty-24', 'all-day',
    'service-clinic', 'expert-clinic', 'auto-scheduling', 'staff',
    'leave-requests', 'shift-swaps', 'requirements', 'reports',
    'holidays', 'notifications', 'audit-history',
  ].map((code) => makeItem(code, code, code === activeCode));
}

describe('groupNavigationItems', () => {
  afterEach(() => cleanup());

  it('groups flat items into 4 buckets in display order', () => {
    const groups = groupNavigationItems(makeAllItems());
    const keys = groups.map((g) => g.key);
    expect(keys).toEqual(['overview', 'scheduling', 'operations', 'insights']);
  });

  it('puts dashboard + monthly-schedule under overview', () => {
    const groups = groupNavigationItems(makeAllItems());
    const overview = groups.find((g) => g.key === 'overview')!;
    expect(overview.items.map((i) => i.code)).toEqual(['dashboard', 'monthly-schedule']);
  });

  it('excludes settings + profile items even if provided', () => {
    const items = [
      ...makeAllItems(),
      makeItem('settings', 'Cài đặt'),
      { code: 'profile', label: 'Hồ sơ', href: '/staff/profile', icon: 'person' },
    ];
    const groups = groupNavigationItems(items);
    const allCodes = groups.flatMap((g) => g.items.map((i) => i.code));
    expect(allCodes).not.toContain('settings');
    expect(allCodes).not.toContain('profile');
  });

  it('drops groups with zero matching items', () => {
    // Only items for the overview group — scheduling/operations/insights should drop
    const items = [makeItem('dashboard', 'Tổng quan'), makeItem('monthly-schedule', 'Lập lịch tháng')];
    const groups = groupNavigationItems(items);
    expect(groups).toHaveLength(1);
    expect(groups[0].key).toBe('overview');
  });

  it('attaches the correct group label to each section', () => {
    expect(getGroupForSection('dashboard')).toBe('overview');
    expect(getGroupForSection('duty-24')).toBe('scheduling');
    expect(getGroupForSection('staff')).toBe('operations');
    expect(getGroupForSection('reports')).toBe('insights');
    expect(getGroupForSection('settings')).toBeUndefined();
  });

  it('exposes every section key in exactly one group', () => {
    const sectionKeys = SIDEBAR_GROUPS.flatMap((g) => g.sections);
    const uniqueKeys = new Set(sectionKeys);
    expect(uniqueKeys.size).toBe(sectionKeys.length);
  });
});
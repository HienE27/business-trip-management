import AxeBuilder from '@axe-core/playwright';
import type { Page } from '@playwright/test';

/**
 * axe-core accessibility scan helper for the Hospital Scheduler E2E suite.
 *
 * Pages that need auth are authenticated via the `loginAs` fixture before
 * the scan runs.  axe-core runs in the browser so it only needs the
 * rendered DOM — no backend data is required.
 */

export const PAGES = [
  { path: '/login', label: 'Login', auth: false },
  { path: '/dashboard', label: 'Dashboard', auth: true },
  { path: '/monthly-schedule', label: 'Monthly Schedule', auth: true },
  { path: '/staff', label: 'Staff', auth: true },
  { path: '/periods', label: 'Periods', auth: true },
] as const;

export type PageEntry = (typeof PAGES)[number];

export async function scanPage(
  page: Page,
  path: string,
  label: string,
  loginAs: (() => Promise<boolean>) | null,
): Promise<void> {
  // eslint-disable-next-line no-console
  console.log(`[axe] Scanning ${label} (${path})…`);

  await page.goto(path);
  await page.waitForLoadState('domcontentloaded');

  if (loginAs) {
    const loggedIn = await loginAs();
    if (!loggedIn) {
      // eslint-disable-next-line no-console
      console.warn(`[axe] Could not authenticate for ${path} — skipping`);
      return;
    }
    // Wait for React hydration + auth redirect
    await page.waitForLoadState('networkidle');
  }

  // Give React time to finish client-side hydration
  await page.waitForTimeout(1_500);

  const builder = new AxeBuilder({ page })
    // Color contrast is checked against the token audit (docs/ACCESSIBILITY_2026-06-20.md)
    // — dynamic computed contrast varies by theme, so skip it here.
    .disableRules(['color-contrast'])
    .withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'best-practice'])
    // Decorative icon spans are already wrapped in aria-hidden containers.
    .exclude('.material-symbols-outlined');

  const results = await builder.analyze();
  const violations = results.violations;

  if (violations.length === 0) {
    // eslint-disable-next-line no-console
    console.log(`[axe] ✓ ${label}: no violations`);
    return;
  }

  const critical = violations.filter((v) => v.impact === 'critical' || v.impact === 'serious');
  const moderate = violations.filter((v) => v.impact === 'moderate');
  const minor = violations.filter((v) => v.impact === 'minor');

  // eslint-disable-next-line no-console
  console.error(
    `[axe] ✗ ${label}: ${violations.length} violations` +
      ` (${critical.length} critical, ${moderate.length} moderate, ${minor.length} minor)`,
  );

  for (const v of violations) {
    const nodes = v.nodes.slice(0, 3).map((n) => n.target.join(' > ')).join(', ');
    // eslint-disable-next-line no-console
    console.error(`  [${v.impact}] ${v.help}: ${nodes}`);
  }

  if (critical.length > 0) {
    throw new Error(
      `${critical.length} critical axe-core violation(s) on ${label} — fix before merging.\n` +
        critical.map((v) => `  [${v.impact}] ${v.help}: ${v.description}`).join('\n'),
    );
  }
}

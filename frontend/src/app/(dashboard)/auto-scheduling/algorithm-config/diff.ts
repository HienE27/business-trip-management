import type { RuntimeConfig } from "./types";

/**
 * Trả về danh sách keys có giá trị khác giữa 2 config objects.
 * Deep-compare bằng JSON.stringify (đủ tốt cho config phẳng).
 */
export function getChangedKeys(
  oldCfg: RuntimeConfig,
  newCfg: RuntimeConfig,
): (keyof RuntimeConfig)[] {
  return (Object.keys(newCfg) as (keyof RuntimeConfig)[]).filter(
    k => JSON.stringify(oldCfg[k]) !== JSON.stringify(newCfg[k]),
  );
}
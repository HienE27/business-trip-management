import { describe, it, expect } from "vitest";
import { sanitizeAllowedSpecialties, NONE_SENTINELS } from "./crossSpecialty";

describe("sanitizeAllowedSpecialties", () => {
  it("returns empty array for null / undefined input", () => {
    expect(sanitizeAllowedSpecialties(null)).toEqual([]);
    expect(sanitizeAllowedSpecialties(undefined)).toEqual([]);
  });

  it("drops the legacy __NONE__ sentinel", () => {
    expect(sanitizeAllowedSpecialties(["__NONE__"])).toEqual([]);
  });

  it("drops empty string entries", () => {
    expect(sanitizeAllowedSpecialties([""])).toEqual([]);
    expect(sanitizeAllowedSpecialties(["Mắt", "", "Sản"])).toEqual(["Mắt", "Sản"]);
  });

  it("preserves order and de-duplicates real specialty names", () => {
    expect(sanitizeAllowedSpecialties(["Mắt", "Sản", "Mắt"])).toEqual(["Mắt", "Sản"]);
  });

  it("filters out non-string entries defensively", () => {
    const input = ["Mắt", null as unknown as string, undefined as unknown as string, 1 as unknown as string, "Sản"];
    expect(sanitizeAllowedSpecialties(input)).toEqual(["Mắt", "Sản"]);
  });

  it("NONE_SENTINELS contains the legacy marker", () => {
    expect(NONE_SENTINELS.has("__NONE__")).toBe(true);
  });
});

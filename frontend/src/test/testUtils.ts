/**
 * Shared test utilities — imported BEFORE vi.mock hoisting
 * so references here are always "real" (not intercepted by mocks).
 */

// Shared cache instance for tests — NOT imported from @/lib/queryCache
// (that module is mocked globally, so its exports would be undefined here).
export const testDataCache = new Map<string, { data: unknown; timestamp: number }>();

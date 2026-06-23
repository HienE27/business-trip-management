/**
 * Lightweight request deduplication cache.
 *
 * Problem: multiple components may call the same GET endpoint simultaneously
 * (e.g. dashboard + sidebar both need staff list). Without caching, each call
 * hits the network even though the data is identical.
 *
 * Solution: a shared in-memory cache keyed by endpoint+params.
 * Concurrent requests for the same key return the same Promise.
 *
 * Usage:
 *   const result = await queryCache.getOrFetch("/staff", () => api.get<Staff[]>("/staff"));
 *
 * This gives deduplication + concurrent coalescing with ~50 lines of code,
 * zero new dependencies, and no extra bundle weight.
 */

type PendingRequest<T> = {
  promise: Promise<T>;
  timestamp: number;
};

// In-memory cache: key → { promise, timestamp }
const pending = new Map<string, PendingRequest<unknown>>();
// Separate cache for resolved data
const dataCache = new Map<string, { data: unknown; timestamp: number }>();

// Cache TTL: 30 seconds for most queries, 5 minutes for stable reference data
const TTL_SHORT = 30_000;
const TTL_LONG = 5 * 60_000;

// Endpoints that change infrequently — use longer TTL
const LONG_TTL_PATTERNS = ["/staff", "/specialty", "/shift-types", "/periods"];

function getTTL(endpoint: string): number {
  return LONG_TTL_PATTERNS.some((p) => endpoint.startsWith(p)) ? TTL_LONG : TTL_SHORT;
}

function buildCacheKey(endpoint: string, params?: Record<string, string | number | boolean>): string {
  if (!params) return endpoint;
  const qs = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== "") qs.set(k, String(v));
  }
  return `${endpoint}?${qs.toString()}`;
}

/**
 * Fetch data, or return a pending request if the same request is already in flight.
 * Also caches resolved results for TTL milliseconds.
 *
 * @param endpoint  API path (e.g. "/staff")
 * @param fetchFn   Async function that actually fetches (e.g. () => api.get<Staff[]>(endpoint))
 * @param params    Optional query params for cache key uniqueness
 */
export async function queryCache<T>(
  endpoint: string,
  fetchFn: () => Promise<T>,
  params?: Record<string, string | number | boolean>
): Promise<T> {
  const key = buildCacheKey(endpoint, params);

  // Return cached data if fresh
  const cached = dataCache.get(key);
  if (cached && Date.now() - cached.timestamp < getTTL(endpoint)) {
    return cached.data as T;
  }

  // Reuse in-flight request
  const pendingReq = pending.get(key);
  if (pendingReq) {
    return pendingReq.promise as Promise<T>;
  }

  // Start new request
  let settle!: (v: T) => void;
  let fail!: (e: unknown) => void;
  const promise = new Promise<T>((resolve, reject) => {
    settle = resolve;
    fail = reject;
  });

  pending.set(key, { promise, timestamp: Date.now() });

  try {
    const result = await fetchFn();
    dataCache.set(key, { data: result, timestamp: Date.now() });
    settle(result);
  } catch (err) {
    fail(err);
  } finally {
    pending.delete(key);
  }

  return promise;
}

/** Invalidate all cached data (e.g. after a mutation). */
export function invalidateCache(pattern?: string): void {
  if (!pattern) {
    dataCache.clear();
    return;
  }
  for (const key of dataCache.keys()) {
    if (key.startsWith(pattern)) dataCache.delete(key);
  }
}

/** Invalidate specific endpoint */
export function invalidateEndpoint(endpoint: string): void {
  for (const key of dataCache.keys()) {
    if (key.startsWith(endpoint)) dataCache.delete(key);
  }
}

/** Stats for debugging */
export function getCacheStats(): { entries: number; pending: number } {
  return { entries: dataCache.size, pending: pending.size };
}

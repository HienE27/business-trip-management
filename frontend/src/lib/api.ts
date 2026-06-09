/**
 * Unified API client for the Hospital Scheduler application.
 * All API calls go through this module. The underlying ApiClient
 * class lives in api-client.ts — this file re-exports it as `api`
 * for convenience and backward compatibility.
 */
export { api as api } from "./api-client";

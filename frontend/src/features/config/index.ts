/**
 * Config Engine — v11.0.4
 *
 * Unified configuration management:
 * - Backend: ConfigDomain + ConfigMetadata + ConfigMapper + ConfigValidator + ConfigService
 * - Frontend: ConfigContext + FieldRenderer + ProfileManagerDrawer
 *
 * Usage:
 *
 * Frontend providers:
 * ```tsx
 * import { ConfigProvider } from "@/features/config";
 * // Wrap app
 * <ConfigProvider>...</ConfigProvider>
 * ```
 *
 * Within components:
 * ```tsx
 * import { useConfig, ProfileSelector, ProfileManagerDrawer } from "@/features/config";
 *
 * // Hook
 * const { metadata, config, validation, updateField, save } = useConfig();
 *
 * // Profile selector for auto-scheduling page
 * <ProfileSelector onProfileChange={handleProfileChange} />
 *
 * // Profile manager drawer for full CRUD
 * <ProfileManagerDrawer open={show} onClose={() => setShow(false)} />
 * ```
 *
 * TypeScript:
 * ```ts
 * import type { ConfigDomain, FieldMetadata, ValidationResponse, ConfigProfile } from "@/features/config";
 * ```
 */
export { ConfigProvider, useConfig } from "./context/ConfigContext";
export { ProfileSelector } from "./selector/ProfileSelector";
export { ProfileDiffDialog } from "./diff/ProfileDiffDialog";
export { ProfileHealthBadge, getHealthStatus, getHealthDescription } from "./components/ProfileHealthBadge";
export { CreateProfileDialog } from "./components/CreateProfileDialog";
export { ImportExportDialog } from "./components/ImportExportDialog";
export { ProfileManagerDrawer } from "./components/ProfileManagerDrawer";
export { ApplyPreviewDialog } from "./components/ApplyPreviewDialog";

export * from "./types/ConfigMetadata";
export * from "./renderer";

// Re-export ConfigProfile from api types
export type { ConfigProfile, ConfigProfileCategory, CreateProfileRequest, UpdateProfileRequest, ProfileComparison, ProfileDiffEntry } from "@/types/api";

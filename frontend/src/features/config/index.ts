/**
 * Config Engine — v11.0.4
 *
 * Unified configuration management:
 * - Backend: ConfigDomain + ConfigMetadata + ConfigMapper + ConfigValidator + ConfigService
 * - Frontend: ConfigContext + FieldRenderer + Create/Import/Export dialogs + Health badge
 *
 * NOTE: Earlier revisions of this file re-exported `ProfileSelector`,
 * `ProfileManagerDrawer`, `ProfileDiffDialog`, and `ApplyPreviewDialog` even
 * though those components were never implemented (no source file, no consumer).
 * Those exports were removed (CFG-001) because they made the barrel file look
 * like a public surface that did not actually exist, and would have produced
 * confusing build failures the moment any module tried to consume them.
 *
 * If a future PR needs the profile manager UI, the components must be added
 * under `selector/`, `components/`, and `diff/` first, then re-exported here.
 *
 * Usage:
 *
 * Frontend providers:
 * ```tsx
 * import { ConfigProvider } from "@/features/config";
 * <ConfigProvider>...</ConfigProvider>
 * ```
 *
 * Within components:
 * ```tsx
 * import { useConfig, CreateProfileDialog, ImportExportDialog, ProfileHealthBadge } from "@/features/config";
 * ```
 *
 * TypeScript:
 * ```ts
 * import type { ConfigDomain, FieldMetadata, ValidationResponse, ConfigProfile } from "@/features/config";
 * ```
 */
export { ConfigProvider, useConfig } from "./context/ConfigContext";
export { ProfileHealthBadge, getHealthStatus, getHealthDescription } from "./components/ProfileHealthBadge";
export { CreateProfileDialog } from "./components/CreateProfileDialog";
export { ImportExportDialog } from "./components/ImportExportDialog";

export * from "./types/ConfigMetadata";
export * from "./renderer";
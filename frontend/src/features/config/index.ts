/**
 * Config Engine — v11.0.2
 *
 * Unified configuration management:
 * - Backend: ConfigDomain + ConfigMetadata + ConfigMapper + ConfigValidator + ConfigService
 * - Frontend: ConfigContext + FieldRenderer + MetadataConfigEditor
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
 * import { useConfig, MetadataConfigEditor } from "@/features/config";
 *
 * // Hook
 * const { metadata, config, validation, updateField, save } = useConfig();
 *
 * // Or use the full metadata-driven editor
 * <MetadataConfigEditor />
 * ```
 *
 * TypeScript:
 * ```ts
 * import type { ConfigDomain, FieldMetadata, ValidationResponse } from "@/features/config";
 * ```
 */
export { ConfigProvider, useConfig } from "./context/ConfigContext";
export { MetadataConfigEditor } from "../MetadataConfigEditor";

export * from "./types/ConfigMetadata";
export * from "./renderer";

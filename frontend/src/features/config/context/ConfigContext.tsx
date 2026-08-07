"use client";

import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useReducer,
} from "react";
import { api } from "@/lib/api-client";
import type {
  CategoryMetadata,
  ConfigDomain,
  ValidationResponse,
} from "@/features/config/types/ConfigMetadata";

// ─── State ───────────────────────────────────────────────────────────────────

interface ConfigState {
  metadata: CategoryMetadata[] | null;
  config: ConfigDomain | null;
  validation: ValidationResponse | null;
  localDraft: Partial<ConfigDomain> | null;
  isLoading: boolean;
  isValidating: boolean;
  isSaving: boolean;
  error: string | null;
  hasUnsavedChanges: boolean;
}

type ConfigAction =
  | { type: "LOAD_START" }
  | { type: "LOAD_SUCCESS"; metadata: CategoryMetadata[]; config: ConfigDomain }
  | { type: "LOAD_ERROR"; error: string }
  | { type: "UPDATE_FIELD"; field: string; value: unknown }
  | { type: "VALIDATE_START" }
  | { type: "VALIDATE_SUCCESS"; validation: ValidationResponse }
  | { type: "SAVE_START" }
  | { type: "SAVE_SUCCESS"; config: ConfigDomain }
  | { type: "SAVE_ERROR"; error: string }
  | { type: "RESET_DRAFT" }
  | { type: "APPLY_CONFIG"; config: ConfigDomain };

function configReducer(state: ConfigState, action: ConfigAction): ConfigState {
  switch (action.type) {
    case "LOAD_START":
      return { ...state, isLoading: true, error: null };

    case "LOAD_SUCCESS":
      return {
        ...state,
        isLoading: false,
        metadata: action.metadata,
        config: action.config,
        localDraft: null,
        validation: null,
        hasUnsavedChanges: false,
      };

    case "LOAD_ERROR":
      return { ...state, isLoading: false, error: action.error };

    case "UPDATE_FIELD": {
      const draft = state.localDraft
        ? { ...state.localDraft, [action.field]: action.value }
        : state.config
        ? Object.fromEntries(
            Object.entries(state.config).map(([k, v]) => [k, k === action.field ? action.value : v])
          )
        : {};
      draft[action.field] = action.value;
      const hasUnsavedChanges = !deepEqual(draft, state.config);
      return { ...state, localDraft: draft as Partial<ConfigDomain>, hasUnsavedChanges };
    }

    case "VALIDATE_START":
      return { ...state, isValidating: true };

    case "VALIDATE_SUCCESS":
      return { ...state, isValidating: false, validation: action.validation };

    case "SAVE_START":
      return { ...state, isSaving: true, error: null };

    case "SAVE_SUCCESS":
      return {
        ...state,
        isSaving: false,
        config: action.config,
        localDraft: null,
        validation: null,
        hasUnsavedChanges: false,
      };

    case "SAVE_ERROR":
      return { ...state, isSaving: false, error: action.error };

    case "RESET_DRAFT":
      return {
        ...state,
        localDraft: null,
        hasUnsavedChanges: false,
        validation: null,
      };

    case "APPLY_CONFIG":
      return {
        ...state,
        config: action.config,
        localDraft: null,
        hasUnsavedChanges: false,
      };

    default:
      return state;
  }
}

// ─── Context ─────────────────────────────────────────────────────────────────

interface ConfigContextValue extends ConfigState {
  load: () => Promise<void>;
  updateField: (field: string, value: unknown) => void;
  validate: (config?: Partial<ConfigDomain>) => Promise<ValidationResponse | null>;
  save: (config: ConfigDomain) => Promise<void>;
  reset: () => void;
  getFieldValue: (field: string) => unknown;
  getEffectiveConfig: () => Partial<ConfigDomain>;
  applyPreset: (presetKey: string) => Promise<void>;
  resetField: (fieldPath: string) => Promise<void>;
}

const ConfigContext = createContext<ConfigContextValue | null>(null);

const initialState: ConfigState = {
  metadata: null,
  config: null,
  validation: null,
  localDraft: null,
  isLoading: false,
  isValidating: false,
  isSaving: false,
  error: null,
  hasUnsavedChanges: false,
};

// ─── Provider ─────────────────────────────────────────────────────────────────

export function ConfigProvider({ children }: { children: React.ReactNode }) {
  const [state, dispatch] = useReducer(configReducer, initialState);

  const load = useCallback(async () => {
    dispatch({ type: "LOAD_START" });
    try {
      const [metadata, cfg] = await Promise.all([
        api.get<CategoryMetadata[]>("/config/metadata"),
        api.get<ConfigDomain>("/config"),
      ]);
      if (metadata && cfg) {
        dispatch({
          type: "LOAD_SUCCESS",
          metadata: metadata as unknown as CategoryMetadata[],
          config: cfg as unknown as ConfigDomain,
        });
      } else {
        dispatch({ type: "LOAD_ERROR", error: "Failed to load configuration" });
      }
    } catch (err) {
      dispatch({ type: "LOAD_ERROR", error: String(err) });
    }
  }, []);

  const updateField = useCallback((field: string, value: unknown) => {
    dispatch({ type: "UPDATE_FIELD", field, value });
  }, []);

  const validate = useCallback(
    async (config?: Partial<ConfigDomain>): Promise<ValidationResponse | null> => {
      dispatch({ type: "VALIDATE_START" });
      try {
        const body = (config ?? state.localDraft ?? state.config) as ConfigDomain;
        const result = await api.post<ValidationResponse>("/config/validate", body);
        if (result) {
          const validation = result as unknown as ValidationResponse;
          dispatch({ type: "VALIDATE_SUCCESS", validation });
          return validation;
        }
      } catch (err) {
      }
      return null;
    },
    [state.localDraft, state.config]
  );

  const save = useCallback(async (config: ConfigDomain) => {
    dispatch({ type: "SAVE_START" });
    try {
      const result = await api.put<ConfigDomain>("/config", config);
      if (result) {
        dispatch({ type: "SAVE_SUCCESS", config: result as unknown as ConfigDomain });
      } else {
        dispatch({ type: "SAVE_ERROR", error: "Failed to save configuration" });
      }
    } catch (err) {
      dispatch({ type: "SAVE_ERROR", error: String(err) });
    }
  }, []);

  const reset = useCallback(() => {
    dispatch({ type: "RESET_DRAFT" });
  }, []);

  const getFieldValue = useCallback(
    (field: string): unknown => {
      if (state.localDraft && field in state.localDraft) {
        return (state.localDraft as Record<string, unknown>)[field];
      }
      if (state.config && field in state.config) {
        return (state.config as unknown as Record<string, unknown>)[field];
      }
      return undefined;
    },
    [state.localDraft, state.config]
  );

  const getEffectiveConfig = useCallback((): Partial<ConfigDomain> => {
    if (state.localDraft) {
      return { ...state.config, ...state.localDraft } as Partial<ConfigDomain>;
    }
    return state.config ?? {};
  }, [state.localDraft, state.config]);

  const applyPreset = useCallback(async (presetKey: string) => {
    dispatch({ type: "SAVE_START" });
    try {
      const result = await api.post<ConfigDomain>(`/config/presets/${presetKey}/apply`, {});
      if (result) {
        dispatch({ type: "APPLY_CONFIG", config: result as unknown as ConfigDomain });
      }
    } catch (err) {
      dispatch({ type: "SAVE_ERROR", error: String(err) });
    }
  }, []);

  const resetField = useCallback(async (fieldPath: string) => {
    try {
      await api.post<void>(`/config/reset/${fieldPath}`, undefined);
      await load();
    } catch (err) {
    }
  }, [load]);

  useEffect(() => {
    load();
  }, [load]);

  const value = useMemo<ConfigContextValue>(
    () => ({
      ...state,
      load,
      updateField,
      validate,
      save,
      reset,
      getFieldValue,
      getEffectiveConfig,
      applyPreset,
      resetField,
    }),
    [state, load, updateField, validate, save, reset, getFieldValue, getEffectiveConfig, applyPreset, resetField]
  );

  return (
    <ConfigContext.Provider value={value}>
      {children}
    </ConfigContext.Provider>
  );
}

// ─── Hook ────────────────────────────────────────────────────────────────────

export function useConfig() {
  const ctx = useContext(ConfigContext);
  if (!ctx) {
    throw new Error("useConfig must be used within ConfigProvider");
  }
  return ctx;
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function deepEqual(a: unknown, b: unknown): boolean {
  if (a === b) return true;
  if (typeof a !== typeof b) return false;
  if (a === null || b === null) return a === b;
  if (Array.isArray(a) && Array.isArray(b)) {
    return a.length === b.length && a.every((v, i) => deepEqual(v, b[i]));
  }
  if (typeof a === "object" && typeof b === "object") {
    const aKeys = Object.keys(a as object);
    const bKeys = Object.keys(b as object);
    return (
      aKeys.length === bKeys.length &&
      aKeys.every((k) => deepEqual((a as Record<string, unknown>)[k], (b as Record<string, unknown>)[k]))
    );
  }
  return false;
}

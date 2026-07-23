"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { Skeleton } from "@/components/ui/Skeleton";
import type { ConfigVersion, ConfigVersionDiff } from "@/types/api";

/**
 * Version history component with diff and rollback.
 */
export function VersionHistory() {
  const [versions, setVersions] = useState<ConfigVersion[]>([]);
  const [selectedVersion, setSelectedVersion] = useState<ConfigVersion | null>(null);
  const [compareVersion, setCompareVersion] = useState<ConfigVersion | null>(null);
  const [diff, setDiff] = useState<{ diffs: ConfigVersionDiff[] } | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Load versions (using period ID 1 for demo)
  const loadVersions = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const result = await api.getConfigVersionHistory();
      setVersions(result.versions);
      if (result.versions.length > 0 && !selectedVersion) {
        setSelectedVersion(result.versions[0]);
      }
    } catch (err) {
      setError(getErrorMessage(err, "Có lỗi xảy ra"));
    } finally {
      setLoading(false);
    }
  }, [selectedVersion]);

  useEffect(() => {
    loadVersions();
  }, [loadVersions]);

  // Load diff when comparing
  useEffect(() => {
    if (selectedVersion && compareVersion && selectedVersion.id !== compareVersion.id) {
      api.getConfigVersionDiff(selectedVersion.id, compareVersion.id)
        .then((d) => setDiff(d))
        .catch(console.error);
    } else {
      setDiff(null);
    }
  }, [selectedVersion, compareVersion]);

  const handleRollback = async (version: ConfigVersion) => {
    if (!confirm(`Rollback to version ${version.versionNumber}?`)) return;

    try {
      await api.rollbackConfigVersion(version.id, "Manual rollback from UI");
      loadVersions();
    } catch (err) {
      console.error("Rollback failed:", err);
    }
  };

  if (loading) {
    return (
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-1">
          <Skeleton className="h-96 rounded-xl" />
        </div>
        <div className="lg:col-span-2">
          <Skeleton className="h-96 rounded-xl" />
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-6 bg-error-container rounded-xl">
        <p className="text-error">{error}</p>
        <Button variant="ghost" className="mt-2" onClick={loadVersions}>
          Thử lại
        </Button>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
      {/* Version List */}
      <div className="bg-surface-container-lowest border border-outline-variant rounded-xl overflow-hidden">
        <div className="p-4 border-b border-outline-variant bg-surface-container-low">
          <h3 className="font-title-lg text-title-lg text-on-surface">Versions ({versions.length})</h3>
        </div>

        <div className="overflow-y-auto max-h-[600px]">
          {versions.length === 0 ? (
            <div className="p-8 text-center">
              <span className="material-symbols-outlined text-[48px] text-on-surface-variant">history</span>
              <p className="mt-2 text-label-md text-on-surface-variant">Chưa có version nào</p>
            </div>
          ) : (
            <div className="divide-y divide-outline-variant">
              {versions.map((version) => (
                <button
                  key={version.id}
                  onClick={() => {
                    setSelectedVersion(version);
                    setCompareVersion(null);
                  }}
                  className={`w-full p-4 text-left transition-colors ${
                    selectedVersion?.id === version.id
                      ? "bg-primary-fixed border-l-4 border-primary"
                      : "hover:bg-surface-container-low"
                  }`}
                >
                  <div className="flex items-center justify-between mb-1">
                    <span className="font-medium text-body-md text-on-surface">
                      v{version.versionNumber}
                    </span>
                    {version.active && (
                      <Badge tone="success">Active</Badge>
                    )}
                    {version.locked && (
                      <span className="material-symbols-outlined text-warning text-[16px]">lock</span>
                    )}
                  </div>
                  <div className="text-label-sm text-on-surface-variant">
                    {(version.createdAt ? new Date(version.createdAt).toLocaleDateString("vi-VN") : "N/A")}
                  </div>
                  {version.changeComment && (
                    <div className="text-label-sm text-on-surface mt-1 line-clamp-2">
                      {version.changeComment}
                    </div>
                  )}
                  <div className="text-label-xs text-on-surface-variant mt-1">
                    by {version.createdByName || "Unknown"}
                  </div>
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Version Detail */}
      <div className="lg:col-span-2 space-y-6">
        {selectedVersion ? (
          <>
            {/* Detail Card */}
            <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5">
              <div className="flex items-center justify-between mb-4">
                <h3 className="font-title-lg text-title-lg text-on-surface">
                  Version {selectedVersion.versionNumber}
                </h3>
                <div className="flex gap-2">
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => setCompareVersion(versions.find(v => v.id !== selectedVersion.id) || null)}
                  >
                    So sánh
                  </Button>
                  {!selectedVersion.active && !selectedVersion.locked && (
                    <Button variant="primary" size="sm" onClick={() => handleRollback(selectedVersion)}>
                      Rollback
                    </Button>
                  )}
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <div className="text-label-sm text-on-surface-variant">Created</div>
                  <div className="text-body-md text-on-surface">
                    {(selectedVersion.createdAt ? new Date(selectedVersion.createdAt).toLocaleString("vi-VN") : "N/A")}
                  </div>
                </div>
                <div>
                  <div className="text-label-sm text-on-surface-variant">By</div>
                  <div className="text-body-md text-on-surface">
                    {selectedVersion.createdByName || "Unknown"}
                  </div>
                </div>
                <div>
                  <div className="text-label-sm text-on-surface-variant">Source</div>
                  <div className="text-body-md text-on-surface">
                    {selectedVersion.source}
                  </div>
                </div>
                <div>
                  <div className="text-label-sm text-on-surface-variant">Checksum</div>
                  <div className="text-body-sm text-on-surface font-mono truncate">
                    {selectedVersion.checksum || "N/A"}
                  </div>
                </div>
              </div>

              {selectedVersion.changeComment && (
                <div className="mt-4 p-3 bg-surface-container-low rounded-lg">
                  <div className="text-label-sm text-on-surface-variant mb-1">Comment</div>
                  <div className="text-body-md text-on-surface">
                    {selectedVersion.changeComment}
                  </div>
                </div>
              )}

              {selectedVersion.configSnapshot && (
                <div className="mt-4">
                  <div className="text-label-sm text-on-surface-variant mb-2">Config Snapshot</div>
                  <div className="bg-surface-container-low rounded-lg p-3 max-h-48 overflow-auto">
                    <pre className="text-label-sm text-on-surface font-mono whitespace-pre-wrap">
                      {JSON.stringify(selectedVersion.configSnapshot, null, 2)}
                    </pre>
                  </div>
                </div>
              )}
            </div>

            {/* Diff View */}
            {diff && (
              <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5">
                <h3 className="font-title-lg text-title-lg text-on-surface mb-4">
                  Changes from v{versions.find(v => v.id === compareVersion?.id)?.versionNumber || "?"}
                </h3>

                <div className="flex gap-4 mb-4 text-label-sm">
                  <span className="text-secondary">+{diff.diffs.filter(d => d.changeType === "ADDED").length} added</span>
                  <span className="text-error">-{diff.diffs.filter(d => d.changeType === "REMOVED").length} removed</span>
                  <span className="text-warning">~{diff.diffs.filter(d => d.changeType === "MODIFIED").length} changed</span>
                </div>

                <div className="space-y-2">
                  {diff.diffs.map((d: import("@/types/api").ConfigVersionDiff, idx: number) => (
                    <div
                      key={idx}
                      className={`p-3 rounded-lg ${
                        d.changeType === "ADDED"
                          ? "bg-secondary-container"
                          : d.changeType === "REMOVED"
                          ? "bg-error-container"
                          : "bg-surface-container-low"
                      }`}
                    >
                      <div className="flex items-center gap-2 mb-1">
                        <Badge
                          tone={
                            d.changeType === "ADDED"
                              ? "success"
                              : d.changeType === "REMOVED"
                              ? "error"
                              : "warning"
                          }
                        >
                          {d.changeType}
                        </Badge>
                        <span className="font-mono text-label-md text-on-surface">{d.key}</span>
                      </div>
                      <div className="flex gap-4 text-label-sm">
                        {d.oldValue !== null && (
                          <span className="text-error">
                            - {String(d.oldValue)}
                          </span>
                        )}
                        {d.newValue !== null && (
                          <span className="text-secondary">
                            + {String(d.newValue)}
                          </span>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </>
        ) : (
          <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-8 text-center">
            <span className="material-symbols-outlined text-[48px] text-on-surface-variant">touch_app</span>
            <p className="mt-2 text-label-md text-on-surface-variant">
              Chọn một version để xem chi tiết
            </p>
          </div>
        )}
      </div>
    </div>
  );
}

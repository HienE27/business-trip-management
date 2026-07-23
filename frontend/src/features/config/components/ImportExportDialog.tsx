"use client";

import { useCallback, useState } from "react";
import { api } from "@/lib/api-client";
import type { ConfigProfile } from "@/types/api";
import { useToast } from "@/hooks/useToast";

interface ImportExportDialogProps {
  open: boolean;
  onClose: () => void;
  profile?: ConfigProfile | null;
  onImported?: (profile: ConfigProfile) => void;
  mode?: "import" | "export" | "both";
}

export function ImportExportDialog({
  open,
  onClose,
  profile,
  onImported,
  mode: initialMode = "both",
}: ImportExportDialogProps) {
  const { success, error } = useToast();
  const [activeTab, setActiveTab] = useState<"import" | "export">(initialMode === "export" ? "export" : "import");
  const [isLoading, setIsLoading] = useState(false);
  const [exportJson, setExportJson] = useState<string | null>(null);
  const [importJson, setImportJson] = useState<string>("");
  const [isDragging, setIsDragging] = useState(false);

  const handleExport = useCallback(async () => {
    if (!profile) return;
    setIsLoading(true);
    try {
      const json = await api.exportConfigProfile(profile.id);
      setExportJson(json);
      success("Đã xuất cấu hình thành công");
    } catch (err) {
      error(`Lỗi khi xuất: ${err}`);
    } finally {
      setIsLoading(false);
    }
  }, [profile, success, error]);

  const handleImport = useCallback(async () => {
    if (!importJson.trim()) {
      error("Vui lòng nhập JSON");
      return;
    }

    setIsLoading(true);
    try {
      const imported = await api.importConfigProfile(importJson);
      success(`Đã nhập cấu hình "${imported.nameVi}"`);
      onImported?.(imported);
      handleClose();
    } catch (err) {
      error(`Lỗi khi nhập: ${err}`);
    } finally {
      setIsLoading(false);
    }
  }, [importJson, success, error, onImported]);

  const handleDownload = useCallback(() => {
    if (!exportJson || !profile) return;
    const blob = new Blob([exportJson], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${profile.profileKey}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    success("Đã tải file");
  }, [exportJson, profile, success]);

  const handleClose = () => {
    setExportJson(null);
    setImportJson("");
    setActiveTab("import");
    onClose();
  };

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      setIsDragging(false);
      const file = e.dataTransfer.files[0];
      if (file && file.type === "application/json") {
        const reader = new FileReader();
        reader.onload = (event) => {
          setImportJson(event.target?.result as string || "");
        };
        reader.readAsText(file);
      } else {
        error("Vui lòng chọn file JSON");
      }
    },
    [error]
  );

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* Backdrop */}
      <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={handleClose} />

      {/* Dialog */}
      <div className="relative bg-surface-container-lowest rounded-2xl shadow-xl w-full max-w-lg animate-scale-in">
        {/* Header */}
        <div className="px-6 py-4 border-b border-outline-variant flex items-center justify-between">
          <div>
            <h2 className="text-headline-md text-on-surface font-semibold">
              {activeTab === "import" ? "Nhập cấu hình" : "Xuất cấu hình"}
            </h2>
            <p className="text-label-sm text-on-surface-variant mt-0.5">
              {activeTab === "import"
                ? "Nhập cấu hình từ file JSON"
                : `Xuất "${profile?.nameVi}" ra file JSON`}
            </p>
          </div>
          <button onClick={handleClose} className="p-2 hover:bg-surface-container-low rounded-xl transition-colors">
            <span className="material-symbols-outlined text-[20px] text-on-surface-variant">close</span>
          </button>
        </div>

        {/* Tabs */}
        {initialMode === "both" && (
          <div className="px-6 pt-4 flex border-b border-outline-variant">
            <button
              onClick={() => setActiveTab("import")}
              className={`px-4 pb-3 text-label-md border-b-2 transition-colors ${
                activeTab === "import"
                  ? "border-primary text-primary"
                  : "border-transparent text-on-surface-variant hover:text-on-surface"
              }`}
            >
              <span className="material-symbols-outlined text-[16px] align-text-bottom mr-1">upload</span>
              Nhập
            </button>
            <button
              onClick={() => setActiveTab("export")}
              className={`px-4 pb-3 text-label-md border-b-2 transition-colors ${
                activeTab === "export"
                  ? "border-primary text-primary"
                  : "border-transparent text-on-surface-variant hover:text-on-surface"
              }`}
            >
              <span className="material-symbols-outlined text-[16px] align-text-bottom mr-1">download</span>
              Xuất
            </button>
          </div>
        )}

        {/* Content */}
        <div className="px-6 py-4">
          {activeTab === "import" ? (
            <div className="space-y-4">
              {/* Drop zone */}
              <div
                className={`
                  border-2 border-dashed rounded-xl p-8 text-center transition-colors
                  ${isDragging ? "border-primary bg-primary-fixed/30" : "border-outline-variant hover:border-primary/50"}
                `}
                onDragOver={(e) => {
                  e.preventDefault();
                  setIsDragging(true);
                }}
                onDragLeave={() => setIsDragging(false)}
                onDrop={handleDrop}
              >
                <span className="material-symbols-outlined text-[48px] text-on-surface-variant mb-3 block">
                  upload_file
                </span>
                <p className="text-label-md text-on-surface mb-2">Kéo thả file JSON vào đây</p>
                <p className="text-[12px] text-on-surface-variant">hoặc</p>
                <label className="inline-block mt-2 px-4 py-2 bg-surface-container text-label-md text-on-surface rounded-lg hover:bg-surface-container-high cursor-pointer transition-colors">
                  Chọn file
                  <input
                    type="file"
                    accept=".json"
                    className="hidden"
                    onChange={(e) => {
                      const file = e.target.files?.[0];
                      if (file) {
                        const reader = new FileReader();
                        reader.onload = (event) => {
                          setImportJson(event.target?.result as string || "");
                        };
                        reader.readAsText(file);
                      }
                    }}
                  />
                </label>
              </div>

              {/* JSON textarea */}
              <div>
                <label className="block text-label-md text-on-surface mb-1.5">Hoặc dán JSON</label>
                <textarea
                  value={importJson}
                  onChange={(e) => setImportJson(e.target.value)}
                  placeholder='{"nameVi": "Cấu hình mới", ...}'
                  rows={6}
                  className="w-full px-3 py-2 border border-outline-variant bg-surface-container-lowest rounded-lg text-body-sm font-mono text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 resize-none"
                />
              </div>

              {/* Preview */}
              {importJson && (
                <div className="p-3 bg-surface-container rounded-lg">
                  <div className="flex items-center gap-2 mb-2">
                    <span className="material-symbols-outlined text-[16px] text-secondary">info</span>
                    <span className="text-label-sm text-on-surface font-semibold">Preview</span>
                  </div>
                  <p className="text-[12px] text-on-surface-variant font-mono truncate">
                    {importJson.substring(0, 100)}...
                  </p>
                </div>
              )}
            </div>
          ) : (
            <div className="space-y-4">
              {!exportJson ? (
                <div className="text-center py-8">
                  <span className="material-symbols-outlined text-[64px] text-primary/50 mb-4 block">
                    description
                  </span>
                  <p className="text-label-md text-on-surface mb-2">Xuất cấu hình "{profile?.nameVi}"</p>
                  <p className="text-[12px] text-on-surface-variant mb-4">
                    File JSON sẽ chứa toàn bộ cấu hình thuật toán
                  </p>
                  <button
                    onClick={handleExport}
                    disabled={isLoading || !profile}
                    className="px-6 py-2.5 bg-primary text-on-primary text-label-md font-semibold rounded-lg hover:bg-primary/90 transition-colors disabled:opacity-50 flex items-center gap-2 mx-auto"
                  >
                    {isLoading ? (
                      <>
                        <div className="w-4 h-4 border-2 border-on-primary border-t-transparent rounded-full animate-spin" />
                        Đang xuất...
                      </>
                    ) : (
                      <>
                        <span className="material-symbols-outlined text-[16px]">download</span>
                        Xuất file JSON
                      </>
                    )}
                  </button>
                </div>
              ) : (
                <div className="space-y-4">
                  <div className="p-3 bg-secondary-container/50 border border-secondary/20 rounded-lg">
                    <div className="flex items-center gap-2 mb-2">
                      <span className="material-symbols-outlined text-[16px] text-secondary" style={{ fontVariationSettings: "'FILL' 1" }}>
                        check_circle
                      </span>
                      <span className="text-label-md text-on-secondary-container font-semibold">Xuất thành công!</span>
                    </div>
                    <p className="text-[12px] text-on-secondary-container">
                      File JSON đã sẵn sàng để tải về
                    </p>
                  </div>

                  <div className="p-3 bg-surface-container rounded-lg max-h-40 overflow-auto">
                    <pre className="text-[11px] font-mono text-on-surface whitespace-pre-wrap break-all">
                      {exportJson.substring(0, 500)}
                      {exportJson.length > 500 && "..."}
                    </pre>
                  </div>

                  <div className="flex gap-3">
                    <button
                      onClick={() => setExportJson(null)}
                      className="flex-1 px-4 py-2 bg-surface-container text-label-md text-on-surface rounded-lg hover:bg-surface-container-high transition-colors"
                    >
                      Xuất lại
                    </button>
                    <button
                      onClick={handleDownload}
                      className="flex-1 px-4 py-2 bg-primary text-on-primary text-label-md font-semibold rounded-lg hover:bg-primary/90 transition-colors flex items-center justify-center gap-2"
                    >
                      <span className="material-symbols-outlined text-[16px]">download</span>
                      Tải file
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="px-6 py-4 border-t border-outline-variant flex justify-end">
          <button
            onClick={handleClose}
            className="px-4 py-2 text-label-md text-on-surface-variant hover:bg-surface-container-low rounded-lg transition-colors"
          >
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
}

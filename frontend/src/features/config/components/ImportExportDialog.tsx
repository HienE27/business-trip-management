"use client";

import { useCallback, useState } from "react";
import { Modal, ModalFooter } from "@/components/ui/Modal";
import { Button } from "@/components/ui";
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
    <Modal
      open={open}
      onClose={handleClose}
      title={activeTab === "import" ? "Nhập cấu hình" : "Xuất cấu hình"}
      description={activeTab === "import"
        ? "Nhập cấu hình từ file JSON"
        : `Xuất "${profile?.nameVi}" ra file JSON`}
      size="md"
    >

        {/* Tabs */}
        {initialMode === "both" && (
          <div className="px-6 pt-4 flex border-b border-outline-variant">
            <button
              onClick={() => setActiveTab("import")}
              className={`px-4 pb-3 text-label-md border-b-2 transition-colors ${
                activeTab === "import"
                  ? "border-primary text-blue-800"
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
                  ? "border-primary text-blue-800"
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
                  ${isDragging ? "border-primary bg-blue-100" : "border-outline-variant hover:border-primary/50"}
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
                  className="w-full px-3 py-2 border border-outline-variant bg-surface-container-lowest rounded-lg text-body-sm font-mono text-on-surface focus:border-blue-300 focus:ring-2 focus:ring-blue-300 resize-none"
                />
              </div>

              {/* Preview */}
              {importJson && (
                <div className="p-3 bg-surface-container rounded-lg">
                  <div className="flex items-center gap-2 mb-2">
                    <span className="material-symbols-outlined text-[16px] text-emerald-800">info</span>
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
                  <span className="material-symbols-outlined text-[64px] text-blue-800/50 mb-4 block">
                    description
                  </span>
                  <p className="text-label-md text-on-surface mb-2">Xuất cấu hình "{profile?.nameVi}"</p>
                  <p className="text-[12px] text-on-surface-variant mb-4">
                    File JSON sẽ chứa toàn bộ cấu hình thuật toán
                  </p>
                  <Button
                    variant="primary"
                    size="md"
                    onClick={handleExport}
                    disabled={isLoading || !profile}
                    loading={isLoading}
                    icon={!isLoading ? <span className="material-symbols-outlined text-[16px]">download</span> : undefined}
                  >
                    {isLoading ? "Đang xuất..." : "Xuất file JSON"}
                  </Button>
                </div>
              ) : (
                <div className="space-y-4">
                  <div className="p-3 bg-emerald-100 text-emerald-800 border border-secondary/20 rounded-lg">
                    <div className="flex items-center gap-2 mb-2">
                      <span className="material-symbols-outlined text-[16px] text-emerald-800" style={{ fontVariationSettings: "'FILL' 1" }}>
                        check_circle
                      </span>
                      <span className="text-label-md text-amber-800 font-semibold">Xuất thành công!</span>
                    </div>
                    <p className="text-[12px] text-amber-800">
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
                    <Button
                      variant="secondary"
                      size="md"
                      onClick={() => setExportJson(null)}
                      fullWidth
                    >
                      Xuất lại
                    </Button>
                    <Button
                      variant="primary"
                      size="md"
                      onClick={handleDownload}
                      fullWidth
                      icon={<span className="material-symbols-outlined text-[16px]">download</span>}
                    >
                      Tải file
                    </Button>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>

        <ModalFooter>
        <Button
          variant="secondary"
          size="md"
          onClick={handleClose}
        >
          Đóng
        </Button>
      </ModalFooter>
    </Modal>
  );
}

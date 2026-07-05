"use client";

import { useState } from "react";
import { Button } from "@/components/ui";
import { BackButton } from "@/components/ui/BackButton";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { useRole } from "@/hooks/useRole";
import { useToast } from "@/hooks/useToast";
import { ConfigAuditLog } from "@/components/algorithm-config/ConfigAuditLog";
import { TabBar } from "./TabBar";
import { RuntimeConfigEditor } from "./RuntimeConfigEditor";
import { MetricsHistory } from "./MetricsHistory";
import { ReferenceSection } from "./ReferenceSection";
import { CreateConfigModal } from "./CreateConfigModal";
import { CustomConfigsCard } from "./CustomConfigsCard";
import { AccessDeniedCard } from "./AccessDeniedCard";
import type { TabKey } from "./types";

export default function AlgorithmConfigPage() {
  const role = useRole();
  const isAdmin = role === "ADMIN";
  const { success, error: toastError } = useToast();

  const [activeTab, setActiveTab] = useState<TabKey>("config");
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createMsg, setCreateMsg] = useState<{ type: "success" | "error"; text: string } | null>(null);
  const [refreshSignal, setRefreshSignal] = useState(0);
  const [syncingDesc, setSyncingDesc] = useState(false);
  const [syncConfirmOpen, setSyncConfirmOpen] = useState(false);

  async function handleCreate(form: { paramKey: string; paramValue: string; valueType: string; description: string }) {
    setCreating(true);
    try {
      await api.createAlgorithmConfig({
        paramKey: form.paramKey.trim(),
        paramValue: form.paramValue.trim(),
        valueType: form.valueType,
        description: form.description.trim(),
      });
      setCreateMsg({ type: "success", text: "Đã tạo cấu hình mới." });
      setRefreshSignal(s => s + 1);
    } catch (err) {
      setCreateMsg({ type: "error", text: getErrorMessage(err, "Tạo thất bại.") });
    } finally {
      setCreating(false);
    }
  }

  async function handleSyncDescriptions() {
    setSyncingDesc(true);
    try {
      await api.syncAlgorithmConfigDescriptions();
      success("Đã đồng bộ mô tả. Đang tải lại danh sách...");
      setRefreshSignal(s => s + 1);
    } catch (e) {
      toastError(getErrorMessage(e, "Đồng bộ mô tả thất bại"));
    } finally {
      setSyncingDesc(false);
    }
  }

  if (!isAdmin) return <AccessDeniedCard />;

  return (
    <div className="space-y-5">
      <BackButton href="/auto-scheduling" variant="full" label="Quay lại" className="mb-1" />

      <PageHeader
        activeTab={activeTab}
        onTabChange={setActiveTab}
        onSync={() => setSyncConfirmOpen(true)}
        syncing={syncingDesc}
        onCreate={() => setCreateModalOpen(true)}
      />

      {activeTab === "config" && (
        <div className="space-y-5">
          <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant overflow-hidden">
            <div className="px-5 py-3.5 border-b border-outline-variant bg-surface-container-low flex items-center gap-2.5">
              <span className="material-symbols-outlined text-primary text-[18px]" aria-hidden="true">tune</span>
              <h2 className="text-title-sm font-semibold text-on-surface">Thông số runtime</h2>
              <span className="text-[11px] text-on-surface-variant ml-auto hidden sm:block">Áp dụng cho mọi kỳ lịch</span>
            </div>
            <div className="p-5">
              <RuntimeConfigEditor onSaved={() => setRefreshSignal(s => s + 1)} />
            </div>
          </div>
          <CustomConfigsCard onCreate={() => setCreateModalOpen(true)} refreshSignal={refreshSignal} />
        </div>
      )}

      {activeTab === "history" && <MetricsHistory />}
      {activeTab === "audit" && <ConfigAuditLog />}
      {activeTab === "reference" && <ReferenceSection />}

      <CreateConfigModal
        open={createModalOpen}
        onClose={() => { setCreateModalOpen(false); setCreateMsg(null); }}
        onCreate={handleCreate}
        creating={creating}
        message={createMsg}
      />

      <ConfirmDialog
        open={syncConfirmOpen}
        onClose={() => setSyncConfirmOpen(false)}
        onConfirm={() => { setSyncConfirmOpen(false); void handleSyncDescriptions(); }}
        title="Đồng bộ mô tả tham số?"
        description="Hành động này sẽ reset toàn bộ mô tả về phiên bản mặc định trong code. Mô tả tùy chỉnh sẽ bị mất."
        confirmLabel="Đồng bộ"
        cancelLabel="Hủy"
        variant="danger"
        loading={syncingDesc}
      />
    </div>
  );
}

/* ─── Page Header ─────────────────────────────────────────── */

type HeaderProps = {
  activeTab: TabKey;
  onTabChange: (tab: TabKey) => void;
  onSync: () => void;
  syncing: boolean;
  onCreate: () => void;
};

function PageHeader({ activeTab, onTabChange, onSync, syncing, onCreate }: HeaderProps) {
  return (
    <div className="flex items-center justify-between gap-4 flex-wrap">
      <div>
        <h1 className="text-headline-lg font-bold text-on-surface tracking-tight">Cấu hình thuật toán</h1>
        <p className="text-label-sm text-on-surface-variant mt-0.5">Thiết lập thông số vận hành cho thuật toán xếp lịch</p>
      </div>
      <div className="flex items-center gap-2.5">
        <TabBar active={activeTab} onChange={onTabChange} />
        <Button
          variant="secondary"
          size="sm"
          onClick={onSync}
          disabled={syncing}
          loading={syncing}
          icon={!syncing ? <span className="material-symbols-outlined text-[14px]" aria-hidden="true">sync</span> : undefined}
          title="Cập nhật mô tả các tham số về phiên bản mặc định theo code"
        >
          Đồng bộ
        </Button>
        <Button
          variant="primary"
          size="sm"
          onClick={onCreate}
          icon={<span className="material-symbols-outlined text-[14px]" aria-hidden="true">add</span>}
        >
          Thêm
        </Button>
      </div>
    </div>
  );
}
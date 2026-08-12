"use client";

import { useEffect, useState } from "react";
import { IconButton } from "@/components/ui";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { useToast } from "@/hooks/useToast";
import type { ConfigEntry, EditingConfig } from "./types";

type Props = {
  config: ConfigEntry;
  onSave: (updated: EditingConfig) => void;
  onDelete: () => void;
};

export function ConfigRowInline({ config, onSave, onDelete }: Props) {
  const { error: toastError } = useToast();
  const [editingDesc, setEditingDesc] = useState(false);
  const [desc, setDesc] = useState(config.description);
  const [saving, setSaving] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);

  useEffect(() => { setDesc(config.description); }, [config.description]);

  async function handleSaveDesc() {
    if (desc === config.description) { setEditingDesc(false); return; }
    setSaving(true);
    try {
      await api.updateAlgorithmConfig(config.paramKey, { paramValue: config.paramValue, description: desc });
      onSave({ paramValue: config.paramValue, description: desc });
      setEditingDesc(false);
    } catch (err) {
      toastError(getErrorMessage(err, "Lưu thất bại"));
    } finally { setSaving(false); }
  }

  async function handleDelete() {
    try {
      await api.deleteAlgorithmConfig(config.paramKey);
      onDelete();
    } catch (err) {
      toastError(getErrorMessage(err, "Xóa thất bại"));
    }
  }

  if (editingDesc) {
    return (
      <div className="flex items-center gap-1">
        <input
          className="h-7 w-40 rounded-lg border border-primary bg-surface px-2.5 text-[11px] text-on-surface focus:outline-none focus:ring-1 focus:ring-primary/20"
          value={desc}
          onChange={e => setDesc(e.target.value)}
          placeholder="Mô tả..."
          autoFocus
        />
        <IconButton label="Lưu mô tả" variant="primary" size="sm" disabled={saving} loading={saving} onClick={handleSaveDesc} className="text-white">
          <span className="material-symbols-outlined text-[14px]" aria-hidden="true">check</span>
        </IconButton>
        <IconButton label="Hủy" variant="ghost" size="sm" onClick={() => { setEditingDesc(false); setDesc(config.description); }} className="border border-outline-variant text-on-surface">
          <span className="material-symbols-outlined text-[14px]" aria-hidden="true">close</span>
        </IconButton>
      </div>
    );
  }

  return (
    <>
      <IconButton label="Sửa mô tả" variant="ghost" size="sm" onClick={() => setEditingDesc(true)} className="text-on-surface-variant">
        <span className="material-symbols-outlined text-[14px]" aria-hidden="true">edit_note</span>
      </IconButton>
      <IconButton label="Xóa" variant="ghost" size="sm" onClick={() => setConfirmOpen(true)} className="text-red-800 hover:bg-red-100">
        <span className="material-symbols-outlined text-[14px]" aria-hidden="true">delete</span>
      </IconButton>
      <ConfirmDialog
        open={confirmOpen}
        onClose={() => setConfirmOpen(false)}
        onConfirm={() => { setConfirmOpen(false); void handleDelete(); }}
        title="Xóa cấu hình?"
        description={config.paramKey}
        confirmLabel="Xóa"
        cancelLabel="Hủy"
        variant="danger"
      />
    </>
  );
}
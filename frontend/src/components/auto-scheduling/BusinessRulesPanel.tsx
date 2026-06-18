"use client";

import { useState } from "react";
import Link from "next/link";
import { api } from "@/lib/api";
import { FormInput, FormTextarea, FormSelect, Button } from "@/components/ui";

type RuleType = "required" | "preference";

type Rule = {
  id: string;
  icon: string;
  iconColor: string;
  title: string;
  description: string;
  type: RuleType;
  hasConfig?: boolean;
};

const DEFAULT_RULES: Rule[] = [
  {
    id: "rule-1",
    icon: "check_circle",
    iconColor: "text-secondary",
    title: "Quy tắc nghỉ sau Trực 24/24",
    description: "Bắt buộc nghỉ ít nhất 1 ca Thông tầm (hoặc 24h) ngay sau ca trực 24/24.",
    type: "required",
  },
  {
    id: "rule-2",
    icon: "check_circle",
    iconColor: "text-secondary",
    title: "Tránh xung đột lịch Phòng khám",
    description: "Không xếp trực đêm nếu có lịch Phòng khám Dịch vụ sang hôm sau.",
    type: "required",
  },
  {
    id: "rule-3",
    icon: "balance",
    iconColor: "text-primary",
    title: "Cân bằng khối lượng công việc",
    description: "Ưu tiên chia đều số ca trực cuối tuần (Thứ 7, CN) cho tất cả nhân sự trong khoa.",
    type: "preference",
    hasConfig: true,
  },
];

export function BusinessRulesPanel({ onAddRule }: { onAddRule?: () => void }) {
  const [rules, setRules] = useState<Rule[]>(DEFAULT_RULES);
  const [showAddForm, setShowAddForm] = useState(false);
  const [newTitle, setNewTitle] = useState("");
  const [newDesc, setNewDesc] = useState("");
  const [newType, setNewType] = useState<RuleType>("preference");
  const [saving, setSaving] = useState(false);
  const [titleError, setTitleError] = useState("");

  const handleAddRule = async () => {
    if (!newTitle.trim()) {
      setTitleError("Tên quy tắc không được để trống");
      return;
    }
    setTitleError("");
    setSaving(true);
    try {
      await api.createAlgorithmConfig({
        paramKey: `custom_rule_${Date.now()}`,
        paramValue: newTitle.trim(),
        valueType: "string",
        description: newDesc.trim(),
      });
      setRules((prev) => [
        ...prev,
        {
          id: `custom-${Date.now()}`,
          icon: "star",
          iconColor: "text-tertiary",
          title: newTitle.trim(),
          description: newDesc.trim() || "Quy tắc tùy chỉnh",
          type: newType,
        },
      ]);
      setNewTitle("");
      setNewDesc("");
      setNewType("preference");
      setShowAddForm(false);
      onAddRule?.();
    } catch {
      setRules((prev) => [
        ...prev,
        {
          id: `custom-${Date.now()}`,
          icon: "star",
          iconColor: "text-tertiary",
          title: newTitle.trim(),
          description: newDesc.trim() || "Quy tắc tùy chỉnh",
          type: newType,
        },
      ]);
      setNewTitle("");
      setNewDesc("");
      setNewType("preference");
      setShowAddForm(false);
      onAddRule?.();
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="relative">
      <h2 className="text-title-lg font-semibold mb-4 flex items-center gap-2 border-b border-outline-variant/30 pb-3">
        <span className="material-symbols-outlined text-primary shrink-0">rule</span>
        Luật nghiệp vụ đang áp dụng
      </h2>

      <div className="flex flex-col gap-4">
        {rules.map((rule) => (
          <div
            className="bg-surface-container-low p-4 rounded-lg border border-outline-variant/30 hover:border-primary/50 transition-colors"
            key={rule.id}
          >
            <div className="flex items-start gap-3">
              <span className={`material-symbols-outlined ${rule.iconColor} mt-0.5`}>{rule.icon}</span>
              <div className="flex-1 min-w-0">
                <h3 className="font-label-md font-bold mb-1">{rule.title}</h3>
                <p className="font-body-sm text-on-surface-variant">{rule.description}</p>
                <div className="mt-2 flex items-center gap-2">
                  {rule.type === "required" ? (
                    <span className="px-2 py-0.5 bg-error-container text-on-error-container text-label-sm font-bold rounded uppercase tracking-wider">
                      BẮT BUỘC
                    </span>
                  ) : (
                    <span className="px-2 py-0.5 bg-tertiary-fixed text-on-tertiary text-label-sm font-bold rounded uppercase tracking-wider">
                      ƯU TIÊN
                    </span>
                  )}
                  {rule.hasConfig && (
                    <Link className="text-label-sm text-primary hover:underline ml-auto" href="/auto-scheduling/algorithm-config">
                      Cấu hình
                    </Link>
                  )}
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>

      {showAddForm ? (
        <div className="mt-4 p-4 bg-surface-container-lowest rounded-lg border border-outline-variant space-y-4">
          <FormInput
            label="Tên quy tắc"
            value={newTitle}
            onChange={(e) => {
              setNewTitle(e.target.value);
              if (titleError) setTitleError("");
            }}
            error={titleError}
            placeholder="Nhập tên quy tắc..."
            required
            disabled={saving}
          />

          <FormTextarea
            label="Mô tả"
            value={newDesc}
            onChange={(e) => setNewDesc(e.target.value)}
            placeholder="Mô tả quy tắc (tùy chọn)..."
            rows={2}
            disabled={saving}
          />

          <div className="flex items-center gap-2">
            <div className="flex-1">
              <FormSelect
                label="Loại quy tắc"
                value={newType}
                onChange={(e) => setNewType(e.target.value as RuleType)}
                options={[
                  { value: "required", label: "Bắt buộc" },
                  { value: "preference", label: "Ưu tiên" },
                ]}
                disabled={saving}
              />
            </div>
          </div>

          <div className="flex items-center gap-2 pt-1">
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={() => {
                setShowAddForm(false);
                setNewTitle("");
                setNewDesc("");
                setTitleError("");
              }}
              disabled={saving}
            >
              Hủy
            </Button>
            <Button
              type="button"
              variant="primary"
              size="sm"
              loading={saving}
              onClick={handleAddRule}
            >
              Lưu
            </Button>
          </div>
        </div>
      ) : (
        <button
          className="w-full mt-4 py-2 text-primary font-label-sm border border-dashed border-primary/40 rounded-lg hover:bg-primary/5 transition-colors flex items-center justify-center gap-2"
          onClick={() => setShowAddForm(true)}
          type="button"
        >
          <span className="material-symbols-outlined" style={{ fontSize: "16px" }}>add</span>
          Thêm luật tùy chỉnh
        </button>
      )}
    </div>
  );
}

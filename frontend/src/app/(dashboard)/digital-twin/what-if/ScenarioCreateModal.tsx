"use client";

import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { FormInput } from "@/components/ui/FormInput";
import { FormTextarea } from "@/components/ui/FormTextarea";

interface ScenarioCreateModalProps {
  onClose: () => void;
  onSubmit: (data: {
    name: string;
    description: string;
    configOverrides: Record<string, unknown>;
  }) => void;
}

/**
 * Modal for creating a new what-if scenario.
 */
export function ScenarioCreateModal({ onClose, onSubmit }: ScenarioCreateModalProps) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [configOverrides, setConfigOverrides] = useState<Record<string, unknown>>({});

  // Common config presets
  const presets = [
    { label: "Tabu Size", key: "tabuSize", type: "number", defaultValue: 25 },
    { label: "Max Iterations", key: "maxIterations", type: "number", defaultValue: 1000 },
    { label: "Coverage Weight", key: "coverageWeight", type: "number", defaultValue: 10 },
    { label: "Fairness Weight", key: "fairnessWeight", type: "number", defaultValue: 5 },
  ];

  const handlePresetChange = (key: string, value: string, type: string) => {
    const parsedValue = type === "number" ? parseFloat(value) : value;
    setConfigOverrides((prev) => ({
      ...prev,
      [key]: parsedValue,
    }));
  };

  const handleSubmit = () => {
    if (!name.trim()) return;
    onSubmit({
      name: name.trim(),
      description: description.trim(),
      configOverrides,
    });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50">
      <div className="bg-surface-container-lowest border border-outline-variant rounded-xl w-full max-w-lg max-h-[90vh] overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b border-outline-variant">
          <div className="flex items-center gap-3">
            <span className="material-symbols-outlined text-primary">science</span>
            <h2 className="font-title-lg text-title-lg text-on-surface">Tạo Scenario mới</h2>
          </div>
          <button onClick={onClose} className="p-2 rounded-lg hover:bg-surface-container-high transition-colors">
            <span className="material-symbols-outlined text-[20px]">close</span>
          </button>
        </div>

        {/* Content */}
        <div className="p-4 space-y-4 overflow-y-auto max-h-[60vh]">
          {/* Name */}
          <FormInput
            label="Tên Scenario"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="VD: Tăng Tabu Size"
            required
          />

          {/* Description */}
          <FormTextarea
            label="Mô tả"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Mục đích của scenario này..."
            rows={3}
          />

          {/* Config Overrides */}
          <div className="space-y-3">
            <label className="text-label-md text-on-surface">Thay đổi cấu hình</label>
            <div className="space-y-2">
              {presets.map((preset) => (
                <div key={preset.key} className="flex items-center gap-3 p-3 bg-surface-container-low rounded-lg">
                  <div className="flex-1">
                    <div className="text-label-sm text-on-surface">{preset.label}</div>
                  </div>
                  <input
                    type={preset.type}
                    value={String(configOverrides[preset.key] ?? preset.defaultValue)}
                    onChange={(e) => handlePresetChange(preset.key, e.target.value, preset.type)}
                    className="w-24 px-3 py-1.5 bg-surface-container-lowest border border-outline-variant rounded-lg text-body-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                  />
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end gap-2 p-4 border-t border-outline-variant">
          <Button variant="ghost" onClick={onClose}>
            Hủy
          </Button>
          <Button variant="primary" onClick={handleSubmit} disabled={!name.trim()}>
            Tạo Scenario
          </Button>
        </div>
      </div>
    </div>
  );
}

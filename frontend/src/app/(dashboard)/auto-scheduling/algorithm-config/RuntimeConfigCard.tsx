"use client";

import { useState, useEffect } from "react";
import { Button } from "@/components/ui";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { useToast } from "@/hooks/useToast";

interface RuntimeConfig {
  maxIterations: number;
  weekendWeight: number;
  overnightRecoveryHours: number;
  greedyCoverageThreshold: number;
  balanceScoreMin: number;
  autoCompensationEnabled: boolean;
  backtrackTimeLimitSeconds: number;
}

interface RuntimeConfigCardProps {
  onConfigSaved?: () => void;
}

export function RuntimeConfigCard({ onConfigSaved }: RuntimeConfigCardProps) {
  const [config, setConfig] = useState<RuntimeConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<RuntimeConfig | null>(null);
  const { success, error } = useToast();

  const loadConfig = async () => {
    setLoading(true);
    try {
      const res = await api.getRuntimeConfig();
      const data = (res as { data: RuntimeConfig }).data;
      setConfig(data);
      setForm(data);
    } catch {
      error("Không thể tải cấu hình runtime");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadConfig();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSave = async () => {
    if (!form) return;
    setSaving(true);
    try {
      await api.updateRuntimeConfig(form);
      setConfig(form);
      setEditing(false);
      success("Lưu cấu hình thành công");
      onConfigSaved?.();
    } catch (err) {
      error(getErrorMessage(err, "Lưu cấu hình thất bại"));
    } finally {
      setSaving(false);
    }
  };

  const handleReset = () => {
    if (config) {
      setForm(config);
      setEditing(false);
    }
  };

  if (loading) {
    return (
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-6 animate-pulse">
        <div className="h-6 bg-surface-container-low rounded w-48 mb-4"></div>
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-12 bg-surface-container-low rounded"></div>
          ))}
        </div>
      </div>
    );
  }

  if (!config || !form) return null;

  const inputClass = "h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-label-md text-on-surface transition-all focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20";

  return (
    <div className="bg-surface-container-lowest rounded-xl border border-outline-variant shadow-sm overflow-hidden">
      <div className="px-6 py-4 border-b border-outline-variant bg-surface-container-low flex items-center justify-between">
        <div className="flex items-center gap-3">
          <span className="material-symbols-outlined text-primary">tune</span>
          <h3 className="text-title-md font-semibold text-on-surface">Cấu hình Runtime</h3>
        </div>
        <div className="flex items-center gap-2">
          {editing ? (
            <>
              <Button
                variant="secondary"
                size="sm"
                onClick={handleReset}
              >
                Hủy
              </Button>
              <Button
                variant="primary"
                size="sm"
                onClick={handleSave}
                disabled={saving}
                loading={saving}
              >
                Lưu thay đổi
              </Button>
            </>
          ) : (
            <Button
              variant="secondary"
              size="sm"
              onClick={() => setEditing(true)}
              icon={<span className="material-symbols-outlined text-[16px]" aria-hidden="true">edit</span>}
            >
              Chỉnh sửa
            </Button>
          )}
        </div>
      </div>

      <div className="p-6">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {/* maxIterations */}
          <div className="space-y-1.5">
            <label className="text-label-sm text-on-surface-variant flex items-center gap-1.5">
              <code className="font-mono text-primary bg-primary-fixed/20 px-1.5 py-0.5 rounded text-[11px]">max_iterations</code>
            </label>
            {editing ? (
              <input
                type="number"
                className={inputClass}
                value={form.maxIterations}
                onChange={(e) => setForm({ ...form, maxIterations: parseInt(e.target.value) || 0 })}
                min={100}
                max={10000}
              />
            ) : (
              <p className="text-body-md font-mono text-on-surface bg-surface-container-low px-3 py-2 rounded-lg border border-outline-variant/30">
                {config.maxIterations}
              </p>
            )}
            <p className="text-label-xs text-outline">Số vòng lặp tối đa cho Backtracking</p>
          </div>

          {/* weekendWeight */}
          <div className="space-y-1.5">
            <label className="text-label-sm text-on-surface-variant flex items-center gap-1.5">
              <code className="font-mono text-primary bg-primary-fixed/20 px-1.5 py-0.5 rounded text-[11px]">weekend_weight</code>
            </label>
            {editing ? (
              <input
                type="number"
                step="0.1"
                className={inputClass}
                value={form.weekendWeight}
                onChange={(e) => setForm({ ...form, weekendWeight: parseFloat(e.target.value) || 0 })}
                min={1}
                max={5}
              />
            ) : (
              <p className="text-body-md font-mono text-on-surface bg-surface-container-low px-3 py-2 rounded-lg border border-outline-variant/30">
                {config.weekendWeight}
              </p>
            )}
            <p className="text-label-xs text-outline">Trọng số cuối tuần (cao = tránh T7/CN)</p>
          </div>

          {/* overnightRecoveryHours */}
          <div className="space-y-1.5">
            <label className="text-label-sm text-on-surface-variant flex items-center gap-1.5">
              <code className="font-mono text-primary bg-primary-fixed/20 px-1.5 py-0.5 rounded text-[11px]">overnight_recovery_hours</code>
            </label>
            {editing ? (
              <input
                type="number"
                className={inputClass}
                value={form.overnightRecoveryHours}
                onChange={(e) => setForm({ ...form, overnightRecoveryHours: parseInt(e.target.value) || 0 })}
                min={12}
                max={72}
              />
            ) : (
              <p className="text-body-md font-mono text-on-surface bg-surface-container-low px-3 py-2 rounded-lg border border-outline-variant/30">
                {config.overnightRecoveryHours}h
              </p>
            )}
            <p className="text-label-xs text-outline">Giờ nghỉ sau trực 24/24</p>
          </div>

          {/* greedyCoverageThreshold */}
          <div className="space-y-1.5">
            <label className="text-label-sm text-on-surface-variant flex items-center gap-1.5">
              <code className="font-mono text-primary bg-primary-fixed/20 px-1.5 py-0.5 rounded text-[11px]">greedy_coverage_threshold</code>
            </label>
            {editing ? (
              <input
                type="number"
                step="0.05"
                className={inputClass}
                value={form.greedyCoverageThreshold}
                onChange={(e) => setForm({ ...form, greedyCoverageThreshold: parseFloat(e.target.value) || 0 })}
                min={0.5}
                max={1}
              />
            ) : (
              <p className="text-body-md font-mono text-on-surface bg-surface-container-low px-3 py-2 rounded-lg border border-outline-variant/30">
                {config.greedyCoverageThreshold}
              </p>
            )}
            <p className="text-label-xs text-outline">Ngưỡng phủ lịch (0.5-1.0)</p>
          </div>

          {/* balanceScoreMin */}
          <div className="space-y-1.5">
            <label className="text-label-sm text-on-surface-variant flex items-center gap-1.5">
              <code className="font-mono text-primary bg-primary-fixed/20 px-1.5 py-0.5 rounded text-[11px]">balance_score_min</code>
            </label>
            {editing ? (
              <input
                type="number"
                step="0.05"
                className={inputClass}
                value={form.balanceScoreMin}
                onChange={(e) => setForm({ ...form, balanceScoreMin: parseFloat(e.target.value) || 0 })}
                min={0.3}
                max={1}
              />
            ) : (
              <p className="text-body-md font-mono text-on-surface bg-surface-container-low px-3 py-2 rounded-lg border border-outline-variant/30">
                {config.balanceScoreMin}
              </p>
            )}
            <p className="text-label-xs text-outline">Ngưỡng cân bằng tải (0.3-1.0)</p>
          </div>

          {/* backtrackTimeLimitSeconds */}
          <div className="space-y-1.5">
            <label className="text-label-sm text-on-surface-variant flex items-center gap-1.5">
              <code className="font-mono text-primary bg-primary-fixed/20 px-1.5 py-0.5 rounded text-[11px]">backtrack_time_limit</code>
            </label>
            {editing ? (
              <input
                type="number"
                className={inputClass}
                value={form.backtrackTimeLimitSeconds}
                onChange={(e) => setForm({ ...form, backtrackTimeLimitSeconds: parseInt(e.target.value) || 0 })}
                min={10}
                max={300}
              />
            ) : (
              <p className="text-body-md font-mono text-on-surface bg-surface-container-low px-3 py-2 rounded-lg border border-outline-variant/30">
                {config.backtrackTimeLimitSeconds}s
              </p>
            )}
            <p className="text-label-xs text-outline">Giới hạn thời gian Backtracking</p>
          </div>
        </div>

        {/* autoCompensationEnabled - Full width */}
        <div className="mt-5 pt-5 border-t border-outline-variant">
          <div className="flex items-center justify-between max-w-md">
            <div>
              <label className="text-label-sm text-on-surface font-medium flex items-center gap-1.5">
                <code className="font-mono text-primary bg-primary-fixed/20 px-1.5 py-0.5 rounded text-[11px]">auto_compensation_enabled</code>
              </label>
              <p className="text-label-xs text-outline mt-0.5">Tự động tạo ngày nghỉ bù sau trực 24/24</p>
            </div>
            {editing ? (
              <label className="relative inline-flex items-center cursor-pointer">
                <input
                  type="checkbox"
                  className="sr-only peer"
                  checked={form.autoCompensationEnabled}
                  onChange={(e) => setForm({ ...form, autoCompensationEnabled: e.target.checked })}
                />
                <div className="w-11 h-6 bg-surface-variant rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary"></div>
              </label>
            ) : (
              <span className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-full text-label-sm font-semibold ${
                config.autoCompensationEnabled
                  ? "bg-secondary-container text-on-secondary-container"
                  : "bg-surface-container-high text-outline"
              }`}>
                <span className={`w-2 h-2 rounded-full ${config.autoCompensationEnabled ? "bg-secondary" : "bg-outline"}`}></span>
                {config.autoCompensationEnabled ? "Bật" : "Tắt"}
              </span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

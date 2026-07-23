"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { Skeleton } from "@/components/ui/Skeleton";
import { ScenarioCreateModal } from "./ScenarioCreateModal";
import { ScenarioCard } from "./ScenarioCard";
import { ScenarioComparison } from "./ScenarioComparison";
import type {
  ScenarioResponse,
  ScenarioStatus,
} from "@/types/api";

/**
 * v11.1.8 What-if Analyzer Page
 *
 * Decision Support System for configuration testing:
 * - Scenario management
 * - Batch simulation
 * - Comparison
 * - Impact analysis
 * - Recommendations
 */
export default function WhatIfPage() {
  const router = useRouter();
  const [scenarios, setScenarios] = useState<ScenarioResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Modal state
  const [showCreateModal, setShowCreateModal] = useState(false);

  // Selected scenarios for comparison
  const [selectedForCompare, setSelectedForCompare] = useState<number[]>([]);
  const [compareMode, setCompareMode] = useState(false);

  // Load scenarios
  const loadScenarios = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const data = await api.getWhatIfScenarios();
      setScenarios((data as ScenarioResponse[]) || []);
    } catch (err) {
      setError(getErrorMessage(err, "Có lỗi xảy ra"));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadScenarios();
  }, [loadScenarios]);

  // Create scenario
  const handleCreateScenario = async (data: { name: string; description: string; configOverrides: Record<string, unknown> }) => {
    try {
      await api.createWhatIfScenario({
        name: data.name,
        description: data.description,
        configOverrides: data.configOverrides,
      });
      setShowCreateModal(false);
      loadScenarios();
    } catch (err) {
      console.error("Failed to create scenario:", err);
    }
  };

  // Run scenario
  const handleRunScenario = async (id: number) => {
    try {
      await api.runWhatIfScenario(id);
      loadScenarios();
    } catch (err) {
      console.error("Failed to run scenario:", err);
    }
  };

  // Run batch
  const handleRunBatch = async () => {
    const pending = scenarios.filter((s) => s.status === "DRAFT" || s.status === "FAILED");
    const ids = pending.map((s) => s.id);
    if (ids.length === 0) return;

    try {
      await api.runWhatIfBatch(ids);
      loadScenarios();
    } catch (err) {
      console.error("Failed to run batch:", err);
    }
  };

  // Toggle compare selection
  const handleToggleCompare = (id: number) => {
    setSelectedForCompare((prev) => {
      if (prev.includes(id)) {
        return prev.filter((i) => i !== id);
      }
      if (prev.length >= 2) {
        return [prev[1], id];
      }
      return [...prev, id];
    });
  };

  // Status badge helper
  const statusConfig: Record<ScenarioStatus, { label: string; tone: "success" | "warning" | "error" | "info" }> = {
    DRAFT: { label: "Bản nháp", tone: "info" },
    PENDING: { label: "Chờ xử lý", tone: "info" },
    READY: { label: "Sẵn sàng", tone: "info" },
    RUNNING: { label: "Đang chạy", tone: "info" },
    COMPLETED: { label: "Hoàn thành", tone: "success" },
    FAILED: { label: "Thất bại", tone: "error" },
    CANCELLED: { label: "Đã hủy", tone: "warning" },
  };

  const pendingScenarios = scenarios.filter(
    (s) => s.status === "DRAFT" || s.status === "FAILED"
  );
  const completedScenarios = scenarios.filter(
    (s) => s.status === "COMPLETED"
  );

  return (
    <div className="p-margin-desktop space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="font-display-lg text-display-lg text-on-surface mb-2">What-if Analyzer</h1>
          <p className="text-body-md text-on-surface-variant">
            Decision Support System - Mô phỏng và so sánh các cấu hình
          </p>
        </div>

        <div className="flex gap-2">
          {pendingScenarios.length > 0 && (
            <Button variant="secondary" onClick={handleRunBatch}>
              <span className="material-symbols-outlined text-[18px]">play_arrow</span>
              Run Batch ({pendingScenarios.length})
            </Button>
          )}
          <Button variant="primary" onClick={() => setShowCreateModal(true)}>
            <span className="material-symbols-outlined text-[18px]">add</span>
            Tạo Scenario
          </Button>
        </div>
      </div>

      {/* Compare Mode Toggle */}
      <div className="flex items-center justify-between bg-surface-container-lowest border border-outline-variant rounded-xl p-4">
        <div className="flex items-center gap-3">
          <span className="material-symbols-outlined text-primary">compare</span>
          <div>
            <div className="text-body-md text-on-surface font-medium">So sánh Scenario</div>
            <div className="text-label-sm text-on-surface-variant">
              Chọn 2 scenario để so sánh
            </div>
          </div>
        </div>
        <div className="flex items-center gap-3">
          {selectedForCompare.length > 0 && (
            <span className="text-label-sm text-on-surface-variant">
              {selectedForCompare.length}/2 selected
            </span>
          )}
          <Button
            variant={compareMode ? "primary" : "ghost"}
            size="sm"
            onClick={() => {
              setCompareMode(!compareMode);
              if (!compareMode) {
                setSelectedForCompare([]);
              }
            }}
          >
            {compareMode ? "Hủy so sánh" : "Bật so sánh"}
          </Button>
        </div>
      </div>

      {/* Comparison View */}
      {compareMode && selectedForCompare.length === 2 && (
        <ScenarioComparison
          baselineId={selectedForCompare[0]}
          comparedId={selectedForCompare[1]}
          scenarios={scenarios}
        />
      )}

      {/* Scenarios Grid */}
      <section>
        <h2 className="font-headline-md text-headline-md text-on-surface mb-4">
          Scenarios ({scenarios.length})
        </h2>

        {loading ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {[1, 2, 3, 4, 5, 6].map((i) => (
              <Skeleton key={i} className="h-64 rounded-xl" />
            ))}
          </div>
        ) : scenarios.length === 0 ? (
          <div className="p-12 bg-surface-container-lowest border border-outline-variant rounded-xl text-center">
            <span className="material-symbols-outlined text-[48px] text-on-surface-variant">science</span>
            <h3 className="mt-4 font-title-lg text-title-lg text-on-surface">Chưa có scenario nào</h3>
            <p className="mt-2 text-body-md text-on-surface-variant">
              Tạo scenario để bắt đầu phân tích what-if
            </p>
            <Button variant="primary" className="mt-6" onClick={() => setShowCreateModal(true)}>
              Tạo Scenario đầu tiên
            </Button>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {scenarios.map((scenario) => (
              <ScenarioCard
                key={scenario.id}
                scenario={scenario}
                compareMode={compareMode}
                selected={selectedForCompare.includes(scenario.id)}
                onToggleCompare={() => handleToggleCompare(scenario.id)}
                onRun={() => handleRunScenario(scenario.id)}
                onDelete={() => {
                  api.deleteWhatIfScenario(scenario.id).then(loadScenarios);
                }}
                onViewReplay={() => {
                  if (scenario.sessionKey) {
                    router.push(`/digital-twin/replay/${scenario.sessionKey}`);
                  }
                }}
                onViewDecision={() => {
                  if (scenario.sessionKey) {
                    router.push(`/digital-twin/decision/${scenario.sessionKey}`);
                  }
                }}
              />
            ))}
          </div>
        )}
      </section>

      {/* Quick Links */}
      <section className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5 hover:bg-surface-container-low transition-colors cursor-pointer"
             onClick={() => router.push("/digital-twin/compare")}>
          <div className="flex items-center gap-3 mb-3">
            <span className="material-symbols-outlined text-primary bg-primary-fixed p-2 rounded-lg text-[20px]">compare</span>
            <h3 className="font-title-lg text-title-lg text-on-surface">Compare</h3>
          </div>
          <p className="text-label-sm text-on-surface-variant">So sánh kết quả</p>
        </div>

        <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5 hover:bg-surface-container-low transition-colors cursor-pointer"
             onClick={() => router.push("/digital-twin/replay")}>
          <div className="flex items-center gap-3 mb-3">
            <span className="material-symbols-outlined text-secondary bg-secondary-container p-2 rounded-lg text-[20px]">replay</span>
            <h3 className="font-title-lg text-title-lg text-on-surface">Replay</h3>
          </div>
          <p className="text-label-sm text-on-surface-variant">Phát lại quá trình</p>
        </div>

        <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5 hover:bg-surface-container-low transition-colors cursor-pointer"
             onClick={() => router.push("/digital-twin/decision")}>
          <div className="flex items-center gap-3 mb-3">
            <span className="material-symbols-outlined text-tertiary bg-tertiary-fixed p-2 rounded-lg text-[20px]">account_tree</span>
            <h3 className="font-title-lg text-title-lg text-on-surface">Decision Graph</h3>
          </div>
          <p className="text-label-sm text-on-surface-variant">Đồ thị quyết định</p>
        </div>

        <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5 hover:bg-surface-container-low transition-colors cursor-pointer"
             onClick={() => router.push("/digital-twin/live")}>
          <div className="flex items-center gap-3 mb-3">
            <span className="material-symbols-outlined text-error bg-error-container p-2 rounded-lg text-[20px]">live_tv</span>
            <h3 className="font-title-lg text-title-lg text-on-surface">Live</h3>
          </div>
          <p className="text-label-sm text-on-surface-variant">Theo dõi trực tiếp</p>
        </div>
      </section>

      {/* Create Modal */}
      {showCreateModal && (
        <ScenarioCreateModal
          onClose={() => setShowCreateModal(false)}
          onSubmit={handleCreateScenario}
        />
      )}
    </div>
  );
}

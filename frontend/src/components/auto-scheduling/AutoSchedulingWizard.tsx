"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import { Button, Badge } from "@/components/ui";
import { useAutoSchedule } from "@/hooks/useAutoSchedule";
import { useAlgorithmProgress } from "@/hooks/useAlgorithmProgress";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { SchedulePeriod, Staff, AutoScheduleResult } from "@/types/api";

type WizardStep = "welcome" | "period" | "config-check" | "exclusions" | "running" | "results" | "apply";

type StepInfo = {
  id: WizardStep;
  title: string;
  icon: string;
  description: string;
  forBeginner: boolean;
};

const WIZARD_STEPS: StepInfo[] = [
  { id: "welcome", title: "Chào mừng", icon: "waving_hand", description: "Hướng dẫn nhanh", forBeginner: true },
  { id: "period", title: "Chọn kỳ lịch", icon: "event", description: "Chọn kỳ cần xếp lịch", forBeginner: true },
  { id: "config-check", title: "Kiểm tra cấu hình", icon: "tune", description: "Đảm bảo cấu hình đã đúng", forBeginner: true },
  { id: "exclusions", title: "Ngoại lệ nhân sự", icon: "group_remove", description: "Loại trừ nhân sự nếu cần", forBeginner: false },
  { id: "running", title: "Đang chạy", icon: "sync", description: "Thuật toán đang xử lý", forBeginner: false },
  { id: "results", title: "Kết quả", icon: "fact_check", description: "Xem kết quả xếp lịch", forBeginner: true },
  { id: "apply", title: "Áp dụng", icon: "check", description: "Xác nhận và lưu lịch", forBeginner: true },
];

const TIPS = [
  { icon: "lightbulb", text: "Nếu đây là lần đầu, hãy bắt đầu với thuật toán CSP-MRV-FC để có kết quả tốt nhất." },
  { icon: "info", text: "Coverage > 90% được coi là tốt. Dưới 70% cần xem lại cấu hình." },
  { icon: "group", text: "Đảm bảo đủ nhân sự đủ điều kiện cho mỗi loại ca trong Cấu hình thuật toán." },
  { icon: "schedule", text: "Nếu kỳ lịch chưa ở trạng thái DRAFT, bạn cần chuyển sang DRAFT trước." },
];

type Props = {
  periods: SchedulePeriod[];
  activeStaff: Staff[];
  onComplete: () => void;
  onSkip: () => void;
};

export function AutoSchedulingWizard({ periods, activeStaff, onComplete, onSkip }: Props) {
  const [currentStep, setCurrentStep] = useState<WizardStep>("welcome");
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [excludedStaffIds, setExcludedStaffIds] = useState<number[]>([]);
  const [previewResult, setPreviewResult] = useState<AutoScheduleResult | null>(null);
  const [autoGenEnabled, setAutoGenEnabled] = useState<boolean | null>(null);
  const [configIssues, setConfigIssues] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [randomTip, setRandomTip] = useState(TIPS[0]);
  const [showTips, setShowTips] = useState(true);
  
  const [autoState, autoActions] = useAutoSchedule();
  const { runPreview, applyPreview } = autoActions;
  const { previewResult: autoPreview, running } = autoState;
  const progress = useAlgorithmProgress(selectedPeriodId, running);

  // Random tip
  useEffect(() => {
    const interval = setInterval(() => {
      setRandomTip(TIPS[Math.floor(Math.random() * TIPS.length)]);
    }, 8000);
    return () => clearInterval(interval);
  }, []);

  // Check auto-gen config
  useEffect(() => {
    api.getAutoGenConfig().then(res => {
      const payload = (res as unknown as { data?: { enabled?: boolean } } | null)?.data;
      setAutoGenEnabled(typeof payload?.enabled === "boolean" ? payload.enabled : null);
    }).catch(() => setAutoGenEnabled(null));
  }, []);

  // Check config issues
  useEffect(() => {
    const issues: string[] = [];
    if (autoGenEnabled === false) {
      issues.push("Auto-gen đang bị tắt. Vào Cấu hình để bật.");
    }
    if (activeStaff.length < 5) {
      issues.push(`Chỉ có ${activeStaff.length} nhân sự. Cần ít nhất 5 người để xếp lịch hiệu quả.`);
    }
    setConfigIssues(issues);
  }, [autoGenEnabled, activeStaff]);

  const selectedPeriod = periods.find(p => p.id === selectedPeriodId) ?? null;
  const isDraft = selectedPeriod?.status === "DRAFT";

  const handleNext = useCallback(() => {
    const steps = WIZARD_STEPS.map(s => s.id);
    const currentIndex = steps.indexOf(currentStep);
    if (currentIndex < steps.length - 1) {
      setCurrentStep(steps[currentIndex + 1]);
    }
  }, [currentStep]);

  const handlePrev = useCallback(() => {
    const steps = WIZARD_STEPS.map(s => s.id);
    const currentIndex = steps.indexOf(currentStep);
    if (currentIndex > 0) {
      setCurrentStep(steps[currentIndex - 1]);
    }
  }, [currentStep]);

  const handleRunAlgorithm = useCallback(async () => {
    if (!selectedPeriodId) return;
    setLoading(true);
    setError(null);
    try {
      await runPreview(selectedPeriodId, excludedStaffIds);
      setCurrentStep("results");
    } catch (err) {
      setError(getErrorMessage(err, "Không thể chạy thuật toán"));
    } finally {
      setLoading(false);
    }
  }, [selectedPeriodId, excludedStaffIds, runPreview]);

  const handleApply = useCallback(async () => {
    if (!previewResult || !selectedPeriodId) return;
    setLoading(true);
    try {
      const schedules = previewResult.schedules.map(s => ({
        workDate: s.workDate,
        shiftTypeId: s.shiftTypeId,
        staffId: s.staffId,
      }));
      await applyPreview(selectedPeriodId, schedules, onComplete);
    } catch (err) {
      setError(getErrorMessage(err, "Không thể áp dụng lịch"));
    } finally {
      setLoading(false);
    }
  }, [previewResult, selectedPeriodId, applyPreview, onComplete]);

  const currentStepIndex = WIZARD_STEPS.findIndex(s => s.id === currentStep);

  return (
    <div className="fixed inset-0 z-[200] flex flex-col bg-surface-container-lowest overflow-hidden">
        {/* Header */}
        <div className="bg-primary px-6 py-4 text-white shrink-0">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-white/20">
                <span className="material-symbols-outlined text-[24px]">auto_mode</span>
              </div>
              <div>
                <h2 className="text-title-md font-bold">Trình hướng dẫn xếp lịch</h2>
                <p className="text-[12px] text-white/80">Tự động xếp lịch trong 7 bước đơn giản</p>
              </div>
            </div>
            <button
              onClick={onSkip}
              className="flex items-center gap-1 text-white/80 hover:text-white transition-colors"
            >
              <span className="material-symbols-outlined text-[20px]">close</span>
              Bỏ qua
            </button>
          </div>
          
          {/* Progress bar */}
          <div className="mt-4 flex items-center gap-2">
            {WIZARD_STEPS.map((step, idx) => (
              <div key={step.id} className="flex items-center gap-2 flex-1">
                <div className={`h-1.5 flex-1 rounded-full transition-all ${
                  idx <= currentStepIndex ? "bg-white" : "bg-white/30"
                }`} />
              </div>
            ))}
          </div>
        </div>

        {/* Content */}
        <div className="p-6 md:p-10 min-h-[400px] flex-1 overflow-y-auto">
          <div className="max-w-5xl mx-auto">
          {/* Welcome Step */}
          {currentStep === "welcome" && (
            <div className="text-center py-8">
              <div className="w-20 h-20 mx-auto mb-6 rounded-full bg-primary-fixed flex items-center justify-center">
                <span className="material-symbols-outlined text-[48px] text-primary">waving_hand</span>
              </div>
              <h3 className="text-headline-md font-bold text-on-surface mb-2">Chào mừng bạn!</h3>
              <p className="text-on-surface-variant max-w-md mx-auto mb-8">
                Trình hướng dẫn này sẽ giúp bạn xếp lịch tự động một cách dễ dàng, 
                ngay cả khi bạn chưa có kinh nghiệm.
              </p>
              
              <div className="grid grid-cols-3 gap-4 max-w-xl mx-auto mb-8">
                <div className="p-4 rounded-xl bg-surface-container-low">
                  <span className="material-symbols-outlined text-[32px] text-primary mb-2 block">event</span>
                  <p className="text-[12px] font-medium text-on-surface">Chọn kỳ lịch</p>
                </div>
                <div className="p-4 rounded-xl bg-surface-container-low">
                  <span className="material-symbols-outlined text-[32px] text-secondary mb-2 block">psychology</span>
                  <p className="text-[12px] font-medium text-on-surface">Chạy thuật toán</p>
                </div>
                <div className="p-4 rounded-xl bg-surface-container-low">
                  <span className="material-symbols-outlined text-[32px] text-tertiary mb-2 block">check_circle</span>
                  <p className="text-[12px] font-medium text-on-surface">Áp dụng lịch</p>
                </div>
              </div>

              <div className={`p-4 rounded-xl border transition-all ${
                showTips ? "bg-blue-50 border-blue-200" : "bg-surface-container-low border-outline-variant"
              }`}>
                <button
                  onClick={() => setShowTips(!showTips)}
                  className="flex items-center gap-2 text-blue-700 mb-2"
                >
                  <span className="material-symbols-outlined text-[16px]">{randomTip.icon}</span>
                  <span className="text-[12px] font-medium">Mẹo hữu ích</span>
                  <span className="material-symbols-outlined text-[14px] ml-auto">{showTips ? "expand_less" : "expand_more"}</span>
                </button>
                {showTips && (
                  <p className="text-[11px] text-blue-800 leading-relaxed text-left">
                    {randomTip.text}
                  </p>
                )}
              </div>
            </div>
          )}

          {/* Period Selection Step */}
          {currentStep === "period" && (
            <div className="py-4">
              <h3 className="text-title-lg font-bold text-on-surface mb-1">Chọn kỳ xếp lịch</h3>
              <p className="text-[12px] text-on-surface-variant mb-6">
                Chọn kỳ lịch bạn muốn xếp tự động
              </p>

              <div className="space-y-3 max-w-xl">
                {periods.map(period => {
                  const isSelected = selectedPeriodId === period.id;
                  const periodIsDraft = period.status === "DRAFT";
                  return (
                    <button
                      key={period.id}
                      onClick={() => setSelectedPeriodId(period.id!)}
                      className={`w-full p-4 rounded-xl border-2 text-left transition-all ${
                        isSelected
                          ? "border-primary bg-primary-fixed/50 shadow-sm"
                          : "border-outline-variant hover:border-primary/40"
                      }`}
                    >
                      <div className="flex items-center justify-between">
                        <div>
                          <p className="font-semibold text-on-surface">{period.periodName}</p>
                          <p className="text-[11px] text-on-surface-variant mt-1">
                            {period.startDate && period.endDate && (
                              <>
                                {new Date(period.startDate).toLocaleDateString("vi-VN")} → {new Date(period.endDate).toLocaleDateString("vi-VN")}
                              </>
                            )}
                          </p>
                        </div>
                        <Badge tone={periodIsDraft ? "info" : "success"} size="sm">
                          {periodIsDraft ? "Nháp" : "Đã công bố"}
                        </Badge>
                      </div>
                    </button>
                  );
                })}
              </div>

              {!isDraft && selectedPeriod && (
                <div className="mt-4 p-4 rounded-xl bg-amber-50 border border-amber-200">
                  <div className="flex items-start gap-3">
                    <span className="material-symbols-outlined text-amber-600">warning</span>
                    <div>
                      <p className="font-medium text-amber-800 text-[12px]">Kỳ lịch đã công bố</p>
                      <p className="text-[11px] text-amber-700 mt-1">
                        Chỉ kỳ ở trạng thái DRAFT mới có thể xếp lịch. 
                        Bạn cần tạo kỳ mới hoặc chuyển kỳ này về DRAFT.
                      </p>
                    </div>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Config Check Step */}
          {currentStep === "config-check" && (
            <div className="py-4">
              <h3 className="text-title-lg font-bold text-on-surface mb-1">Kiểm tra cấu hình</h3>
              <p className="text-[12px] text-on-surface-variant mb-6">
                Đảm bảo hệ thống đã sẵn sàng để xếp lịch
              </p>

              <div className="space-y-3 max-w-xl">
                <div className={`p-4 rounded-xl border flex items-start gap-3 ${
                  autoGenEnabled === true ? "bg-green-50 border-green-200" : "bg-amber-50 border-amber-200"
                }`}>
                  <span className={`material-symbols-outlined ${
                    autoGenEnabled === true ? "text-green-600" : "text-amber-600"
                  }`}>
                    {autoGenEnabled === true ? "check_circle" : "warning"}
                  </span>
                  <div>
                    <p className={`font-medium text-[12px] ${
                      autoGenEnabled === true ? "text-green-800" : "text-amber-800"
                    }`}>
                      Tự động tạo yêu cầu ca trực
                    </p>
                    <p className="text-[11px] text-on-surface-variant mt-1">
                      {autoGenEnabled === true ? "Đã bật ✓" : "Đang bị tắt - cần bật trong Cấu hình thuật toán"}
                    </p>
                  </div>
                </div>

                <div className={`p-4 rounded-xl border flex items-start gap-3 ${
                  activeStaff.length >= 5 ? "bg-green-50 border-green-200" : "bg-amber-50 border-amber-200"
                }`}>
                  <span className={`material-symbols-outlined ${
                    activeStaff.length >= 5 ? "text-green-600" : "text-amber-600"
                  }`}>
                    {activeStaff.length >= 5 ? "check_circle" : "warning"}
                  </span>
                  <div>
                    <p className={`font-medium text-[12px] ${
                      activeStaff.length >= 5 ? "text-green-800" : "text-amber-800"
                    }`}>
                      Nhân sự hoạt động: {activeStaff.length} người
                    </p>
                    <p className="text-[11px] text-on-surface-variant mt-1">
                      {activeStaff.length >= 5 ? "Đủ điều kiện xếp lịch ✓" : "Cần ít nhất 5 người để xếp lịch hiệu quả"}
                    </p>
                  </div>
                </div>

                <div className={`p-4 rounded-xl border flex items-start gap-3 ${
                  configIssues.length === 0 ? "bg-green-50 border-green-200" : "bg-amber-50 border-amber-200"
                }`}>
                  <span className={`material-symbols-outlined ${
                    configIssues.length === 0 ? "text-green-600" : "text-amber-600"
                  }`}>
                    {configIssues.length === 0 ? "check_circle" : "warning"}
                  </span>
                  <div>
                    <p className={`font-medium text-[12px] ${
                      configIssues.length === 0 ? "text-green-800" : "text-amber-800"
                    }`}>
                      Cấu hình thuật toán
                    </p>
                    <p className="text-[11px] text-on-surface-variant mt-1">
                      {configIssues.length === 0 ? "Tất cả đã sẵn sàng ✓" : configIssues.join(". ")}
                    </p>
                  </div>
                </div>
              </div>

              {configIssues.length > 0 && (
                <div className="mt-4 p-4 rounded-xl bg-surface-container-low">
                  <p className="text-[12px] font-medium text-on-surface mb-2">Bạn có thể:</p>
                  <ul className="text-[11px] text-on-surface-variant space-y-1">
                    <li>• <a href="/auto-scheduling/algorithm-config" className="text-primary hover:underline">Mở Cấu hình thuật toán</a> để kiểm tra</li>
                    <li>• Bỏ qua bước này và tiếp tục (thuật toán có thể vẫn chạy được)</li>
                  </ul>
                </div>
              )}
            </div>
          )}

          {/* Exclusions Step */}
          {currentStep === "exclusions" && (
            <div className="py-4">
              <h3 className="text-title-lg font-bold text-on-surface mb-1">Ngoại lệ nhân sự</h3>
              <p className="text-[12px] text-on-surface-variant mb-2">
                Chọn nhân sự muốn loại trừ khỏi lịch tự động lần này
              </p>
              <p className="text-[11px] text-on-surface-variant mb-6">
                Những người này sẽ không được xếp ca trong kỳ này
              </p>

              <div className="flex items-center justify-between mb-3 px-1">
                <span className="text-[11px] text-on-surface-variant">
                  {activeStaff.length - excludedStaffIds.length} / {activeStaff.length} nhân sự tham gia
                </span>
                <button
                  onClick={() => setExcludedStaffIds([])}
                  className="text-[11px] text-primary hover:underline"
                >
                  Chọn tất cả tham gia
                </button>
              </div>

              <div className="max-h-64 overflow-y-auto space-y-2 border border-outline-variant rounded-xl p-3">
                {activeStaff.map(staff => {
                  const isExcluded = excludedStaffIds.includes(staff.id!);
                  return (
                    <label
                      key={staff.id}
                      className={`flex items-center gap-3 p-3 rounded-lg cursor-pointer transition-colors ${
                        isExcluded ? "bg-red-50" : "hover:bg-surface-container-low"
                      }`}
                    >
                      <input
                        type="checkbox"
                        checked={isExcluded}
                        onChange={() => {
                          setExcludedStaffIds(prev =>
                            isExcluded
                              ? prev.filter(id => id !== staff.id)
                              : [...prev, staff.id!]
                          );
                        }}
                        className="h-4 w-4 rounded border-outline-variant text-primary"
                      />
                      <div className="flex-1">
                        <p className={`text-[12px] font-medium ${isExcluded ? "text-red-700" : "text-on-surface"}`}>
                          {staff.fullName}
                        </p>
                        <p className="text-[10px] text-on-surface-variant">{staff.specialty?.name}</p>
                      </div>
                      {isExcluded && (
                        <Badge tone="error" size="sm">Loại trừ</Badge>
                      )}
                    </label>
                  );
                })}
              </div>
            </div>
          )}

          {/* Running Step */}
          {currentStep === "running" && (
            <div className="py-8 text-center">
              <div className="w-20 h-20 mx-auto mb-6 rounded-full bg-primary-fixed flex items-center justify-center animate-pulse">
                <span className="material-symbols-outlined text-[48px] text-primary">sync</span>
              </div>
              <h3 className="text-headline-md font-bold text-on-surface mb-2">Đang chạy thuật toán...</h3>
              <p className="text-on-surface-variant mb-6">
                Vui lòng đợi trong khi hệ thống phân bổ ca trực
              </p>

              <div className="max-w-md mx-auto">
                <div className="w-full bg-surface-container-low rounded-full h-2 overflow-hidden mb-3">
                  <div
                    className="h-full bg-primary rounded-full transition-all duration-500"
                    style={{ width: `${progress.percent || 10}%` }}
                  />
                </div>
                <p className="text-[12px] text-on-surface-variant">
                  {progress.step || progress.message || "Đang xử lý..."} ({progress.percent || 0}%)
                </p>
              </div>

              {error && (
                <div className="mt-6 p-4 rounded-xl bg-error-container text-error text-left max-w-md mx-auto">
                  <p className="font-medium text-[12px]">Đã xảy ra lỗi</p>
                  <p className="text-[11px] mt-1">{error}</p>
                </div>
              )}
            </div>
          )}

          {/* Results Step */}
          {currentStep === "results" && autoPreview && (
            <div className="py-4">
              <h3 className="text-title-lg font-bold text-on-surface mb-1">Kết quả xếp lịch</h3>
              <p className="text-[12px] text-on-surface-variant mb-6">
                Kiểm tra kết quả trước khi áp dụng
              </p>

              <div className="grid grid-cols-4 gap-3 max-w-xl mb-6">
                <div className="p-4 rounded-xl bg-green-50 border border-green-200 text-center">
                  <p className="text-[24px] font-bold text-green-700">{autoPreview.totalSchedulesCreated}</p>
                  <p className="text-[10px] text-green-600">Ca tạo</p>
                </div>
                <div className="p-4 rounded-xl bg-blue-50 border border-blue-200 text-center">
                  <p className="text-[24px] font-bold text-blue-700">
                    {Math.min(Math.round(parseFloat(String(autoPreview.coverageRate)) || 0), 100)}%
                  </p>
                  <p className="text-[10px] text-blue-600">Tỷ lệ phủ</p>
                </div>
                <div className="p-4 rounded-xl bg-purple-50 border border-purple-200 text-center">
                  <p className="text-[24px] font-bold text-purple-700">
                    {Math.round(parseFloat(String(autoPreview.balanceScore)) || 0)}%
                  </p>
                  <p className="text-[10px] text-purple-600">Cân bằng</p>
                </div>
                <div className="p-4 rounded-xl bg-amber-50 border border-amber-200 text-center">
                  <p className="text-[24px] font-bold text-amber-700">{autoPreview.conflictCount}</p>
                  <p className="text-[10px] text-amber-600">Xung đột</p>
                </div>
              </div>

              {autoPreview.conflictCount > 0 && (
                <div className="p-4 rounded-xl bg-amber-50 border border-amber-200 mb-4">
                  <div className="flex items-start gap-2">
                    <span className="material-symbols-outlined text-amber-600">warning</span>
                    <p className="text-[11px] text-amber-800">
                      Có {autoPreview.conflictCount} xung đột được phát hiện. Hệ thống sẽ cố gắng giải quyết.
                    </p>
                  </div>
                </div>
              )}

              {((autoPreview.unassignedDays?.length ?? 0) > 0) && (
                <div className="p-4 rounded-xl bg-surface-container-low border border-outline-variant">
                  <p className="text-[12px] font-medium text-on-surface mb-2">
                    Ngày thiếu nhân sự: {autoPreview.unassignedDays?.length ?? 0}
                  </p>
                  <p className="text-[11px] text-on-surface-variant">
                    Một số ca chưa được phân bổ đủ. Có thể cần thêm nhân sự hoặc điều chỉnh cấu hình.
                  </p>
                </div>
              )}

              <div className="p-4 rounded-xl bg-green-50 border border-green-200 mt-4">
                <div className="flex items-start gap-2">
                  <span className="material-symbols-outlined text-green-600">check_circle</span>
                  <p className="text-[11px] text-green-800">
                    Thuật toán đã hoàn thành. Nhấn &quot;Tiếp tục&quot; để xem và áp dụng kết quả.
                  </p>
                </div>
              </div>
            </div>
          )}

          {/* Apply Step */}
          {currentStep === "apply" && (
            <div className="py-4">
              <h3 className="text-title-lg font-bold text-on-surface mb-1">Xác nhận áp dụng</h3>
              <p className="text-[12px] text-on-surface-variant mb-6">
                Xác nhận để lưu lịch trực vào hệ thống
              </p>

              <div className="p-4 rounded-xl bg-surface-container-low border border-outline-variant mb-6">
                <div className="space-y-2 text-[12px]">
                  <div className="flex justify-between">
                    <span className="text-on-surface-variant">Kỳ lịch:</span>
                    <span className="font-medium text-on-surface">{selectedPeriod?.periodName}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-on-surface-variant">Tổng ca tạo:</span>
                    <span className="font-medium text-on-surface">{autoPreview?.totalSchedulesCreated || 0}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-on-surface-variant">Nhân sự loại trừ:</span>
                    <span className="font-medium text-on-surface">{excludedStaffIds.length}</span>
                  </div>
                </div>
              </div>

              <div className="p-4 rounded-xl bg-blue-50 border border-blue-200">
                <div className="flex items-start gap-2">
                  <span className="material-symbols-outlined text-blue-600">info</span>
                  <p className="text-[11px] text-blue-800">
                    Sau khi áp dụng, bạn vẫn có thể chỉnh sửa lịch thủ công trong trang Lịch trực.
                  </p>
                </div>
              </div>
            </div>
          )}
          </div>
        </div>

        {/* Footer */}
        <div className="px-6 py-4 border-t border-outline-variant bg-surface-container-low flex items-center justify-between shrink-0">
          <Button
            variant="ghost"
            size="sm"
            onClick={currentStep === "welcome" ? onSkip : handlePrev}
          >
            {currentStep === "welcome" ? "Bỏ qua" : "← Quay lại"}
          </Button>

          <div className="flex items-center gap-2">
            <span className="text-[11px] text-on-surface-variant">
              Bước {currentStepIndex + 1} / {WIZARD_STEPS.length}
            </span>
            
            {currentStep === "welcome" || currentStep === "period" || currentStep === "config-check" || currentStep === "exclusions" ? (
              <Button
                variant="primary"
                size="sm"
                onClick={handleNext}
                disabled={currentStep === "period" && (!selectedPeriodId || !isDraft)}
              >
                Tiếp tục →
              </Button>
            ) : currentStep === "results" ? (
              <Button
                variant="primary"
                size="sm"
                onClick={handleNext}
              >
                Tiếp tục →
              </Button>
            ) : currentStep === "apply" ? (
              <Button
                variant="primary"
                size="sm"
                onClick={handleApply}
                loading={loading}
              >
                Áp dụng lịch
              </Button>
            ) : null}
          </div>
        </div>
      </div>
  );
}

// Helper to parse number
function parseFloat(val: string | number): number {
  if (typeof val === "number") return val;
  const parsed = parseFloat(val);
  return isNaN(parsed) ? 0 : parsed;
}

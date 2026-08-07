"use client";

import { Modal, ModalFooter } from "@/components/ui/Modal";
import { Button } from "@/components/ui";
import { EmptyState } from "@/components/ui/EmptyState";
import { formatDate } from "@/lib/date";
import type { ScheduleTemplate, TemplatePreviewItem, Staff } from "@/types/api";

interface Props {
  open: boolean;
  templates: ScheduleTemplate[];
  loadingTemplates: boolean;
  selectedTemplateId: number | null;
  selectedTemplate: ScheduleTemplate | null;
  templatePreview: TemplatePreviewItem[] | null;
  previewLoading: boolean;
  applying?: boolean;
  editingStaffIds: Map<string | number, number>;
  activeStaff: Staff[];
  onClose: () => void;
  onPreview: (templateId: number) => void;
  onApply: () => void;
  onSelectTemplate: (templateId: number) => void;
  onStaffEdit: (slotId: string | number, staffId: number) => void;
  onClearSelection: () => void;
}

export function ApplyTemplateModal({
  open,
  templates,
  loadingTemplates,
  selectedTemplateId,
  selectedTemplate,
  templatePreview,
  previewLoading,
  applying = false,
  editingStaffIds,
  activeStaff,
  onClose,
  onPreview,
  onApply,
  onSelectTemplate,
  onStaffEdit,
  onClearSelection,
}: Props) {
  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Áp dụng mẫu lịch"
      description="Chọn mẫu lịch đã lưu để xem trước và chỉnh sửa trước khi áp dụng cho kỳ hiện tại."
    >
      {!selectedTemplateId ? (
        <div className="space-y-3">
          {loadingTemplates ? (
            <p className="text-label-sm text-on-surface-variant text-center py-6">Đang tải mẫu lịch...</p>
          ) : templates.length === 0 ? (
            <EmptyState
              size="compact"
              icon="bookmarks"
              title="Chưa có mẫu lịch nào"
              description="Chạy auto schedule trước rồi lưu mẫu để có thể áp dụng lại sau."
            />
          ) : (
            <div className="space-y-2 max-h-64 overflow-y-auto">
              {templates.map((t) => (
                <div
                  key={t.id}
                  className="flex items-center justify-between gap-3 p-3 rounded-lg border border-outline-variant bg-surface-container-lowest hover:bg-surface-container-low transition-colors"
                >
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <p className="text-label-md font-semibold text-on-surface truncate">{t.name}</p>
                      {t.templateType === "GENERATED" && (
                        <span className="shrink-0 inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-tertiary-fixed text-tertiary text-[11px] font-bold">
                          <span className="material-symbols-outlined text-[10px]">auto_awesome</span>
                          GENERATED
                        </span>
                      )}
                      {t.templateType === "PATTERN" && (
                        <span className="shrink-0 inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-primary-fixed text-primary text-[11px] font-bold">
                          <span className="material-symbols-outlined text-[10px]">tune</span>
                          PATTERN
                        </span>
                      )}
                    </div>
                    {t.description && (
                      <p className="text-label-sm text-on-surface-variant truncate">{t.description}</p>
                    )}
                    <p className="text-[11px] text-outline mt-0.5">
                      {new Date(t.createdAt).toLocaleDateString("vi-VN")}
                      {t.shiftTypeId ? " · " + t.shiftTypeId + (t.specialtyName ? " · " + t.specialtyName : "") : t.templateType === "GENERATED" ? "" : ""}
                      {t.shiftTypeId ? " · " + (t.requiredStaffCount ?? 1) + " người/ca" : ""}
                    </p>
                  </div>
                  <div className="flex items-center gap-2 shrink-0">
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => onPreview(t.id)}
                      disabled={previewLoading}
                      icon={<span className="material-symbols-outlined text-[14px]">visibility</span>}
                    >
                      Xem trước
                    </Button>
                    <Button
                      variant="primary"
                      size="sm"
                      onClick={() => onSelectTemplate(t.id)}
                      disabled={previewLoading}
                      icon={<span className="material-symbols-outlined text-[14px]">check</span>}
                    >
                      Áp dụng
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      ) : previewLoading ? (
        <div className="py-8 text-center text-label-sm text-on-surface-variant">
          <div className="inline-block size-6 animate-spin rounded-full border-2 border-primary border-t-transparent mr-2" />
          Đang tải bản xem trước...
        </div>
      ) : templatePreview === null ? (
        <div className="space-y-3">
          <p className="text-label-sm text-on-surface-variant">Mẫu lịch này không có dữ liệu để xem trước.</p>
          <Button
            variant="primary"
            size="md"
            onClick={onApply}
            disabled={previewLoading || applying}
            loading={applying}
            icon={!applying ? <span className="material-symbols-outlined text-[14px]">check</span> : undefined}
          >
            Áp dụng trực tiếp
          </Button>
        </div>
      ) : (
        <div className="space-y-3">
          {selectedTemplate?.templateType === "GENERATED" ? (
            <>
              <p className="text-label-sm text-on-surface-variant">
                Mẫu này sẽ tạo <strong className="text-on-surface">{templatePreview.length}</strong> ca trực trong kỳ.
                Bạn có thể sửa nhân sự được phân công cho từng ca trước khi xác nhận.
              </p>
              <div className="overflow-x-auto max-h-80">
                <table className="w-full text-left border-collapse" aria-label="Applytemplatemodal Table">
                  <thead className="sticky top-0 bg-surface-container-low border-b border-outline-variant">
                    <tr>
                      <th scope="col" className="p-2.5 text-label-xs text-on-surface-variant uppercase">Ngày</th>
                      <th scope="col" className="p-2.5 text-label-xs text-on-surface-variant uppercase">Thứ</th>
                      <th scope="col" className="p-2.5 text-label-xs text-on-surface-variant uppercase">Loại ca</th>
                      <th scope="col" className="p-2.5 text-label-xs text-on-surface-variant uppercase">Chuyên khoa</th>
                      <th scope="col" className="p-2.5 text-label-xs text-on-surface-variant uppercase">Số người</th>
                      <th scope="col" className="p-2.5 text-label-xs text-on-surface-variant uppercase">Nhân sự phân công</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-variant/30">
                    {templatePreview.map((item) => {
                      const slotKey = item.id > 0 ? item.id : `${item.workDate}_${item.shiftTypeId}`;
                      const currentStaffId = editingStaffIds.get(slotKey) ?? item.assignedStaffId ?? 0;
                      return (
                        <tr key={slotKey} className="hover:bg-surface-container-low/50 transition-colors">
                          <td className="p-2.5 text-label-sm text-on-surface">{formatDate(item.workDate)}</td>
                          <td className="p-2.5 text-label-sm text-on-surface-variant">{item.dayOfWeek}</td>
                          <td className="p-2.5 text-label-sm text-on-surface-variant">{item.shiftTypeName}</td>
                          <td className="p-2.5 text-label-sm text-on-surface-variant">{item.specialtyName ?? "—"}</td>
                          <td className="p-2.5 text-label-sm text-on-surface-variant text-center">{item.requiredStaffCount}</td>
                          <td className="p-2.5">
                            <div className="relative">
                              <select
                                className="h-8 w-full appearance-none rounded-md border border-outline-variant bg-surface-container-lowest px-2 pr-7 text-label-sm text-on-surface outline-none transition-colors focus:border-primary focus:ring-1 focus:ring-primary/20"
                                value={currentStaffId}
                                onChange={(e) => onStaffEdit(slotKey, Number(e.target.value))}
                              >
                                <option value={0}>-- Chưa phân công --</option>
                                {activeStaff.map((s) => (
                                  <option key={s.id} value={s.id}>{s.fullName}</option>
                                ))}
                              </select>
                              <span className="material-symbols-outlined pointer-events-none absolute right-1 top-1/2 -translate-y-1/2 text-outline text-[14px]">expand_more</span>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
              <p className="text-label-xs text-on-surface-variant italic">
                Lưu ý: Việc phân công nhân sự ở đây chỉ là tham khảo. Sau khi áp dụng, hệ thống sẽ tự động tạo lịch trực theo mẫu.
              </p>
            </>
          ) : (
            <>
              <p className="text-label-sm text-on-surface-variant">
                Mẫu PATTERN sẽ tạo <strong className="text-on-surface">{templatePreview.length}</strong> yêu cầu nhân sự trong kỳ.
                Hệ thống sẽ tự động phân công nhân sự phù hợp khi áp dụng.
              </p>
              <div className="overflow-x-auto max-h-80">
                <table className="w-full text-left border-collapse" aria-label="Applytemplatemodal Table">
                  <thead className="sticky top-0 bg-surface-container-low border-b border-outline-variant">
                    <tr>
                      <th scope="col" className="p-2.5 text-label-xs text-on-surface-variant uppercase">Ngày</th>
                      <th scope="col" className="p-2.5 text-label-xs text-on-surface-variant uppercase">Thứ</th>
                      <th scope="col" className="p-2.5 text-label-xs text-on-surface-variant uppercase">Loại ca</th>
                      <th scope="col" className="p-2.5 text-label-xs text-on-surface-variant uppercase">Chuyên khoa</th>
                      <th scope="col" className="p-2.5 text-label-xs text-on-surface-variant uppercase">Số người</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-variant/30">
                    {templatePreview.map((item, idx) => (
                      <tr key={`${item.workDate}_${item.shiftTypeId}_${idx}`} className="hover:bg-surface-container-low/50 transition-colors">
                        <td className="p-2.5 text-label-sm text-on-surface">{formatDate(item.workDate)}</td>
                        <td className="p-2.5 text-label-sm text-on-surface-variant">{item.dayOfWeek}</td>
                        <td className="p-2.5 text-label-sm text-on-surface-variant">{item.shiftTypeName}</td>
                        <td className="p-2.5 text-label-sm text-on-surface-variant">{item.specialtyName ?? "—"}</td>
                        <td className="p-2.5 text-label-sm text-on-surface-variant text-center">{item.requiredStaffCount}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </div>
      )}
      <ModalFooter>
        {selectedTemplateId && templatePreview && (
          <>
            <Button
              variant="secondary"
              size="md"
              onClick={onClearSelection}
            >
              ← Quay lại danh sách
            </Button>
            <Button
              variant="primary"
              size="md"
              onClick={onApply}
              disabled={previewLoading || applying}
              loading={applying}
              icon={!applying ? <span className="material-symbols-outlined text-[16px]">check</span> : undefined}
            >
              {applying ? "Đang áp dụng..." : "Xác nhận áp dụng"}
            </Button>
          </>
        )}
        {!selectedTemplateId && (
          <Button
            variant="secondary"
            size="md"
            onClick={onClose}
          >
            Đóng
          </Button>
        )}
      </ModalFooter>
    </Modal>
  );
}

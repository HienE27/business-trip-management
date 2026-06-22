"use client";

import { Modal, ModalFooter } from "@/components/ui/Modal";
import { EmptyState } from "@/components/ui/EmptyState";
import { formatDate } from "@/lib/date";
import type { ScheduleTemplate, TemplatePreviewItem, Staff } from "@/types/api";

interface Props {
  open: boolean;
  templates: ScheduleTemplate[];
  loadingTemplates: boolean;
  selectedTemplateId: number | null;
  templatePreview: TemplatePreviewItem[] | null;
  previewLoading: boolean;
  editingStaffIds: Map<number, number>;
  activeStaff: Staff[];
  onClose: () => void;
  onPreview: (templateId: number) => void;
  onApply: () => void;
  onStaffEdit: (slotId: number, staffId: number) => void;
  onClearSelection: () => void;
}

export function ApplyTemplateModal({
  open,
  templates,
  loadingTemplates,
  selectedTemplateId,
  templatePreview,
  previewLoading,
  editingStaffIds,
  activeStaff,
  onClose,
  onPreview,
  onApply,
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
                    <button
                      type="button"
                      onClick={() => onPreview(t.id)}
                      disabled={previewLoading}
                      className="inline-flex items-center gap-1.5 rounded-lg border border-outline-variant bg-surface px-3 py-1.5 text-label-sm font-medium text-on-surface hover:bg-surface-container-low transition-colors disabled:opacity-50"
                    >
                      <span className="material-symbols-outlined text-[14px]">visibility</span>
                      Xem trước
                    </button>
                    <button
                      type="button"
                      onClick={onApply}
                      className="shrink-0 inline-flex items-center gap-1.5 rounded-lg bg-primary px-3 py-1.5 text-label-sm font-medium text-on-primary hover:bg-primary/90 transition-colors"
                    >
                      <span className="material-symbols-outlined text-[14px]">check</span>
                      Áp dụng
                    </button>
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
          <button
            type="button"
            onClick={onApply}
            className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2 text-label-sm font-semibold text-on-primary hover:bg-primary/90 transition-colors"
          >
            <span className="material-symbols-outlined text-[14px]">check</span>
            Áp dụng trực tiếp
          </button>
        </div>
      ) : (
        <div className="space-y-3">
          <p className="text-label-sm text-on-surface-variant">
            Mẫu này sẽ tạo <strong className="text-on-surface">{templatePreview.length}</strong> yêu cầu nhân sự trong kỳ.
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
                  const currentStaffId = editingStaffIds.get(item.id) ?? item.assignedStaffId ?? 0;
                  return (
                    <tr key={item.id} className="hover:bg-surface-container-low/50 transition-colors">
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
                            onChange={(e) => onStaffEdit(item.id, Number(e.target.value))}
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
            Lưu ý: Việc phân công nhân sự ở đây chỉ là tham khảo. Sau khi áp dụng, hệ thống sẽ tự động tạo yêu cầu nhân sự theo mẫu.
          </p>
        </div>
      )}
      <ModalFooter>
        {selectedTemplateId && templatePreview && (
          <>
            <button
              type="button"
              onClick={onClearSelection}
              className="px-4 py-2 rounded-lg border border-outline-variant text-label-md text-on-surface hover:bg-surface-container-low transition-colors"
            >
              ← Quay lại danh sách
            </button>
            <button
              type="button"
              onClick={onApply}
              disabled={previewLoading}
              className="inline-flex items-center gap-2 px-5 py-2 rounded-lg bg-primary text-label-md font-semibold text-on-primary hover:bg-primary/90 disabled:opacity-50 transition-colors"
            >
              <span className="material-symbols-outlined text-[16px]">check</span>
              Xác nhận áp dụng
            </button>
          </>
        )}
        {!selectedTemplateId && (
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 rounded-lg border border-outline-variant text-label-md text-on-surface hover:bg-surface-container-low transition-colors"
          >
            Đóng
          </button>
        )}
      </ModalFooter>
    </Modal>
  );
}

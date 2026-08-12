"use client";

import { useCallback, useState } from "react";
import { Modal, ModalFooter } from "@/components/ui/Modal";
import { Button } from "@/components/ui";
import { api } from "@/lib/api-client";
import type {
  ConfigProfile,
  ConfigProfileCategory,
  CreateProfileRequest,
} from "@/types/api";
import { useToast } from "@/hooks/useToast";

interface CreateProfileDialogProps {
  open: boolean;
  onClose: () => void;
  onCreated?: (profile: ConfigProfile) => void;
}

const CATEGORIES: Array<{ value: ConfigProfileCategory; label: string }> = [
  { value: "GENERAL", label: "Tổng quát" },
  { value: "ALGORITHM", label: "Thuật toán" },
  { value: "FAIRNESS", label: "Công bằng" },
  { value: "COVERAGE", label: "Phủ sóng" },
  { value: "EMERGENCY", label: "Khẩn cấp" },
  { value: "HOLIDAY", label: "Ngày nghỉ" },
  { value: "TESTING", label: "Thử nghiệm" },
];

const ICONS = [
  { value: "tune", label: "Cài đặt" },
  { value: "speed", label: "Tốc độ" },
  { value: "balance", label: "Cân bằng" },
  { value: "verified_user", label: "Xác minh" },
  { value: "groups", label: "Nhóm" },
  { value: "emergency", label: "Khẩn cấp" },
  { value: "celebration", label: "Lễ" },
  { value: "science", label: "Khoa học" },
  { value: "auto_mode", label: "Tự động" },
];

export function CreateProfileDialog({ open, onClose, onCreated }: CreateProfileDialogProps) {
  const { success, error } = useToast();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [form, setForm] = useState<CreateProfileRequest>({
    nameVi: "",
    nameEn: "",
    description: "",
    category: "GENERAL",
    icon: "tune",
    tags: [],
  });
  const [tagInput, setTagInput] = useState("");

  const handleSubmit = useCallback(async () => {
    if (!form.nameVi.trim()) {
      error("Vui lòng nhập tên cấu hình");
      return;
    }

    setIsSubmitting(true);
    try {
      const created = await api.createConfigProfile(form);
      success(`Đã tạo cấu hình "${created.nameVi}"`);
      onCreated?.(created);
      handleClose();
    } catch (err) {
      error(`Lỗi khi tạo: ${err}`);
    } finally {
      setIsSubmitting(false);
    }
  }, [form, success, error, onCreated]);

  const handleClose = () => {
    setForm({
      nameVi: "",
      nameEn: "",
      description: "",
      category: "GENERAL",
      icon: "tune",
      tags: [],
    });
    setTagInput("");
    onClose();
  };

  const addTag = (tag: string) => {
    if (tag && !form.tags?.includes(tag)) {
      setForm((f) => ({ ...f, tags: [...(f.tags || []), tag] }));
    }
    setTagInput("");
  };

  const removeTag = (tag: string) => {
    setForm((f) => ({ ...f, tags: f.tags?.filter((t) => t !== tag) || [] }));
  };

  if (!open) return null;

  return (
    <Modal
      open={open}
      onClose={handleClose}
      title="Tạo cấu hình mới"
      description="Lưu cấu hình hiện tại thành profile mới"
      size="md"
    >
      {/* Form */}
      <div className="space-y-4">
          {/* Name Vi */}
          <div>
            <label className="block text-label-md text-on-surface mb-1.5">
              Tên cấu hình <span className="text-red-800">*</span>
            </label>
            <input
              type="text"
              value={form.nameVi}
              onChange={(e) => setForm((f) => ({ ...f, nameVi: e.target.value }))}
              placeholder="VD: Cấu hình tháng 7"
              className="w-full h-10 px-3 border border-outline-variant bg-surface-container-lowest rounded-lg text-body-md text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
            />
          </div>

          {/* Name En */}
          <div>
            <label className="block text-label-md text-on-surface mb-1.5">Tên tiếng Anh</label>
            <input
              type="text"
              value={form.nameEn || ""}
              onChange={(e) => setForm((f) => ({ ...f, nameEn: e.target.value }))}
              placeholder="VD: July Configuration"
              className="w-full h-10 px-3 border border-outline-variant bg-surface-container-lowest rounded-lg text-body-md text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
            />
          </div>

          {/* Description */}
          <div>
            <label className="block text-label-md text-on-surface mb-1.5">Mô tả</label>
            <textarea
              value={form.description || ""}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              placeholder="Mô tả ngắn về cấu hình này..."
              rows={2}
              className="w-full px-3 py-2 border border-outline-variant bg-surface-container-lowest rounded-lg text-body-md text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 resize-none"
            />
          </div>

          {/* Category */}
          <div>
            <label className="block text-label-md text-on-surface mb-1.5">Danh mục</label>
            <div className="grid grid-cols-4 gap-2">
              {CATEGORIES.map((cat) => (
                <button
                  key={cat.value}
                  type="button"
                  onClick={() => setForm((f) => ({ ...f, category: cat.value }))}
                  className={`
                    px-3 py-2 rounded-lg text-label-sm font-medium border transition-colors
                    ${form.category === cat.value
                      ? "bg-blue-100 text-blue-800 border-primary"
                      : "bg-surface-container-lowest text-on-surface border-outline-variant hover:border-primary"
                    }
                  `}
                >
                  {cat.label}
                </button>
              ))}
            </div>
          </div>

          {/* Icon */}
          <div>
            <label className="block text-label-md text-on-surface mb-1.5">Biểu tượng</label>
            <div className="flex flex-wrap gap-2">
              {ICONS.map((ic) => (
                <button
                  key={ic.value}
                  type="button"
                  onClick={() => setForm((f) => ({ ...f, icon: ic.value }))}
                  className={`
                    w-10 h-10 rounded-lg border flex items-center justify-center transition-colors
                    ${form.icon === ic.value
                      ? "bg-blue-100 text-blue-800 border-primary"
                      : "bg-surface-container-lowest text-on-surface-variant border-outline-variant hover:border-primary"
                    }
                  `}
                  title={ic.label}
                >
                  <span className="material-symbols-outlined text-[20px]">{ic.value}</span>
                </button>
              ))}
            </div>
          </div>

          {/* Tags */}
          <div>
            <label className="block text-label-md text-on-surface mb-1.5">Nhãn</label>
            <div className="flex flex-wrap gap-2 mb-2">
              {form.tags?.map((tag) => (
                <span
                  key={tag}
                  className="inline-flex items-center gap-1 px-2 py-1 bg-surface-container text-label-sm text-on-surface rounded-full"
                >
                  {tag}
                  <button type="button" onClick={() => removeTag(tag)} className="hover:text-red-800">
                    <span className="material-symbols-outlined text-[14px]">close</span>
                  </button>
                </span>
              ))}
            </div>
            <div className="flex gap-2">
              <input
                type="text"
                value={tagInput}
                onChange={(e) => setTagInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    addTag(tagInput.trim());
                  }
                }}
                placeholder="Nhấn Enter để thêm nhãn..."
                className="flex-1 h-10 px-3 border border-outline-variant bg-surface-container-lowest rounded-lg text-body-md text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
              />
              <Button
              variant="secondary"
              size="sm"
              onClick={() => addTag(tagInput.trim())}
              disabled={!tagInput.trim()}
            >
              Thêm
            </Button>
          </div>
        </div>
      </div>

      <ModalFooter>
        <Button
          variant="secondary"
          size="md"
          onClick={handleClose}
        >
          Hủy
        </Button>
        <Button
          variant="primary"
          size="md"
          onClick={handleSubmit}
          disabled={isSubmitting || !form.nameVi.trim()}
          loading={isSubmitting}
        >
          Tạo cấu hình
        </Button>
      </ModalFooter>
    </Modal>
  );
}

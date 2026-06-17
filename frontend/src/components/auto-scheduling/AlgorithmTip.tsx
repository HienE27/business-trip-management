"use client";

export function AlgorithmTip() {
  return (
    <div className="bg-surface p-5 border border-outline-variant flex items-start gap-3 rounded-lg">
      <span className="material-symbols-outlined text-primary shrink-0 mt-0.5" style={{ fontVariationSettings: "'FILL' 1" }}>lightbulb</span>
      <div>
        <h4 className="font-label-md font-bold mb-1">Mẹo thuật toán</h4>
        <p className="font-body-sm text-on-surface-variant">
          Thuật toán sẽ chạy mất khoảng 15-30 giây tùy thuộc vào độ phức tạp của quy tắc và số lượng nhân sự. Bạn có thể xem và chỉnh sửa bản nháp trước khi chốt lịch chính thức.
        </p>
      </div>
    </div>
  );
}

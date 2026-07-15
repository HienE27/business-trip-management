import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { ConflictPanel } from "./ConflictPanel";
import type { ConflictItem } from "@/types/schedule";

const mockConflicts: ConflictItem[] = [
  {
    id: "1",
    type: "L01",
    staffName: "Bs. Nguyễn Văn A",
    date: "2024-01-15",
    severity: "Chặn lưu",
    detail: "Trùng lịch trực 24/24 với ngày nghỉ bù",
    periodId: 1,
    workDate: "2024-01-15",
    shiftTypeId: "L01",
  },
  {
    id: "2",
    type: "L02",
    staffName: "Bs. Trần Thị B",
    date: "2024-01-16",
    severity: "Cảnh báo",
    detail: "Ca liền kề với lịch trực ngày hôm trước",
    periodId: 1,
    workDate: "2024-01-16",
    shiftTypeId: "L02",
  },
  {
    id: "3",
    type: "L03",
    staffName: "Bs. Lê Văn C",
    date: "2024-01-17",
    severity: "Chặn lưu",
    detail: "Trùng lịch phòng khám dịch vụ với nghỉ phép",
    periodId: 1,
    workDate: "2024-01-17",
    shiftTypeId: "L03",
  },
];

describe("ConflictPanel", () => {
  it("renders empty state when no conflicts", () => {
    render(<ConflictPanel conflicts={[]} />);
    expect(screen.getByText("Không có xung đột")).toBeInTheDocument();
  });

  it("renders conflict count in header", () => {
    render(<ConflictPanel conflicts={mockConflicts} />);
    expect(screen.getByText(/Cảnh báo xung đột \(3\)/)).toBeInTheDocument();
  });

  it("displays conflict items with correct staff names", () => {
    render(<ConflictPanel conflicts={mockConflicts} maxItems={5} />);
    expect(screen.getByText("Bs. Nguyễn Văn A")).toBeInTheDocument();
    expect(screen.getByText("Bs. Trần Thị B")).toBeInTheDocument();
    expect(screen.getByText("Bs. Lê Văn C")).toBeInTheDocument();
  });

  it("limits displayed conflicts to maxItems", () => {
    render(<ConflictPanel conflicts={mockConflicts} maxItems={2} />);
    // After sorting by severity (blocking first), first 2 items are blocking conflicts
    expect(screen.getByText("Bs. Nguyễn Văn A")).toBeInTheDocument();
    expect(screen.getByText("Bs. Lê Văn C")).toBeInTheDocument();
    expect(screen.queryByText("Bs. Trần Thị B")).not.toBeInTheDocument();
  });

  it("shows expand button when conflicts exceed maxItems", () => {
    render(<ConflictPanel conflicts={mockConflicts} maxItems={2} />);
    expect(screen.getByText("Xem tất cả (3)")).toBeInTheDocument();
  });

  it("expands all conflicts when expand button clicked", () => {
    render(<ConflictPanel conflicts={mockConflicts} maxItems={2} />);
    
    const expandButton = screen.getByText("Xem tất cả (3)");
    fireEvent.click(expandButton);
    
    expect(screen.getByText("Thu gọn")).toBeInTheDocument();
    expect(screen.getByText("Bs. Lê Văn C")).toBeInTheDocument();
  });

  it("collapses when collapse button clicked", () => {
    render(<ConflictPanel conflicts={mockConflicts} maxItems={2} />);
    
    // Expand first
    fireEvent.click(screen.getByText("Xem tất cả (3)"));
    expect(screen.getByText("Thu gọn")).toBeInTheDocument();
    expect(screen.getByText("Bs. Lê Văn C")).toBeInTheDocument();
    
    // Then collapse - use getAllByText for multiple matching elements
    fireEvent.click(screen.getByText("Thu gọn"));
    // After collapse, all 3 should be hidden since maxItems=2 and expanded=false
    // Bs. Lê Văn C is not in first 2 (sorted by severity), so it won't appear
    expect(screen.queryByText("Bs. Trần Thị B")).not.toBeInTheDocument();
  });

  it("displays conflict detail text", () => {
    render(<ConflictPanel conflicts={mockConflicts} maxItems={5} />);
    expect(screen.getByText(/Trùng lịch trực 24\/24/)).toBeInTheDocument();
  });

  it("displays work date", () => {
    render(<ConflictPanel conflicts={mockConflicts} maxItems={5} />);
    expect(screen.getByText("2024-01-15")).toBeInTheDocument();
  });

  it("displays severity badges correctly", () => {
    render(<ConflictPanel conflicts={mockConflicts} maxItems={5} />);
    const blockingBadges = screen.getAllByText("Chặn lưu");
    const warningBadges = screen.getAllByText("Cảnh báo");
    expect(blockingBadges).toHaveLength(2);
    expect(warningBadges).toHaveLength(1);
  });

  it("calls onResolve when resolve button clicked", () => {
    const handleResolve = vi.fn();
    render(
      <ConflictPanel
        conflicts={[mockConflicts[0]]} // Only 1 blocking conflict
        onResolve={handleResolve}
      />
    );
    
    // Find and click the first "Xử lý" button
    fireEvent.click(screen.getByText("Xử lý"));
    
    expect(handleResolve).toHaveBeenCalledWith(mockConflicts[0]);
  });

  it("calls onRemove when remove button clicked", () => {
    const handleRemove = vi.fn();
    render(
      <ConflictPanel
        conflicts={[mockConflicts[0]]} // Only 1 blocking conflict
        onRemove={handleRemove}
      />
    );
    
    fireEvent.click(screen.getByText("Xóa lịch"));
    
    expect(handleRemove).toHaveBeenCalledWith(mockConflicts[0]);
  });

  it("calls onSwap when swap button clicked", () => {
    const handleSwap = vi.fn();
    render(
      <ConflictPanel
        conflicts={[mockConflicts[0]]} // Only 1 blocking conflict
        onSwap={handleSwap}
      />
    );
    
    fireEvent.click(screen.getByText("Đổi ca"));
    
    expect(handleSwap).toHaveBeenCalledWith(mockConflicts[0]);
  });

  it("does not show action buttons for non-blocking conflicts", () => {
    render(
      <ConflictPanel
        conflicts={[mockConflicts[1]]} // Only warning conflict
        maxItems={5}
      />
    );
    
    // Warning conflicts should not have action buttons
    expect(screen.queryByText("Xóa lịch")).not.toBeInTheDocument();
    expect(screen.queryByText("Đổi ca")).not.toBeInTheDocument();
    expect(screen.queryByText("Xử lý")).not.toBeInTheDocument();
  });

  it("handles conflict without detail gracefully", () => {
    const conflictWithoutDetail: ConflictItem = {
      id: "4",
      type: "L01",
      staffName: "Bs. Test",
      date: "2024-01-18",
      severity: "Chặn lưu",
      periodId: 1,
      workDate: "2024-01-18",
      shiftTypeId: "L01",
    };
    
    render(<ConflictPanel conflicts={[conflictWithoutDetail]} maxItems={5} />);
    expect(screen.getByText("Bs. Test")).toBeInTheDocument();
    // The conflict type line shows "Loại: " followed by a span with the type
    expect(screen.getByText(/Loại:/)).toBeInTheDocument();
  });

  it("handles conflict without workDate gracefully", () => {
    const conflictWithoutDate: ConflictItem = {
      id: "5",
      type: "L01",
      staffName: "Bs. No Date",
      date: "2024-01-19",
      severity: "Chặn lưu",
      detail: "Test conflict",
      periodId: 1,
      shiftTypeId: "L01",
    };
    
    render(<ConflictPanel conflicts={[conflictWithoutDate]} maxItems={5} />);
    expect(screen.getByText("Bs. No Date")).toBeInTheDocument();
  });

  it("sorts conflicts by severity - blocking first", () => {
    render(<ConflictPanel conflicts={mockConflicts} maxItems={5} />);
    
    // The first displayed conflict should be blocking
    const firstConflictStaffName = screen.getAllByText(/Bs\./)[0];
    expect(firstConflictStaffName?.textContent).toBe("Bs. Nguyễn Văn A");
  });

  it("applies custom className", () => {
    const { container } = render(
      <ConflictPanel conflicts={[]} className="custom-class" />
    );
    expect(container.firstChild).toHaveClass("custom-class");
  });

  it("displays correct icon for different conflict types", () => {
    render(<ConflictPanel conflicts={mockConflicts} maxItems={5} />);
    
    // Check that material symbols icons are present (emergency icon for 24/24 conflicts)
    const emergencyIcons = screen.getAllByText("emergency");
    expect(emergencyIcons.length).toBeGreaterThan(0);
    
    // Header warning icon should be present
    expect(screen.getByText("warning")).toBeInTheDocument();
  });

  it("does not show expand button when conflicts equal maxItems", () => {
    render(<ConflictPanel conflicts={mockConflicts} maxItems={3} />);
    expect(screen.queryByText(/Xem tất cả/)).not.toBeInTheDocument();
  });

  it("does not show expand button when conflicts less than maxItems", () => {
    render(<ConflictPanel conflicts={[mockConflicts[0]]} maxItems={5} />);
    expect(screen.queryByText(/Xem tất cả/)).not.toBeInTheDocument();
  });

  it("renders with different maxItems values", () => {
    const { rerender } = render(<ConflictPanel conflicts={mockConflicts} maxItems={1} />);
    expect(screen.queryByText("Xem tất cả (3)")).toBeInTheDocument();
    
    rerender(<ConflictPanel conflicts={mockConflicts} maxItems={10} />);
    expect(screen.queryByText(/Xem tất cả/)).not.toBeInTheDocument();
  });
});

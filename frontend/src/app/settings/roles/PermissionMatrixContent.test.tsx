import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, act, waitFor } from '@testing-library/react';
import { ToastProvider } from '@/components/ui/ToastProvider';
import { PermissionMatrixContent } from './PermissionMatrixContent';

// ── Mock setup ────────────────────────────────────────────────────────────────
const { getMatrixMock, toggleMock } = vi.hoisted(() => ({
  getMatrixMock: vi.fn(),
  toggleMock: vi.fn(),
}));

const mockMatrix = {
  roles: [
    { id: 1, name: 'ADMIN',   description: 'Admin',   isActive: true },
    { id: 2, name: 'MANAGER', description: 'Manager', isActive: true },
    { id: 3, name: 'STAFF',   description: 'Staff',  isActive: true },
  ],
  permissions: [
    { id: 1, name: 'SCHEDULE_READ',            description: 'View schedule' },
    { id: 2, name: 'SCHEDULE_WRITE',            description: 'Manage schedule' },
  ],
  matrix: [
    { roleId: 1, roleName: 'ADMIN',   permissionId: 1, permissionName: 'SCHEDULE_READ',  granted: true },
    { roleId: 1, roleName: 'ADMIN',   permissionId: 2, permissionName: 'SCHEDULE_WRITE', granted: true },
    { roleId: 2, roleName: 'MANAGER', permissionId: 1, permissionName: 'SCHEDULE_READ',  granted: true },
    { roleId: 2, roleName: 'MANAGER', permissionId: 2, permissionName: 'SCHEDULE_WRITE', granted: false },
    { roleId: 3, roleName: 'STAFF',   permissionId: 1, permissionName: 'SCHEDULE_READ',  granted: false },
    { roleId: 3, roleName: 'STAFF',   permissionId: 2, permissionName: 'SCHEDULE_WRITE', granted: false },
  ],
};

vi.mock('@/lib/api', () => ({
  api: { getRolePermissionMatrix: getMatrixMock, toggleRolePermission: toggleMock },
}));

vi.mock('@/hooks/useRole', () => ({
  useRole: vi.fn(() => 'ADMIN'),
}));

function renderWithProvider(ui: React.ReactElement) {
  return render(<ToastProvider>{ui}</ToastProvider>);
}

// ── Tests ─────────────────────────────────────────────────────────────────────
describe('PermissionMatrixContent', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('shows loading skeleton while fetching', () => {
    getMatrixMock.mockReturnValue(new Promise(() => {}));
    renderWithProvider(<PermissionMatrixContent />);
    expect(screen.getByTestId('roles-loading')).toBeInTheDocument();
  });

  it('renders the permission matrix with roles and permissions', async () => {
    getMatrixMock.mockResolvedValue({ success: true, data: mockMatrix } as any);
    await act(async () => { renderWithProvider(<PermissionMatrixContent />); });
    await waitFor(() => {
      expect(screen.getByTestId('roles-matrix')).toBeInTheDocument();
    });
      expect(screen.getByText('Quản lý lịch')).toBeInTheDocument();
      expect(screen.getByText('Trưởng phòng')).toBeInTheDocument();
    expect(screen.getByText('Nhân viên')).toBeInTheDocument();
    expect(screen.getByText('Xem lịch trực')).toBeInTheDocument();
    expect(screen.getByText('Tạo/sửa/xóa lịch trực')).toBeInTheDocument();
  });

  it('renders one toggle button per role x permission cell', async () => {
    getMatrixMock.mockResolvedValue({ success: true, data: mockMatrix } as any);
    await act(async () => { renderWithProvider(<PermissionMatrixContent />); });
    await waitFor(() => { expect(screen.getByTestId('toggle-1-1')).toBeInTheDocument(); });
    expect(screen.getAllByTestId(/^toggle-\d+-\d+$/)).toHaveLength(6);
  });

  it('admin sees enabled toggles', async () => {
    getMatrixMock.mockResolvedValue({ success: true, data: mockMatrix } as any);
    await act(async () => { renderWithProvider(<PermissionMatrixContent />); });
    await waitFor(() => { expect(screen.getByTestId('toggle-1-1')).toBeInTheDocument(); });
    expect(screen.getByTestId('toggle-1-1')).not.toBeDisabled();
    expect(screen.getByTestId('toggle-3-1')).not.toBeDisabled();
  });

  it('non-admin sees disabled toggles', async () => {
    const { useRole } = await import('@/hooks/useRole');
    vi.mocked(useRole).mockReturnValue('STAFF');
    getMatrixMock.mockResolvedValue({ success: true, data: mockMatrix } as any);
    await act(async () => { renderWithProvider(<PermissionMatrixContent />); });
    await waitFor(() => { expect(screen.getByTestId('toggle-1-1')).toBeInTheDocument(); });
    for (const btn of screen.getAllByRole('button')) {
      expect(btn).toBeDisabled();
    }
  });
});

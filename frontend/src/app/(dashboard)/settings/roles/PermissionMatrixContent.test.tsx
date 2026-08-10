import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, act, waitFor } from '@testing-library/react';
import { ToastProvider } from '@/components/ui/ToastProvider';
import { PermissionMatrixContent } from './PermissionMatrixContent';

/* eslint-disable @typescript-eslint/no-explicit-any */

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
    // Mock permissions (SCHEDULE_READ/SCHEDULE_WRITE) are not in the
    // PERM_LABELS registry so they fall into the "Khác" extra group
    // and render their raw perm name as fallback text.
    expect(screen.getAllByText('SCHEDULE_READ').length).toBeGreaterThan(0);
    expect(screen.getAllByText('SCHEDULE_WRITE').length).toBeGreaterThan(0);
    // Role badges are rendered with ROLE_LABELS (ADMIN=Trưởng phòng,
    // MANAGER=Quản lý lịch, STAFF=Nhân viên).
    expect(screen.getByText('Trưởng phòng')).toBeInTheDocument();
    expect(screen.getByText('Quản lý lịch')).toBeInTheDocument();
    expect(screen.getByText('Nhân viên')).toBeInTheDocument();
  });

  it('renders one toggle button per role x permission cell', async () => {
    getMatrixMock.mockResolvedValue({ success: true, data: mockMatrix } as any);
    await act(async () => { renderWithProvider(<PermissionMatrixContent />); });
    await waitFor(() => { expect(screen.getByTestId('toggle-1-SCHEDULE_READ')).toBeInTheDocument(); });
    // 3 roles x 2 permissions = 6 toggle cells.
    expect(screen.getAllByTestId(/^toggle-\d+-SCHEDULE_(READ|WRITE)$/)).toHaveLength(6);
  });

  it('admin sees enabled toggles', async () => {
    getMatrixMock.mockResolvedValue({ success: true, data: mockMatrix } as any);
    await act(async () => { renderWithProvider(<PermissionMatrixContent />); });
    await waitFor(() => { expect(screen.getByTestId('toggle-1-SCHEDULE_READ')).toBeInTheDocument(); });
    expect(screen.getByTestId('toggle-1-SCHEDULE_READ')).not.toBeDisabled();
    expect(screen.getByTestId('toggle-3-SCHEDULE_READ')).not.toBeDisabled();
  });

  it('non-admin sees disabled toggles', async () => {
    const { useRole } = await import('@/hooks/useRole');
    vi.mocked(useRole).mockReturnValue('STAFF');
    getMatrixMock.mockResolvedValue({ success: true, data: mockMatrix } as any);
    await act(async () => { renderWithProvider(<PermissionMatrixContent />); });
    await waitFor(() => { expect(screen.getByTestId('toggle-1-SCHEDULE_READ')).toBeInTheDocument(); });
    // STAFF is read-only — every cell toggle button must be disabled.
    const buttons = screen.getAllByRole('button').filter((b) =>
      /^toggle-\d+-SCHEDULE_(READ|WRITE)$/.test(b.getAttribute('data-testid') ?? ''),
    );
    expect(buttons.length).toBeGreaterThan(0);
    for (const btn of buttons) {
      expect(btn).toBeDisabled();
    }
  });
});

import { describe, it, expect, vi, beforeEach } from 'vitest';

// Mock fetch globally
const mockFetch = vi.fn();
global.fetch = mockFetch;

// Mock localStorage
const localStorageMock = {
  getItem: vi.fn(),
  setItem: vi.fn(),
  removeItem: vi.fn(),
  clear: vi.fn(),
};
Object.defineProperty(global, 'localStorage', {
  value: localStorageMock,
  writable: true,
});

// Mock window.location
const locationMock = {
  pathname: '/dashboard',
  replace: vi.fn(),
};
Object.defineProperty(global, 'window', {
  value: {
    ...global.window,
    location: locationMock,
    localStorage: localStorageMock,
    dispatchEvent: vi.fn((_event: Event) => true),
  },
  writable: true,
});

describe('ApiClient API singleton', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorageMock.getItem.mockReturnValue(null);
    mockFetch.mockReset();
  });

  describe('URL Construction', () => {
    it('should construct correct URL with API_BASE', async () => {
      const { api } = await import('@/lib/api');
      
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ success: true, data: [] }),
      });

      await api.get<unknown[]>('/staff');
      
      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/staff'),
        expect.any(Object)
      );
    });

    it('should include query parameters in searchStaff', async () => {
      const { api } = await import('@/lib/api');
      
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ success: true, data: [] }),
      });

      await api.searchStaff({ keyword: 'test', status: 'ACTIVE' });
      
      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('keyword=test'),
        expect.any(Object)
      );
      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('status=ACTIVE'),
        expect.any(Object)
      );
    });
  });

  describe('Authentication', () => {
    it('should not include Authorization header when no token', async () => {
      const { api } = await import('@/lib/api');
      
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ success: true, data: [] }),
      });

      await api.get<unknown[]>('/staff');
      
      const call = mockFetch.mock.calls[0];
      const headers = call[1].headers;
      expect(headers['Authorization']).toBeUndefined();
    });

    it('should include Authorization header when token exists', async () => {
      localStorageMock.getItem.mockReturnValue('mock-jwt-token');
      
      const { api } = await import('@/lib/api');
      
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ success: true, data: [] }),
      });

      await api.get<unknown[]>('/staff');
      
      const call = mockFetch.mock.calls[0];
      const headers = call[1].headers;
      expect(headers['Authorization']).toBe('Bearer mock-jwt-token');
    });
  });

  describe('Error Handling', () => {
    it('should throw error on non-OK response', async () => {
      const { api } = await import('@/lib/api');
      
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 400,
        json: () => Promise.resolve({ message: 'Bad request' }),
      });

      await expect(api.get('/staff')).rejects.toThrow('Bad request');
    });

    it('should include status code in error when no message', async () => {
      const { api } = await import('@/lib/api');
      
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500,
        json: () => Promise.resolve({}),
      });

      await expect(api.get('/staff')).rejects.toThrow('HTTP 500');
    });

    it('should handle network errors', async () => {
      const { api } = await import('@/lib/api');
      
      mockFetch.mockRejectedValueOnce(new Error('Network failed'));

      await expect(api.get('/staff')).rejects.toThrow('Network failed');
    });

    it('should remove user from localStorage on 401', async () => {
      const { api } = await import('@/lib/api');

      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 401,
        json: () => Promise.resolve({ message: 'Unauthorized' }),
      });

      try {
        await api.get('/staff');
      } catch {
        // Expected to throw
      }

      expect(localStorageMock.removeItem).toHaveBeenCalledWith('medschedule.user');
    });

    // BUGFIX (was PERM-VER-LOOP): when the backend rejects a JWT with
    // { code: "PERMISSION_VERSION_STALE" }, the interceptor must NOT try
    // to refresh — the refresh token carries the same stale permVer claim
    // and would just produce another stale JWT. Instead it must clear
    // localStorage and bounce the user to /login.
    it('clears auth immediately on PERMISSION_VERSION_STALE 401 (no refresh attempt)', async () => {
      localStorageMock.getItem.mockImplementation((key: string) => {
        if (key === 'medschedule.token') return 'old-access';
        if (key === 'medschedule.refreshToken') return 'old-refresh';
        return null;
      });

      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 401,
        json: () =>
          Promise.resolve({
            success: false,
            code: 'PERMISSION_VERSION_STALE',
            message: 'Permission matrix has changed — please log in again.',
          }),
      });

      const { api } = await import('@/lib/api');

      await expect(api.get('/staff')).rejects.toThrow(/Permission matrix/);

      // Only one fetch should have happened — the refresh endpoint must NOT be called.
      expect(mockFetch).toHaveBeenCalledTimes(1);
      // Tokens + cached user must be cleared.
      expect(localStorageMock.removeItem).toHaveBeenCalledWith('medschedule.user');
      expect(localStorageMock.removeItem).toHaveBeenCalledWith('medschedule.token');
      expect(localStorageMock.removeItem).toHaveBeenCalledWith('medschedule.refreshToken');
      // Should redirect to /login.
      expect(locationMock.replace).toHaveBeenCalledWith('/login');
    });

    it('still attempts refresh on regular 401 (no PERMISSION_VERSION_STALE code)', async () => {
      // The interceptor's refresh-on-401 path must keep working for plain
      // expired access tokens — only the permVer stale case should bypass it.
      localStorageMock.getItem.mockImplementation((key: string) => {
        if (key === 'medschedule.token') return 'old-access';
        if (key === 'medschedule.refreshToken') return 'old-refresh';
        return null;
      });

      // First call: 401 with plain message (no PERMISSION_VERSION_STALE code).
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 401,
        json: () => Promise.resolve({ message: 'Access token expired' }),
      });
      // Refresh succeeds and returns a new pair.
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            success: true,
            data: { token: 'new-access', refreshToken: 'new-refresh' },
          }),
      });
      // Replayed original request succeeds.
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ success: true, data: [] }),
      });

      const { api } = await import('@/lib/api');

      await api.get('/staff');

      expect(mockFetch).toHaveBeenCalledTimes(3);
      expect(mockFetch.mock.calls[1][0]).toContain('/auth/refresh');
      // New tokens persisted.
      expect(localStorageMock.setItem).toHaveBeenCalledWith('medschedule.token', 'new-access');
      expect(localStorageMock.setItem).toHaveBeenCalledWith('medschedule.refreshToken', 'new-refresh');
    });
  });

  describe('HTTP Methods', () => {
    it('should use correct method for GET requests', async () => {
      const { api } = await import('@/lib/api');
      
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ success: true, data: [] }),
      });

      await api.get('/staff');
      
      expect(mockFetch.mock.calls[0][1].method).toBe('GET');
    });

    it('should use correct method for POST requests', async () => {
      const { api } = await import('@/lib/api');
      
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ success: true, data: {} }),
      });

      await api.post('/staff', { name: 'Test' });
      
      expect(mockFetch.mock.calls[0][1].method).toBe('POST');
    });

    it('should use correct method for PUT requests', async () => {
      const { api } = await import('@/lib/api');
      
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ success: true, data: {} }),
      });

      await api.put('/staff/1', { name: 'Test' });
      
      expect(mockFetch.mock.calls[0][1].method).toBe('PUT');
    });

    it('should use correct method for DELETE requests', async () => {
      const { api } = await import('@/lib/api');
      
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ success: true, data: null }),
      });

      await api.delete('/staff/1');
      
      expect(mockFetch.mock.calls[0][1].method).toBe('DELETE');
    });

    it('should stringify body for POST and PUT requests', async () => {
      const { api } = await import('@/lib/api');
      const body = { name: 'Test', email: 'test@example.com' };
      
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ success: true, data: {} }),
      });

      await api.post('/staff', body);
      
      expect(mockFetch.mock.calls[0][1].body).toBe(JSON.stringify(body));
    });

    it('should include Content-Type header', async () => {
      const { api } = await import('@/lib/api');
      
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ success: true, data: [] }),
      });

      await api.get('/staff');
      
      const headers = mockFetch.mock.calls[0][1].headers;
      expect(headers['Content-Type']).toBe('application/json');
    });
  });

  describe('Response Parsing', () => {
    it('should return data from ApiResponse structure', async () => {
      const mockData = [{ id: 1, name: 'Test' }];
      
      const { api } = await import('@/lib/api');
      
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ success: true, data: mockData }),
      });

      const result = await api.get<typeof mockData>('/staff');
      
      expect(result).toEqual(mockData);
    });

it('should handle null data in response', async () => {
      const { api } = await import('@/lib/api');

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ success: true, data: null }),
      });

      const result = await api.get<unknown>('/staff');

      expect(result).toEqual({ success: true, data: null });
    });
  });
});

describe('getErrorMessage', () => {
  it('should return error message when error has message', async () => {
    const { getErrorMessage } = await import('@/lib/errors');
    const error = new Error('Test error');
    expect(getErrorMessage(error, 'Fallback')).toBe('Test error');
  });

  it('should return fallback when error has empty message', async () => {
    const { getErrorMessage } = await import('@/lib/errors');
    const error = new Error('');
    expect(getErrorMessage(error, 'Fallback')).toBe('Fallback');
  });

  it('should return fallback for non-Error objects', async () => {
    const { getErrorMessage } = await import('@/lib/errors');
    expect(getErrorMessage('string error', 'Fallback')).toBe('Fallback');
    expect(getErrorMessage(null, 'Fallback')).toBe('Fallback');
    expect(getErrorMessage(undefined, 'Fallback')).toBe('Fallback');
  });
});

/**
 * The 4 refactored shift-type pages (M02..M05) all funnel through
 * the same handful of ApiClient methods. The tests below pin the
 * exact URL / param shape for each one so a future refactor of
 * api-client.ts cannot silently break the page contract.
 */
describe('ApiClient methods used by the 4 refactored shift-type pages', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorageMock.getItem.mockReturnValue(null);
    mockFetch.mockReset();
  });

  function mockOk(data: unknown) {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ success: true, data }),
    });
  }

  it('getPeriods() hits /periods', async () => {
    const { api } = await import('@/lib/api');
    mockOk([{ id: 1, periodName: 'Tháng 6', status: 'DRAFT' }]);

    const periods = await api.get('/periods');

    expect(mockFetch.mock.calls[0][0]).toMatch(/\/periods$/);
    expect(periods).toHaveLength(1);
  });

  it('getActiveStaff() hits /staff/active', async () => {
    const { api } = await import('@/lib/api');
    mockOk([{ id: 1, fullName: 'BS. A' }]);

    await api.get('/staff/active');

    expect(mockFetch.mock.calls[0][0]).toMatch(/\/staff\/active$/);
  });

  it('getSchedulesByPeriod() hits /schedules/period/{id}', async () => {
    const { api } = await import('@/lib/api');
    mockOk([{ id: 100, workDate: '2026-06-10' }]);

    await api.get('/schedules/period/1');

    expect(mockFetch.mock.calls[0][0]).toMatch(/\/schedules\/period\/1$/);
  });

  it('getCompensationDays() hits /schedules/compensation-days/{id}', async () => {
    const { api } = await import('@/lib/api');
    mockOk([{ id: 1, staffName: 'BS. A', compensationDate: '2026-06-11' }]);

    await api.get('/schedules/compensation-days/1');

    expect(mockFetch.mock.calls[0][0]).toMatch(/\/schedules\/compensation-days\/1$/);
  });

  it('getExpertClinicSchedules(periodId) hits /schedules/expert-clinic with periodId', async () => {
    const { api } = await import('@/lib/api');
    mockOk([{ id: 200 }]);

    await api.get('/schedules/expert-clinic', { periodId: 7 });

    const url = mockFetch.mock.calls[0][0] as string;
    expect(url).toMatch(/\/schedules\/expert-clinic/);
    expect(url).toContain('periodId=7');
    // No specialty filter means no specialtyId param.
    expect(url).not.toContain('specialtyId');
  });

  it('getExpertClinicSchedules(periodId, specialtyId) appends specialtyId', async () => {
    const { api } = await import('@/lib/api');
    mockOk([{ id: 200 }]);

    await api.get('/schedules/expert-clinic', {
      periodId: 7,
      specialtyId: 3,
    });

    const url = mockFetch.mock.calls[0][0] as string;
    expect(url).toMatch(/\/schedules\/expert-clinic/);
    expect(url).toContain('periodId=7');
    expect(url).toContain('specialtyId=3');
  });

  it('getActiveSpecialties() hits /specialties/active (used only by expert-clinic mode)', async () => {
    const { api } = await import('@/lib/api');
    mockOk([{ id: 1, name: 'Nội khoa', active: true }]);

    await api.get('/specialties/active');

    expect(mockFetch.mock.calls[0][0]).toMatch(/\/specialties\/active$/);
  });

  describe('Role / Permission matrix (M01-F05)', () => {
    const roleMatrixResponse = {
      roles: [{ id: 1, name: 'ADMIN', description: 'Admin', isActive: true }],
      permissions: [{ id: 1, name: 'SCHEDULE_READ', description: 'View schedule' }],
      matrix: [{ roleId: 1, roleName: 'ADMIN', permissionId: 1, permissionName: 'SCHEDULE_READ', granted: true }],
    };

    it('getRolePermissionMatrix() hits GET /roles/permissions/matrix', async () => {
      const { api } = await import('@/lib/api');
      mockOk(roleMatrixResponse);

      await api.getRolePermissionMatrix();

      expect(mockFetch.mock.calls[0][0]).toMatch(/\/roles\/permissions\/matrix$/);
    });

    it('getRolePermissionMatrix() returns parsed data', async () => {
      const { api } = await import('@/lib/api');
      mockOk(roleMatrixResponse);

      const result = await api.getRolePermissionMatrix();

      expect(result.data.roles).toHaveLength(1);
      expect(result.data.permissions).toHaveLength(1);
      expect(result.data.matrix).toHaveLength(1);
      expect(result.data.matrix[0].granted).toBe(true);
    });

    it('toggleRolePermission() hits POST /roles/permissions/toggle with correct body', async () => {
      const { api } = await import('@/lib/api');
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ success: true, data: null }),
      });

      await api.toggleRolePermission({ roleId: 1, permissionId: 2, granted: true });

      expect(mockFetch.mock.calls[0][0]).toMatch(/\/roles\/permissions\/toggle$/);
      const [, opts] = mockFetch.mock.calls[0] as [string, RequestInit];
      const body = JSON.parse(opts.body as string) as Record<string, unknown>;
      expect(body).toEqual({ roleId: 1, permissionId: 2, granted: true });
      expect(opts.method).toBe('POST');
    });

    it('toggleRolePermission() sends granted=false for revoke', async () => {
      const { api } = await import('@/lib/api');
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ success: true, data: null }),
      });

      await api.toggleRolePermission({ roleId: 3, permissionId: 1, granted: false });

      const [, opts] = mockFetch.mock.calls[0] as [string, RequestInit];
      const body = JSON.parse(opts.body as string) as Record<string, unknown>;
      expect(body).toEqual({ roleId: 3, permissionId: 1, granted: false });
    });
  });
});

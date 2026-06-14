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
      
      expect(result).toBeNull();
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

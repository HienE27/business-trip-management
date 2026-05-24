# Command: /gen-api-client
## Mô tả: Tạo API client cho Next.js

Tạo API service layer:

## API Client cơ bản

```typescript
// lib/api/index.ts
import axios from 'axios';

const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Interceptor for auth token
api.interceptors.request.use((config) => {
  const token = typeof window !== 'undefined' ? localStorage.getItem('token') : null;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Handle logout
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default api;
```

## API endpoints

```typescript
// lib/api/staff.ts
import api from './index';

export const staffApi = {
  getAll: () => api.get('/staff'),
  getById: (id: number) => api.get(`/staff/${id}`),
  create: (data: StaffRequest) => api.post('/staff', data),
  update: (id: number, data: StaffRequest) => api.put(`/staff/${id}`, data),
  delete: (id: number) => api.delete(`/staff/${id}`),
  search: (params: SearchParams) => api.get('/staff/search', { params }),
};

export const scheduleApi = {
  getByPeriod: (periodId: number) => api.get(`/schedules/period/${periodId}`),
  create: (data: ScheduleRequest) => api.post('/schedules', data),
  checkConflict: (staffId: number, date: string, shiftType: string) =>
    api.get('/schedules/conflicts/check', { params: { staffId, date, shiftType } }),
  publish: (periodId: number) => api.post(`/schedules/period/${periodId}/publish`),
};
```

## Typed Response

```typescript
// types/api.ts
export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
  timestamp: string;
}

export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
```

## Ví dụ:
- "/gen-api-client staff" → tạo lib/api/staff.ts
- "/gen-api-client schedule" → tạo lib/api/schedule.ts

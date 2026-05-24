---
name: frontend-nextjs
description: Phát triển frontend với Next.js, Tailwind CSS, TypeScript
---

# Skill: Frontend Development - Next.js

## Tech Stack
- **Framework**: Next.js 14+ (App Router)
- **Package Manager**: pnpm
- **Styling**: Tailwind CSS
- **State**: React Hooks / Zustand
- **UI**: shadcn/ui
- **Forms**: React Hook Form + Zod
- **HTTP**: Axios

## Cấu trúc thư mục

```
frontend/
├── app/                    # Next.js App Router
│   ├── (auth)/            # Auth routes (login, register)
│   │   ├── login/page.tsx
│   │   └── register/page.tsx
│   ├── (dashboard)/       # Protected routes
│   │   ├── layout.tsx     # Dashboard layout with sidebar
│   │   ├── page.tsx       # Dashboard home
│   │   ├── schedule/
│   │   └── staff/
│   ├── api/               # API routes
│   ├── layout.tsx         # Root layout
│   └── globals.css
├── components/
│   ├── ui/                # shadcn/ui components
│   ├── layout/            # Layout components
│   │   ├── Sidebar.tsx
│   │   ├── Header.tsx
│   │   └── Footer.tsx
│   └── shared/            # Shared components
├── lib/
│   ├── api/               # API clients
│   └── utils.ts           # Utilities
├── hooks/                 # Custom hooks
├── contexts/              # React contexts
├── types/                 # TypeScript types
└── constants/             # Constants
```

## UI Components đề xuất

### Sidebar Navigation

```typescript
// components/layout/Sidebar.tsx
const navigation = [
  { name: 'Dashboard', href: '/dashboard', icon: HomeIcon },
  { name: 'Lịch trực 24/24', href: '/dashboard/schedule/24h', icon: CalendarIcon },
  { name: 'Lịch thông tầm', href: '/dashboard/schedule/thong-tam', icon: ClockIcon },
  { name: 'Nhân sự', href: '/dashboard/staff', icon: UsersIcon },
  // ...
];
```

### Calendar Component

```typescript
// components/calendar/MonthlyCalendar.tsx
interface MonthlyCalendarProps {
  year: number;
  month: number;
  schedules: Schedule[];
  onDateClick?: (date: Date) => void;
  onCellClick?: (date: Date, staffId: number) => void;
}

export function MonthlyCalendar({ year, month, schedules, ... }: MonthlyCalendarProps) {
  // Grid 7x5/6 cho tháng
  // Màu phân biệt theo loại lịch (L01, L02, L03, L04)
  // Click vào ô để xem chi tiết
}
```

### Schedule Card

```typescript
// Màu theo loại lịch
const scheduleColors = {
  L01: 'bg-red-100 border-red-500',    // Lịch trực 24/24
  L02: 'bg-blue-100 border-blue-500',   // Lịch thông tầm
  L03: 'bg-green-100 border-green-500', // Phòng khám dịch vụ
  L04: 'bg-purple-100 border-purple-500', // Phòng khám chuyên gia
};
```

## API Integration

```typescript
// lib/api/index.ts
import axios from 'axios';

const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL,
});

export default api;

// Sử dụng trong Server Component
export async function getSchedules(periodId: number) {
  const res = await api.get(`/schedules/period/${periodId}`);
  return res.data.data;
}
```

## Responsive Design

```typescript
// Tailwind responsive classes
<div className="hidden md:block">Desktop only</div>
<div className="block md:hidden">Mobile only</div>
<div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
  // Responsive grid
</div>
```

## Khi nào trigger
- Phát triển component mới
- Tạo page mới
- Tích hợp API
- Thiết kế UI/UX

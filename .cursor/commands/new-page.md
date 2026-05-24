# Command: /new-page
## Mô tả: Tạo trang mới trong Next.js

Tạo page với App Router (Next.js 14+):

## Cấu trúc Page

```typescript
// app/{page}/page.tsx
import { Metadata } from 'next';

export const metadata: Metadata = {
  title: '{Page Name}',
  description: 'Mô tả trang',
};

export default function {PageName}Page() {
  return (
    <div className="container mx-auto py-8">
      <h1 className="text-2xl font-bold mb-4">{Page Name}</h1>
      {/* Nội dung trang */}
    </div>
  );
}
```

## Cấu trúc Page với Server Component có Data

```typescript
// app/{page}/page.tsx
import { getData } from '@/lib/api';

export default async function {PageName}Page() {
  const data = await getData();
  
  return (
    <div className="container mx-auto py-8">
      <h1 className="text-2xl font-bold mb-4">{Page Name}</h1>
      <div className="grid gap-4">
        {data.map((item) => (
          <Card key={item.id} data={item} />
        ))}
      </div>
    </div>
  );
}
```

## Cấu trúc Page với Client Component

```typescript
// app/{page}/page.tsx
'use client';

import { useState, useEffect } from 'react';
import { api } from '@/lib/api';

export default function {PageName}Page() {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(true);
  
  useEffect(() => {
    api.get('/endpoint')
      .then((res) => setData(res.data))
      .finally(() => setLoading(false));
  }, []);
  
  if (loading) return <LoadingSkeleton />;
  
  return (
    <div className="container mx-auto py-8">
      <h1 className="text-2xl font-bold mb-4">{Page Name}</h1>
      {/* Nội dung */}
    </div>
  );
}
```

## Ví dụ:
- "/new-page schedule" → tạo app/schedule/page.tsx
- "/new-page staff" → tạo app/staff/page.tsx

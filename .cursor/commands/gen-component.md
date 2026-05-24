# Command: /gen-component
## Mô tả: Tạo Component React

Tạo component với TypeScript:

## Component đơn giản

```typescript
// components/{ComponentName}/index.tsx
interface {ComponentName}Props {
  className?: string;
}

export function {ComponentName}({ className }: {ComponentName}Props) {
  return (
    <div className={cn('default-styles', className)}>
      {/* Nội dung */}
    </div>
  );
}
```

## Component với Props

```typescript
// components/{ComponentName}/index.tsx
interface {ComponentName}Props {
  title: string;
  items: Item[];
  onSelect?: (item: Item) => void;
  variant?: 'default' | 'compact';
}

export function {ComponentName}({
  title,
  items,
  onSelect,
  variant = 'default',
}: {ComponentName}Props) {
  return (
    <div className={cn('base-class', { 'variant-compact': variant === 'compact' })}>
      <h2 className="text-lg font-semibold">{title}</h2>
      <ul>
        {items.map((item) => (
          <li key={item.id} onClick={() => onSelect?.(item)}>
            {item.name}
          </li>
        ))}
      </ul>
    </div>
  );
}
```

## Component với State

```typescript
// components/{ComponentName}/index.tsx
'use client';

import { useState } from 'react';

export function {ComponentName}() {
  const [isOpen, setIsOpen] = useState(false);
  
  return (
    <div>
      <button onClick={() => setIsOpen(!isOpen)}>
        Toggle
      </button>
      {isOpen && <Content />}
    </div>
  );
}
```

## Ví dụ:
- "/gen-component ScheduleCard" → tạo ScheduleCard component
- "/gen-component Calendar" → tạo Calendar component

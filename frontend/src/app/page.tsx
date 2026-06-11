import { redirect } from "next/navigation";

export default function HomePage() {
  redirect("/dashboard");
}

export function Loading() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-background">
      <div className="flex flex-col items-center gap-4">
        <div className="size-10 animate-spin rounded-full border-4 border-primary border-t-transparent" />
        <p className="text-body-sm text-on-surface-variant">Đang tải...</p>
      </div>
    </div>
  );
}

"use client";

import dynamic from "next/dynamic";
import type { ComponentProps } from "react";

const LoginForm = dynamic(
  () => import("./LoginForm").then((m) => m.LoginForm),
  {
    ssr: false,
    loading: () => (
      <div className="min-h-screen flex w-full bg-surface">
        {/* Left — form skeleton */}
        <div className="flex w-1/2 flex-col justify-center p-16">
          <div className="space-y-5">
            <div className="mb-10 space-y-3">
              <div className="h-10 w-10 bg-surface-container rounded-lg animate-pulse" />
              <div className="h-8 w-48 bg-surface-container rounded-lg animate-pulse" />
            </div>
            <div className="space-y-1.5">
              <div className="h-4 w-24 bg-surface-container rounded animate-pulse" />
              <div className="h-12 bg-surface-container rounded-lg" />
            </div>
            <div className="space-y-1.5">
              <div className="h-4 w-16 bg-surface-container rounded animate-pulse" />
              <div className="h-12 bg-surface-container rounded-lg" />
            </div>
            <div className="h-10 bg-primary/50 rounded-lg animate-pulse mt-6" />
          </div>
        </div>
        {/* Right — illustration skeleton */}
        <div className="w-1/2 bg-surface-container animate-pulse" />
      </div>
    ),
  }
);

export default function LoginPage() {
  return <LoginForm />;
}

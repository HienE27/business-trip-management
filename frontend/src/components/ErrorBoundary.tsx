"use client";

import { Component, type ReactNode } from "react";

type Props = { children: ReactNode; fallback?: ReactNode };

type State = { hasError: boolean; error?: Error };

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false };
  }
  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }
  render() {
    if (this.state.hasError) {
      return this.props.fallback ?? (
        <div className="flex flex-col items-center justify-center min-h-screen gap-4 text-center p-8">
          <span className="material-symbols-outlined text-[64px] text-error">error</span>
          <h3 className="text-display-sm font-bold text-on-surface mb-2">
            Đã xảy ra lỗi
          </h3>
          <p className="text-on-surface-variant max-w-md">{this.state.error?.message}</p>
          <button
            className="mt-4 rounded-lg bg-primary px-6 py-3 font-semibold text-on-primary"
            onClick={() => window.location.reload()}
          >
            Tải lại trang
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}

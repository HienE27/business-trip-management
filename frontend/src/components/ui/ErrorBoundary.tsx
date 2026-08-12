'use client';

import { Component, type ReactNode } from 'react';
import { cn } from '@/lib/utils';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
  className?: string;
  onError?: (error: Error, errorInfo: React.ErrorInfo) => void;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo): void {
    console.error('ErrorBoundary caught an error:', error, errorInfo);
    this.props.onError?.(error, errorInfo);
  }

  handleRetry = (): void => {
    this.setState({ hasError: false, error: null });
  };

  render(): ReactNode {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <div
          className={cn(
            'min-h-[200px] flex items-center justify-center p-6',
            this.props.className
          )}
        >
          <div className="bg-surface-container-lowest border border-red-300 rounded-lg p-6 max-w-md w-full text-center shadow-sm">
            <div className="w-16 h-16 mx-auto mb-4 rounded-full bg-red-100 flex items-center justify-center">
              <span className="material-symbols-outlined text-red-800 text-3xl" style={{ fontVariationSettings: "'FILL' 1" }}>
                error
              </span>
            </div>

            <h3 className="font-title-lg text-on-surface mb-2">
              Đã xảy ra lỗi
            </h3>

            <p className="font-body-sm text-on-surface-variant mb-4">
              {this.state.error?.message || 'Một lỗi không mong muốn đã xảy ra.'}
            </p>

            <button
              onClick={this.handleRetry}
              className="inline-flex items-center gap-2 px-4 py-2 bg-blue-100 text-blue-800 rounded-lg font-label-md hover:bg-blue-100/90 transition-colors"
            >
              <span className="material-symbols-outlined text-[18px]">
                refresh
              </span>
              Thử lại
            </button>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

export function ClientErrorBoundary({
  children,
  className,
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <ErrorBoundary className={className}>
      {children}
    </ErrorBoundary>
  );
}

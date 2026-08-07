'use client';

import { useEffect } from 'react';

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
  }, [error]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-background p-6">
      <div className="bg-surface-container-lowest border border-error-container rounded-xl p-8 max-w-lg w-full text-center shadow-sm">
        <div className="w-20 h-20 mx-auto mb-6 rounded-full bg-error-container flex items-center justify-center">
          <span
            className="material-symbols-outlined text-error text-5xl"
            style={{ fontVariationSettings: "'FILL' 1" }}
          >
            error
          </span>
        </div>

        <h2 className="font-display-lg text-on-surface mb-3">
          Đã xảy ra lỗi nghiêm trọng
        </h2>

        <p className="font-body-md text-on-surface-variant mb-2">
          Xin lỗi, đã có lỗi không mong muốn xảy ra.
        </p>

        {error.digest && (
          <p className="font-label-sm text-outline mb-6">
            Mã lỗi: {error.digest}
          </p>
        )}

        <div className="flex flex-col sm:flex-row gap-3 justify-center">
          <button
            onClick={() => reset()}
            className="inline-flex items-center justify-center gap-2 px-5 py-2.5 bg-primary text-on-primary rounded-lg font-label-md hover:bg-primary/90 transition-colors"
          >
            <span className="material-symbols-outlined text-[18px]">
              refresh
            </span>
            Thử lại
          </button>

          <button
            onClick={() => (window.location.href = '/')}
            className="inline-flex items-center justify-center gap-2 px-5 py-2.5 bg-surface-container-low text-on-surface border border-outline-variant rounded-lg font-label-md hover:bg-surface-container transition-colors"
          >
            <span className="material-symbols-outlined text-[18px]">
              home
            </span>
            Về trang chủ
          </button>
        </div>

        {process.env.NODE_ENV === 'development' && (
          <details className="mt-6 text-left">
            <summary className="font-label-md text-on-surface-variant cursor-pointer hover:text-on-surface">
              Chi tiết lỗi (dev only)
            </summary>
            <pre className="mt-2 p-3 bg-surface-container-low rounded-lg text-[12px] text-error overflow-auto max-h-40 font-mono">
              {error.message}
              {'\n\n'}
              {error.stack}
            </pre>
          </details>
        )}
      </div>
    </div>
  );
}

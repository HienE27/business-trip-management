export function getErrorMessage(error: unknown, fallback: string): string {
  // Direct Error instance (from our api-client throw)
  if (error instanceof Error) {
    const msg = error.message.trim();
    if (msg) return msg;
  }

  // Object with message field (backend ApiResponse, axios-style errors)
  if (error && typeof error === "object" && !Array.isArray(error)) {
    const e = error as Record<string, unknown>;
    const msg = (e.message ?? e.error ?? e.msg ?? e.description) as string | undefined;
    if (msg && typeof msg === "string" && msg.trim()) {
      return msg.trim();
    }
  }

  return fallback;
}

import type { SwapRequest } from "@/types/schedule";

type SwapRequestsPanelProps = {
  requests: SwapRequest[];
  className?: string;
};

const AVATAR_COLORS: Record<string, string> = {
  A: "bg-primary-container text-on-primary-container",
  H: "bg-secondary-container text-on-secondary",
};

export function SwapRequestsPanel({ requests, className = "" }: SwapRequestsPanelProps) {
  return (
    <section
      className={`flex flex-col rounded-xl border border-outline-variant bg-surface-container-lowest shadow-[0_1px_3px_0_rgba(0,0,0,0.05)] flex-1 overflow-hidden ${className}`}
    >
      {/* Header */}
      <div className="p-4 border-b border-outline-variant bg-surface-bright flex items-center justify-between">
        <h3 className="font-title-lg text-on-surface">Yêu cầu đổi trực</h3>
        <span className="bg-primary/10 text-primary px-2 py-0.5 rounded-full font-label-sm text-label-sm">
          {requests.length} chờ duyệt
        </span>
      </div>

      {/* List */}
      <div className="p-0 flex-1 overflow-y-auto flex flex-col">
        {requests.map((req, index) => (
          <article
            key={req.id}
            className={`p-4 border-b border-surface-container-high flex items-start gap-3 hover:bg-surface-container-low transition-colors ${
              index === requests.length - 1 ? "border-b-0" : ""
            }`}
          >
            {/* Avatar */}
            <div
              className={`w-8 h-8 rounded-full flex items-center justify-center font-bold text-xs shrink-0 ${
                AVATAR_COLORS[req.requesterInitials] || "bg-primary-container text-on-primary-container"
              }`}
            >
              {req.requesterInitials}
            </div>

            {/* Content */}
            <div className="flex-1 min-w-0">
              <p className="font-label-md text-label-md text-on-surface">
                {req.requester}
                {req.target && ` xin đổi với ${req.target}`}
              </p>
              <p className="font-body-sm text-body-sm text-on-surface-variant mt-1">
                {req.shiftType}
              </p>

              {/* Actions */}
              <div className="flex gap-2 mt-2">
                {req.type === "exchange" ? (
                  <>
                    <button className="px-3 py-1 bg-primary text-on-primary rounded-lg text-xs font-medium">
                      Duyệt
                    </button>
                    <button className="px-3 py-1 bg-surface text-on-surface border border-outline-variant rounded-lg text-xs font-medium">
                      Từ chối
                    </button>
                  </>
                ) : (
                  <button className="px-3 py-1 bg-primary text-on-primary rounded-lg text-xs font-medium">
                    Tìm thay thế
                  </button>
                )}
              </div>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

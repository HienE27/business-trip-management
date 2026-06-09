import type { SwapRequest } from "@/types/schedule";

type SwapRequestsPanelProps = {
  requests: SwapRequest[];
  className?: string;
};

export function SwapRequestsPanel({ requests, className = "" }: SwapRequestsPanelProps) {
  return (
    <section className={`bg-surface-container-lowest border border-outline-variant rounded-xl shadow-[0_1px_3px_0_rgba(0,0,0,0.05)] flex-1 overflow-hidden flex flex-col ${className}`}>
      {/* Header */}
      <div className="p-4 border-b border-outline-variant bg-surface flex items-center justify-between">
        <h3 className="text-title-lg text-on-surface">Yeu cau doi truc</h3>
        <span className="bg-primary/10 text-primary px-2 py-0.5 rounded-full text-label-sm text-label-sm">
          {requests.length} cho duyet
        </span>
      </div>

      {/* List */}
      <div className="p-0 flex-1 overflow-y-auto">
        {requests.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-10 gap-3">
            <span aria-hidden="true" className="material-symbols-outlined text-[40px] text-outline/30">
              swap_horiz
            </span>
            <p className="text-label-md text-label-md text-on-surface-variant">Chua co yeu cau doi truc</p>
          </div>
        ) : (
          requests.map((req, index) => (
            <div
              key={req.id}
              className={`p-4 border-b border-outline flex items-start gap-3 hover:bg-surface-container-low transition-colors ${
                index === requests.length - 1 ? "border-b-0" : ""
              }`}
            >
              {/* Avatar */}
              <div className="w-8 h-8 rounded-full bg-primary-container text-primary flex items-center justify-center font-bold text-xs shrink-0">
                {req.requesterInitials}
              </div>

              {/* Content */}
              <div className="flex-1 min-w-0">
                <p className="text-label-md text-label-md text-on-surface font-medium">
                  {req.requester}
                  {req.target && <span className="text-on-surface-variant"> xin doi voi {req.target}</span>}
                </p>
                <p className="text-body-sm text-body-sm text-on-surface-variant mt-0.5">
                  {req.shiftType}
                </p>

                {/* Actions */}
                <div className="flex gap-2 mt-2">
                  <button className="px-3 py-1 bg-primary text-on-primary rounded text-xs font-medium hover:opacity-90 transition-opacity">
                    Duyet
                  </button>
                  <button className="px-3 py-1 bg-surface text-on-surface border border-outline-variant rounded text-xs font-medium hover:bg-surface-container-low transition-colors">
                    Tu choi
                  </button>
                </div>
              </div>
            </div>
          ))
        )}
      </div>
    </section>
  );
}

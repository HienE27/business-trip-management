"use client";

/**
 * Reference section - tài liệu tham khảo vận hành cho M07 Auto-Scheduling.
 *
 * Design locks (taste-skill pre-flight):
 *   - Tone palette: primary (mặc định) + error (critical). Không có tone thứ 3.
 *   - Shape scale: rounded-2xl (card) | rounded-xl (icon box) | rounded-md (pill).
 *   - Copy: cấm em-dash (`—`), en-dash (`–`), cấm arrow (`→`).
 *   - Hover micro-interactions motivated: tăng hierarchy + tactile feedback.
 *   - Không có text-on-photo pills; không có scroll cue; không có fake analytics.
 */

type ParamRefItem = {
  key: string;
  icon: string;
  tone: Tone;
  title: string;
  desc: string;
  range: string;
  defaultValue: string;
};

type ParamCategory = {
  id: string;
  label: string;
  icon: string;
  tone: Tone;
  description: string;
  params: readonly ParamRefItem[];
};

/** Tailwind v4 JIT không parse string interpolation — literal phải nằm trong source. */
type Tone = "primary" | "error";

const TONE_CLASSES: Record<Tone, string> = {
  primary: "bg-primary-fixed text-primary",
  error:   "bg-error-container text-error",
};

/** Icon-box shells — shape-lock: rounded-xl cố định cho mọi icon box. */
const ICON_BOX_SM = "flex h-9 w-9 shrink-0 items-center justify-center rounded-xl";
const ICON_BOX_LG = "flex h-10 w-10 shrink-0 items-center justify-center rounded-xl";

const PARAM_CATEGORY: readonly ParamCategory[] = [
  {
    id: "coverage",
    label: "Ngưỡng chất lượng",
    icon: "target",
    tone: "primary",
    description: "Tiêu chí dừng sớm & đánh giá kết quả",
    params: [
      {
        key: "greedy_coverage_threshold",
        icon: "radio_button_checked",
        tone: "primary",
        title: "Ngưỡng phủ lịch",
        desc: "Greedy dừng sớm khi đạt ngưỡng. Giảm: chạy nhanh. Tăng: phủ kỹ hơn.",
        range: "50%-100%",
        defaultValue: "85%",
      },
      {
        key: "balance_score_min",
        icon: "balance",
        tone: "primary",
        title: "Ngưỡng cân bằng",
        desc: "Ngưỡng cân bằng tải tối thiểu. Cao: phân bổ công bằng hơn nhưng khó đạt.",
        range: "30%-100%",
        defaultValue: "70%",
      },
    ],
  },
  {
    id: "weights",
    label: "Trọng số & ưu tiên",
    icon: "tune",
    tone: "primary",
    description: "Điều chỉnh penalty cho từng ngữ cảnh",
    params: [
      {
        key: "weekend_weight",
        icon: "weekend",
        tone: "primary",
        title: "Trọng số cuối tuần",
        desc: "Hệ số nhân penalty T7/CN. Lớn hơn 1: ưu tiên tránh cuối tuần. Đặt bằng 1 để tắt.",
        range: "1.0-5.0",
        defaultValue: "2.0x",
      },
      {
        key: "overnight_recovery_hours",
        icon: "hotel",
        tone: "error",
        title: "Nghỉ giữa ca trực",
        desc: "Số giờ nghỉ bắt buộc giữa hai ca trực 24/24.",
        range: "12-72 giờ",
        defaultValue: "24h",
      },
    ],
  },
];

type AlgoTone = "fast" | "medium" | "slow";

type AlgoMeta = {
  name: string;
  displayName: string;
  icon: string;
  speed: AlgoTone;
  quality: "good" | "optimal";
  best: string;
  detail: string;
};

const ALGO_META: Record<string, AlgoMeta> = {
  GREEDY: {
    name: "GREEDY",
    displayName: "Greedy",
    icon: "bolt",
    speed: "fast",
    quality: "good",
    best: "Phủ lịch nhanh, dữ liệu lớn",
    detail: "Chọn nhân sự tốt nhất ở mỗi bước. Không tối ưu toàn cục nhưng chạy rất nhanh.",
  },
  ROUND_ROBIN: {
    name: "ROUND_ROBIN",
    displayName: "Round Robin",
    icon: "autorenew",
    speed: "fast",
    quality: "good",
    best: "Chia đều tải, nhanh hơn Backtrack",
    detail: "Phân ca theo vòng tròn, đảm bảo mỗi nhân sự có lượng ca tương đương.",
  },
  CSP_MRV_FC: {
    name: "CSP_MRV_FC",
    displayName: "CSP-MRV-FC",
    icon: "account_tree",
    speed: "medium",
    quality: "optimal",
    best: "CSP + MRV + Forward Checking: fallback an toàn cho kỳ over-constrained",
    detail: "Chọn biến ít domain nhất trước (MRV), lan truyền ràng buộc (FC). Hiệu quả khi kỳ quá khó.",
  },
};

const SPEED_META: Record<AlgoTone, { label: string; dots: number; fillTone: string }> = {
  fast:   { label: "Rất nhanh",  dots: 5, fillTone: "bg-primary" },
  medium: { label: "Trung bình", dots: 3, fillTone: "bg-primary" },
  slow:   { label: "Chậm",       dots: 1, fillTone: "bg-error" },
};

const QUALITY_META: Record<"good" | "optimal", { label: string; bg: string; tone: string }> = {
  good:    { label: "Tốt",    bg: "bg-primary-fixed",      tone: "text-primary" },
  optimal: { label: "Tối ưu", bg: "bg-primary-fixed",      tone: "text-primary" },
};

export function ReferenceSection() {
  return (
    <div className="space-y-5 animate-fade-in">
      <HeroIntro />
      <ParamReferenceGrid />
      <AlgorithmComparison />
      <BestPracticeTips />
    </div>
  );
}

/* ─── Hero ─────────────────────────────────────────────────── */

function HeroIntro() {
  return (
    <div className="relative overflow-hidden rounded-2xl border border-outline-variant bg-surface-container-lowest p-6 sm:p-8">
      <div className="absolute inset-0 bg-gradient-to-br from-primary-fixed/50 via-transparent to-transparent pointer-events-none" aria-hidden="true" />

      <div className="relative flex items-start gap-4 sm:gap-5">
        <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-primary text-on-primary">
          <span className="material-symbols-outlined text-[24px]" aria-hidden="true">menu_book</span>
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1 flex-wrap">
            <h2 className="text-headline-md font-bold text-on-surface">Tài liệu tham khảo</h2>
            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-primary-fixed text-primary text-[10px] font-bold uppercase tracking-wide">
              <span className="material-symbols-outlined text-[10px]" aria-hidden="true">info</span>
              Reference
            </span>
          </div>
          <p className="text-body-sm text-on-surface-variant max-w-3xl leading-relaxed">
            Giải thích chi tiết từng tham số vận hành và đặc tính của 4 thuật toán xếp lịch.
            Dùng tài liệu này để hiểu rõ <strong className="text-on-surface font-semibold">trade-off</strong> giữa tốc độ và chất lượng trước khi điều chỉnh.
          </p>
        </div>
      </div>
    </div>
  );
}

/* ─── Param Reference Grid ─────────────────────────────────── */

function ParamReferenceGrid() {
  return (
    <div className="space-y-4">
      <SectionHeader icon="tune" title="Tham số vận hành" subtitle="Điều chỉnh để cân bằng tốc độ và chất lượng" />
      <div className="space-y-4 stagger-children">
        {PARAM_CATEGORY.map(category => (
          <CategoryBlock key={category.id} category={category} />
        ))}
      </div>
    </div>
  );
}

function CategoryBlock({ category }: { category: ParamCategory }) {
  return (
    <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant overflow-hidden">
      <div className="px-5 py-3 bg-surface-container-low flex items-center gap-3">
        <div className={`${ICON_BOX_SM} ${TONE_CLASSES[category.tone]}`}>
          <span className="material-symbols-outlined text-[18px]" aria-hidden="true">{category.icon}</span>
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-label-md font-semibold text-on-surface">{category.label}</p>
          <p className="text-[11px] text-on-surface-variant">{category.description}</p>
        </div>
        <span className="hidden sm:inline-flex items-center px-2 py-1 rounded-md bg-surface-container-lowest border border-outline-variant text-[10px] font-bold text-on-surface-variant tabular-nums">
          {category.params.length} tham số
        </span>
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 md:divide-x divide-outline-variant/40">
        {category.params.map((param, idx) => (
          <div key={param.key} className={idx === 0 || idx === 1 ? "" : "border-t md:border-t-0"}>
            <ParamRefCard param={param} />
          </div>
        ))}
      </div>
    </div>
  );
}

function ParamRefCard({ param }: { param: ParamRefItem }) {
  return (
    <article className="p-5 hover:bg-surface-container-low/40 transition-colors duration-200">
      <header className="flex items-start gap-3 mb-3">
        <div className={`${ICON_BOX_LG} ${TONE_CLASSES[param.tone]}`}>
          <span className="material-symbols-outlined text-[20px]" aria-hidden="true">{param.icon}</span>
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap mb-0.5">
            <h3 className="text-label-md font-semibold text-on-surface">{param.title}</h3>
            <span className="inline-flex items-center px-1.5 py-0.5 rounded-md text-[10px] font-bold bg-surface-container-low text-on-surface-variant border border-outline-variant/40 tabular-nums">
              {param.range}
            </span>
          </div>
          <code className="font-mono text-[11px] text-primary bg-primary-fixed/40 px-1.5 py-0.5 rounded break-all">
            {param.key}
          </code>
        </div>
      </header>

      <p className="text-[12px] text-on-surface-variant leading-relaxed mb-3">
        {param.desc}
      </p>

      <div className="flex items-center justify-between gap-2 pt-2.5 border-t border-outline-variant/40">
        <span className="text-[10px] uppercase tracking-wide font-semibold text-on-surface-variant">Mặc định</span>
        <span className="font-mono text-[13px] font-bold text-primary tabular-nums">
          {param.defaultValue}
        </span>
      </div>
    </article>
  );
}

/* ─── Algorithm Comparison ─────────────────────────────────── */

function AlgorithmComparison() {
  const algos = Object.values(ALGO_META);

  return (
    <div className="space-y-4">
      <SectionHeader
        icon="compare_arrows"
        title="So sánh thuật toán"
        subtitle="Chọn thuật toán phù hợp với quy mô kỳ lịch và yêu cầu chất lượng"
      />
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-3 stagger-children">
        {algos.map(algo => (
          <AlgoCard key={algo.name} algo={algo} />
        ))}
      </div>
    </div>
  );
}

function AlgoCard({ algo }: { algo: AlgoMeta }) {
  const speed = SPEED_META[algo.speed];
  const quality = QUALITY_META[algo.quality];

  return (
    <article className="bg-surface-container-lowest rounded-2xl border border-outline-variant overflow-hidden hover:shadow-md hover:-translate-y-0.5 transition-all duration-300">
      <div className="px-4 py-3 border-b border-outline-variant bg-surface-container-low/50 flex items-center gap-3">
        <div className={`${ICON_BOX_SM} bg-primary-fixed text-primary`}>
          <span className="material-symbols-outlined text-[18px]" aria-hidden="true">{algo.icon}</span>
        </div>
        <div className="flex-1 min-w-0">
          <h3 className="text-label-md font-bold text-on-surface truncate">{algo.displayName}</h3>
          <code className="text-[10px] text-on-surface-variant font-mono">{algo.name}</code>
        </div>
      </div>

      <div className="p-4 space-y-3">
        <p className="text-[11px] text-on-surface-variant leading-relaxed line-clamp-3 min-h-[44px]">
          {algo.detail}
        </p>

        <div className="space-y-2">
          <MetricRow label="Tốc độ">
            <SpeedIndicator filled={speed.dots} fillTone={speed.fillTone} />
            <span className="text-label-xs font-semibold text-on-surface tabular-nums">{speed.label}</span>
          </MetricRow>
          <MetricRow label="Chất lượng">
            <QualityBadge label={quality.label} bg={quality.bg} tone={quality.tone} />
          </MetricRow>
        </div>

        <div className="pt-2.5 border-t border-outline-variant/40">
          <p className="text-[10px] uppercase tracking-wide font-bold text-on-surface-variant mb-1">Phù hợp nhất</p>
          <p className="text-[11px] text-on-surface leading-snug">{algo.best}</p>
        </div>
      </div>
    </article>
  );
}

function MetricRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-2">
      <span className="text-[10px] uppercase tracking-wide font-bold text-on-surface-variant">{label}</span>
      <div className="flex items-center gap-2">
        {children}
      </div>
    </div>
  );
}

function SpeedIndicator({ filled, fillTone }: { filled: number; fillTone: string }) {
  return (
    <div className="flex items-center gap-0.5" aria-label={`Tốc độ ${filled}/5`}>
      {[1, 2, 3, 4, 5].map(i => (
        <span
          key={i}
          className={`h-1.5 w-1 rounded-full ${i <= filled ? fillTone : "bg-surface-variant"}`}
          aria-hidden="true"
        />
      ))}
    </div>
  );
}

function QualityBadge({ label, bg, tone }: { label: string; bg: string; tone: string }) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-md text-[10px] font-bold ${bg} ${tone}`}>
      {label}
    </span>
  );
}

/* ─── Tips ─────────────────────────────────────────────────── */

const TIPS: readonly { icon: string; tone: Tone; title: string; desc: string }[] = [
  {
    icon: "speed",
    tone: "primary",
    title: "Bắt đầu nhanh",
    desc: "Dùng preset 'Cân bằng' cho kỳ đầu tiên. Sau đó điều chỉnh dần theo feedback thực tế.",
  },
  {
    icon: "trending_up",
    tone: "primary",
    title: "Tăng chất lượng",
    desc: "Nếu còn nhiều ca thiếu (coverage dưới 90%), tăng greedy_coverage_threshold lên 0.90 trở lên.",
  },
  {
    icon: "balance",
    tone: "primary",
    title: "Cân bằng tải",
    desc: "Một số nhân sự có thể quá tải. Tăng balance_score_min để ép phân bổ đều hơn.",
  },
  {
    icon: "auto_fix_high",
    tone: "error",
    title: "Tránh over-constrained",
    desc: "Khi kỳ quá khó, các thuật toán tối ưu sẽ chạy lâu. CSP-MRV-FC là lựa chọn fallback an toàn.",
  },
];

function BestPracticeTips() {
  return (
    <div className="space-y-4">
      <SectionHeader
        icon="lightbulb"
        title="Best practices"
        subtitle="Gợi ý từ kinh nghiệm vận hành"
      />
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3 stagger-children">
        {TIPS.map(tip => (
          <article
            key={tip.title}
            className="flex gap-4 p-4 rounded-2xl border border-outline-variant bg-surface-container-lowest hover:border-primary/40 transition-colors duration-200"
          >
            <div className={`${ICON_BOX_LG} ${TONE_CLASSES[tip.tone]}`}>
              <span className="material-symbols-outlined text-[20px]" aria-hidden="true">{tip.icon}</span>
            </div>
            <div className="flex-1 min-w-0">
              <h3 className="text-label-md font-semibold text-on-surface mb-1">{tip.title}</h3>
              <p className="text-[12px] text-on-surface-variant leading-relaxed">{tip.desc}</p>
            </div>
          </article>
        ))}
      </div>
    </div>
  );
}

/* ─── Reusable Section Header ──────────────────────────────── */

function SectionHeader({ icon, title, subtitle }: { icon: string; title: string; subtitle: string }) {
  return (
    <div className="flex items-start gap-3 px-1">
      <div className={`${ICON_BOX_SM} bg-primary-fixed text-primary`}>
        <span className="material-symbols-outlined text-[18px]" aria-hidden="true">{icon}</span>
      </div>
      <div className="flex-1 min-w-0">
        <h2 className="text-title-sm font-bold text-on-surface">{title}</h2>
        <p className="text-[11px] text-on-surface-variant">{subtitle}</p>
      </div>
    </div>
  );
}

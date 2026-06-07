"use client";

import Link from "next/link";

type Rule = {
  id: string;
  icon: string;
  iconColor: string;
  title: string;
  description: string;
  type: "required" | "preference";
  hasConfig?: boolean;
};

const RULES: Rule[] = [
  {
    id: "rule-1",
    icon: "check_circle",
    iconColor: "text-secondary",
    title: "Quy tac nghi sau Truc 24/24",
    description: "Bat buoc nghi it nhat 1 ca Thong tam (hoac 24h) ngay sau ca truc 24/24.",
    type: "required",
  },
  {
    id: "rule-2",
    icon: "check_circle",
    iconColor: "text-secondary",
    title: "Tranh xung dot lich Phong kham",
    description: "Khong xep truc dem neu co lich Phong kham Dich vu sang hom sau.",
    type: "required",
  },
  {
    id: "rule-3",
    icon: "balance",
    iconColor: "text-primary",
    title: "Can bang khoi luong cong viec",
    description: "Uu tien chia deu so ca truc cuoi tuan (Thu 7, CN) cho tat ca nhan su trong khoa.",
    type: "preference",
    hasConfig: true,
  },
];

type BusinessRulesPanelProps = {
  onAddRule?: () => void;
};

export function BusinessRulesPanel({ onAddRule }: BusinessRulesPanelProps) {
  return (
    <div className="relative">
      <h2 className="text-lg font-semibold mb-4 flex items-center gap-2 border-b border-outline-variant/30 pb-3">
        <span className="material-symbols-outlined text-primary-container">rule</span>
        Luat nghiep vu dang ap dung
      </h2>
      <div className="flex flex-col gap-4">
        {RULES.map((rule) => (
          <div className="bg-surface-container-low p-4 rounded-lg border border-outline-variant/30 hover:border-primary/50 transition-colors" key={rule.id}>
            <div className="flex items-start gap-3">
              <span className={`material-symbols-outlined ${rule.iconColor} mt-0.5`}>{rule.icon}</span>
              <div className="flex-1 min-w-0">
                <h3 className="font-label-md font-bold mb-1">{rule.title}</h3>
                <p className="font-body-sm text-on-surface-variant">{rule.description}</p>
                <div className="mt-2 flex items-center gap-2">
                  {rule.type === "required" ? (
                    <span className="px-2 py-0.5 bg-error-container text-on-error-container text-[10px] font-bold rounded uppercase tracking-wider">
                      BAT BUOC
                    </span>
                  ) : (
                    <span className="px-2 py-0.5 bg-tertiary-container text-on-tertiary text-[10px] font-bold rounded uppercase tracking-wider">
                      UU TIEN
                    </span>
                  )}
                  {rule.hasConfig && (
                    <Link className="text-xs text-primary hover:underline ml-auto" href="#">
                      Cau hinh
                    </Link>
                  )}
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>
      <button
        className="w-full mt-4 py-2 text-primary font-label-sm border border-dashed border-primary/40 rounded-lg hover:bg-primary/5 transition-colors"
        onClick={onAddRule}
        type="button"
      >
        + Them luat tuy chinh
      </button>
    </div>
  );
}

"use client";

import { useRouter } from "next/navigation";
import { translateRoleListToDisplay } from "@/lib/roleLabels";

type ProfileCardProps = {
  id: number;
  fullName: string;
  username: string;
  specialty?: string;
  roles: string[];
  isActive: boolean;
  avatarUrl?: string;
  onEdit?: () => void;
};

export function ProfileCard({
  id,
  fullName,
  username,
  specialty,
  roles,
  isActive,
  avatarUrl,
  onEdit,
}: ProfileCardProps) {
  const router = useRouter();

  const getInitials = (name: string) =>
    name
      .split(" ")
      .filter(Boolean)
      .slice(0, 2)
      .map((p) => p[0]?.toUpperCase() ?? "")
      .join("");

  return (
    <div className="relative bg-gradient-to-br from-primary via-primary/90 to-primary/80 rounded-2xl p-6 text-white shadow-xl overflow-hidden">
      {/* Decorative */}
      <div className="absolute -top-8 -right-8 size-32 rounded-full bg-white/10 blur-xl" />
      <div className="absolute -bottom-4 -left-4 size-24 rounded-full bg-white/5 blur-lg" />

      <div className="relative flex flex-col items-center text-center gap-4">
        {/* Avatar */}
        <div className="relative group">
          <div className="size-24 rounded-2xl bg-white/20 backdrop-blur-sm flex items-center justify-center text-3xl font-bold shadow-xl ring-4 ring-white/30 transition-transform group-hover:scale-105">
            {avatarUrl ? (
              <img alt={fullName} className="w-full h-full object-cover rounded-2xl" src={avatarUrl} />
            ) : (
              getInitials(fullName)
            )}
          </div>
          <span className={`absolute -bottom-1 -right-1 size-6 rounded-full border-2 border-white ${
            isActive ? "bg-secondary" : "bg-white/40"
          }`} />
        </div>

        {/* Info */}
        <div>
          <h2 className="text-xl font-bold">{fullName}</h2>
          <p className="text-white/70 text-label-sm mt-0.5">@{username}</p>
          <div className="flex flex-wrap justify-center gap-2 mt-3">
            {translateRoleListToDisplay(roles).map((role, idx) => (
              <span key={role} className="px-2.5 py-1 rounded-full text-label-sm font-medium bg-white/20 backdrop-blur-sm border border-white/20">
                {role}
              </span>
            ))}
          </div>
          {specialty && (
            <p className="mt-3 text-label-sm text-white/80 bg-white/10 px-3 py-1 rounded-full inline-block">
              {specialty}
            </p>
          )}
        </div>

        {/* Actions */}
        <div className="w-full space-y-2 mt-2">
          <button
            className="w-full bg-white text-primary h-11 rounded-xl font-label-md font-semibold hover:bg-white/90 transition-colors flex items-center justify-center gap-2 shadow-lg"
            type="button"
            onClick={onEdit ?? (() => router.push(`/staff/${id}/edit`))}
          >
            <span className="material-symbols-outlined text-[18px]">edit</span>
            Chỉnh sửa hồ sơ
          </button>
        </div>
      </div>
    </div>
  );
}

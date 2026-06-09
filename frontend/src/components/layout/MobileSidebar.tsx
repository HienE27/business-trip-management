"use client";

import { useState } from "react";
import Link from "next/link";
import type { NavigationItem } from "@/types/schedule";

type AppSidebarProps = {
  items: NavigationItem[];
  mobileOpen: boolean;
  onClose: () => void;
};

export function AppSidebar({ items, mobileOpen, onClose }: AppSidebarProps) {
  return (
    <>
      {/* Mobile overlay */}
      {mobileOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/40 backdrop-blur-sm md:hidden"
          onClick={onClose}
          aria-hidden="true"
        />
      )}

      {/* Sidebar */}
      <aside
        aria-label="Điều hướng chính"
        className={`
          fixed left-0 top-0 h-full w-[260px] border-r border-outline-variant bg-surface-container-low z-50
          flex flex-col py-4
          transform transition-transform duration-200 ease-out
          hidden md:flex
          ${mobileOpen ? "translate-x-0" : "-translate-x-full"}
          md:translate-x-0
        `}
      >
        {/* Logo */}
        <div className="px-6 mb-6 flex items-center gap-3">
          <div className="w-10 h-10 rounded-lg bg-primary text-on-primary flex items-center justify-center shadow-sm shrink-0">
            <span aria-hidden="true" className="material-symbols-outlined text-[20px]">medical_services</span>
          </div>
          <div>
            <h1 className="font-title-lg text-primary font-bold leading-tight">Quản lý Lịch</h1>
            <p className="text-label-sm text-on-surface-variant">Hệ thống điều phối</p>
          </div>
        </div>

        {/* Nav */}
        <nav className="flex-1 flex flex-col gap-1 px-3" aria-label="Điều hướng chính">
          {items.map((item) => {
            const isActive = item.active;
            return (
              <Link
                className={`flex items-center gap-3 px-4 py-2.5 rounded-lg transition-all font-medium text-body-sm ${
                  isActive
                    ? "bg-primary-container text-on-primary-container border-l-4 border-primary font-semibold"
                    : "text-on-surface-variant hover:bg-surface-container-high"
                }`}
                href={item.href}
                key={item.code}
                onClick={onClose}
              >
                <span
                  aria-hidden="true"
                  className="material-symbols-outlined text-[20px] shrink-0"
                >
                  {item.icon || "dashboard"}
                </span>
                <span className="truncate">{item.label}</span>
              </Link>
            );
          })}
        </nav>

        {/* Footer */}
        <div className="mt-auto px-3 border-t border-outline-variant pt-4 flex flex-col gap-1">
          {[
            { label: "Thông báo", icon: "notifications", href: "/notifications" },
            { label: "Cài đặt", icon: "settings", href: "/settings" },
            { label: "Hồ sơ cá nhân", icon: "person", href: "/staff/profile" },
          ].map((item) => (
            <Link
              className="flex items-center gap-3 px-4 py-2.5 rounded-lg text-on-surface-variant hover:bg-surface-container-high transition-all font-medium text-body-sm"
              href={item.href}
              key={item.href}
              onClick={onClose}
            >
              <span aria-hidden="true" className="material-symbols-outlined text-[20px] shrink-0">
                {item.icon}
              </span>
              <span className="truncate">{item.label}</span>
            </Link>
          ))}
        </div>
      </aside>
    </>
  );
}

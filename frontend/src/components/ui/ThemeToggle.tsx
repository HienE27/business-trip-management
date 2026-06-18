"use client";

import { useEffect, useState } from "react";
import { IconButton } from "./Button";

/* ── ThemeToggle ──
 *
 * Allows manual dark/light mode override.
 * Persists preference to localStorage.
 * Respects system preference on first visit.
 *
 * Usage:
 *   <ThemeToggle />
 *
 * This component does NOT add the `dark` class to <html> directly —
 * it toggles a data attribute instead for better CSS selector specificity:
 *   html[data-theme="dark"] { ... }
 *
 * Combined with @media (prefers-color-scheme: dark) for automatic detection.
 */

type Theme = "light" | "dark" | "system";

const STORAGE_KEY = "medschedule-theme";

function getStored(): Theme {
  if (typeof window === "undefined") return "system";
  return (localStorage.getItem(STORAGE_KEY) as Theme) ?? "system";
}

function store(theme: Theme) {
  localStorage.setItem(STORAGE_KEY, theme);
}

function applyTheme(theme: Theme) {
  const root = document.documentElement;
  if (theme === "system") {
    root.removeAttribute("data-theme");
  } else {
    root.setAttribute("data-theme", theme);
  }
}

export function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>("system");

  useEffect(() => {
    const stored = getStored();
    setTheme(stored);
    applyTheme(stored);
  }, []);

  const toggle = () => {
    const next: Theme = theme === "light" ? "dark" : theme === "dark" ? "system" : "light";
    setTheme(next);
    store(next);
    applyTheme(next);
  };

  const label =
    theme === "light" ? "Chuyển sang chế độ tối" :
    theme === "dark"  ? "Chuyển sang chế độ sáng" :
    "Chuyển sang chế độ hệ thống";

  const icon =
    theme === "light" ? "light_mode" :
    theme === "dark"  ? "dark_mode" :
    "contrast";

  return (
    <IconButton
      variant="ghost"
      label={label}
      onClick={toggle}
      className="transition-transform active:scale-90"
    >
      <span className="material-symbols-outlined text-[20px]">{icon}</span>
    </IconButton>
  );
}

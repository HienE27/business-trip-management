"use client";

import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from "react";

/* ── Button Component ──
 *
 * Variants: primary, secondary, danger, ghost
 * Sizes: sm, md, lg
 * States: default, hover, active, disabled, loading
 * Supports forwardRef for focus management
 *
 * Design tokens:
 *   primary:   bg-primary text-on-primary
 *   secondary: border-outline-variant bg-surface-container-lowest
 *   danger:    bg-error text-on-error
 *   ghost:     bg-transparent hover:bg-surface-container-low
 */

type ButtonVariant = "primary" | "secondary" | "danger" | "ghost";
type ButtonSize = "sm" | "md" | "lg";

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  icon?: ReactNode;
  iconPosition?: "left" | "right";
  fullWidth?: boolean;
};

const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  primary:   "bg-primary text-on-primary hover:opacity-90 hover:shadow-md active:scale-[0.98]",
  secondary: "border border-outline-variant bg-surface-container-lowest text-on-surface hover:bg-surface-container-low hover:shadow-sm active:scale-[0.98]",
  danger:    "bg-error text-on-error hover:opacity-90 hover:shadow-md active:scale-[0.98]",
  ghost:     "bg-transparent text-on-surface hover:bg-surface-container-low hover:shadow-sm active:scale-[0.98]",
};

const SIZE_CLASSES: Record<ButtonSize, string> = {
  sm: "h-8 px-3 text-label-sm rounded-lg gap-1.5",
  md: "h-10 px-4 text-label-md rounded-lg gap-2",
  lg: "h-12 px-6 text-body-md rounded-lg gap-2.5",
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {
    variant = "primary",
    size = "md",
    loading = false,
    disabled,
    icon,
    iconPosition = "left",
    fullWidth = false,
    children,
    className = "",
    ...rest
  },
  ref
) {
  const isDisabled = disabled || loading;

  return (
    <button
      {...rest}
      ref={ref}
      disabled={isDisabled}
      aria-disabled={isDisabled}
      aria-busy={loading}
      className={[
        "inline-flex items-center justify-center font-semibold",
        "transition-all duration-200",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30",
        "disabled:opacity-50 disabled:cursor-not-allowed disabled:pointer-events-none",
        VARIANT_CLASSES[variant],
        SIZE_CLASSES[size],
        fullWidth ? "w-full" : "",
        className,
      ].join(" ")}
    >
      {loading ? (
        <>
          <span
            className="btn-spinner"
            role="status"
            aria-label={rest["aria-label"] ?? "Đang xử lý"}
          />
          {children && <span>{children}</span>}
        </>
      ) : (
        <>
          {icon && iconPosition === "left" && icon}
          {children}
          {icon && iconPosition === "right" && icon}
        </>
      )}
    </button>
  );
});

/* ── IconButton — square icon-only button ── */
type IconButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  size?: ButtonSize;
  label: string;
};

export const IconButton = forwardRef<HTMLButtonElement, IconButtonProps>(function IconButton(
  { variant = "ghost", size = "md", label, children, className = "", ...rest },
  ref
) {
  return (
    <button
      {...rest}
      ref={ref}
      aria-label={label}
      title={label}
      className={[
        "inline-flex items-center justify-center rounded-lg",
        "transition-all duration-200",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30",
        "disabled:opacity-50 disabled:cursor-not-allowed",
        VARIANT_CLASSES[variant],
        SIZE_CLASSES[size],
        className,
      ].join(" ")}
    >
      {children}
    </button>
  );
});

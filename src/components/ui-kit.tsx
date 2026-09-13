import type { ReactNode } from "react";
import { cn } from "../lib/utils";

export function PageHeader({
  title,
  subtitle,
  actions,
}: {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
}) {
  return (
    <header className="grid grid-cols-[minmax(0,1fr)_auto] items-start gap-4 border-b border-border pb-5 sm:flex sm:flex-wrap sm:items-center sm:justify-between">
      <div className="min-w-0">
        <h1 className="truncate text-2xl font-semibold uppercase tracking-wide sm:text-3xl">
          {title}
        </h1>
        {subtitle ? (
          <p className="mt-1 text-sm text-muted-foreground">{subtitle}</p>
        ) : null}
      </div>
      {actions ? <div className="flex shrink-0 flex-wrap gap-2">{actions}</div> : null}
    </header>
  );
}

export function Panel({
  title,
  description,
  actions,
  children,
  className,
}: {
  title?: string;
  description?: string;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={cn("rounded-lg border border-border bg-card", className)}>
      {title ? (
        <div className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-3 border-b border-border px-4 py-3 sm:flex sm:justify-between">
          <div className="min-w-0">
            <h2 className="truncate text-sm font-semibold uppercase tracking-widest text-foreground">
              {title}
            </h2>
            {description ? (
              <p className="mt-0.5 truncate text-xs text-muted-foreground">{description}</p>
            ) : null}
          </div>
          {actions ? <div className="flex shrink-0 items-center gap-2">{actions}</div> : null}
        </div>
      ) : null}
      <div className="p-4">{children}</div>
    </section>
  );
}

const toneMap: Record<string, string> = {
  critical: "bg-destructive/15 text-destructive border-destructive/40",
  high: "bg-warning/15 text-warning border-warning/40",
  moderate: "bg-info/15 text-info border-info/40",
  low: "bg-muted text-muted-foreground border-border",
  success: "bg-success/15 text-success border-success/40",
  neutral: "bg-secondary text-secondary-foreground border-border",
  primary: "bg-primary/15 text-primary border-primary/40",
};

export function toneFor(value: string): keyof typeof toneMap {
  const v = value.toLowerCase();
  if (["critical", "active", "rejected", "low stock"].includes(v)) return "critical";
  if (["high", "dispatched", "pending", "maintenance", "standby"].includes(v)) return "high";
  if (["moderate", "processing", "contained", "on duty"].includes(v)) return "moderate";
  if (["resolved", "received", "approved", "operational", "deployed"].includes(v)) return "success";
  return "neutral";
}

export function StatusBadge({ label, tone }: { label: string; tone?: keyof typeof toneMap }) {
  const t = tone ?? toneFor(label);
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium whitespace-nowrap",
        toneMap[t],
      )}
    >
      <span className="relative inline-block h-1.5 w-1.5 shrink-0 rounded-full bg-current" />
      {label}
    </span>
  );
}

export function StatCard({
  label,
  value,
  delta,
  hint,
  icon,
}: {
  label: string;
  value: string;
  delta?: string;
  hint?: string;
  icon?: ReactNode;
}) {
  return (
    <div className="rounded-lg border border-border bg-card p-4">
      <div className="flex items-start justify-between gap-3">
        <p className="min-w-0 text-xs font-medium uppercase tracking-widest text-muted-foreground">
          {label}
        </p>
        {icon ? <span className="shrink-0 text-primary">{icon}</span> : null}
      </div>
      <p className="mt-3 font-display text-3xl font-semibold tabular-nums">{value}</p>
      <div className="mt-1 flex flex-wrap items-center gap-2 text-xs">
        {delta ? (
          <span
            className={cn(
              "font-medium",
              delta.startsWith("-") ? "text-destructive" : "text-success",
            )}
          >
            {delta}
          </span>
        ) : null}
        {hint ? <span className="text-muted-foreground">{hint}</span> : null}
      </div>
    </div>
  );
}

export function Bar({ value, max, tone = "primary" }: { value: number; max: number; tone?: string }) {
  const pct = Math.min(100, Math.round((value / max) * 100));
  return (
    <div className="h-1.5 w-full overflow-hidden rounded-full bg-secondary">
      <div
        className={cn("h-full rounded-full", tone === "primary" ? "bg-primary" : `bg-${tone}`)}
        style={{ width: `${pct}%` }}
      />
    </div>
  );
}

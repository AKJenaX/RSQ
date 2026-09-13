import { useState } from "react";
import { AlertTriangle, Banknote, Cpu, Radio, Users } from "lucide-react";
import { PageHeader, Panel, StatCard } from "../components/ui-kit";
import { useActivities } from "../hooks/useActivities";

const kindMeta: Record<string, { icon: typeof Radio; tone: string; label: string }> = {
  dispatch: { icon: Radio, tone: "text-info", label: "Dispatch" },
  alert: { icon: AlertTriangle, tone: "text-destructive", label: "Alert" },
  system: { icon: Cpu, tone: "text-muted-foreground", label: "System" },
  finance: { icon: Banknote, tone: "text-success", label: "Finance" },
  volunteer: { icon: Users, tone: "text-primary", label: "Volunteer" },
};

export function ActivityPage() {
  const { activities } = useActivities();
  const [filter, setFilter] = useState("all");
  
  // Use metadata.kind if available, otherwise guess from type
  const getKind = (type: string) => {
    if (type.includes("ASSIGNED") || type.includes("DISPATCH")) return "dispatch";
    if (type.includes("ESCALATED") || type.includes("CRITICAL")) return "alert";
    if (type.includes("FINANCE") || type.includes("FUND")) return "finance";
    if (type.includes("VOLUNTEER")) return "volunteer";
    return "system";
  };

  const rows = activities.filter((a) => {
    const kind = a.metadata?.kind || getKind(a.type);
    return filter === "all" || kind === filter;
  });

  return (
    <>
      <PageHeader title="Coordination Activity" subtitle="Immutable operations log - today" />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Log Entries" value={activities.length.toString()} hint="last 24 hours" />
        <StatCard label="Escalations" value={activities.filter(a => a.type === "INCIDENT_ESCALATED").length.toString()} hint="severity raised" />
        <StatCard label="Dispatch Orders" value={activities.filter(a => a.type === "VOLUNTEER_ASSIGNED" || a.type === "RESOURCE_ASSIGNED").length.toString()} hint="units tasked" />
        <StatCard label="Approvals" value={activities.filter(a => a.type === "CASE_RESOLVED").length.toString()} hint="financial & operational" />
      </div>

      <Panel title="Timeline" description={`${rows.length} entries`}>
        <div className="mb-4 flex flex-wrap gap-2">
          {["all", ...Object.keys(kindMeta)].map((k) => (
            <button
              key={k}
              onClick={() => setFilter(k)}
              className={`rounded-full border px-3 py-1 text-xs capitalize transition-colors ${
                filter === k
                  ? "border-primary bg-primary/15 text-primary"
                  : "border-border text-muted-foreground hover:text-foreground"
              }`}
            >
              {k === "all" ? "All events" : (kindMeta[k]?.label ?? k)}
            </button>
          ))}
        </div>

        <ol className="relative space-y-5 border-l border-border pl-6">
          {rows.map((a) => {
            const kind = a.metadata?.kind || getKind(a.type);
            const meta = kindMeta[kind] ?? kindMeta["system"]!;
            const Icon = meta.icon;
            
            const description = a.metadata?.description || `${a.type.replace(/_/g, ' ')}`;
            const entityType = a.metadata?.entityType || 'System';

            return (
              <li key={a.id} className="relative">
                <span className="absolute -left-[34px] grid h-6 w-6 place-items-center rounded-full border border-border bg-card">
                  <Icon className={`h-3.5 w-3.5 ${meta.tone}`} />
                </span>
                <div className="grid grid-cols-[minmax(0,1fr)_auto] gap-3">
                  <div className="min-w-0">
                    <p className="text-sm leading-snug">{description}</p>
                    <p className="mt-0.5 text-xs text-muted-foreground">
                      {entityType} - {meta.label} - by {a.performedBy}
                    </p>
                  </div>
                  <span className="shrink-0 font-mono text-xs text-muted-foreground">
                    {a.timestamp ? new Date(a.timestamp).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : '—'}
                  </span>
                </div>
              </li>
            );
          })}
        </ol>
      </Panel>
    </>
  );
}

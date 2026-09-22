import { Link, useNavigate } from "react-router-dom";
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { AlertTriangle, Banknote, Package, Users } from "lucide-react";
import { PageHeader, Panel, StatCard, StatusBadge, Bar as MiniBar } from "../components/ui-kit";
import { currency } from "../lib/mock-data";
import { useReports } from "../hooks/useReports";
import { useVolunteers } from "../hooks/useVolunteers";
import { useResources } from "../hooks/useResources";
import { useFinance } from "../hooks/useFinance";
import { useActivities } from "../hooks/useActivities";
import { useOperationalIntelligence } from "../hooks/useOperationalIntelligence";
import { isActiveIncident, isResolvedIncident } from "../utils/statusUtils";

const PIE_COLORS = [
  "var(--color-chart-1)",
  "var(--color-chart-2)",
  "var(--color-chart-3)",
  "var(--color-chart-4)",
  "var(--color-chart-5)",
];

const tooltipStyle = {
  backgroundColor: "var(--color-card)",
  border: "1px solid var(--color-border)",
  borderRadius: "8px",
  fontSize: "12px",
  color: "var(--color-foreground)",
};

export function DashboardPage() {
  const navigate = useNavigate();
  const { reports, loadState: reportsLoadState } = useReports();
  const { volunteers, loadState: volunteersLoadState } = useVolunteers();
  const { resources, loadState: resourcesLoadState } = useResources();
  const { funds, loadState: financeLoadState } = useFinance();
  const { activities, loadState: activitiesLoadState } = useActivities();
  const intel = useOperationalIntelligence(reports, volunteers, resources);

  // Financial Stats
  const fundsAvailable = funds.reduce((acc, f) => acc + ((f.allocatedAmount || 0) - (f.utilizedAmount || 0)), 0);

  // Incident Mix calculation
  const incidentMix = reports.reduce((acc: any, r) => {
    const type = r.title || r.incidentType || "Other";
    if (!acc[type]) acc[type] = 0;
    acc[type]++;
    return acc;
  }, {});
  const incidentTypes = Object.entries(incidentMix).map(([name, count]) => ({
    name,
    value: Math.round(((count as number) / Math.max(reports.length, 1)) * 100)
  })).sort((a, b) => b.value - a.value).slice(0, 5);

  // Incident Trend calculation (last 7 days mock structure based on real data)
  const incidentTrend = Array.from({ length: 7 }).map((_, i) => {
    const d = new Date();
    d.setDate(d.getDate() - (6 - i));
    const dayStr = d.toLocaleDateString("en-US", { weekday: "short" });
    const dayStart = new Date(d.setHours(0, 0, 0, 0)).getTime();
    const dayEnd = new Date(d.setHours(23, 59, 59, 999)).getTime();
    const dayIncidents = reports.filter(r => {
      return typeof r.timestamp === 'number' && r.timestamp >= dayStart && r.timestamp <= dayEnd;
    }).length;
    const dayResolved = reports.filter(r => {
      const ts = r.resolvedAt;
      if (!ts) return false;
      return isResolvedIncident(r.status) && ts >= dayStart && ts <= dayEnd;
    }).length;
    return { day: dayStr, incidents: dayIncidents, resolved: dayResolved };
  });

  const primaryDataError = [reportsLoadState, volunteersLoadState, resourcesLoadState, financeLoadState].some(s => s === 'error' || s === 'permission-denied');

  return (
    <>
      <PageHeader
        title="Command Dashboard"
        subtitle="Operational picture - All zones"
      />

      {primaryDataError && (
        <p className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive" role="alert">
          Unable to load primary dashboard data. Please check your network and access permissions.
        </p>
      )}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <div className="cursor-pointer transition-transform hover:scale-[1.02]" onClick={() => navigate('/incidents')}>
          <StatCard
            label="Active Incidents"
            value={reportsLoadState === 'error' || reportsLoadState === 'permission-denied' ? "Unavailable" : reports.filter(r => isActiveIncident(r.status)).length.toString()}
            hint={reportsLoadState === 'error' || reportsLoadState === 'permission-denied' ? "Permission Denied" : "total active"}
            icon={<AlertTriangle className="h-4 w-4" />}
          />
        </div>
        <div className="cursor-pointer transition-transform hover:scale-[1.02]" onClick={() => navigate('/volunteers')}>
          <StatCard
            label="Volunteers Deployed"
            value={volunteersLoadState === 'error' || volunteersLoadState === 'permission-denied' ? "Unavailable" : volunteers.filter(v => v.status === "ASSIGNED").length.toString()}
            hint={volunteersLoadState === 'error' || volunteersLoadState === 'permission-denied' ? "Permission Denied" : `of ${volunteers.length} registered`}
            icon={<Users className="h-4 w-4" />}
          />
        </div>
        <div className="cursor-pointer transition-transform hover:scale-[1.02]" onClick={() => navigate('/resources')}>
          <StatCard
            label="Resource Readiness"
            value={resourcesLoadState === 'error' || resourcesLoadState === 'permission-denied' ? "Unavailable" : `${Math.round((resources.filter(r => r.status === "AVAILABLE").length / Math.max(resources.length, 1)) * 100)}%`}
            hint={resourcesLoadState === 'error' || resourcesLoadState === 'permission-denied' ? "Permission Denied" : "fleet availability"}
            icon={<Package className="h-4 w-4" />}
          />
        </div>
        <div className="cursor-pointer transition-transform hover:scale-[1.02]" onClick={() => navigate('/funds')}>
          <StatCard
            label="Funds Available"
            value={financeLoadState === 'error' || financeLoadState === 'permission-denied' ? "Unavailable" : currency(fundsAvailable)}
            hint={financeLoadState === 'error' || financeLoadState === 'permission-denied' ? "Permission Denied" : `across ${funds.length} funds`}
            icon={<Banknote className="h-4 w-4" />}
          />
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <Panel
          title="Incident Volume"
          description="Reported vs resolved, last 7 days"
          className="lg:col-span-2"
        >
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={incidentTrend}>
                <defs>
                  <linearGradient id="gInc" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="var(--color-chart-1)" stopOpacity={0.5} />
                    <stop offset="100%" stopColor="var(--color-chart-1)" stopOpacity={0} />
                  </linearGradient>
                  <linearGradient id="gRes" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="var(--color-chart-3)" stopOpacity={0.4} />
                    <stop offset="100%" stopColor="var(--color-chart-3)" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
                <XAxis dataKey="day" stroke="var(--color-muted-foreground)" fontSize={12} tickLine={false} axisLine={false} />
                <YAxis stroke="var(--color-muted-foreground)" fontSize={12} tickLine={false} axisLine={false} width={28} />
                <Tooltip contentStyle={tooltipStyle} />
                <Area type="monotone" dataKey="incidents" stroke="var(--color-chart-1)" fill="url(#gInc)" strokeWidth={2} />
                <Area type="monotone" dataKey="resolved" stroke="var(--color-chart-3)" fill="url(#gRes)" strokeWidth={2} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </Panel>

        <Panel title="Incident Mix" description="Share by category this month">
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={incidentTypes}
                  dataKey="value"
                  nameKey="name"
                  innerRadius={55}
                  outerRadius={85}
                  paddingAngle={2}
                  stroke="var(--color-card)"
                >
                  {incidentTypes.map((_, i) => (
                    <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip contentStyle={tooltipStyle} />
              </PieChart>
            </ResponsiveContainer>
          </div>
          <ul className="mt-2 grid grid-cols-2 gap-1.5 text-xs">
            {incidentTypes.map((t, i) => (
              <li key={t.name} className="flex items-center gap-2 text-muted-foreground">
                <span
                  className="h-2 w-2 shrink-0 rounded-full"
                  style={{ backgroundColor: PIE_COLORS[i % PIE_COLORS.length] }}
                />
                <span className="truncate">{t.name}</span>
                <span className="ml-auto tabular-nums text-foreground">{t.value}%</span>
              </li>
            ))}
          </ul>
        </Panel>
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <Panel
          title="Priority Incidents"
          description="Highest severity open calls"
          className="lg:col-span-2"
          actions={
            <Link to="/reports" className="text-xs font-medium text-primary hover:underline">
              View all
            </Link>
          }
        >
          <div className="overflow-x-auto">
            <table className="w-full min-w-[560px] text-sm">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wider text-muted-foreground">
                  <th className="pb-2 font-medium">ID</th>
                  <th className="pb-2 font-medium">Type</th>
                  <th className="pb-2 font-medium">Location</th>
                  <th className="pb-2 font-medium">Severity</th>
                  <th className="pb-2 font-medium">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {intel.incidentQueue.filter(r => r.status !== 'RESOLVED').slice(0, 5).map((i) => (
                  <tr key={i.reportId} className="cursor-pointer hover:bg-muted/50" onClick={() => navigate(`/incidents/${i.reportId}`)}>
                    <td className="py-2.5 font-mono text-xs text-muted-foreground">{i.reportId.slice(0, 8)}</td>
                    <td className="py-2.5 font-medium">{i.title || i.incidentType || 'Unknown'}</td>
                    <td className="py-2.5 text-muted-foreground">{i.latitude ? `${i.latitude}, ${i.longitude}` : "Unknown"}</td>
                    <td className="py-2.5"><StatusBadge label={(i.severity as string) || "UNKNOWN"} /></td>
                    <td className="py-2.5"><StatusBadge label={(i.status as string) || "OPEN"} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Panel>

        <Panel
          title="Live Activity"
          description="Latest coordination log"
          actions={
            <Link to="/activity" className="text-xs font-medium text-primary hover:underline">
              Open
            </Link>
          }
        >
          <div className="space-y-4">
            {activitiesLoadState === 'loading' ? (
              <p className="py-8 text-center text-sm text-muted-foreground">Loading activity feed...</p>
            ) : activitiesLoadState === 'permission-denied' ? (
              <p className="py-8 text-center text-sm text-destructive">Unable to load activity feed (Permission Denied).</p>
            ) : activitiesLoadState === 'error' ? (
              <p className="py-8 text-center text-sm text-destructive">Unable to load activity feed.</p>
            ) : activitiesLoadState === 'empty' ? (
              <p className="py-8 text-center text-sm text-muted-foreground">No activity recorded.</p>
            ) : (
              <ul className="space-y-3">
                {activities.slice(0, 5).map((activity) => (
                  <li 
                    key={activity.id} 
                    className={`flex gap-3 text-sm ${activity.metadata?.reportId || activity.metadata?.incidentId ? "cursor-pointer hover:bg-muted/50 p-1 -m-1 rounded-md" : ""}`}
                    onClick={() => {
                      const id = activity.metadata?.reportId || activity.metadata?.incidentId;
                      if (id) navigate(`/incidents/${id}`);
                    }}
                  >
                    <span className="mt-1 font-mono text-xs text-muted-foreground">{activity.timestamp ? new Date(activity.timestamp).toLocaleTimeString("en-US", { hour: "2-digit", minute: "2-digit" }) : '—'}</span>
                    <div className="min-w-0">
                      <p className="leading-snug">{activity.metadata?.description || activity.type.replace(/_/g, " ")}</p>
                      <p className="truncate text-xs text-muted-foreground">{activity.performedBy}</p>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </Panel>
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <Panel title="Resource Availability" description="Critical assets on hand" className="lg:col-span-1">
          <ul className="space-y-3">
            {resources.slice(0, 5).map((r) => (
              <li 
                key={r.id}
                className="cursor-pointer hover:bg-muted/50 p-2 -mx-2 rounded-md"
                onClick={() => navigate(`/resources?resourceId=${r.id}`)}
              >
                <div className="flex items-center justify-between text-sm">
                  <span className="truncate">{r.name}</span>
                  <span className="tabular-nums text-muted-foreground">
                    {r.status === "AVAILABLE" ? 1 : 0}/1
                  </span>
                </div>
                <div className="mt-1.5">
                  <MiniBar value={r.status === "AVAILABLE" ? 1 : 0} max={1} tone={r.status === "AVAILABLE" ? "success" : "high"} />
                </div>
              </li>
            ))}
          </ul>
        </Panel>

        <Panel title="Fund Utilisation" description="Spent against allocation" className="lg:col-span-2">
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={funds.map((f) => ({ fundId: f.fundId, name: (f.purpose || f.fundId).split(" ")[0], allocated: f.allocatedAmount || 0, spent: f.utilizedAmount || 0 }))}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
                <XAxis dataKey="name" stroke="var(--color-muted-foreground)" fontSize={12} tickLine={false} axisLine={false} />
                <YAxis stroke="var(--color-muted-foreground)" fontSize={12} tickLine={false} axisLine={false} width={50} tickFormatter={(v) => `${v / 1000}k`} />
                <Tooltip contentStyle={tooltipStyle} formatter={(v: any) => currency(v as number)} cursor={{fill: 'var(--color-muted)', opacity: 0.2}} />
                <Bar dataKey="allocated" fill="var(--color-chart-2)" radius={[4, 4, 0, 0]} className="cursor-pointer hover:opacity-80" onClick={(data: any) => { if (data?.fundId) navigate(`/funds?fundId=${data.fundId}`); }} />
                <Bar dataKey="spent" fill="var(--color-chart-1)" radius={[4, 4, 0, 0]} className="cursor-pointer hover:opacity-80" onClick={(data: any) => { if (data?.fundId) navigate(`/funds?fundId=${data.fundId}`); }} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Panel>
      </div>
    </>
  );
}

import {
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { PageHeader, Panel, StatCard } from "../components/ui-kit";
import { useReports } from "../hooks/useReports";
import { isResolvedIncident } from "../utils/statusUtils";

const tooltipStyle = {
  backgroundColor: "var(--color-card)",
  border: "1px solid var(--color-border)",
  borderRadius: "8px",
  fontSize: "12px",
};

export function AnalyticsPage() {
  const { reports } = useReports();
  
  const incidentTrend = Array.from({ length: 7 }).map((_, i) => {
    const d = new Date();
    d.setDate(d.getDate() - (6 - i));
    const dayStr = d.toLocaleDateString("en-US", { weekday: "short" });
    const dayStart = new Date(d.setHours(0,0,0,0)).getTime();
    const dayEnd = new Date(d.setHours(23,59,59,999)).getTime();
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

  const responseTimes = Array.from({ length: 7 }).map((_, i) => {
    const d = new Date();
    d.setDate(d.getDate() - (6 - i));
    const dayStr = d.toLocaleDateString("en-US", { weekday: "short" });
    const dayStart = new Date(d.setHours(0,0,0,0)).getTime();
    const dayEnd = new Date(d.setHours(23,59,59,999)).getTime();
    
    const dayReports = reports.filter(r => {
      return typeof r.timestamp === 'number' && r.timestamp >= dayStart && r.timestamp <= dayEnd && r.assignedAt;
    });
    let avgTime = 0;
    if (dayReports.length > 0) {
      const totalTime = dayReports.reduce((acc, r) => acc + ((r.assignedAt || 0) - (r.timestamp || 0)) / 60000, 0);
      avgTime = totalTime / dayReports.length;
    }
    
    // Cap at reasonable numbers for chart readability if data is skewed
    const finalTime = avgTime > 0 ? Math.min(Math.max(avgTime, 1), 60) : 0;
    return { day: dayStr, minutes: Number(finalTime.toFixed(1)) };
  });

  return (
    <>
      <PageHeader title="Operational Analytics" subtitle="Performance review - Last 7 Days" />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard 
          label="Avg. Response" 
          value={`${(() => {
            const withResponse = reports.filter(r => r.assignedAt && r.timestamp);
            if (withResponse.length === 0) return 'N/A';
            const totalMins = withResponse.reduce((acc, r) => acc + ((r.assignedAt || 0) - (r.timestamp || 0)) / 60000, 0);
            return (totalMins / withResponse.length).toFixed(1) + ' min';
          })()}`} 
          hint={reports.filter(r => r.assignedAt && r.timestamp).length === 0 ? "Response timestamps not available" : "Last 7 days"} 
        />
        <StatCard label="Resolution Rate" value={`${Math.round((reports.filter(r => isResolvedIncident(r.status)).length / Math.max(reports.length, 1)) * 100)}%`} hint="All time" />
        <StatCard label="Incidents / Week" value={reports.filter(r => r.timestamp && r.timestamp >= Date.now() - 7 * 24 * 60 * 60 * 1000).length.toString()} hint="Last 7 days" />
        <StatCard label="Readiness Index" value="N/A" hint="all zones" />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Panel title="Response Time Trend" description="Average minutes to first unit on scene">
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={responseTimes}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
                <XAxis dataKey="day" stroke="var(--color-muted-foreground)" fontSize={12} tickLine={false} axisLine={false} />
                <YAxis stroke="var(--color-muted-foreground)" fontSize={12} tickLine={false} axisLine={false} width={32} />
                <Tooltip contentStyle={tooltipStyle} />
                <Line type="monotone" dataKey="minutes" stroke="var(--color-chart-1)" strokeWidth={2.5} dot={{ r: 3 }} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </Panel>

        <Panel title="Zone Readiness" description="Composite score by response zone">
          <div className="flex h-64 items-center justify-center text-sm text-muted-foreground border rounded-md border-border">
            Zone readiness tracking not configured.
          </div>
        </Panel>
      </div>

      <Panel title="Reported vs Resolved" description="Weekly throughput">
        <div className="h-72">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={incidentTrend}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
              <XAxis dataKey="day" stroke="var(--color-muted-foreground)" fontSize={12} tickLine={false} axisLine={false} />
              <YAxis stroke="var(--color-muted-foreground)" fontSize={12} tickLine={false} axisLine={false} width={30} />
              <Tooltip contentStyle={tooltipStyle} cursor={{ fill: "var(--color-accent)" }} />
              <Bar dataKey="incidents" fill="var(--color-chart-1)" radius={[4, 4, 0, 0]} />
              <Bar dataKey="resolved" fill="var(--color-chart-3)" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </Panel>
    </>
  );
}

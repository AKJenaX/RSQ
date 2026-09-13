import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Filter, Plus, Search } from "lucide-react";
import { PageHeader, Panel, StatCard, StatusBadge } from "../components/ui-kit";
import { useReports } from "../hooks/useReports";
import { useAuth } from "../hooks/useAuth";
import { ReportModal } from "../components/Modals/ReportModal";
import { isActiveIncident, isResolvedIncident } from "../utils/statusUtils";

const mapSeverity = (s: string) => {
  if (s === "Moderate") return "MEDIUM";
  return s.toUpperCase();
};

const mapStatus = (s: string) => {
  if (s === "Active") return "OPEN";
  if (s === "Assigned") return "ASSIGNED";
  if (s === "In Progress") return "IN_PROGRESS";
  if (s === "Escalated") return "ESCALATED";
  return s.toUpperCase();
};

export function ReportsPage() {
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [severity, setSeverity] = useState("All");
  const [status, setStatus] = useState("All");
  const [showReportModal, setShowReportModal] = useState(false);

  const { reports, loadState } = useReports();
  const { user } = useAuth();
  const rows = useMemo(
    () =>
      reports.filter(
        (i) =>
          (severity === "All" || i.severity === mapSeverity(severity) || (severity === "Unknown" && !i.severity)) &&
          (status === "All" || i.status === mapStatus(status) || (status === "Active" && !i.status)) &&
          ((i.title || i.incidentType || "Unknown") +
            (typeof i.latitude === 'number' && typeof i.longitude === 'number' ? `${i.latitude},${i.longitude}` : "Unknown") +
            i.reportId).toLowerCase().includes(query.toLowerCase()),
      ),
    [reports, query, severity, status],
  );

  console.log("REPORTS_DUMP", JSON.stringify(reports));

  const startOfToday = new Date();
  startOfToday.setHours(0, 0, 0, 0);
  const endOfToday = new Date();
  endOfToday.setHours(23, 59, 59, 999);

  const resolvedToday = reports.filter(r =>
    isResolvedIncident(r.status) &&
    r.resolvedAt &&
    r.resolvedAt >= startOfToday.getTime() &&
    r.resolvedAt <= endOfToday.getTime()
  ).length.toString();

  return (
    <>
      <PageHeader
        title="Incident Register"
        subtitle="All reported emergencies - auto-refresh 30s"
        actions={
          <button onClick={() => setShowReportModal(true)} className="inline-flex items-center gap-2 rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground hover:opacity-90">
            <Plus className="h-4 w-4" /> Log incident
          </button>
        }
      />
      
      {loadState === 'permission-denied' && (
        <p className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive" role="alert">
          You do not have permission to view incident reports.
        </p>
      )}
      {loadState === 'error' && (
        <p className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive" role="alert">
          Live incidents could not be loaded. Please check your connection and try again.
        </p>
      )}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Active Incidents" value={reports.filter(r => isActiveIncident(r.status)).length.toString()} hint="active response" />
        <StatCard label="Critical" value={reports.filter(r => r.severity === "CRITICAL").length.toString()} hint="requires command approval" />
        <StatCard
          label="Avg. Response"
          value={`${(() => {
            const withResponse = reports.filter(r => r.assignedAt && r.timestamp);
            if (withResponse.length === 0) return 'N/A';
            const totalMins = withResponse.reduce((acc, r) => acc + ((r.assignedAt! - r.timestamp!) / 60000), 0);
            return (totalMins / withResponse.length).toFixed(1) + ' min';
          })()}`}
          hint={reports.filter(r => r.assignedAt && r.timestamp).length === 0 ? "Response timestamps not available" : "avg time to dispatch"}
        />
        <StatCard label="Resolved Today" value={resolvedToday} hint="cleared" />
      </div>

      <Panel
        title="Incident Log"
        description={`${rows.length} records match current filters`}
        actions={
          <span className="hidden items-center gap-1 text-xs text-muted-foreground sm:inline-flex">
            <Filter className="h-3.5 w-3.5" /> Filters
          </span>
        }
      >
        <div className="mb-4 grid gap-2 sm:grid-cols-[minmax(0,1fr)_auto_auto]">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search by ID, type or location"
              className="w-full rounded-md border border-input bg-background py-2 pl-9 pr-3 text-sm outline-none focus:border-ring"
            />
          </div>
          <select
            value={severity}
            onChange={(e) => setSeverity(e.target.value)}
            className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:border-ring"
          >
            {["All", "Critical", "High", "Moderate", "Low"].map((s) => (
              <option key={s}>{s}</option>
            ))}
          </select>
          <select
            value={status}
            onChange={(e) => setStatus(e.target.value)}
            className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:border-ring"
          >
            {["All", "Active", "Assigned", "In Progress", "Escalated", "Resolved"].map((s) => (
              <option key={s}>{s}</option>
            ))}
          </select>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[820px] text-sm">
            <thead>
              <tr className="text-left text-xs uppercase tracking-wider text-muted-foreground">
                <th className="pb-2 font-medium">ID</th>
                <th className="pb-2 font-medium">Type</th>
                <th className="pb-2 font-medium">Location</th>
                <th className="pb-2 font-medium">Severity</th>
                <th className="pb-2 font-medium">Status</th>
                <th className="pb-2 font-medium">Units</th>
                <th className="pb-2 font-medium">Responder</th>
                <th className="pb-2 font-medium">Reported</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {rows.map((i) => (
                <tr
                  key={i.reportId}
                  className="hover:bg-accent/40 cursor-pointer"
                  onClick={() => navigate(`/reports/${i.reportId}`)}
                >
                  <td className="py-3 font-mono text-xs text-muted-foreground">{i.reportId.slice(0, 8)}</td>
                  <td className="py-3 font-medium">{i.title || i.incidentType || 'Unknown'}</td>
                  <td className="py-3 text-muted-foreground">{typeof i.latitude === 'number' && typeof i.longitude === 'number' ? `${i.latitude.toFixed(4)}, ${i.longitude.toFixed(4)}` : 'Unknown'}</td>
                  <td className="py-3"><StatusBadge label={(i.severity as string) || "UNKNOWN"} /></td>
                  <td className="py-3"><StatusBadge label={(i.status as string) || "OPEN"} /></td>
                  <td className="py-3 tabular-nums">{i.assignedVolunteerId ? 1 : 0}</td>
                  <td className="py-3 text-muted-foreground">{i.assignedVolunteerId ? "Assigned" : "None"}</td>
                  <td className="py-3 font-mono text-xs text-muted-foreground">
                    {i.timestamp ? new Date(i.timestamp).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : '—'}
                  </td>
                </tr>
              ))}
              {rows.length === 0 ? (
                <tr>
                  <td colSpan={8} className="py-8 text-center text-sm text-muted-foreground">
                    No incidents match these filters.
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </Panel>
      {showReportModal && user ? <ReportModal authorityUid={user.uid} onClose={() => setShowReportModal(false)} onSuccess={(reportId) => navigate(`/reports/${reportId}`)} /> : null}
    </>
  );
}

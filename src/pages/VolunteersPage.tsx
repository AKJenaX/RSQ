import { useMemo, useState, useEffect } from "react";
import { useSearchParams, useNavigate, Link } from "react-router-dom";
import { Search, UserPlus } from "lucide-react";


import { PageHeader, Panel, StatCard, StatusBadge } from "../components/ui-kit";
import { useVolunteers } from "../hooks/useVolunteers";
import { useReports } from "../hooks/useReports";
import { VolunteerModal } from '../components/Modals/VolunteerModal';

const mapStatus = (s: string) => {
  if (s === "On Duty") return "AVAILABLE"; // Just mapping for the dropdown to work
  if (s === "Deployed") return "ASSIGNED";
  if (s === "Standby") return "AVAILABLE";
  if (s === "Off Duty") return "UNAVAILABLE";
  return s.toUpperCase();
};

export function VolunteersPage() {
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("All");
  const [showVolunteerModal, setShowVolunteerModal] = useState(false);
  const [editingVolunteer, setEditingVolunteer] = useState<any>(null);


  const [searchParams] = useSearchParams();
  const volunteerId = searchParams.get('volunteerId');
  const navigate = useNavigate();

  const { volunteers, loadState: volunteersLoadState } = useVolunteers();
  const { reports } = useReports();
  
  const loadState = volunteersLoadState;

  useEffect(() => {
    if (volunteerId && loadState === 'success') {
      const el = document.getElementById(`volunteer-${volunteerId}`);
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
    }
  }, [volunteerId, loadState, volunteers]);

  const rows = useMemo(
    () =>
      volunteers.filter(
        (v) =>
          (status === "All" || v.status === mapStatus(status)) &&
          ((v.name || "") + (v.role || "") + (typeof v.latitude === 'number' && typeof v.longitude === 'number' ? `${v.latitude},${v.longitude}` : "")).toLowerCase().includes(query.toLowerCase()),
      ),
    [volunteers, query, status],
  );

  const rolesMap = volunteers.reduce((acc, v) => {
    const role = v.role || "Not specified";
    acc[role] = (acc[role] || 0) + 1;
    return acc;
  }, {} as Record<string, number>);
  const rolesArr = Object.entries(rolesMap).sort((a, b) => b[1] - a[1]).slice(0, 5);
  return (
    <>
      <PageHeader
        title="Volunteer Roster"
        subtitle={`${volunteers.length} registered - ${volunteers.filter(v => v.status === "ASSIGNED").length} currently deployed`}
        actions={
          <button onClick={() => { setEditingVolunteer(null); setShowVolunteerModal(true); }} className="inline-flex items-center gap-2 rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground hover:opacity-90">
            <UserPlus className="h-4 w-4" /> Register volunteer
          </button>
        }
      />
      {loadState === 'permission-denied' && (
        <p className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive" role="alert">
          You do not have permission to view volunteer records.
        </p>
      )}
      {loadState === 'error' && (
        <p className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive" role="alert">
          Volunteer records could not be loaded. Please check your connection.
        </p>
      )}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Registered" value={volunteers.length.toString()} hint="this month" />
        <StatCard label="Deployed" value={volunteers.filter(v => v.status === "ASSIGNED").length.toString()} hint="across all zones" />
        <StatCard label="On Standby" value={volunteers.filter(v => v.status === "AVAILABLE").length.toString()} hint="ready within 30 min" />
        <StatCard label="Service Hours" value="N/A" hint="tracked personnel" />
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <Panel title="Roster" description={`${rows.length} volunteers`} className="lg:col-span-2">
          <div className="mb-4 grid gap-2 sm:grid-cols-[minmax(0,1fr)_auto]">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search name, skill or zone"
                className="w-full rounded-md border border-input bg-background py-2 pl-9 pr-3 text-sm outline-none focus:border-ring"
              />
            </div>
            <select
              value={status}
              onChange={(e) => setStatus(e.target.value)}
              className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:border-ring"
            >
              {["All", "On Duty", "Deployed", "Standby", "Off Duty"].map((s) => (
                <option key={s}>{s}</option>
              ))}
            </select>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full min-w-[700px] text-sm">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wider text-muted-foreground">
                                                                        <th className="pb-2 font-medium w-[24%]">Volunteer</th>
                  <th className="pb-2 font-medium w-[15%]">Skill</th>
                  <th className="pb-2 font-medium w-[14%]">Zone</th>
                  <th className="pb-2 font-medium w-[14%]">Status</th>
                  <th className="pb-2 font-medium w-[18%]">Assigned Report</th>
                  <th className="pb-2 font-medium w-[6%]">Hours</th>
                  <th className="pb-2 font-medium w-[9%] text-right">Contact</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {rows.map((v) => {
                  const activeReport = reports.find(r => r.status !== 'RESOLVED' && r.assignedVolunteerId === v.id);
                  const isClickable = !!activeReport;
                  return (
                    <tr 
                      key={v.id} 
                      id={`volunteer-${v.id}`} 
                      className={`${isClickable ? 'cursor-pointer hover:bg-muted/50' : 'hover:bg-accent/40'} transition-colors ${volunteerId === v.id ? 'bg-primary/5 ring-1 ring-inset ring-primary' : ''}`}
                      onClick={() => {
                        if (activeReport) {
                          navigate(`/reports/${activeReport.reportId}`);
                        } else {
                          setEditingVolunteer(v);
                          setShowVolunteerModal(true);
                        }
                      }}
                    >
                      <td className="py-3">
                        <div className="min-w-0">
                          <p className="truncate font-medium">{v.name}</p>
                          <p className="font-mono text-[11px] text-muted-foreground">{v.id}</p>
                        </div>
                      </td>
                      <td className="py-3 text-muted-foreground">{v.role || "Not specified"}</td>
                      <td className="py-3 text-muted-foreground">{typeof v.latitude === 'number' && typeof v.longitude === 'number' ? `${v.latitude.toFixed(4)}, ${v.longitude.toFixed(4)}` : "Not specified"}</td>
                      <td className="py-3"><StatusBadge label={v.status} /></td>
                      <td className="py-3">
                        {(() => {
                          if (activeReport) {
                            const identifier = activeReport.title || `Incident ${activeReport.reportId}`;
                            return (
                              <Link 
                                to={`/reports/${activeReport.reportId}`}
                                onClick={(e) => e.stopPropagation()} className="group inline-flex flex-col hover:bg-muted/50 p-1.5 -ml-1.5 rounded transition-colors min-w-0"
                                title={identifier}
                              >
                                <span className="font-medium text-foreground group-hover:text-primary transition-colors truncate max-w-[140px]">
                                  {activeReport.title || activeReport.incidentType || 'Unknown'}
                                </span>
                                <span className="font-mono text-[10px] text-muted-foreground">
                                  {activeReport.reportId.slice(0, 8)}
                                </span>
                              </Link>
                            );
                          }
                          return <span className="text-muted-foreground italic">None</span>;
                        })()}
                      </td>
                      <td className="py-3 tabular-nums text-muted-foreground">0</td>
                      <td className="py-3 font-mono text-xs text-muted-foreground text-right">{v.contact || "Not specified"}</td>
                    </tr>
                  );
                })}
                {rows.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="py-8 text-center text-sm text-muted-foreground">
                      No volunteers match these filters.
                    </td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>
        </Panel>

        <div className="space-y-4">
          <Panel title="Skill Coverage" description="Active roles">
            {rolesArr.length > 0 ? (
              <ul className="space-y-3 text-sm">
                {rolesArr.map(([skill, n]) => (
                  <li key={skill}>
                    <div className="flex justify-between">
                      <span className="truncate pr-2">{skill}</span>
                      <span className="tabular-nums text-muted-foreground">{n}</span>
                    </div>
                    <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-secondary">
                      <div
                        className="h-full rounded-full bg-primary"
                        style={{ width: `${(n / Math.max(volunteers.length, 1)) * 100}%` }}
                      />
                    </div>
                  </li>
                ))}
              </ul>
            ) : (
              <div className="text-sm text-muted-foreground">No roles configured.</div>
            )}
          </Panel>

          <Panel title="Shift Board" description="Next 24 hours">
            <div className="text-sm text-muted-foreground py-4 text-center border rounded-md border-border">
              Shift tracking not configured
            </div>
          </Panel>
        </div>
      </div>
      {showVolunteerModal ? <VolunteerModal volunteer={editingVolunteer} onClose={() => { setShowVolunteerModal(false); setEditingVolunteer(null); }} /> : null}

      
    </>
  );
}

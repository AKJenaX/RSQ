import { useState, useEffect } from "react";
import { useSearchParams } from "react-router-dom";
import { PackagePlus } from "lucide-react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { PageHeader, Panel, StatCard, StatusBadge, Bar as MiniBar } from "../components/ui-kit";
import { Link } from "react-router-dom";
import { useResources } from "../hooks/useResources";
import { useReports } from "../hooks/useReports";
import { ResourceModal } from "../components/Modals/ResourceModal";
import type { Resource } from "../types/incident";

const tooltipStyle = {
  backgroundColor: "var(--color-card)",
  border: "1px solid var(--color-border)",
  borderRadius: "8px",
  fontSize: "12px",
};

export function ResourcesPage() {
  const { resources, loadState: resourcesLoadState } = useResources();
  const { reports } = useReports();
  const [editingResource, setEditingResource] = useState<Resource | null>(null);
  const [showResourceModal, setShowResourceModal] = useState(false);
  
  const loadState = resourcesLoadState;
  const [searchParams] = useSearchParams();

  useEffect(() => {
    const resourceId = searchParams.get('resourceId');
    if (resourceId && loadState === 'success') {
      const resource = resources.find(r => r.id === resourceId);
      if (resource) {
        setEditingResource(resource);
        setShowResourceModal(true);
      }
    }
  }, [searchParams, resources, loadState]);

  const closeResourceModal = () => {
    setShowResourceModal(false);
    setEditingResource(null);
  };

  return (
    <>
      <PageHeader
        title="Resource Inventory"
        subtitle="Fleet, equipment and relief stock across all depots"
        actions={
          <button onClick={() => setShowResourceModal(true)} className="inline-flex items-center gap-2 rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground hover:opacity-90">
            <PackagePlus className="h-4 w-4" /> Add stock
          </button>
        }
      />
      {loadState === 'permission-denied' && (
        <p className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive" role="alert">
          You do not have permission to view resource records.
        </p>
      )}
      {loadState === 'error' && (
        <p className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive" role="alert">
          Resource records could not be loaded. Please check your connection.
        </p>
      )}

      {(() => {
        const totalUnits = resources.reduce((acc, r) => acc + (r.totalUnits ?? 1), 0);
        const availableUnits = resources.reduce((acc, r) => acc + (r.availableUnits ?? (r.status === 'AVAILABLE' ? 1 : 0)), 0);
        const maintenanceUnits = resources.reduce((acc, r) => acc + (r.maintenanceUnits ?? (r.status === 'UNAVAILABLE' ? 1 : 0)), 0);
        const availabilityPct = totalUnits > 0 ? Math.round((availableUnits / totalUnits) * 100) : 0;

        return (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <StatCard label="Asset Categories" value={Array.from(new Set(resources.map(r => r.type))).length.toString()} hint="tracked lines" />
            <StatCard label="Availability" value={`${availabilityPct}%`} hint="fleet-wide" />
            <StatCard label="In Maintenance" value={maintenanceUnits.toString()} hint="units offline" />
            <StatCard label="Low Stock Alerts" value={resources.filter(r => (r.availableUnits ?? (r.status === 'AVAILABLE' ? 1 : 0)) === 0).length.toString()} hint="assets" />
          </div>
        );
      })()}

      <div className="grid gap-4 lg:grid-cols-3">
        <Panel title="Depot Inventory" description="Available against total holdings" className="lg:col-span-2">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[680px] text-sm">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wider text-muted-foreground">
                  <th className="pb-2 font-medium">Asset</th>
                  <th className="pb-2 font-medium">Depot</th>
                  <th className="pb-2 font-medium">Availability</th>
                  <th className="pb-2 font-medium">On Hand</th>
                  <th className="pb-2 font-medium">Condition</th>
                  <th className="pb-2 font-medium">Assigned Report</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {resources.map((r) => {
                  const avail = r.availableUnits ?? (r.status === "AVAILABLE" ? 1 : 0);
                  const total = r.totalUnits ?? 1;
                  return (
                    <tr key={r.id} className="cursor-pointer hover:bg-accent/40" onClick={() => { setEditingResource(r); setShowResourceModal(true); }}>
                      <td className="py-3 font-medium">{r.name}</td>
                      <td className="py-3 text-muted-foreground">{typeof r.latitude === 'number' && typeof r.longitude === 'number' ? `${r.latitude.toFixed(4)}, ${r.longitude.toFixed(4)}` : "Unknown"}</td>
                      <td className="w-40 py-3">
                        <MiniBar value={avail} max={total || 1} />
                      </td>
                      <td className="py-3 tabular-nums">
                        {avail}
                        <span className="text-muted-foreground"> / {total}</span>
                      </td>
                      <td className="py-3"><StatusBadge label={r.status} /></td>
                      <td className="py-3" onClick={(e) => e.stopPropagation()}>
                        {(() => {
                          const activeReports = reports.filter(rep => rep.status !== 'RESOLVED' && rep.assignedResourceIds && rep.assignedResourceIds.includes(r.id));
                          if (activeReports.length > 0) {
                            return (
                              <div className="flex flex-col gap-1">
                                {activeReports.map(activeReport => {
                                  const identifier = activeReport.title || `Incident ${activeReport.reportId}`;
                                  return (
                                    <Link 
                                      key={activeReport.reportId}
                                      to={`/reports/${activeReport.reportId}`}
                                      className="text-primary hover:underline hover:text-primary/80 font-medium truncate max-w-[200px] block"
                                      title={identifier}
                                    >
                                      {identifier}
                                    </Link>
                                  );
                                })}
                              </div>
                            );
                          }
                          return <span className="text-muted-foreground italic">None</span>;
                        })()}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </Panel>

        <Panel title="Utilisation" description="Deployed units by asset">
          <div className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart
                layout="vertical"
                data={resources.map((r) => {
                  const avail = r.availableUnits ?? (r.status === "AVAILABLE" ? 1 : 0);
                  const total = r.totalUnits ?? 1;
                  return { name: r.name, used: Math.max(0, total - avail) };
                })}
                margin={{ left: 10 }}
              >
                <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" horizontal={false} />
                <XAxis type="number" stroke="var(--color-muted-foreground)" fontSize={11} tickLine={false} axisLine={false} />
                <YAxis
                  type="category"
                  dataKey="name"
                  stroke="var(--color-muted-foreground)"
                  fontSize={11}
                  width={92}
                  tickLine={false}
                  axisLine={false}
                />
                <Tooltip contentStyle={tooltipStyle} cursor={{ fill: "var(--color-accent)" }} />
                <Bar dataKey="used" fill="var(--color-chart-1)" radius={[0, 4, 4, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Panel>
      </div>
      {showResourceModal ? <ResourceModal resource={editingResource ?? undefined} onClose={closeResourceModal} /> : null}
    </>
  );
}

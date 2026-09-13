
import { Layers, Radio } from "lucide-react";
import { PageHeader, Panel, StatCard } from "../components/ui-kit";
import { useReports } from "../hooks/useReports";
import { LiveIncidentMap } from "../components/LiveIncidentMap";
import { isResolvedIncident } from "../utils/statusUtils";

export function LiveMapPage() {
  const { reports } = useReports();
  
  const mappedReports = reports.filter(r => !isResolvedIncident(r.status) && r.latitude && r.longitude);
  const unmappedCount = reports.filter(r => !isResolvedIncident(r.status) && (!r.latitude || !r.longitude)).length;

  return (
    <>
      <PageHeader
        title="Live Operations Map"
        subtitle="Zone coverage · GPS feed synced 12 seconds ago"
        actions={
          <span className="inline-flex items-center gap-2 rounded-md border border-border px-3 py-2 text-xs text-muted-foreground">
            <Radio className="h-4 w-4 text-success" /> Telemetry online
          </span>
        }
      />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Mapped Incidents" value={mappedReports.length.toString()} hint={`${unmappedCount} without coordinates`} />
        <StatCard label="Units In Field" value={reports.filter(r => r.assignedVolunteerId).length.toString()} hint="vehicles & teams" />
        <StatCard label="Staging Areas" value="N/A" hint="not configured" />
        <StatCard label="Coverage" value="N/A" hint="not configured" />
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <Panel
          title="Sector Overview"
          description="Interactive geographic view"
          className="lg:col-span-2"
          actions={
            <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
              <Layers className="h-3.5 w-3.5" /> Incident layer
            </span>
          }
        >
          <div className="h-[500px] w-full rounded-md overflow-hidden border border-border">
            <LiveIncidentMap incidents={reports.filter(r => r.status !== 'RESOLVED') as any} />
          </div>
        </Panel>

        <div className="space-y-4">
          <Panel title="Field Channels" description="Radio traffic status">
            <div className="py-4 text-center text-sm text-muted-foreground border rounded-md border-border">
              Not configured
            </div>
          </Panel>
        </div>
      </div>
    </>
  );
}
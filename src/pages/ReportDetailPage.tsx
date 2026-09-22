import { useState, useEffect } from 'react';
import { useNavigate, useParams } from "react-router-dom";
import { PageHeader, Panel, StatusBadge } from "../components/ui-kit";
import { useReport } from "../hooks/useReport";
import { ArrowLeft, Image as ImageIcon, MapPin, Info, Activity as ActivityIcon, Users, Box, RotateCw, CheckCircle, History, ShieldAlert } from "lucide-react";
import { useAuth } from "../hooks/useAuth";
import type { ActivityRecord, Volunteer, Resource } from "../types/incident";
import { subscribeToReportActivity, getVolunteers, getResources, escalateIncident } from "../services/incidentService";

import { AssignVolunteerModal } from "../components/Modals/AssignVolunteerModal";
import { AssignResourceModal } from "../components/Modals/AssignResourceModal";
import { ChangeStatusModal } from "../components/Modals/ChangeStatusModal";
import { ResolveIncidentModal } from "../components/Modals/ResolveIncidentModal";
import { ImageLightbox } from "../components/media/ImageLightbox";
import type { DisasterReport } from "../types/report";

const getReportMediaUrls = (report: DisasterReport): string[] => {
  const urls: string[] = [];
  if (report.imageUrls && Array.isArray(report.imageUrls) && report.imageUrls.length > 0) {
    urls.push(...report.imageUrls.filter(u => typeof u === 'string' && u.trim() !== ''));
  } else if (report.imageUrl && typeof report.imageUrl === 'string' && report.imageUrl.trim() !== '') {
    urls.push(report.imageUrl);
  }
  return Array.from(new Set(urls));
};

export function ReportDetailPage() {
  const { id } = useParams<{ id: string }>();
  // Use empty string fallback if id is undefined to satisfy useReport which expects string
  const { report, loadState, error } = useReport(id || "");
  const { user } = useAuth();
  const navigate = useNavigate();

  const [activities, setActivities] = useState<ActivityRecord[]>([]);
  const [volunteers, setVolunteers] = useState<Volunteer[]>([]);
  const [resources, setResources] = useState<Resource[]>([]);

  // Modal states
  const [activeModal, setActiveModal] = useState<'volunteer' | 'resource' | 'status' | 'resolve' | null>(null);
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null);

  useEffect(() => {
    if (!id) return;
    const unsub = subscribeToReportActivity(id, (logs) => {
      setActivities(logs);
    });
    return () => unsub();
  }, [id]);

  useEffect(() => {
    if (report?.assignedVolunteerId) {
      getVolunteers().then(vols => {
        setVolunteers(vols.filter(v => v.id === report.assignedVolunteerId));
      });
    }
    if (report?.assignedResourceIds?.length) {
      getResources().then(res => {
        setResources(res.filter(r => report.assignedResourceIds?.includes(r.id)));
      });
    }
  }, [report]);

  const handleEscalate = async () => {
    if (!user || !id) return;
    if (window.confirm('Are you sure you want to escalate this incident?')) {
      try {
        await escalateIncident(id, user.uid);
        alert('Incident escalated successfully');
      } catch (err: any) {
        alert('Error escalating: ' + err.message);
      }
    }
  };

  if (loadState === 'loading') return <div className="p-8 text-center text-muted-foreground">Loading incident data...</div>;
  if (error) return <div className="p-8 text-center text-destructive">Error loading incident: {error}</div>;
  if (!report) return <div className="p-8 text-center text-muted-foreground">Incident not found</div>;

  const mediaUrls = getReportMediaUrls(report);

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <button onClick={() => navigate('/incidents')} className="p-2 hover:bg-secondary rounded-full">
          <ArrowLeft className="h-5 w-5" />
        </button>
        <PageHeader
          title={report.title || 'Untitled Incident'}
          subtitle={"Incident ID: " + report.reportId}
          actions={
            <div className="flex gap-2">
              <StatusBadge label={report.status || 'OPEN'} />
              <StatusBadge label={report.severity || 'UNKNOWN'} />
            </div>
          }
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <div className="lg:col-span-2 space-y-4">
          <Panel title="Incident Details">
            <dl className="grid grid-cols-2 gap-4 text-sm mb-6">
              <div>
                <dt className="text-muted-foreground mb-1 flex items-center gap-2"><Info size={14} /> Description</dt>
                <dd>{report.description || 'No description provided'}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground mb-1 flex items-center gap-2"><MapPin size={14} /> Location</dt>
                <dd>{report.latitude ? `${report.latitude}, ${report.longitude}` : 'Unknown'}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground mb-1 flex items-center gap-2"><History size={14} /> Reported At</dt>
                <dd>{report.timestamp ? new Date(report.timestamp).toLocaleString() : 'Not recorded'}</dd>
              </div>
            </dl>
            <div className="mt-4">
              <dt className="text-muted-foreground mb-2 flex items-center gap-2 text-sm"><ImageIcon size={14} /> Attached Media</dt>
              {mediaUrls.length > 0 ? (
                <div className="grid gap-2 grid-cols-1 sm:grid-cols-2 md:grid-cols-3">
                  {mediaUrls.map((url, i) => (
                    <button 
                      key={i} 
                      onClick={() => setLightboxIndex(i)}
                      className="rounded-md w-full h-48 overflow-hidden relative group"
                      aria-label={`View image ${i + 1}`}
                    >
                      <img 
                        src={url} 
                        alt={`Report media ${i + 1}`} 
                        className="w-full h-full object-cover transition-transform group-hover:scale-105"
                        onError={(e) => {
                          (e.target as HTMLImageElement).src = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="100%" height="100%"><rect width="100%" height="100%" fill="%23222"/><text x="50%" y="50%" fill="%23888" font-family="sans-serif" font-size="14" text-anchor="middle" dy=".3em">Media unavailable</text></svg>';
                        }}
                      />
                      <div className="absolute inset-0 bg-black/0 group-hover:bg-black/10 transition-colors pointer-events-none" />
                    </button>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-muted-foreground italic">No media attached.</p>
              )}
            </div>
          </Panel>

          <Panel title="Activity Log">
            {activities.length > 0 ? (
              <div className="space-y-4">
                {activities.map(act => (
                  <div key={act.id} className="flex items-start gap-3 text-sm">
                    <div className="mt-0.5 p-1.5 bg-secondary rounded-full">
                      <ActivityIcon size={12} className="text-muted-foreground" />
                    </div>
                    <div>
                      <p className="font-medium">{act.type.replace(/_/g, ' ')}</p>
                      <p className="text-muted-foreground text-xs">{new Date(act.timestamp).toLocaleString()} by {act.performedBy}</p>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="text-sm text-muted-foreground">No activity logged yet.</div>
            )}
          </Panel>
        </div>

        <div className="space-y-4">
          <Panel title="Command Actions">
            <div className="flex flex-col gap-2">
              <button className="flex items-center gap-2 w-full px-4 py-2 bg-primary text-primary-foreground rounded-md text-sm font-medium hover:bg-primary/90" onClick={() => setActiveModal('volunteer')}>
                <Users size={16} /> Assign Personnel
              </button>
              <button className="flex items-center gap-2 w-full px-4 py-2 bg-secondary text-foreground rounded-md text-sm font-medium hover:bg-secondary/80" onClick={() => setActiveModal('resource')}>
                <Box size={16} /> Deploy Resources
              </button>
              <button className="flex items-center gap-2 w-full px-4 py-2 bg-secondary text-foreground rounded-md text-sm font-medium hover:bg-secondary/80" onClick={() => setActiveModal('status')}>
                <RotateCw size={16} /> Update Status
              </button>
              <button className="flex items-center gap-2 w-full px-4 py-2 bg-warning/20 text-warning rounded-md text-sm font-medium hover:bg-warning/30" onClick={handleEscalate}>
                <ShieldAlert size={16} /> Escalate Incident
              </button>
              <button className="flex items-center gap-2 w-full px-4 py-2 bg-success/20 text-success rounded-md text-sm font-medium hover:bg-success/30" onClick={() => setActiveModal('resolve')}>
                <CheckCircle size={16} /> Mark Resolved
              </button>
            </div>
          </Panel>

          <Panel title="Assigned Personnel">
            {volunteers.length > 0 ? (
              <ul className="space-y-2">
                {volunteers.map(v => (
                  <li key={v.id} className="flex justify-between items-center text-sm p-2 bg-secondary rounded-md">
                    <span>{v.name}</span>
                    <StatusBadge label={v.status} />
                  </li>
                ))}
              </ul>
            ) : (
              <div className="text-sm text-muted-foreground">No personnel assigned.</div>
            )}
          </Panel>

          <Panel title="Deployed Resources">
            {resources.length > 0 ? (
              <ul className="space-y-2">
                {resources.map(r => (
                  <li key={r.id} className="flex justify-between items-center text-sm p-2 bg-secondary rounded-md">
                    <span>{r.name}</span>
                    <StatusBadge label={r.status} />
                  </li>
                ))}
              </ul>
            ) : (
              <div className="text-sm text-muted-foreground">No resources deployed.</div>
            )}
          </Panel>
        </div>
      </div>

      {activeModal === 'volunteer' && <AssignVolunteerModal reportId={report.reportId} authorityUid={user?.uid || ""} onSuccess={() => setActiveModal(null)} onClose={() => setActiveModal(null)} />}
      {activeModal === 'resource' && <AssignResourceModal reportId={report.reportId} authorityUid={user?.uid || ""} onSuccess={() => setActiveModal(null)} onClose={() => setActiveModal(null)} />}
      {activeModal === 'status' && <ChangeStatusModal reportId={report.reportId} currentStatus={report.status || "OPEN"} authorityUid={user?.uid || ""} onSuccess={() => setActiveModal(null)} onClose={() => setActiveModal(null)} />}
      {activeModal === 'resolve' && (
        <ResolveIncidentModal reportId={report.reportId} authorityUid={user?.uid || ""} onSuccess={() => setActiveModal(null)} onClose={() => setActiveModal(null)} />
      )}

      {lightboxIndex !== null && (
        <ImageLightbox 
          images={mediaUrls} 
          initialIndex={lightboxIndex} 
          onClose={() => setLightboxIndex(null)} 
        />
      )}
    </div>
  );
}

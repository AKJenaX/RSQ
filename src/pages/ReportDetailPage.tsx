import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useReport } from '../hooks/useReport';
import { SeverityBadge, StatusBadge } from '../components/Badge';
import { LoadingSpinner } from '../components/LoadingSpinner';
import { ErrorState } from '../components/ErrorState';
import { formatTimestamp, formatReportId } from '../utils/formatters';
import { ArrowLeft, Image as ImageIcon, MapPin, AlignLeft, Info, ImageOff, Activity as ActivityIcon, Users, Box, RotateCw, CheckCircle, CheckCircle2, History } from 'lucide-react';
import { useAuth } from '../hooks/useAuth';
import type { ActivityRecord } from '../types/incident';
import { subscribeToReportActivity, getVolunteers, getResources } from '../services/incidentService';
import type { Volunteer, Resource } from '../types/incident';

import { AssignVolunteerModal } from '../components/Modals/AssignVolunteerModal';
import { AssignResourceModal } from '../components/Modals/AssignResourceModal';
import { ChangeStatusModal } from '../components/Modals/ChangeStatusModal';
import { ResolveIncidentModal } from '../components/Modals/ResolveIncidentModal';

interface ReportImageProps {
  url: string | undefined;
}

function ReportImage({ url }: ReportImageProps): React.ReactElement | null {
  const [failed, setFailed] = useState(false);
  const [loaded, setLoaded] = useState(false);

  if (!url) return null;

  if (failed) {
    return (
      <div className="card">
        <div className="section-title mb-4 flex items-center gap-2">
          <ImageIcon size={16} /> Attached Media
        </div>
        <div className="media-item-error border border-surface-border rounded bg-surface-1 p-8 text-center text-tertiary flex flex-col items-center gap-2">
          <ImageOff size={24} />
          <span className="text-sm">Unable to load report image</span>
        </div>
      </div>
    );
  }

  return (
    <div className="card">
      <div className="section-title mb-4 flex items-center gap-2">
        <ImageIcon size={16} /> Attached Media
      </div>
      <div className="relative rounded overflow-hidden" style={{ minHeight: loaded ? 'auto' : '180px', background: 'var(--surface-1)' }}>
        {!loaded && (
          <div className="absolute inset-0 flex items-center justify-center text-sm text-tertiary">
            <span>Loading media…</span>
          </div>
        )}
        <img
          src={url}
          alt="Report media"
          onLoad={() => setLoaded(true)}
          onError={() => {
            setLoaded(true);
            setFailed(true);
          }}
          className="w-full object-cover"
          style={{ opacity: loaded ? 1 : 0, transition: 'opacity 0.3s' }}
        />
      </div>
    </div>
  );
}

interface FieldProps {
  label: string;
  children: React.ReactNode;
}

function Field({ label, children }: FieldProps): React.ReactElement {
  return (
    <div className="flex flex-col gap-1 mb-4 last:mb-0">
      <span className="text-xs font-semibold uppercase text-tertiary">{label}</span>
      <div className="text-sm font-medium text-primary">{children}</div>
    </div>
  );
}

export function ReportDetailPage(): React.ReactElement {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { report, loadState, error } = useReport(id);
  const { user } = useAuth();
  
  const [activeModal, setActiveModal] = useState<'volunteer' | 'resource' | 'status' | 'resolve' | null>(null);
  const [activities, setActivities] = useState<ActivityRecord[]>([]);
  const [volunteers, setVolunteers] = useState<Record<string, Volunteer>>({});
  const [resources, setResources] = useState<Record<string, Resource>>({});

  useEffect(() => {
    if (!id) return;
    const unsub = subscribeToReportActivity(id, (data) => setActivities(data));
    return () => unsub();
  }, [id]);

  useEffect(() => {
    getVolunteers().then(vols => {
      const map: Record<string, Volunteer> = {};
      vols.forEach(v => map[v.id] = v);
      setVolunteers(map);
    });
    getResources().then(res => {
      const map: Record<string, Resource> = {};
      res.forEach(r => map[r.id] = r);
      setResources(map);
    });
  }, []);

  if (loadState === 'loading') {
    return <LoadingSpinner message="Loading incident details…" />;
  }

  if (loadState === 'not-found' || loadState === 'error' || loadState === 'permission-denied' || loadState === 'not-configured') {
    return (
      <>
        <button className="btn-secondary mb-6 inline-flex" onClick={() => navigate('/reports')}>
          <ArrowLeft size={16} className="mr-2" /> Back to Incidents
        </button>
        <ErrorState type={loadState === 'not-configured' ? 'not-configured' : loadState} message={error ?? undefined} />
      </>
    );
  }

  if (!report) return <></>;

  const hasCoordinates = report.latitude !== undefined || report.longitude !== undefined;
  const isResolved = report.status === 'RESOLVED';
  const authorityUid = user?.uid ?? 'unknown-authority';

  return (
    <>
      {activeModal === 'volunteer' && id && (
        <AssignVolunteerModal reportId={id} authorityUid={authorityUid} onClose={() => setActiveModal(null)} onSuccess={() => setActiveModal(null)} />
      )}
      {activeModal === 'resource' && id && (
        <AssignResourceModal reportId={id} authorityUid={authorityUid} onClose={() => setActiveModal(null)} onSuccess={() => setActiveModal(null)} />
      )}
      {activeModal === 'status' && id && (
        <ChangeStatusModal reportId={id} currentStatus={report.status || 'OPEN'} authorityUid={authorityUid} onClose={() => setActiveModal(null)} onSuccess={() => setActiveModal(null)} />
      )}
      {activeModal === 'resolve' && id && (
        <ResolveIncidentModal reportId={id} authorityUid={authorityUid} onClose={() => setActiveModal(null)} onSuccess={() => setActiveModal(null)} />
      )}

      <button className="text-xs font-semibold text-secondary hover:text-primary transition-colors flex items-center mb-6" onClick={() => navigate('/reports')}>
        <ArrowLeft size={14} className="mr-1" /> BACK TO INCIDENTS
      </button>

      <div className="enhanced-header mb-6">
        <div>
          <h1 className="page-title">{report.title ?? 'Emergency Report'}</h1>
          <p className="page-subtitle" style={{ fontFamily: "'JetBrains Mono', monospace" }}>{formatReportId(report.reportId)}</p>
        </div>
        <div className="header-right-context">
          <SeverityBadge value={report.severity} />
          <StatusBadge value={report.status} />
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* ── Left Column: Information ── */}
        <div className="lg:col-span-2 flex flex-col gap-6">
          
          {report.description && (
            <div className="card">
              <div className="section-title mb-4 flex items-center gap-2">
                <AlignLeft size={16} /> Summary
              </div>
              <p className="text-base text-primary leading-relaxed whitespace-pre-wrap">
                {report.description}
              </p>
            </div>
          )}

          {hasCoordinates && (
            <div className="card">
              <div className="section-title mb-4 flex items-center gap-2">
                <MapPin size={16} /> Location
              </div>
              <div className="flex gap-8">
                {report.latitude !== undefined && (
                  <Field label="Latitude">{report.latitude}</Field>
                )}
                {report.longitude !== undefined && (
                  <Field label="Longitude">{report.longitude}</Field>
                )}
              </div>
            </div>
          )}

          <ReportImage url={report.imageUrl} />

          {/* Activity Timeline */}
          <div className="card">
            <div className="section-title mb-4 flex items-center gap-2">
              <History size={16} /> Activity History
            </div>
            {activities.length === 0 ? (
              <p className="text-sm text-tertiary">No activity recorded yet.</p>
            ) : (
              <div className="activity-timeline-dense mt-2">
                {activities.map(act => (
                  <div key={act.id} className="activity-dense-item">
                    <div className="activity-dense-icon">
                      {act.type === 'STATUS_CHANGED' ? <RotateCw size={12} className="text-secondary" /> :
                       act.type === 'VOLUNTEER_ASSIGNED' ? <Users size={12} className="text-info" /> :
                       act.type === 'RESOURCE_ASSIGNED' ? <Box size={12} className="text-info" /> :
                       act.type === 'CASE_RESOLVED' ? <CheckCircle size={12} className="text-success" /> :
                       act.type === 'REPORT_CREATED' ? <ActivityIcon size={12} className="text-error" /> :
                       <ActivityIcon size={12} className="text-tertiary" />}
                    </div>
                    <div className="activity-dense-content">
                      <span className="activity-dense-desc">
                        {act.type === 'STATUS_CHANGED' ? `Status changed to ${act.metadata?.to || 'Unknown'}` :
                         act.type === 'VOLUNTEER_ASSIGNED' ? 'Personnel assigned' :
                         act.type === 'RESOURCE_ASSIGNED' ? 'Resources assigned' :
                         act.type === 'CASE_RESOLVED' ? 'Incident resolved' :
                         act.type === 'REPORT_CREATED' ? 'Incident reported' :
                         'Unknown action'}
                      </span>
                      <span className="text-xs text-secondary mt-1">
                        {act.type === 'VOLUNTEER_ASSIGNED' && (act.metadata?.volunteerId ? (volunteers[act.metadata.volunteerId as string]?.name || act.metadata.volunteerId) : '')}
                        {act.type === 'RESOURCE_ASSIGNED' && act.metadata?.resourceIds && ((act.metadata.resourceIds as string[]).map((rid: string) => resources[rid]?.name || rid).join(', '))}
                        {act.type === 'CASE_RESOLVED' && act.metadata?.resolutionNote}
                      </span>
                      <span className="activity-dense-time mt-1">
                        {formatTimestamp(act.timestamp)} • {act.performedBy === authorityUid ? 'You' : (act.performedBy || 'Authority')}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* ── Right Column: Operations ── */}
        <div className="flex flex-col gap-6">
          
          {/* ACTIONS */}
          <div className="card border-error">
            <div className="section-title mb-4 flex items-center gap-2 text-primary">
              <RotateCw size={16} /> Operational Actions
            </div>
            
            {isResolved ? (
              <div className="bg-success/10 border border-success/20 rounded-md p-3 text-success text-xs font-medium mb-4 flex items-start gap-2">
                <CheckCircle size={14} className="mt-0.5 shrink-0" />
                This incident has been resolved. No further operational actions can be taken.
              </div>
            ) : null}
            
            <div className="flex flex-col gap-3">
              <button className="btn-secondary w-full justify-start" onClick={() => setActiveModal('volunteer')} disabled={isResolved}>
                <Users size={16} className="mr-3" /> Assign Personnel
              </button>
              <button className="btn-secondary w-full justify-start" onClick={() => setActiveModal('resource')} disabled={isResolved}>
                <Box size={16} className="mr-3" /> Assign Resources
              </button>
              <button className="btn-secondary w-full justify-start" onClick={() => setActiveModal('status')} disabled={isResolved}>
                <RotateCw size={16} className="mr-3" /> Update Status
              </button>
              
              <div className="h-px bg-surface-border my-2" />
              
              <button className="btn-primary w-full justify-start" style={{ background: 'var(--success)' }} onClick={() => setActiveModal('resolve')} disabled={isResolved}>
                <CheckCircle2 size={16} className="mr-3 text-white" /> <span className="text-white">Resolve Incident</span>
              </button>
            </div>
          </div>

          {/* ASSIGNMENTS */}
          <div className="card">
            <div className="section-title mb-4 flex items-center gap-2">
              <ActivityIcon size={16} /> Current Assignments
            </div>
            
            <Field label="Assigned Personnel">
              {report.assignedVolunteerId ? (
                <div className="flex items-center gap-2">
                  <span className="w-2 h-2 rounded-full bg-info"></span>
                  {volunteers[report.assignedVolunteerId]?.name || report.assignedVolunteerId}
                </div>
              ) : (
                <span className="text-tertiary flex items-center gap-2"><span className="w-2 h-2 rounded-full bg-surface-border"></span>Unassigned</span>
              )}
            </Field>
            
            <Field label="Assigned Resources">
              {report.assignedResourceIds && report.assignedResourceIds.length > 0 ? (
                <div className="flex flex-col gap-2">
                  {report.assignedResourceIds.map(rid => (
                    <div key={rid} className="flex items-center gap-2">
                      <span className="w-2 h-2 rounded-full bg-info"></span>
                      {resources[rid]?.name || rid}
                    </div>
                  ))}
                </div>
              ) : (
                <span className="text-tertiary flex items-center gap-2"><span className="w-2 h-2 rounded-full bg-surface-border"></span>None deployed</span>
              )}
            </Field>
          </div>

          {/* METADATA */}
          <div className="card">
            <div className="section-title mb-4 flex items-center gap-2">
              <Info size={16} /> Metadata
            </div>
            {report.timestamp !== undefined && (
              <Field label="Reported At">
                {formatTimestamp(report.timestamp)}
              </Field>
            )}
            {report.userId && (
              <Field label="Reporter ID">
                <span style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: '0.75rem', wordBreak: 'break-all', color: 'var(--text-secondary)' }}>
                  {report.userId}
                </span>
              </Field>
            )}
            
            {isResolved && report.resolvedAt && (
              <>
                <div className="h-px bg-surface-border my-4" />
                <Field label="Resolved At">
                  {formatTimestamp(report.resolvedAt)}
                </Field>
                {report.resolutionNote && (
                  <Field label="Resolution Note">
                    <span className="text-secondary italic">{report.resolutionNote}</span>
                  </Field>
                )}
              </>
            )}
          </div>
        </div>
      </div>
    </>
  );
}

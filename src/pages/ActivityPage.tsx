import React, { useMemo } from 'react';
import { useReports } from '../hooks/useReports';
import { LoadingSpinner } from '../components/LoadingSpinner';
import { ErrorState } from '../components/ErrorState';
import { EmptyState } from '../components/EmptyState';
import { AlertTriangle, Activity, CheckCircle } from 'lucide-react';
import { formatRelativeTime } from '../utils/formatters';

export function ActivityPage(): React.ReactElement {
  const { reports, loadState, error } = useReports();

  const activities = useMemo(() => {
    const list: { id: string, timestamp: number, type: string, description: string, reportId: string }[] = [];
    
    reports.forEach(r => {
      if (r.timestamp) {
        list.push({ id: `act-new-${r.reportId}`, timestamp: r.timestamp, type: 'NEW', description: `Incident reported: ${r.title || r.reportId}`, reportId: r.reportId });
      }
      if (r.assignedAt) {
        list.push({ id: `act-assign-${r.reportId}`, timestamp: r.assignedAt, type: 'ASSIGNED', description: `Units assigned to ${r.title || r.reportId}`, reportId: r.reportId });
      }
      if (r.resolvedAt) {
        list.push({ id: `act-resolve-${r.reportId}`, timestamp: r.resolvedAt, type: 'RESOLVED', description: `Incident resolved: ${r.title || r.reportId}`, reportId: r.reportId });
      }
    });

    list.sort((a, b) => b.timestamp - a.timestamp);
    return list;
  }, [reports]);

  return (
    <>
      <div className="page-header">
        <h1 className="page-title">Activity Monitoring</h1>
        <p className="page-subtitle">
          System-wide operational timeline
        </p>
      </div>

      {loadState === 'loading' && <LoadingSpinner message="Loading activity…" />}

      {(loadState === 'error' || loadState === 'permission-denied' || loadState === 'not-configured') && (
        <ErrorState type={loadState === 'not-configured' ? 'not-configured' : loadState} message={error ?? undefined} />
      )}

      {loadState === 'success' && activities.length === 0 && (
        <EmptyState title="No activity" message="No operational activity has been recorded yet." type="default" />
      )}

      {loadState === 'success' && activities.length > 0 && (
        <div className="bg-card border border-surface-border rounded-lg overflow-hidden">
          <div className="p-4 border-b border-surface-border">
             <h2 className="text-sm font-semibold text-primary">System Timeline</h2>
          </div>
          <div className="p-4">
             <div className="activity-timeline-dense">
              {activities.map(act => (
                <div key={act.id} className="activity-dense-item">
                  <div className="activity-dense-icon">
                    {act.type === 'NEW' ? <AlertTriangle size={12} className="text-error" /> : 
                     act.type === 'RESOLVED' ? <CheckCircle size={12} className="text-success" /> : 
                     <Activity size={12} className="text-secondary" />}
                  </div>
                  <div className="activity-dense-content">
                    <span className="activity-dense-desc text-primary font-medium">{act.description}</span>
                    <span className="activity-dense-time">{formatRelativeTime(act.timestamp)}</span>
                  </div>
                  <div className="ml-auto">
                     <button onClick={() => window.location.href = `/reports/${act.reportId}`} className="btn-secondary" style={{ padding: '2px 8px', fontSize: '0.65rem' }}>View Incident</button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </>
  );
}

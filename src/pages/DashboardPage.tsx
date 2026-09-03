import React from 'react';
import { useReports } from '../hooks/useReports';
import { useVolunteers } from '../hooks/useVolunteers';
import { useResources } from '../hooks/useResources';
import { useOperationalIntelligence } from '../hooks/useOperationalIntelligence';
import { StatCard } from '../components/StatCard';
import { SeverityBadge, StatusBadge } from '../components/Badge';
import { LoadingSpinner } from '../components/LoadingSpinner';
import { ErrorState } from '../components/ErrorState';
import { EmptyState } from '../components/EmptyState';
import { AlertTriangle, Activity, ShieldAlert, CheckCircle } from 'lucide-react';
import { formatRelativeTime, formatReportId } from '../utils/formatters';
import { Link } from 'react-router-dom';

export function DashboardPage(): React.ReactElement {
  const { reports, loadState: reportsLoadState, error: reportsError } = useReports();
  const { volunteers, loadState: volLoadState } = useVolunteers();
  const { resources, loadState: resLoadState } = useResources();

  const loading = reportsLoadState === 'loading' || volLoadState === 'loading' || resLoadState === 'loading';
  const error = reportsError; 

  const intelligence = useOperationalIntelligence(reports, volunteers, resources);
  
  if (reportsLoadState === 'not-configured') {
    return <ErrorState type="not-configured" />;
  }

  // Calculate Health Class
  let healthStripClass = 'alert-nominal-strip';
  let HealthIcon = CheckCircle;
  if (intelligence.healthStatus === 'CRITICAL') {
    healthStripClass = 'alert-critical';
    HealthIcon = ShieldAlert;
  } else if (intelligence.healthStatus === 'WATCH') {
    healthStripClass = 'alert-warning';
    HealthIcon = AlertTriangle;
  }

  return (
    <div className="dashboard-root">
      {/* ── COMMAND HEADER ── */}
      <div className="command-header">
        <div className="command-header-main">
          <div className="command-title-group">
            <h1 className="command-title">Operations Command Center</h1>
            <p className="command-subtitle">Real-time emergency response overview</p>
          </div>
          <div className="command-live-badge">
            <div className="live-dot-pulse" aria-hidden="true" />
            LIVE
          </div>
        </div>
        
        <div className="command-status-strip">
          <div className="strip-item">
            <span className="strip-label">Active Incidents</span>
            <span className="strip-value">{intelligence.activeCount}</span>
          </div>
          <div className="strip-item">
            <span className="strip-label">Critical</span>
            <span className="strip-value text-error">{intelligence.criticalCount}</span>
          </div>
          <div className="strip-item">
            <span className="strip-label">Unassigned</span>
            <span className="strip-value text-warning">{intelligence.unassignedCount}</span>
          </div>
          <div className="strip-item">
            <span className="strip-label">Vols Available</span>
            <span className={`strip-value ${intelligence.volunteersAvailable === 0 ? 'text-error' : 'text-success'}`}>{intelligence.volunteersAvailable}</span>
          </div>
          <div className="strip-item">
            <span className="strip-label">Res Available</span>
            <span className={`strip-value ${intelligence.resourcesAvailable === 0 ? 'text-error' : 'text-success'}`}>{intelligence.resourcesAvailable}</span>
          </div>
        </div>
      </div>

      {(reportsLoadState === 'error' || reportsLoadState === 'permission-denied') && (
        <ErrorState type={reportsLoadState} message={error ?? undefined} />
      )}

      {loading && <LoadingSpinner message="Syncing operational data…" />}

      {/* ── OPERATIONAL HEALTH & ALERTS ── */}
      <div className="dashboard-alerts">
        {!loading && (
          <div className={`alert-banner ${healthStripClass}`} style={{ alignItems: 'center' }}>
            <HealthIcon size={16} className="mr-2" />
            <div className="alert-content">
              <span className="font-semibold uppercase mr-2">{intelligence.healthStatus}</span>
              <span className="alert-message text-tertiary hidden-mobile">— {intelligence.healthReason}</span>
              <span className="alert-link ml-auto">{intelligence.summaryStatement}</span>
            </div>
          </div>
        )}
      </div>

      {/* ── WHAT NEEDS ATTENTION NOW? ── */}
      {!loading && intelligence.attentionItems.length > 0 && (
        <div className="dashboard-section priority-section mb-6 border-error">
          <div className="section-header bg-error-subtle">
            <h2 className="section-title text-error flex items-center gap-2">
              <ShieldAlert size={16} /> Requires Attention
            </h2>
          </div>
          <div className="incident-table-container">
            <table className="incident-table">
              <tbody>
                {intelligence.attentionItems.slice(0, 3).map((report) => {
                  return (
                    <tr key={report.reportId} className="incident-row severity-critical" onClick={() => window.location.href = `/reports/${report.reportId}`}>
                      <td><SeverityBadge value={report.severity} /></td>
                      <td>
                        <div className="incident-title">{report.title || 'Emergency Report'}</div>
                        <div className="text-error text-xs font-semibold">{report.priorityReason}</div>
                      </td>
                      <td><StatusBadge value={report.status} /></td>
                      <td className="incident-time text-error font-medium">{formatRelativeTime(report.timestamp)}</td>
                      <td>
                        <button className="btn-secondary" style={{ padding: '4px 12px', fontSize: '0.7rem' }}>VIEW INCIDENT</button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* ── ESSENTIAL METRICS ── */}
      <div className="dashboard-metrics">
        <StatCard label="Critical" value={intelligence.criticalCount} indicatorClass="critical" description="Requires immediate attention" />
        <StatCard label="Unassigned" value={intelligence.unassignedCount} indicatorClass="open" description="Awaiting response" />
        <StatCard label="Total Active" value={intelligence.activeCount} indicatorClass="total" description="Current workload" />
        
        {/* Render 0-availability as explicit warning stat cards */}
        {intelligence.volunteersAvailable === 0 ? (
          <div className="stat-card border-error bg-error-subtle">
             <div className="stat-card-label text-error">Vols Available</div>
             <div className="stat-card-value-row">
               <span className="stat-card-value text-error">0</span>
               <ShieldAlert size={16} className="text-error" />
             </div>
             <div className="stat-card-desc text-error">No personnel available</div>
          </div>
        ) : (
          <StatCard label="Vols Available" value={intelligence.volunteersAvailable} indicatorClass="resolved" description={`${intelligence.volunteersTotal} Total`} />
        )}

        {intelligence.resourcesAvailable === 0 ? (
           <div className="stat-card border-error bg-error-subtle">
             <div className="stat-card-label text-error">Res Available</div>
             <div className="stat-card-value-row">
               <span className="stat-card-value text-error">0</span>
               <ShieldAlert size={16} className="text-error" />
             </div>
             <div className="stat-card-desc text-error">No resources available</div>
          </div>
        ) : (
          <StatCard label="Res Available" value={intelligence.resourcesAvailable} indicatorClass="resolved" description={`${intelligence.resourcesTotal} Total`} />
        )}
      </div>

      {/* ── MAIN OPERATIONS AREA (TRIMMED FOR OVERVIEW) ── */}
      <div className="dashboard-main-area" style={{ gridTemplateColumns: '2fr 1fr' }}>
        
        <div className="dashboard-primary">
          {/* RECENT INCIDENTS (COMPACT) */}
          <section className="dashboard-section incident-section">
            <div className="section-header">
              <h2 className="section-title">Recent Incidents</h2>
              <Link to="/reports" className="view-all-link">View all incidents &rarr;</Link>
            </div>
            
            {intelligence.incidentQueue.length === 0 ? (
              <EmptyState title="No active incidents" message="All operations nominal. There are no ongoing emergencies requiring attention." type="default" />
            ) : (
              <div className="incident-table-container">
                <table className="incident-table">
                  <tbody>
                    {intelligence.incidentQueue.slice(0, 5).map((report) => {
                      const volName = report.assignedVolunteerId ? intelligence.volMap.get(report.assignedVolunteerId) : null;
                      
                      return (
                        <tr key={report.reportId} className={`incident-row severity-${report.severity?.toLowerCase() || 'unknown'}`} onClick={() => window.location.href = `/reports/${report.reportId}`}>
                          <td><SeverityBadge value={report.severity} /></td>
                          <td>
                            <div className="incident-title">{report.title || 'Emergency Report'}</div>
                            <div className="incident-id">{formatReportId(report.reportId)}</div>
                          </td>
                          <td><StatusBadge value={report.status} /></td>
                          <td>
                            <div className="incident-units">
                              {volName ? <span className="unit-badge vol text-[10px]">{volName}</span> : <span className="unit-badge empty text-error font-medium text-[10px]">UNASSIGNED</span>}
                            </div>
                          </td>
                          <td className="incident-time">{formatRelativeTime(report.timestamp)}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </div>
        
        <div className="dashboard-secondary">
          {/* CAPACITY SUMMARY */}
          <section className="dashboard-section ops-panel">
            <div className="section-header">
              <h2 className="section-title">Capacity Overview</h2>
            </div>
            <div className="p-4 flex flex-col gap-4">
               <div className="flex justify-between items-center">
                  <div className="flex flex-col">
                     <span className="text-sm font-bold text-primary">Volunteers</span>
                     <span className="text-[10px] text-tertiary uppercase">{intelligence.volunteersAvailable} / {intelligence.volunteersTotal} Available</span>
                  </div>
                  <Link to="/volunteers" className="btn-secondary" style={{ padding: '2px 8px', fontSize: '0.65rem' }}>View</Link>
               </div>
               <div className="flex justify-between items-center">
                  <div className="flex flex-col">
                     <span className="text-sm font-bold text-primary">Resources</span>
                     <span className="text-[10px] text-tertiary uppercase">{intelligence.resourcesAvailable} / {intelligence.resourcesTotal} Available</span>
                  </div>
                  <Link to="/resources" className="btn-secondary" style={{ padding: '2px 8px', fontSize: '0.65rem' }}>View</Link>
               </div>
               
               {intelligence.capacityWarnings.length > 0 && (
                 <div className="text-[10px] text-error font-medium mt-2">
                   <ShieldAlert size={10} className="inline mr-1"/> Capacity warnings active
                 </div>
               )}
            </div>
          </section>

          {/* RECENT ACTIVITY SUMMARY */}
          <section className="dashboard-section activity-panel">
            <div className="section-header border-b-0 pb-0">
              <h2 className="section-title">Latest Activity</h2>
              <Link to="/activity" className="view-all-link">View all &rarr;</Link>
            </div>
            <div className="activity-timeline-dense p-4">
              {intelligence.incidentQueue.slice(0, 3).map(report => (
                  <div key={`act-${report.reportId}`} className="activity-dense-item">
                    <div className="activity-dense-icon">
                      {report.status === 'RESOLVED' ? <CheckCircle size={12} className="text-success" /> : 
                       report.status === 'OPEN' ? <AlertTriangle size={12} className="text-error" /> : 
                       <Activity size={12} className="text-info" />}
                    </div>
                    <div className="activity-dense-content">
                      <span className="activity-dense-desc">
                        {report.status === 'RESOLVED' ? `Resolved: ${report.title}` :
                         report.status === 'OPEN' ? `New: ${report.title}` :
                         `Update: ${report.title}`}
                      </span>
                      <span className="activity-dense-time">{formatRelativeTime(report.timestamp)}</span>
                    </div>
                  </div>
              ))}
              {intelligence.incidentQueue.length === 0 && (
                  <div className="ops-empty-row">No recent activity.</div>
              )}
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}

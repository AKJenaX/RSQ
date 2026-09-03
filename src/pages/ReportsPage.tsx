import React, { useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useReports } from '../hooks/useReports';
import { useVolunteers } from '../hooks/useVolunteers';
import { useResources } from '../hooks/useResources';
import { useOperationalIntelligence } from '../hooks/useOperationalIntelligence';
import { SeverityBadge, StatusBadge } from '../components/Badge';
import { LoadingSpinner } from '../components/LoadingSpinner';
import { ErrorState } from '../components/ErrorState';
import { EmptyState } from '../components/EmptyState';
import { Search } from 'lucide-react';
import {
  formatTimestamp,
  formatCoordinates,
  formatReportId,
  truncate,
  getSeverityVariant,
} from '../utils/formatters';

export function ReportsPage(): React.ReactElement {
  const { reports, loadState: reportsLoadState, error } = useReports();
  const { volunteers, loadState: volsLoadState } = useVolunteers();
  const { resources, loadState: resLoadState } = useResources();
  const navigate = useNavigate();

  const loading = reportsLoadState === 'loading' || volsLoadState === 'loading' || resLoadState === 'loading';
  const intelligence = useOperationalIntelligence(reports, volunteers, resources);

  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [severityFilter, setSeverityFilter] = useState('');

  const filtered = useMemo(() => {
    return reports.filter((r) => {
      const q = search.toLowerCase();
      const matchesSearch =
        !q ||
        r.reportId.toLowerCase().includes(q) ||
        (r.title ?? '').toLowerCase().includes(q) ||
        (r.description ?? '').toLowerCase().includes(q) ||
        formatCoordinates(r.latitude, r.longitude).toLowerCase().includes(q);

      const matchesStatus =
        !statusFilter ||
        (r.status ?? '').toUpperCase() === statusFilter.toUpperCase();

      const matchesSeverity =
        !severityFilter ||
        (r.severity ?? '').toUpperCase() === severityFilter.toUpperCase();

      return matchesSearch && matchesStatus && matchesSeverity;
    });
  }, [reports, search, statusFilter, severityFilter]);

  return (
    <>
      <div className="page-header">
        <h1 className="page-title">Incidents</h1>
        <p className="page-subtitle">
          Real-time incident management workspace
          {reportsLoadState === 'success' && ` · ${reports.length} total`}
        </p>
      </div>

      {reportsLoadState === 'success' && reports.length > 0 && (
        <div className="table-toolbar">
          <div className="search-input-wrapper">
            <Search className="search-icon" size={16} aria-hidden="true" />
            <input
              id="reports-search"
              type="search"
              className="search-input"
              placeholder="Search by title, ID, location…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              aria-label="Search incidents"
            />
          </div>

          <select
            id="filter-status"
            className="filter-select"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            aria-label="Filter by status"
          >
            <option value="">All Statuses</option>
            <option value="OPEN">Open</option>
            <option value="ASSIGNED">Assigned</option>
            <option value="IN_PROGRESS">In Progress</option>
            <option value="RESOLVED">Resolved</option>
          </select>

          <select
            id="filter-severity"
            className="filter-select"
            value={severityFilter}
            onChange={(e) => setSeverityFilter(e.target.value)}
            aria-label="Filter by severity"
          >
            <option value="">All Severities</option>
            <option value="LOW">Low</option>
            <option value="MEDIUM">Medium</option>
            <option value="HIGH">High</option>
            <option value="CRITICAL">Critical</option>
          </select>

          {(search || statusFilter || severityFilter) && (
            <span
              style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}
              aria-live="polite"
            >
              {filtered.length} result{filtered.length !== 1 ? 's' : ''}
            </span>
          )}
        </div>
      )}

      {loading && <LoadingSpinner message="Loading operational data…" />}

      {(reportsLoadState === 'error' || reportsLoadState === 'permission-denied' || reportsLoadState === 'not-configured') && (
        <ErrorState type={reportsLoadState === 'not-configured' ? 'not-configured' : reportsLoadState} message={error ?? undefined} />
      )}

      {reportsLoadState === 'success' && reports.length === 0 && (
        <EmptyState title="No incidents found" message="Incidents submitted through the RSQ Android app will appear here automatically." />
      )}

      {reportsLoadState === 'success' && reports.length > 0 && filtered.length === 0 && (
        <EmptyState title="No incidents match your filters" message="Try adjusting your search or filter criteria." />
      )}

      {reportsLoadState === 'success' && filtered.length > 0 && (
        <div className="table-wrapper">
          <table className="data-table" role="table" aria-label="Incidents">
            <thead>
              <tr>
                <th scope="col">Severity</th>
                <th scope="col">Incident</th>
                <th scope="col">Location</th>
                <th scope="col">Status</th>
                <th scope="col">Assigned Units</th>
                <th scope="col">Reported</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((report) => {
                const sv = getSeverityVariant(report.severity);
                const rowClass = sv === 'critical' ? 'row-critical' : sv === 'high' ? 'row-high' : '';
                
                const volName = report.assignedVolunteerId ? intelligence.volMap.get(report.assignedVolunteerId) : null;
                const resCount = report.assignedResourceIds?.length || 0;

                return (
                  <tr
                    key={report.reportId}
                    id={`report-row-${report.reportId}`}
                    className={`cursor-pointer hover:bg-surface-1 transition-colors ${rowClass}`}
                    onClick={() => navigate(`/reports/${report.reportId}`)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        navigate(`/reports/${report.reportId}`);
                      }
                    }}
                    tabIndex={0}
                    role="row"
                    aria-label={`Incident ${formatReportId(report.reportId)}`}
                  >
                    <td>
                      <SeverityBadge value={report.severity} />
                    </td>
                    <td>
                      <div className="font-semibold text-primary">{report.title ? truncate(report.title, 40) : 'Emergency Report'}</div>
                      <div className="text-xs text-tertiary" style={{ fontFamily: "'JetBrains Mono', monospace" }}>{formatReportId(report.reportId)}</div>
                    </td>
                    <td className="text-sm font-medium text-secondary" style={{ fontFamily: "'JetBrains Mono', monospace" }}>
                      {formatCoordinates(report.latitude, report.longitude)}
                    </td>
                    <td>
                      <StatusBadge value={report.status} />
                    </td>
                    <td>
                       <div className="flex flex-col gap-1">
                          {volName ? <span className="text-[10px] bg-info/10 text-info px-1 rounded inline-block w-max">Vol: {volName}</span> : <span className="text-[10px] text-tertiary">Vol: —</span>}
                          {resCount > 0 ? <span className="text-[10px] bg-info/10 text-info px-1 rounded inline-block w-max">Res: {resCount} unit(s)</span> : <span className="text-[10px] text-tertiary">Res: —</span>}
                       </div>
                    </td>
                    <td style={{ whiteSpace: 'nowrap' }} className="text-secondary text-sm">
                      {formatTimestamp(report.timestamp)}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

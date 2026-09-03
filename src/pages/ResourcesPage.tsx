import React, { useState, useMemo } from 'react';
import { useResources } from '../hooks/useResources';
import { LoadingSpinner } from '../components/LoadingSpinner';
import { ErrorState } from '../components/ErrorState';
import { EmptyState } from '../components/EmptyState';
import { Search } from 'lucide-react';

export function ResourcesPage(): React.ReactElement {
  const { resources, loadState, error } = useResources();

  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');

  const filtered = useMemo(() => {
    return resources.filter((r) => {
      const q = search.toLowerCase();
      const matchesSearch = !q || r.name.toLowerCase().includes(q) || r.type.toLowerCase().includes(q);
      const matchesStatus = !statusFilter || r.status === statusFilter;
      return matchesSearch && matchesStatus;
    });
  }, [resources, search, statusFilter]);

  const stats = useMemo(() => {
    const total = resources.length;
    const available = resources.filter(r => r.status === 'AVAILABLE').length;
    const assigned = resources.filter(r => r.status === 'ASSIGNED').length;
    const unavailable = resources.filter(r => r.status === 'UNAVAILABLE').length;
    return { total, available, assigned, unavailable };
  }, [resources]);

  return (
    <>
      <div className="page-header">
        <h1 className="page-title">Resource Operations</h1>
        <p className="page-subtitle">
          Manage physical response assets and deployment
          {loadState === 'success' && ` · ${stats.available} of ${stats.total} Available`}
        </p>
      </div>

      {loadState === 'success' && resources.length > 0 && (
        <>
          {/* Capacity Top Bar */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
             <div className="card border-l-4" style={{ borderLeftColor: 'var(--text-tertiary)' }}>
                <span className="text-xs uppercase font-bold text-tertiary">Total</span>
                <div className="text-2xl font-bold mt-1 text-primary">{stats.total}</div>
             </div>
             <div className="card border-l-4" style={{ borderLeftColor: 'var(--success)' }}>
                <span className="text-xs uppercase font-bold text-success">Available</span>
                <div className="text-2xl font-bold mt-1 text-primary">{stats.available}</div>
             </div>
             <div className="card border-l-4" style={{ borderLeftColor: 'var(--info)' }}>
                <span className="text-xs uppercase font-bold text-info">Assigned</span>
                <div className="text-2xl font-bold mt-1 text-primary">{stats.assigned}</div>
             </div>
             <div className="card border-l-4" style={{ borderLeftColor: 'var(--text-tertiary)' }}>
                <span className="text-xs uppercase font-bold text-tertiary">Unavailable</span>
                <div className="text-2xl font-bold mt-1 text-primary">{stats.unavailable}</div>
             </div>
          </div>

          <div className="table-toolbar">
            <div className="search-input-wrapper">
              <Search className="search-icon" size={16} aria-hidden="true" />
              <input
                type="search"
                className="search-input"
                placeholder="Search by name or type…"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                aria-label="Search resources"
              />
            </div>

            <select
              className="filter-select"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              aria-label="Filter by status"
            >
              <option value="">All Statuses</option>
              <option value="AVAILABLE">Available</option>
              <option value="ASSIGNED">Assigned</option>
              <option value="UNAVAILABLE">Unavailable</option>
            </select>
          </div>
        </>
      )}

      {loadState === 'loading' && <LoadingSpinner message="Loading resources…" />}

      {(loadState === 'error' || loadState === 'permission-denied') && (
        <ErrorState type={loadState} message={error ?? undefined} />
      )}

      {loadState === 'success' && resources.length === 0 && (
        <EmptyState title="No resources found" message="No resources are currently registered in the system." type="default" />
      )}

      {loadState === 'success' && stats.total > 0 && stats.available === 0 && (!statusFilter || statusFilter === 'AVAILABLE') && (
        <EmptyState title="0 AVAILABLE" message="No physical resources are currently available for assignment." type="warning" />
      )}

      {loadState === 'success' && filtered.length > 0 && (
        <div className="table-wrapper">
          <table className="data-table" role="table">
            <thead>
              <tr>
                <th scope="col">Status</th>
                <th scope="col">Name</th>
                <th scope="col">Type</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((resource) => (
                <tr key={resource.id} className="cursor-default">
                  <td style={{ width: '120px' }}>
                    <div className={`ops-indicator bg-status-${resource.status.toLowerCase()} inline-block mr-2 align-middle`} />
                    <span className={`text-status-${resource.status.toLowerCase()} text-xs font-bold uppercase`}>
                      {resource.status.replace('_', ' ')}
                    </span>
                  </td>
                  <td className="font-semibold text-primary">{resource.name}</td>
                  <td className="text-secondary">{resource.type}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

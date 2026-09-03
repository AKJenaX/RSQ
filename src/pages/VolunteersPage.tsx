import React, { useState, useMemo } from 'react';
import { useVolunteers } from '../hooks/useVolunteers';
import { LoadingSpinner } from '../components/LoadingSpinner';
import { ErrorState } from '../components/ErrorState';
import { EmptyState } from '../components/EmptyState';
import { Search } from 'lucide-react';

export function VolunteersPage(): React.ReactElement {
  const { volunteers, loadState, error } = useVolunteers();

  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');

  const filtered = useMemo(() => {
    return volunteers.filter((v) => {
      const q = search.toLowerCase();
      const matchesSearch = !q || v.name.toLowerCase().includes(q) || v.role.toLowerCase().includes(q);
      const matchesStatus = !statusFilter || v.status === statusFilter;
      return matchesSearch && matchesStatus;
    });
  }, [volunteers, search, statusFilter]);

  const stats = useMemo(() => {
    const total = volunteers.length;
    const available = volunteers.filter(v => v.status === 'AVAILABLE').length;
    const assigned = volunteers.filter(v => v.status === 'ASSIGNED').length;
    const unavailable = volunteers.filter(v => v.status === 'UNAVAILABLE').length;
    return { total, available, assigned, unavailable };
  }, [volunteers]);

  return (
    <>
      <div className="page-header">
        <h1 className="page-title">Volunteer Operations</h1>
        <p className="page-subtitle">
          Manage personnel capacity and assignments
          {loadState === 'success' && ` · ${stats.available} of ${stats.total} Available`}
        </p>
      </div>

      {loadState === 'success' && volunteers.length > 0 && (
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
                placeholder="Search by name or role…"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                aria-label="Search volunteers"
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

      {loadState === 'loading' && <LoadingSpinner message="Loading volunteers…" />}

      {(loadState === 'error' || loadState === 'permission-denied') && (
        <ErrorState type={loadState} message={error ?? undefined} />
      )}

      {loadState === 'success' && volunteers.length === 0 && (
        <EmptyState title="No volunteers found" message="No volunteers are currently registered in the system." type="default" />
      )}

      {loadState === 'success' && stats.total > 0 && stats.available === 0 && (!statusFilter || statusFilter === 'AVAILABLE') && (
        <EmptyState title="0 AVAILABLE" message="No volunteers are currently available for assignment. All personnel are either assigned or offline." type="warning" />
      )}

      {loadState === 'success' && filtered.length > 0 && (
        <div className="table-wrapper">
          <table className="data-table" role="table">
            <thead>
              <tr>
                <th scope="col">Status</th>
                <th scope="col">Name</th>
                <th scope="col">Role</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((volunteer) => (
                <tr key={volunteer.id} className="cursor-default">
                  <td style={{ width: '120px' }}>
                    <div className={`ops-indicator bg-status-${volunteer.status.toLowerCase()} inline-block mr-2 align-middle`} />
                    <span className={`text-status-${volunteer.status.toLowerCase()} text-xs font-bold uppercase`}>
                      {volunteer.status.replace('_', ' ')}
                    </span>
                  </td>
                  <td className="font-semibold text-primary">{volunteer.name}</td>
                  <td className="text-secondary">{volunteer.role}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

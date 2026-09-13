import React, { useState, useEffect } from 'react';
import type { IntelligenceReport } from '../hooks/useOperationalIntelligence';
import { SeverityBadge } from './Badge';

interface ResponseQueueProps {
  queue: IntelligenceReport[];

}

export function ResponseQueue({ queue }: ResponseQueueProps): React.ReactElement {
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 60000);
    return () => clearInterval(timer);
  }, []);

  if (queue.length === 0) {
    return (
      <div className="empty-state">
        <h3 className="empty-state-title">Queue Empty</h3>
        <p className="empty-state-message">No incidents currently require a response.</p>
      </div>
    );
  }

  return (
    <div className="incident-table-container">
      <table className="incident-table w-full text-left">
        <thead>
          <tr className="border-b text-xs text-secondary uppercase">
            <th className="py-2 px-3">Severity</th>
            <th className="py-2 px-3">Incident</th>
            <th className="py-2 px-3">Status</th>
            <th className="py-2 px-3">Wait</th>
            <th className="py-2 px-3 text-right">Action</th>
          </tr>
        </thead>
        <tbody>
          {queue.map(report => {
            const isCritical = report.priorityScore <= 2;
            const rowClass = isCritical ? 'bg-error-subtle' : '';

            // Calculate age roughly for wait time
            let waitTimeStr = '-';
            if (report.timestamp) {
               const ageMs = now - report.timestamp;
               const ageMin = Math.floor(Math.max(0, ageMs) / 60000);
               if (ageMin < 60) waitTimeStr = `${ageMin}m`;
               else {
                 const ageHr = Math.floor(ageMin / 60);
                 waitTimeStr = `${ageHr}h ${ageMin % 60}m`;
               }
            }
            
            const hasVol = !!report.assignedVolunteerId;
            const resCount = report.assignedResourceIds?.length || 0;

            return (
              <tr key={report.reportId} className={`border-b border-surface-border last:border-0 ${rowClass}`}>
                <td className="py-3 px-3 align-top">
                  <div className="flex items-center gap-2 mt-0.5">
                    {isCritical && <span className="w-2 h-2 rounded-full bg-error animate-pulse"></span>}
                    <SeverityBadge value={report.severity} />
                  </div>
                  {report.aiScore && (
                    <div className="text-[9px] mt-1 text-tertiary">AI Risk: {report.aiScore}/10</div>
                  )}
                </td>
                <td className="py-3 px-3 align-top">
                  <div className="font-semibold text-primary">{report.title || 'Unknown'}</div>
                  {report.latitude !== undefined && (
                    <div className="text-xs text-secondary mt-1 font-mono">{report.latitude.toFixed(4)}, {report.longitude?.toFixed(4)}</div>
                  )}
                </td>
                <td className="py-3 px-3 align-top">
                  <div className={`text-xs font-bold mb-1 ${report.isAttentionRequired ? 'text-error' : 'text-primary'}`}>
                    {report.priorityReason}
                  </div>
                  <div className="flex flex-col gap-0.5">
                     <span className="text-[10px] text-tertiary">Vol: {hasVol ? 'Assigned' : 'None'}</span>
                     <span className="text-[10px] text-tertiary">Res: {resCount > 0 ? `${resCount} units` : 'None'}</span>
                  </div>
                </td>
                <td className="py-3 px-3 align-top text-sm font-medium">{waitTimeStr}</td>
                <td className="py-3 px-3 align-top text-right">
                  <button 
                    className={report.isAttentionRequired ? 'btn-primary' : 'btn-secondary'}
                    style={{ padding: '4px 12px', fontSize: '0.75rem' }}
                    onClick={() => window.location.href = `/reports/${report.reportId}`}
                  >
                    MANAGE
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

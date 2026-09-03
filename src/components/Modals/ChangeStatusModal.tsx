import React, { useState } from 'react';
import { X } from 'lucide-react';
import { changeReportStatus } from '../../services/incidentService';

interface ChangeStatusModalProps {
  reportId: string;
  currentStatus: string;
  authorityUid: string;
  onClose: () => void;
  onSuccess: () => void;
}

const AVAILABLE_STATUSES = ['OPEN', 'ASSIGNED', 'IN_PROGRESS'];

export function ChangeStatusModal({ reportId, currentStatus, authorityUid, onClose, onSuccess }: ChangeStatusModalProps): React.ReactElement {
  const [newStatus, setNewStatus] = useState<string>(currentStatus === 'RESOLVED' ? 'OPEN' : currentStatus);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSave = async () => {
    if (newStatus === currentStatus) {
      onClose();
      return;
    }
    
    setSaving(true);
    setError(null);
    try {
      await changeReportStatus(reportId, currentStatus, newStatus, authorityUid);
      onSuccess();
    } catch (err) {
      console.error('Status change error:', err);
      setError('Unable to change incident status. Please try again.');
      setSaving(false);
    }
  };

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true">
      <div className="modal-content">
        <div className="modal-header">
          <h2 className="modal-title">Change Incident Status</h2>
          <button onClick={onClose} aria-label="Close modal" className="text-secondary hover:text-primary transition-colors">
            <X size={20} />
          </button>
        </div>
        
        <div className="modal-body">
          {error && <div className="form-error mb-4" role="alert">{error}</div>}
          
          <div className="form-group">
            <label className="form-label text-tertiary">Current status</label>
            <div className="font-semibold text-primary mb-4">{currentStatus}</div>
            
            <label htmlFor="status-select" className="form-label">New status</label>
            <select
              id="status-select"
              className="form-input"
              value={newStatus}
              onChange={(e) => setNewStatus(e.target.value)}
              disabled={saving}
            >
              {AVAILABLE_STATUSES.map(status => (
                <option key={status} value={status}>{status}</option>
              ))}
            </select>
            <p className="text-xs text-tertiary mt-2">
              Note: To resolve an incident, use the "Resolve Incident" action instead.
            </p>
          </div>
        </div>
        
        <div className="modal-footer">
          <button className="btn-secondary" onClick={onClose} disabled={saving}>Cancel</button>
          <button 
            className="btn-primary" 
            onClick={handleSave} 
            disabled={saving || newStatus === currentStatus}
          >
            {saving ? 'Saving...' : 'Save Changes'}
          </button>
        </div>
      </div>
    </div>
  );
}

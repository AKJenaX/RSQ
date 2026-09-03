import React, { useState } from 'react';
import { X, CheckCircle } from 'lucide-react';
import { resolveIncident } from '../../services/incidentService';

interface ResolveIncidentModalProps {
  reportId: string;
  authorityUid: string;
  onClose: () => void;
  onSuccess: () => void;
}

export function ResolveIncidentModal({ reportId, authorityUid, onClose, onSuccess }: ResolveIncidentModalProps): React.ReactElement {
  const [resolutionNote, setResolutionNote] = useState('');
  const [resolving, setResolving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleResolve = async () => {
    if (!resolutionNote.trim()) {
      setError('Please provide a resolution note.');
      return;
    }
    
    setResolving(true);
    setError(null);
    try {
      await resolveIncident(reportId, authorityUid, resolutionNote.trim());
      onSuccess();
    } catch (err) {
      console.error('Resolve error:', err);
      setError('Unable to resolve incident. Please try again.');
      setResolving(false);
    }
  };

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true">
      <div className="modal-content">
        <div className="modal-header">
          <div className="flex items-center gap-2 text-success">
            <CheckCircle size={20} />
            <h2 className="modal-title">Resolve Incident</h2>
          </div>
          <button onClick={onClose} aria-label="Close modal" className="text-secondary hover:text-primary transition-colors">
            <X size={20} />
          </button>
        </div>
        
        <div className="modal-body">
          {error && <div className="form-error mb-4" role="alert">{error}</div>}
          
          <p className="text-sm text-secondary mb-6">
            Are you sure this incident has been resolved? This action will mark the incident as closed and cannot be easily undone.
          </p>
          
          <div className="form-group">
            <label htmlFor="resolution-note" className="form-label">
              Resolution note <span className="text-error">*</span>
            </label>
            <textarea
              id="resolution-note"
              className="form-input"
              rows={4}
              placeholder="Provide details on how this incident was resolved..."
              value={resolutionNote}
              onChange={(e) => setResolutionNote(e.target.value)}
              disabled={resolving}
              required
            />
          </div>
        </div>
        
        <div className="modal-footer">
          <button className="btn-secondary" onClick={onClose} disabled={resolving}>Cancel</button>
          <button 
            className="btn-danger" 
            onClick={handleResolve} 
            disabled={resolving || !resolutionNote.trim()}
            style={{ backgroundColor: 'var(--success)', borderColor: 'var(--success)', color: 'var(--bg-primary)' }}
          >
            {resolving ? 'Resolving...' : 'Confirm Resolution'}
          </button>
        </div>
      </div>
    </div>
  );
}

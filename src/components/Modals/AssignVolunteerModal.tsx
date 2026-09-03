import React, { useState, useEffect } from 'react';
import { X, Loader2 } from 'lucide-react';
import type { Volunteer } from '../../types/incident';
import { getVolunteers, assignVolunteer } from '../../services/incidentService';

interface AssignVolunteerModalProps {
  reportId: string;
  authorityUid: string;
  onClose: () => void;
  onSuccess: () => void;
}

export function AssignVolunteerModal({ reportId, authorityUid, onClose, onSuccess }: AssignVolunteerModalProps): React.ReactElement {
  const [volunteers, setVolunteers] = useState<Volunteer[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [assigning, setAssigning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getVolunteers().then(data => {
      setVolunteers(data);
      setLoading(false);
    }).catch(err => {
      console.error(err);
      setError('Failed to load volunteers.');
      setLoading(false);
    });
  }, []);

  const handleAssign = async () => {
    if (!selectedId) return;
    setAssigning(true);
    setError(null);
    try {
      await assignVolunteer(reportId, selectedId, authorityUid);
      onSuccess();
    } catch (err) {
      console.error('Assign error:', err);
      setError('Unable to assign volunteer. Please try again.');
      setAssigning(false);
    }
  };

  const availableVolunteers = volunteers.filter(v => v.status === 'AVAILABLE');

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true">
      <div className="modal-content">
        <div className="modal-header">
          <h2 className="modal-title">Assign Volunteer</h2>
          <button onClick={onClose} aria-label="Close modal" className="text-secondary hover:text-primary transition-colors">
            <X size={20} />
          </button>
        </div>
        
        <div className="modal-body">
          {error && <div className="form-error mb-4" role="alert">{error}</div>}
          
          {loading ? (
            <div className="flex flex-col items-center justify-center p-8 gap-2 text-secondary">
              <Loader2 className="spinner" size={24} />
              <span className="text-sm">Loading available volunteers...</span>
            </div>
          ) : availableVolunteers.length === 0 ? (
            <div className="flex flex-col items-center justify-center p-8 text-secondary">
              <span className="text-primary font-medium mb-1">No available volunteers</span>
              <span className="text-sm text-center">There are currently no volunteers available for assignment.</span>
            </div>
          ) : (
            <div className="selectable-list">
              <div className="text-xs font-semibold text-tertiary uppercase mb-2">Available volunteers</div>
              {availableVolunteers.map(vol => (
                <label 
                  key={vol.id} 
                  className={`selectable-item ${selectedId === vol.id ? 'selected' : ''}`}
                >
                  <input 
                    type="radio" 
                    name="volunteer-select" 
                    className="custom-radio"
                    value={vol.id}
                    checked={selectedId === vol.id}
                    onChange={() => setSelectedId(vol.id)}
                    disabled={assigning}
                  />
                  <div className="selectable-item-content">
                    <span className="selectable-item-title">{vol.name}</span>
                    <span className="selectable-item-subtitle">{vol.role} • {vol.status}</span>
                  </div>
                </label>
              ))}
            </div>
          )}
        </div>
        
        <div className="modal-footer">
          <button className="btn-secondary" onClick={onClose} disabled={assigning}>Cancel</button>
          <button 
            className="btn-primary" 
            onClick={handleAssign} 
            disabled={!selectedId || assigning}
          >
            {assigning ? 'Assigning...' : 'Assign'}
          </button>
        </div>
      </div>
    </div>
  );
}

import React, { useState, useEffect } from 'react';
import { X, Loader2 } from 'lucide-react';
import type { Resource } from '../../types/incident';
import { getResources, assignResources } from '../../services/incidentService';

interface AssignResourceModalProps {
  reportId: string;
  authorityUid: string;
  onClose: () => void;
  onSuccess: () => void;
}

export function AssignResourceModal({ reportId, authorityUid, onClose, onSuccess }: AssignResourceModalProps): React.ReactElement {
  const [resources, setResources] = useState<Resource[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [assigning, setAssigning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getResources().then(data => {
      setResources(data);
      setLoading(false);
    }).catch(err => {
      console.error(err);
      setError('Failed to load resources.');
      setLoading(false);
    });
  }, []);

  const handleToggle = (id: string) => {
    const newSet = new Set(selectedIds);
    if (newSet.has(id)) {
      newSet.delete(id);
    } else {
      newSet.add(id);
    }
    setSelectedIds(newSet);
  };

  const handleAssign = async () => {
    if (selectedIds.size === 0) return;
    setAssigning(true);
    setError(null);
    try {
      await assignResources(reportId, Array.from(selectedIds), authorityUid);
      onSuccess();
    } catch (err) {
      console.error('Assign error:', err);
      setError('Unable to assign resources. Please try again.');
      setAssigning(false);
    }
  };

  const availableResources = resources.filter(r => r.status === 'AVAILABLE');

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true">
      <div className="modal-content">
        <div className="modal-header">
          <h2 className="modal-title">Assign Resources</h2>
          <button onClick={onClose} aria-label="Close modal" className="text-secondary hover:text-primary transition-colors">
            <X size={20} />
          </button>
        </div>
        
        <div className="modal-body">
          {error && <div className="form-error mb-4" role="alert">{error}</div>}
          
          {loading ? (
            <div className="flex flex-col items-center justify-center p-8 gap-2 text-secondary">
              <Loader2 className="spinner" size={24} />
              <span className="text-sm">Loading available resources...</span>
            </div>
          ) : availableResources.length === 0 ? (
            <div className="flex flex-col items-center justify-center p-8 text-secondary">
              <span className="text-primary font-medium mb-1">No available resources</span>
              <span className="text-sm text-center">There are currently no resources available for assignment.</span>
            </div>
          ) : (
            <div className="selectable-list">
              <div className="text-xs font-semibold text-tertiary uppercase mb-2">Available resources</div>
              {availableResources.map(res => (
                <label 
                  key={res.id} 
                  className={`selectable-item ${selectedIds.has(res.id) ? 'selected' : ''}`}
                >
                  <input 
                    type="checkbox" 
                    className="custom-checkbox"
                    checked={selectedIds.has(res.id)}
                    onChange={() => handleToggle(res.id)}
                    disabled={assigning}
                  />
                  <div className="selectable-item-content">
                    <span className="selectable-item-title">{res.name}</span>
                    <span className="selectable-item-subtitle">{res.type} • {res.status}</span>
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
            disabled={selectedIds.size === 0 || assigning}
          >
            {assigning ? 'Assigning...' : 'Assign'}
          </button>
        </div>
      </div>
    </div>
  );
}

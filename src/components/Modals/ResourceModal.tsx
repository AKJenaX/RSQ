import React, { useState } from 'react';
import { doc, setDoc, updateDoc, deleteDoc } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { X, Trash2 } from 'lucide-react';
import type { Resource } from '../../types/incident';
import { useReports } from '../../hooks/useReports';
import { Link } from 'react-router-dom';

interface ResourceModalProps {
  onClose: () => void;
  resource?: Resource; // If present, edit mode
}

export function ResourceModal({ onClose, resource }: ResourceModalProps): React.ReactElement {
  const [name, setName] = useState(resource?.name || '');
  const [type, setType] = useState(resource?.type || 'OTHER');
  const [capacity, setCapacity] = useState(resource?.capacity?.toString() || '');
  const [status, setStatus] = useState(resource?.status || 'AVAILABLE');
  const [saving, setSaving] = useState(false);
  const [removing, setRemoving] = useState(false);
  const { reports } = useReports();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      const data: Partial<Resource> = {
        name,
        type,
        status: status as any,
      };
      if (capacity) data.capacity = parseInt(capacity, 10);
      
      if (resource) {
        await updateDoc(doc(db, 'resources', resource.id), data);
      } else {
        const newId = `res-${Date.now()}`;
        await setDoc(doc(db, 'resources', newId), { id: newId, ...data });
      }
      onClose();
    } catch (err) {
      console.error(err);
      alert('Failed to save resource');
    } finally {
      setSaving(false);
    }
  };

  const handleRemove = async () => {
    if (!resource) return;
    const activeReports = reports.filter(rep => rep.status !== 'RESOLVED' && rep.assignedResourceIds?.includes(resource.id));
    if (activeReports.length > 0) return; // UI handles blocking, but double check
    
    if (window.confirm(`Are you sure you want to remove ${resource.name}?`)) {
      setRemoving(true);
      try {
        const historicallyAssigned = reports.some(rep => rep.status === 'RESOLVED' && rep.assignedResourceIds?.includes(resource.id));
        if (historicallyAssigned) {
          await updateDoc(doc(db, 'resources', resource.id), { status: 'UNAVAILABLE' });
        } else {
          await deleteDoc(doc(db, 'resources', resource.id));
        }
        onClose();
      } catch (err: any) {
        console.error(err);
        alert('Failed to remove resource: ' + err.message);
      } finally {
        setRemoving(false);
      }
    }
  };

  const activeReports = resource ? reports.filter(rep => rep.status !== 'RESOLVED' && rep.assignedResourceIds?.includes(resource.id)) : [];
  const isAssigned = activeReports.length > 0;

  return (
    <div className="modal-overlay">
      <div className="modal-container">
        <div className="modal-header">
          <h2 className="modal-title">{resource ? 'Edit Resource' : 'Add Resource'}</h2>
          <button className="modal-close" onClick={onClose} disabled={saving} aria-label="Close">
            <X size={20} />
          </button>
        </div>
        
        <form onSubmit={handleSubmit} className="modal-content" style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div>
            <label className="block text-sm font-semibold mb-1">Name / Identifier</label>
            <input required type="text" className="w-full p-2 border rounded bg-background text-foreground" value={name} onChange={e => setName(e.target.value)} placeholder="e.g. Ambulance-01" />
          </div>
          <div>
            <label className="block text-sm font-semibold mb-1">Type</label>
            <select required className="w-full p-2 border rounded bg-background text-foreground" value={type} onChange={e => setType(e.target.value)}>
              <option value="AMBULANCE">Ambulance</option>
              <option value="MEDICAL_KIT">Medical Kit</option>
              <option value="WATER">Water</option>
              <option value="FOOD">Food</option>
              <option value="RESCUE_VEHICLE">Rescue Vehicle</option>
              <option value="FIRST_AID">First Aid</option>
              <option value="OTHER">Other</option>
            </select>
          </div>
          <div>
            <label className="block text-sm font-semibold mb-1">Capacity / Quantity (Optional)</label>
            <input type="number" className="w-full p-2 border rounded bg-background text-foreground" value={capacity} onChange={e => setCapacity(e.target.value)} placeholder="e.g. 5" />
          </div>
          <div>
            <label className="block text-sm font-semibold mb-1">Status</label>
            <select required className="w-full p-2 border rounded bg-background text-foreground" value={status} onChange={e => setStatus(e.target.value as any)}>
              <option value="AVAILABLE">Available</option>
              <option value="UNAVAILABLE">Unavailable</option>
              {resource && <option value="ASSIGNED">Assigned</option>}
              {resource && <option value="IN_USE">In Use</option>}
              {resource && <option value="RELEASED">Released</option>}
            </select>
          </div>
          
          <div className="modal-footer mt-4 flex items-center justify-between gap-4">
            {resource && (
              <div className="flex-1 flex items-center">
                {isAssigned ? (
                  <div className="text-xs text-destructive bg-destructive/10 px-2 py-1.5 rounded flex items-center gap-2">
                    <span>Cannot remove while assigned to:</span>
                    <Link to={`/reports/${activeReports[0].reportId}`} className="font-medium hover:underline text-destructive">
                      {activeReports[0].title || activeReports[0].incidentType || activeReports[0].reportId.slice(0, 8)}
                    </Link>
                  </div>
                ) : (
                  <button type="button" onClick={handleRemove} disabled={saving || removing} className="text-destructive hover:bg-destructive/10 p-2 rounded transition-colors text-sm font-medium flex items-center gap-1">
                    <Trash2 size={16} />
                    {removing ? 'Removing...' : 'Remove Resource'}
                  </button>
                )}
              </div>
            )}
            <div className="flex gap-2 ml-auto">
              <button type="button" className="btn-secondary" onClick={onClose} disabled={saving || removing}>Cancel</button>
              <button type="submit" className="btn-primary" disabled={saving || removing}>{saving ? 'Saving...' : 'Save Resource'}</button>
            </div>
          </div>
        </form>
      </div>
    </div>
  );
}

import React, { useState } from 'react';
import { doc, setDoc, updateDoc } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { X } from 'lucide-react';
import type { Resource } from '../../types/incident';

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
            <input required type="text" className="w-full p-2 border rounded" value={name} onChange={e => setName(e.target.value)} placeholder="e.g. Ambulance-01" />
          </div>
          <div>
            <label className="block text-sm font-semibold mb-1">Type</label>
            <select required className="w-full p-2 border rounded" value={type} onChange={e => setType(e.target.value)}>
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
            <input type="number" className="w-full p-2 border rounded" value={capacity} onChange={e => setCapacity(e.target.value)} placeholder="e.g. 5" />
          </div>
          <div>
            <label className="block text-sm font-semibold mb-1">Status</label>
            <select required className="w-full p-2 border rounded" value={status} onChange={e => setStatus(e.target.value as any)}>
              <option value="AVAILABLE">Available</option>
              <option value="UNAVAILABLE">Unavailable</option>
              {resource && <option value="ASSIGNED">Assigned</option>}
              {resource && <option value="IN_USE">In Use</option>}
              {resource && <option value="RELEASED">Released</option>}
            </select>
          </div>
          
          <div className="modal-footer mt-4">
            <button type="button" className="btn-secondary" onClick={onClose} disabled={saving}>Cancel</button>
            <button type="submit" className="btn-primary" disabled={saving}>{saving ? 'Saving...' : 'Save Resource'}</button>
          </div>
        </form>
      </div>
    </div>
  );
}

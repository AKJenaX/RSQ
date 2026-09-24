import { useState } from 'react';
import { X, Trash2 } from 'lucide-react';
import { registerVolunteer } from '../../services/incidentService';
import { useReports } from '../../hooks/useReports';
import { doc, deleteDoc, updateDoc } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { Link } from 'react-router-dom';

interface VolunteerModalProps {
  onClose: () => void;
  volunteer?: any;
}

export function VolunteerModal({ onClose, volunteer }: VolunteerModalProps) {
  const { reports } = useReports();
  const [name, setName] = useState(volunteer?.name || '');
  const [role, setRole] = useState(volunteer?.role || '');
  const [contact, setContact] = useState(volunteer?.contact || '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  const [showConfirmRemove, setShowConfirmRemove] = useState(false);
  
  const activeReport = volunteer ? reports.find(r => r.status !== 'RESOLVED' && r.assignedVolunteerId === volunteer.id) : null;
  const isAssigned = !!activeReport;
  const historicallyAssigned = volunteer ? reports.some(r => r.status === 'RESOLVED' && r.assignedVolunteerId === volunteer.id) : false;

  const handleRemove = async () => {
    if (!volunteer || isAssigned) return;
    setSaving(true);
    try {
      if (historicallyAssigned) {
        await updateDoc(doc(db, 'volunteers', volunteer.id), { status: 'OFFLINE' });
      } else {
        await deleteDoc(doc(db, 'volunteers', volunteer.id));
      }
      onClose();
    } catch (err: any) {
      console.error(err);
      setError('Failed to remove volunteer: ' + err.message);
      setSaving(false);
    }
  };

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!name.trim() || !role.trim()) {
      setError('Enter the volunteer name and role.');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      if (volunteer) {
        await updateDoc(doc(db, 'volunteers', volunteer.id), { name, role, contact });
      } else {
        await registerVolunteer({ name, role, contact });
      }
      onClose();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to save the volunteer.');
      setSaving(false);
    }
  };

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" aria-labelledby="volunteer-modal-title">
      <div className="modal-content">
        <form onSubmit={submit}>
          <div className="modal-header">
            <h2 id="volunteer-modal-title" className="modal-title">{volunteer ? 'Edit Volunteer' : 'Register volunteer'}</h2>
            <button type="button" className="modal-close" onClick={onClose} aria-label="Close dialog" disabled={saving}>
              <X size={20} />
            </button>
          </div>
          <div className="modal-body grid gap-4">
            {error ? <div className="form-error" role="alert">{error}</div> : null}
            <div className="form-group"><label className="form-label" htmlFor="volunteer-name">Name</label><input id="volunteer-name" className="form-input" value={name} onChange={(event) => setName(event.target.value)} disabled={saving} required /></div>
            <div className="form-group"><label className="form-label" htmlFor="volunteer-role">Role or skill</label><input id="volunteer-role" className="form-input" value={role} onChange={(event) => setRole(event.target.value)} disabled={saving} required /></div>
            <div className="form-group"><label className="form-label" htmlFor="volunteer-contact">Contact (optional)</label><input id="volunteer-contact" className="form-input" value={contact} onChange={(event) => setContact(event.target.value)} disabled={saving} /></div>
          </div>
          <div className="modal-footer flex justify-between items-center w-full mt-2">
            <div className="flex-1">
              {volunteer && !showConfirmRemove && (
                <button type="button" className="text-destructive hover:bg-destructive/10 px-3 py-2 rounded-md text-sm font-medium transition-colors flex items-center gap-2" onClick={() => setShowConfirmRemove(true)} disabled={saving}>
                  <Trash2 size={16} /> Remove Volunteer
                </button>
              )}
            </div>
            <div className="flex items-center gap-3">
              <button type="button" className="btn-secondary" onClick={onClose} disabled={saving}>Cancel</button>
              <button className="btn-primary" disabled={saving}>{saving ? 'Saving…' : (volunteer ? 'Save Changes' : 'Register volunteer')}</button>
            </div>
          </div>
        </form>

        {showConfirmRemove && volunteer && (
          <div className="border-t border-border mt-4 pt-4 px-6 pb-6 bg-muted/20">
            <h3 className="font-semibold text-destructive mb-2">Remove Volunteer</h3>
            {isAssigned ? (
              <div className="flex flex-col gap-3">
                <p className="text-sm text-muted-foreground">Cannot remove this volunteer while they are assigned to an active report.</p>
                <div className="bg-destructive/5 border border-destructive/20 p-3 rounded-md flex flex-col gap-1">
                  <span className="text-xs text-muted-foreground uppercase font-semibold">Currently assigned to</span>
                  <Link to={`/reports/${activeReport.reportId}`} className="font-medium text-destructive hover:underline text-sm inline-flex" onClick={onClose}>
                    {activeReport.title || activeReport.incidentType || activeReport.reportId.slice(0, 8)}
                  </Link>
                </div>
                <div className="flex justify-end mt-2">
                  <button type="button" className="btn-secondary" onClick={() => setShowConfirmRemove(false)}>Dismiss</button>
                </div>
              </div>
            ) : (
              <div>
                <p className="text-sm text-muted-foreground mb-4">Are you sure you want to remove <strong>{volunteer.name}</strong>?</p>
                <div className="flex justify-end gap-3">
                  <button type="button" className="btn-secondary" onClick={() => setShowConfirmRemove(false)} disabled={saving}>Cancel</button>
                  <button type="button" className="btn-primary bg-destructive hover:bg-destructive/90 text-destructive-foreground border-destructive" onClick={handleRemove} disabled={saving}>
                    {saving ? 'Removing...' : 'Confirm Remove'}
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

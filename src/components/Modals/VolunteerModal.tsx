import { useState } from 'react';
import { X } from 'lucide-react';
import { registerVolunteer } from '../../services/incidentService';

interface VolunteerModalProps {
  onClose: () => void;
}

export function VolunteerModal({ onClose }: VolunteerModalProps) {
  const [name, setName] = useState('');
  const [role, setRole] = useState('');
  const [contact, setContact] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!name.trim() || !role.trim()) {
      setError('Enter the volunteer name and role.');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await registerVolunteer({ name, role, contact });
      onClose();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to register the volunteer.');
      setSaving(false);
    }
  };

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" aria-labelledby="volunteer-modal-title">
      <form className="modal-content" onSubmit={submit}>
        <div className="modal-header"><h2 id="volunteer-modal-title" className="modal-title">Register volunteer</h2><button type="button" className="modal-close" onClick={onClose} aria-label="Close dialog" disabled={saving}><X size={20} /></button></div>
        <div className="modal-body grid gap-4">
          {error ? <div className="form-error" role="alert">{error}</div> : null}
          <div className="form-group"><label className="form-label" htmlFor="volunteer-name">Name</label><input id="volunteer-name" className="form-input" value={name} onChange={(event) => setName(event.target.value)} disabled={saving} required /></div>
          <div className="form-group"><label className="form-label" htmlFor="volunteer-role">Role or skill</label><input id="volunteer-role" className="form-input" value={role} onChange={(event) => setRole(event.target.value)} disabled={saving} required /></div>
          <div className="form-group"><label className="form-label" htmlFor="volunteer-contact">Contact (optional)</label><input id="volunteer-contact" className="form-input" value={contact} onChange={(event) => setContact(event.target.value)} disabled={saving} /></div>
        </div>
        <div className="modal-footer"><button type="button" className="btn-secondary" onClick={onClose} disabled={saving}>Cancel</button><button className="btn-primary" disabled={saving}>{saving ? 'Saving…' : 'Register volunteer'}</button></div>
      </form>
    </div>
  );
}

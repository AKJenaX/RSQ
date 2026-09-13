import { useState } from 'react';
import { X } from 'lucide-react';
import { createReport } from '../../services/reportsService';

interface ReportModalProps {
  authorityUid: string;
  onClose: () => void;
  onSuccess: (reportId: string) => void;
}

const severities = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const;

export function ReportModal({ authorityUid, onClose, onSuccess }: ReportModalProps) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [severity, setSeverity] = useState<(typeof severities)[number]>('MEDIUM');
  const [latitude, setLatitude] = useState('');
  const [longitude, setLongitude] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    const lat = latitude.trim() === '' ? undefined : Number(latitude);
    const lng = longitude.trim() === '' ? undefined : Number(longitude);
    if (!title.trim() || !description.trim()) {
      setError('Enter an incident title and description.');
      return;
    }
    if ((lat === undefined) !== (lng === undefined) || (lat !== undefined && (!Number.isFinite(lat) || !Number.isFinite(lng)))) {
      setError('Provide both latitude and longitude as valid numbers, or leave both blank.');
      return;
    }

    setSaving(true);
    setError(null);
    try {
      const reportId = await createReport({ title, description, severity, latitude: lat, longitude: lng }, authorityUid);
      onSuccess(reportId);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to create the incident.');
      setSaving(false);
    }
  };

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" aria-labelledby="new-report-title">
      <form className="modal-content" onSubmit={submit}>
        <div className="modal-header">
          <h2 id="new-report-title" className="modal-title">Log incident</h2>
          <button type="button" onClick={onClose} aria-label="Close dialog" className="modal-close" disabled={saving}><X size={20} /></button>
        </div>
        <div className="modal-body grid gap-4">
          {error ? <div className="form-error" role="alert">{error}</div> : null}
          <div className="form-group">
            <label className="form-label" htmlFor="report-title">Incident title</label>
            <input id="report-title" className="form-input" value={title} onChange={(event) => setTitle(event.target.value)} disabled={saving} required />
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="report-description">Description</label>
            <textarea id="report-description" className="form-input" rows={4} value={description} onChange={(event) => setDescription(event.target.value)} disabled={saving} required />
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="report-severity">Severity</label>
            <select id="report-severity" className="form-input" value={severity} onChange={(event) => setSeverity(event.target.value as typeof severity)} disabled={saving}>
              {severities.map((item) => <option key={item} value={item}>{item}</option>)}
            </select>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="form-group"><label className="form-label" htmlFor="report-latitude">Latitude</label><input id="report-latitude" className="form-input" inputMode="decimal" value={latitude} onChange={(event) => setLatitude(event.target.value)} disabled={saving} /></div>
            <div className="form-group"><label className="form-label" htmlFor="report-longitude">Longitude</label><input id="report-longitude" className="form-input" inputMode="decimal" value={longitude} onChange={(event) => setLongitude(event.target.value)} disabled={saving} /></div>
          </div>
        </div>
        <div className="modal-footer"><button type="button" className="btn-secondary" onClick={onClose} disabled={saving}>Cancel</button><button className="btn-primary" disabled={saving}>{saving ? 'Saving…' : 'Create incident'}</button></div>
      </form>
    </div>
  );
}

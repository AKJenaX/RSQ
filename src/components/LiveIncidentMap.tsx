import React from 'react';
import { MapContainer, TileLayer, Marker, Popup } from 'react-leaflet';
import type { IntelligenceReport } from '../hooks/useOperationalIntelligence';
import type { Volunteer, Resource } from '../types/incident';
import { formatRelativeTime } from '../utils/formatters';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import { SeverityBadge, StatusBadge } from './Badge';

import icon from 'leaflet/dist/images/marker-icon.png';
import iconShadow from 'leaflet/dist/images/marker-shadow.png';
import iconRetina from 'leaflet/dist/images/marker-icon-2x.png';

let DefaultIcon = L.icon({
    iconUrl: icon,
    shadowUrl: iconShadow,
    iconRetinaUrl: iconRetina,
    iconSize: [25, 41],
    iconAnchor: [12, 41]
});

L.Marker.prototype.options.icon = DefaultIcon;

const getIconForSeverity = (severity: string | undefined) => {
  let color = 'blue';
  if (severity === 'CRITICAL') color = 'red';
  else if (severity === 'HIGH') color = 'orange';
  else if (severity === 'MEDIUM') color = 'yellow';
  
  return new L.Icon({
    iconUrl: `https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-${color}.png`,
    shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png',
    iconSize: [25, 41],
    iconAnchor: [12, 41],
    popupAnchor: [1, -34],
    shadowSize: [41, 41]
  });
};

const volIcon = new L.Icon({
  iconUrl: `https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-green.png`,
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41]
});

const resIcon = new L.Icon({
  iconUrl: `https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-violet.png`,
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41]
});

interface LiveIncidentMapProps {
  incidents: IntelligenceReport[];
  volunteers?: Volunteer[];
  resources?: Resource[];
}

export function LiveIncidentMap({ incidents, volunteers = [], resources = [] }: LiveIncidentMapProps): React.ReactElement {
  const mappedIncidents = incidents.filter(
    r => r.latitude !== undefined && r.longitude !== undefined && !isNaN(r.latitude) && !isNaN(r.longitude)
  );
  
  const mappedVols = volunteers.filter(
    v => v.latitude !== undefined && v.longitude !== undefined && !isNaN(v.latitude) && !isNaN(v.longitude)
  );

  const mappedRes = resources.filter(
    r => r.latitude !== undefined && r.longitude !== undefined && !isNaN(r.latitude) && !isNaN(r.longitude)
  );

  let center: [number, number] = [20.5937, 78.9629];
  if (mappedIncidents.length > 0) {
    center = [mappedIncidents[0].latitude!, mappedIncidents[0].longitude!];
  } else if (mappedVols.length > 0) {
    center = [mappedVols[0].latitude!, mappedVols[0].longitude!];
  } else if (mappedRes.length > 0) {
    center = [mappedRes[0].latitude!, mappedRes[0].longitude!];
  }

  return (
    <div style={{ height: '100%', minHeight: '400px', width: '100%', borderRadius: '8px', overflow: 'hidden', border: '1px solid var(--border)', zIndex: 1 }}>
      <MapContainer center={center} zoom={5} style={{ height: '100%', width: '100%' }}>
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        {mappedIncidents.map(incident => (
          <Marker 
            key={`inc-${incident.reportId}`} 
            position={[incident.latitude!, incident.longitude!]}
            icon={getIconForSeverity(incident.severity)}
          >
            <Popup>
              <div style={{ minWidth: '200px' }}>
                <h3 className="font-bold mb-1">{incident.title || 'Incident'}</h3>
                <div className="flex gap-1 mb-2">
                  <SeverityBadge value={incident.severity} />
                  <StatusBadge value={incident.status} />
                </div>
                <div className="text-xs text-secondary mb-1">
                  Reported: {formatRelativeTime(incident.timestamp)}
                </div>
                <a href={`/reports/${incident.reportId}`} className="btn-secondary" style={{ display: 'block', textAlign: 'center', padding: '4px', fontSize: '0.8rem', textDecoration: 'none' }}>
                  View Incident
                </a>
              </div>
            </Popup>
          </Marker>
        ))}
        {mappedVols.map(vol => (
          <Marker 
            key={`vol-${vol.id}`} 
            position={[vol.latitude!, vol.longitude!]}
            icon={volIcon}
          >
            <Popup>
              <div style={{ minWidth: '150px' }}>
                <h3 className="font-bold text-success mb-1">{vol.name}</h3>
                <div className="text-xs font-semibold uppercase">{vol.status}</div>
                <div className="text-xs text-secondary">{vol.role}</div>
              </div>
            </Popup>
          </Marker>
        ))}
        {mappedRes.map(res => (
          <Marker 
            key={`res-${res.id}`} 
            position={[res.latitude!, res.longitude!]}
            icon={resIcon}
          >
            <Popup>
              <div style={{ minWidth: '150px' }}>
                <h3 className="font-bold text-info mb-1">{res.name}</h3>
                <div className="text-xs font-semibold uppercase">{res.status}</div>
                <div className="text-xs text-secondary">Qty: {res.capacity}</div>
              </div>
            </Popup>
          </Marker>
        ))}
      </MapContainer>
    </div>
  );
}
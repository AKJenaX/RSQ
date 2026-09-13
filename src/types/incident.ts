export interface Volunteer {
  id: string;
  name: string;
  role: string;
  status: 'AVAILABLE' | 'ASSIGNED' | 'UNAVAILABLE' | 'BUSY' | 'OFFLINE';
  latitude?: number;
  longitude?: number;
  lastUpdated?: number;
  contact?: string;
}

export interface Resource {
  id: string;
  name: string;
  type: string;
  status: 'AVAILABLE' | 'ASSIGNED' | 'UNAVAILABLE' | 'IN_USE' | 'RELEASED';
  latitude?: number;
  longitude?: number;
  capacity?: number;
  availableUnits?: number;
  totalUnits?: number;
  maintenanceUnits?: number;
  assignedUnits?: number;
}

export type ActivityType =
  | 'REPORT_CREATED'
  | 'VOLUNTEER_ASSIGNED'
  | 'RESOURCE_ASSIGNED'
  | 'STATUS_CHANGED'
  | 'CASE_RESOLVED'
  | 'VOLUNTEER_UNASSIGNED'
  | 'RESOURCE_UNASSIGNED'
  | 'INCIDENT_ESCALATED'
  | 'INCIDENT_REOPENED'
  | 'AUTHORITY_LOGIN';

export interface ActivityRecord {
  id: string;
  type: ActivityType;
  timestamp: number;
  performedBy: string;
  metadata?: Record<string, any>;
}

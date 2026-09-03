export interface Volunteer {
  id: string;
  name: string;
  role: string;
  status: 'AVAILABLE' | 'ASSIGNED' | 'UNAVAILABLE';
}

export interface Resource {
  id: string;
  name: string;
  type: string;
  status: 'AVAILABLE' | 'ASSIGNED' | 'UNAVAILABLE';
}

export type ActivityType =
  | 'REPORT_CREATED'
  | 'VOLUNTEER_ASSIGNED'
  | 'RESOURCE_ASSIGNED'
  | 'STATUS_CHANGED'
  | 'CASE_RESOLVED'
  | 'VOLUNTEER_UNASSIGNED'
  | 'RESOURCE_UNASSIGNED';

export interface ActivityRecord {
  id: string;
  type: ActivityType;
  timestamp: number;
  performedBy: string;
  metadata?: Record<string, any>;
}

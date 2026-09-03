import { useMemo } from 'react';
import type { DisasterReport } from '../types/report';
import type { Volunteer, Resource } from '../types/incident';

export type IncidentPriority = 1 | 2 | 3 | 4 | 5 | 6;
export type OperationalHealth = 'NORMAL' | 'WATCH' | 'CRITICAL';

export interface IntelligenceReport extends DisasterReport {
  priorityScore: IncidentPriority;
  priorityReason: string;
  isAttentionRequired: boolean;
}

export interface OperationalIntelligence {
  // Queue & Attention
  incidentQueue: IntelligenceReport[];
  attentionItems: IntelligenceReport[];
  
  // Capacities
  volunteersTotal: number;
  volunteersAvailable: number;
  volunteersAssigned: number;
  volunteersRatio: number | null; // 0-1
  
  resourcesTotal: number;
  resourcesAvailable: number;
  resourcesAssigned: number;
  resourcesRatio: number | null; // 0-1
  
  // Health & Warnings
  healthStatus: OperationalHealth;
  healthReason: string;
  capacityWarnings: string[];
  
  // Summary & Metrics
  summaryStatement: string;
  activeCount: number;
  criticalCount: number;
  unassignedCount: number;
  
  // Averages in MS (null if insufficient data)
  avgTimeToAssign: number | null; 
  avgTimeToResolve: number | null;
  
  // Helpers
  volMap: Map<string, string>;
  resMap: Map<string, string>;
}

export function useOperationalIntelligence(
  reports: DisasterReport[],
  volunteers: Volunteer[],
  resources: Resource[]
): OperationalIntelligence {
  return useMemo(() => {
    const volMap = new Map(volunteers.map(v => [v.id, v.name]));
    const resMap = new Map(resources.map(r => [r.id, r.name]));

    // 1. Capacity Calculations
    const volunteersTotal = volunteers.length;
    const volunteersAvailable = volunteers.filter(v => v.status === 'AVAILABLE').length;
    const volunteersAssigned = volunteers.filter(v => v.status === 'ASSIGNED').length;
    const volunteersRatio = volunteersTotal > 0 ? volunteersAvailable / volunteersTotal : null;

    const resourcesTotal = resources.length;
    const resourcesAvailable = resources.filter(r => r.status === 'AVAILABLE').length;
    const resourcesAssigned = resources.filter(r => r.status === 'ASSIGNED').length;
    const resourcesRatio = resourcesTotal > 0 ? resourcesAvailable / resourcesTotal : null;

    // 2. Capacity Warnings
    const capacityWarnings: string[] = [];
    if (volunteersTotal > 0) {
      if (volunteersAvailable === 0) capacityWarnings.push("No volunteers are currently available.");
      else if (volunteersRatio !== null && volunteersRatio < 0.2) capacityWarnings.push(`Only ${volunteersAvailable} of ${volunteersTotal} volunteers are available.`);
    }
    if (resourcesTotal > 0) {
      if (resourcesAvailable === 0) capacityWarnings.push("No resources are currently available.");
      else if (resourcesRatio !== null && resourcesRatio < 0.2) capacityWarnings.push(`Only ${resourcesAvailable} of ${resourcesTotal} resources are available.`);
    }

    // 3. Priority Engine & Metrics
    let totalAssignTime = 0;
    let assignCount = 0;
    let totalResolveTime = 0;
    let resolveCount = 0;

    const incidentQueue: IntelligenceReport[] = reports.map(r => {
      const hasVol = !!r.assignedVolunteerId;
      const hasRes = !!(r.assignedResourceIds && r.assignedResourceIds.length > 0);
      const isResolved = r.status === 'RESOLVED';
      
      // Calculate response metrics if timestamps exist
      if (r.timestamp && r.assignedAt) {
        totalAssignTime += (r.assignedAt - r.timestamp);
        assignCount++;
      }
      if (r.timestamp && r.resolvedAt) {
        totalResolveTime += (r.resolvedAt - r.timestamp);
        resolveCount++;
      }
      
      let score: IncidentPriority = 5;
      let reason = 'Active';
      let attention = false;

      if (isResolved) {
        score = 6;
        reason = 'Resolved';
      } else if (r.severity === 'CRITICAL' && (!hasVol && !hasRes)) {
        score = 1;
        reason = 'Critical & Unassigned';
        attention = true;
      } else if (r.severity === 'CRITICAL' && (!hasVol || !hasRes)) {
        score = 2;
        reason = hasVol ? 'Critical (Missing Resources)' : 'Critical (Missing Volunteer)';
        attention = true;
      } else if (r.severity === 'HIGH' && (!hasVol && !hasRes)) {
        score = 3;
        reason = 'High & Unassigned';
        attention = true;
      } else if (r.severity === 'HIGH' && (!hasVol || !hasRes)) {
        score = 4;
        reason = hasVol ? 'High (Missing Resources)' : 'High (Missing Volunteer)';
        // Attention is required for high severity incidents lacking capacity if we are low on capacity
        if (volunteersRatio !== null && volunteersRatio < 0.3) {
          attention = true;
        }
      } else if (r.status === 'IN_PROGRESS' || r.status === 'ASSIGNED') {
        score = 5;
        reason = 'In Progress';
      }

      return {
        ...r,
        priorityScore: score,
        priorityReason: reason,
        isAttentionRequired: attention
      };
    });

    // 4. Sort Queue (Priority Ascending, then Age Descending)
    incidentQueue.sort((a, b) => {
      if (a.priorityScore !== b.priorityScore) {
        return a.priorityScore - b.priorityScore;
      }
      return (b.timestamp || 0) - (a.timestamp || 0); // Newest first for same priority
    });

    const attentionItems = incidentQueue.filter(r => r.isAttentionRequired);
    const activeCount = incidentQueue.filter(r => r.status !== 'RESOLVED').length;
    const criticalCount = incidentQueue.filter(r => r.severity === 'CRITICAL' && r.status !== 'RESOLVED').length;
    const unassignedCount = incidentQueue.filter(r => (!r.assignedVolunteerId && (!r.assignedResourceIds || r.assignedResourceIds.length === 0)) && r.status !== 'RESOLVED').length;

    // 5. Operational Health
    let healthStatus: OperationalHealth = 'NORMAL';
    let healthReason = 'All active incidents have response coverage.';
    
    if (attentionItems.some(r => r.severity === 'CRITICAL')) {
      healthStatus = 'CRITICAL';
      healthReason = 'Critical incidents currently lack response capacity.';
    } else if (attentionItems.length > 0 || capacityWarnings.length > 0) {
      healthStatus = 'WATCH';
      if (attentionItems.length > 0) {
        healthReason = 'Several incidents require attention.';
      } else {
        healthReason = 'Operational capacity is constrained.';
      }
    }

    // 6. Summary Statement
    let summaryStatement = 'All active incidents currently have response coverage.';
    if (criticalCount > 0 && attentionItems.some(r => r.severity === 'CRITICAL')) {
      summaryStatement = `${criticalCount} critical incident${criticalCount > 1 ? 's' : ''} require attention.`;
    } else if (unassignedCount > 0) {
      summaryStatement = `${unassignedCount} incident${unassignedCount > 1 ? 's' : ''} are awaiting assignment.`;
    } else if (activeCount > 0) {
      summaryStatement = `${activeCount} active incident${activeCount > 1 ? 's' : ''} being managed.`;
    }

    // 7. Metrics
    const avgTimeToAssign = assignCount > 0 ? totalAssignTime / assignCount : null;
    const avgTimeToResolve = resolveCount > 0 ? totalResolveTime / resolveCount : null;

    return {
      incidentQueue,
      attentionItems,
      volunteersTotal,
      volunteersAvailable,
      volunteersAssigned,
      volunteersRatio,
      resourcesTotal,
      resourcesAvailable,
      resourcesAssigned,
      resourcesRatio,
      healthStatus,
      healthReason,
      capacityWarnings,
      summaryStatement,
      activeCount,
      criticalCount,
      unassignedCount,
      avgTimeToAssign,
      avgTimeToResolve,
      volMap,
      resMap
    };
  }, [reports, volunteers, resources]);
}

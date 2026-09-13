export const CANONICAL_ACTIVE_STATUSES: string[] = ['OPEN', 'ESCALATED', 'ASSIGNED', 'DISPATCHED', 'IN_PROGRESS'];
export const CANONICAL_RESOLVED_STATUSES: string[] = ['RESOLVED', 'CLOSED', 'CANCELLED'];

export function isActiveIncident(status: string | undefined | null): boolean {
  if (!status) return true; // Default to active if unknown
  return CANONICAL_ACTIVE_STATUSES.includes(status.toUpperCase());
}

export function isResolvedIncident(status: string | undefined | null): boolean {
  if (!status) return false;
  return CANONICAL_RESOLVED_STATUSES.includes(status.toUpperCase());
}

/**
 * formatters.ts — utility functions for formatting verified RSQ report fields.
 *
 * All display formatting lives here; components should not contain format logic.
 *
 * Verified field formats:
 *   severity  — uppercase string: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL"
 *   status    — uppercase string: "OPEN" (others possible)
 *   timestamp — numeric Unix milliseconds (e.g. 1787505580913)
 *   latitude  — number (decimal degrees)
 *   longitude — number (decimal degrees)
 */

// ── Timestamp formatting ──────────────────────────────────────────────────────

/**
 * Converts a numeric Unix-millisecond timestamp to a Date object.
 * Returns null if the value is missing, non-numeric, or invalid.
 */
function timestampToDate(value: number | undefined | null): Date | null {
  if (value === undefined || value === null) return null;
  if (typeof value !== 'number' || !isFinite(value)) return null;
  const date = new Date(value);
  if (isNaN(date.getTime())) return null;
  return date;
}

/**
 * Formats a numeric Unix-ms timestamp into a human-readable date/time string.
 * Returns '—' for missing, invalid, or non-numeric values.
 */
export function formatTimestamp(value: number | undefined | null): string {
  const date = timestampToDate(value);
  if (!date) return '—';

  return date.toLocaleString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
  });
}

/**
 * Returns a relative time string (e.g. "3m ago") for a numeric Unix-ms timestamp.
 * Falls back to the full formatted date for timestamps older than 7 days.
 * Returns '—' for missing or invalid values.
 */
export function formatRelativeTime(value: number | undefined | null): string {
  const date = timestampToDate(value);
  if (!date) return '—';

  const diffMs = Date.now() - date.getTime();
  const diffSec = Math.floor(diffMs / 1000);
  const diffMin = Math.floor(diffSec / 60);
  const diffHr = Math.floor(diffMin / 60);
  const diffDay = Math.floor(diffHr / 24);

  if (diffSec < 60) return 'just now';
  if (diffMin < 60) return `${diffMin}m ago`;
  if (diffHr < 24) return `${diffHr}h ago`;
  if (diffDay < 7) return `${diffDay}d ago`;

  return formatTimestamp(value);
}

// ── Severity formatting ───────────────────────────────────────────────────────
// Verified values from the RSQ Firestore database use UPPERCASE.

const SEVERITY_LABEL_MAP: Record<string, string> = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
  CRITICAL: 'Critical',
};

export function formatSeverity(value: string | undefined): string {
  if (!value) return 'Unknown';
  // Normalise to uppercase for lookup; fall back to displaying the raw value
  return SEVERITY_LABEL_MAP[value.toUpperCase()] ?? capitalize(value);
}

export type SeverityVariant = 'low' | 'medium' | 'high' | 'critical' | 'unknown';

/**
 * Maps a severity string (case-insensitive) to a CSS variant name.
 * The variant drives the badge CSS class: badge-severity-{variant}
 */
export function getSeverityVariant(value: string | undefined): SeverityVariant {
  if (!value) return 'unknown';
  switch (value.toUpperCase()) {
    case 'LOW':      return 'low';
    case 'MEDIUM':   return 'medium';
    case 'HIGH':     return 'high';
    case 'CRITICAL': return 'critical';
    default:         return 'unknown';
  }
}

// ── Status formatting ─────────────────────────────────────────────────────────
// Verified values from the RSQ Firestore database use UPPERCASE.

const STATUS_LABEL_MAP: Record<string, string> = {
  OPEN: 'Open',
  ASSIGNED: 'Assigned',
  IN_PROGRESS: 'In Progress',
  RESOLVED: 'Resolved',
};

export function formatStatus(value: string | undefined): string {
  if (!value) return 'Unknown';
  return STATUS_LABEL_MAP[value.toUpperCase()] ?? capitalize(value);
}

export type StatusVariant = 'open' | 'assigned' | 'in_progress' | 'resolved' | 'unknown';

/**
 * Maps a status string (case-insensitive) to a CSS variant name.
 * The variant drives the badge CSS class: badge-status-{variant}
 */
export function getStatusVariant(value: string | undefined): StatusVariant {
  if (!value) return 'unknown';
  const val = value.toUpperCase();
  if (val === 'OPEN') return 'open';
  if (val === 'ASSIGNED') return 'assigned';
  if (val === 'IN_PROGRESS') return 'in_progress';
  if (val === 'RESOLVED') return 'resolved';
  return 'unknown';
}

// ── Location formatting ───────────────────────────────────────────────────────

/**
 * Formats latitude and longitude (top-level fields) as a compact string.
 * Used in table cells where space is limited.
 */
export function formatCoordinates(
  latitude: number | undefined,
  longitude: number | undefined
): string {
  if (latitude === undefined && longitude === undefined) return '—';
  const lat = latitude !== undefined ? latitude.toFixed(5) : '?';
  const lng = longitude !== undefined ? longitude.toFixed(5) : '?';
  return `${lat}, ${lng}`;
}

// ── ID formatting ─────────────────────────────────────────────────────────────

/**
 * Formats a Firestore document ID for display.
 * Shows the last 8 characters prefixed with "#" for readability.
 */
export function formatReportId(id: string | undefined): string {
  if (!id) return '—';
  return `#${id.slice(-8).toUpperCase()}`;
}

// ── String helpers ────────────────────────────────────────────────────────────

/**
 * Truncates a string to a maximum length, appending "…" if truncated.
 */
export function truncate(str: string | undefined, maxLength: number): string {
  if (!str) return '—';
  if (str.length <= maxLength) return str;
  return str.slice(0, maxLength) + '…';
}

function capitalize(str: string): string {
  if (!str) return str;
  return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
}

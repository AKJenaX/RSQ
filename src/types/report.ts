/**
 * DisasterReport — TypeScript interface based on the VERIFIED RSQ Firestore schema.
 *
 * Verified Firestore fields (collection: "reports"):
 *   description  — string
 *   imageUrl     — string (direct URL; may point to Firebase Storage)
 *   latitude     — number
 *   longitude    — number
 *   severity     — string ("LOW" | "MEDIUM" | "HIGH" | "CRITICAL" or other)
 *   status       — string ("OPEN" or other)
 *   timestamp    — number (Unix milliseconds, e.g. 1787505580913)
 *   title        — string
 *   userId       — string (Firebase Auth UID of the reporter)
 *
 * The Firestore document ID is injected as `reportId` by reportsService.ts.
 *
 * All fields except `reportId` are optional because Firestore documents may
 * be partially populated. Missing fields must be handled gracefully — never crash.
 *
 * ── Field mapping ─────────────────────────────────────────────────────────────
 * The mapping between raw Firestore field names and this interface is maintained
 * ONLY in reportsService.ts. Do not duplicate field name assumptions in UI code.
 */

// ── Verified severity values ──────────────────────────────────────────────────
// The actual database uses uppercase strings. Other values may be present.
export type KnownSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

// ── Verified status values ────────────────────────────────────────────────────
// The actual database uses uppercase strings. Other values may be present.
export type KnownStatus = 'OPEN' | 'ASSIGNED' | 'IN_PROGRESS' | 'RESOLVED';

// ── Primary interface ─────────────────────────────────────────────────────────

export interface DisasterReport {
  /** Firestore document ID — injected from DocumentSnapshot.id */
  reportId: string;

  /** Short title of the emergency report */
  title?: string;

  /** User-facing description of the emergency */
  description?: string;

  /**
   * Direct URL to the report image (stored in Firebase Storage).
   * Corresponds to Firestore field: imageUrl
   */
  imageUrl?: string;

  /**
   * Geographic latitude in decimal degrees.
   * Corresponds to Firestore field: latitude
   */
  latitude?: number;

  /**
   * Geographic longitude in decimal degrees.
   * Corresponds to Firestore field: longitude
   */
  longitude?: number;

  /**
   * Severity of the disaster.
   * Verified values: "LOW", "MEDIUM", "HIGH", "CRITICAL"
   * Other string values are possible — always render defensively.
   * Corresponds to Firestore field: severity
   */
  severity?: KnownSeverity | string;

  /**
   * Current lifecycle status of the report.
   * Verified values: "OPEN"
   * Other string values are possible — always render defensively.
   * Corresponds to Firestore field: status
   */
  status?: KnownStatus | string;

  /**
   * Report creation time as Unix milliseconds (e.g. 1787505580913).
   * Corresponds to Firestore field: timestamp
   */
  timestamp?: number;

  /**
   * Firebase Auth UID of the user who submitted the report.
   * Corresponds to Firestore field: userId
   */
  userId?: string;

  // ── Assignment & Resolution extensions ──────────────────────────────────────

  /**
   * ID of the volunteer assigned to this incident.
   */
  assignedVolunteerId?: string;

  /**
   * List of IDs for resources assigned to this incident.
   */
  assignedResourceIds?: string[];

  /**
   * Timestamp when the assignment occurred (Unix ms).
   */
  assignedAt?: number;

  /**
   * Firebase Auth UID of the authority who assigned this.
   */
  assignedBy?: string;

  /**
   * Timestamp when the incident was resolved (Unix ms).
   */
  resolvedAt?: number;

  /**
   * Firebase Auth UID of the authority who resolved this.
   */
  resolvedBy?: string;

  /**
   * Note provided upon resolution.
   */
  resolutionNote?: string;

  /**
   * Any additional Firestore fields not part of the known verified schema.
   * Stored here rather than silently dropped, for future extensibility.
   */
  extras?: Record<string, unknown>;
}

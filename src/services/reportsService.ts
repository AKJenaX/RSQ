/**
 * reportsService.ts
 *
 * All Firestore read logic for RSQ disaster reports.
 * UI components must NOT import from firebase/firestore directly.
 *
 * ── Verified Firestore field mapping ─────────────────────────────────────────
 * This is the ONLY file where raw Firestore field names are used.
 * Update FIELDS below if the actual field names change — the rest of the
 * application will follow automatically.
 *
 *   Firestore field   → DisasterReport property
 *   ─────────────────────────────────────────────
 *   (document ID)     → reportId
 *   title             → title
 *   description       → description
 *   imageUrl          → imageUrl
 *   latitude          → latitude    (number)
 *   longitude         → longitude   (number)
 *   severity          → severity    (e.g. "LOW", "HIGH", "CRITICAL")
 *   status            → status      (e.g. "OPEN")
 *   timestamp         → timestamp   (Unix ms, e.g. 1787505580913)
 *   userId            → userId
 *
 * Fields NOT mapped (not verified in the actual database):
 *   riskLevel, createdAt, mediaUrls, mediaRef, aiAnalysis, suggestedResources
 */

import {
  collection,
  doc,
  onSnapshot,
  query,
  orderBy,
  Timestamp,
} from 'firebase/firestore';
import type {
  DocumentSnapshot,
  QuerySnapshot,
  FirestoreError,
  Unsubscribe,
} from 'firebase/firestore';
import { db, REPORTS_COLLECTION } from '../firebase/config';
import type { DisasterReport } from '../types/report';

// ── Verified Firestore field names ────────────────────────────────────────────
// Change string values here (and ONLY here) if the actual Firestore field
// names differ from the verified schema.
const FIELDS = {
  title: 'title',
  description: 'description',
  imageUrl: 'imageUrl',
  latitude: 'latitude',
  longitude: 'longitude',
  severity: 'severity',
  status: 'status',
  timestamp: 'timestamp',
  userId: 'userId',
  // Extension fields
  assignedVolunteerId: 'assignedVolunteerId',
  assignedResourceIds: 'assignedResourceIds',
  assignedAt: 'assignedAt',
  assignedBy: 'assignedBy',
  resolvedAt: 'resolvedAt',
  resolvedBy: 'resolvedBy',
  resolutionNote: 'resolutionNote',
} as const;

// Plain string set of known field names (for extras collection)
const KNOWN_FIELD_NAMES: ReadonlySet<string> = new Set(Object.values(FIELDS));

// ── Helper: safely read a string field ───────────────────────────────────────
function readString(data: Record<string, unknown>, field: string): string | undefined {
  const v = data[field];
  return typeof v === 'string' && v.length > 0 ? v : undefined;
}

// ── Helper: safely read a number field ───────────────────────────────────────
function readNumber(data: Record<string, unknown>, field: string): number | undefined {
  const v = data[field];
  return typeof v === 'number' && isFinite(v) ? v : undefined;
}

// ── Helper: safely read a string array field ─────────────────────────────────
function readStringArray(data: Record<string, unknown>, field: string): string[] | undefined {
  const v = data[field];
  if (Array.isArray(v)) {
    const arr = v.filter(item => typeof item === 'string');
    return arr.length > 0 ? arr : undefined;
  }
  return undefined;
}

// ── Helper: read the timestamp field defensively ──────────────────────────────
// The Android app may store timestamp as:
//   • A raw number (Unix milliseconds, e.g. 1787505580913)  — verified
//   • A Firestore Timestamp object (from FieldValue.serverTimestamp())  — possible
// Both are normalised to a numeric Unix-ms value so the rest of the app
// never needs to know which format was used.
function readTimestampAsMs(data: Record<string, unknown>, field: string): number | undefined {
  const v = data[field];
  // Case 1: already a finite number (Unix ms or seconds — ms if > 1e10)
  if (typeof v === 'number' && isFinite(v)) {
    // Heuristic: if value looks like seconds (< 1e10) convert to ms
    return v < 1e10 ? v * 1000 : v;
  }
  // Case 2: Firestore Timestamp object with .toMillis()
  if (v instanceof Timestamp) {
    return v.toMillis();
  }
  // Case 3: plain object with seconds/nanoseconds (Timestamp serialised)
  if (v && typeof v === 'object') {
    const obj = v as Record<string, unknown>;
    if (typeof obj.seconds === 'number' && isFinite(obj.seconds)) {
      const ns = typeof obj.nanoseconds === 'number' ? obj.nanoseconds : 0;
      return obj.seconds * 1000 + Math.floor(ns / 1e6);
    }
  }
  return undefined;
}

// ── Document mapper ───────────────────────────────────────────────────────────

/**
 * Maps a raw Firestore DocumentSnapshot to a typed DisasterReport.
 * Unknown fields are collected into `extras` rather than silently dropped.
 */
function mapDocumentToReport(snapshot: DocumentSnapshot): DisasterReport {
  const data = (snapshot.data() ?? {}) as Record<string, unknown>;

  // Collect unknown fields for transparency
  const extras: Record<string, unknown> = {};
  for (const key of Object.keys(data)) {
    if (!KNOWN_FIELD_NAMES.has(key)) {
      extras[key] = data[key];
    }
  }

  return {
    reportId: snapshot.id,
    title: readString(data, FIELDS.title),
    description: readString(data, FIELDS.description),
    imageUrl: readString(data, FIELDS.imageUrl),
    latitude: readNumber(data, FIELDS.latitude),
    longitude: readNumber(data, FIELDS.longitude),
    severity: readString(data, FIELDS.severity),
    status: readString(data, FIELDS.status),
    timestamp: readTimestampAsMs(data, FIELDS.timestamp),
    userId: readString(data, FIELDS.userId),
    assignedVolunteerId: readString(data, FIELDS.assignedVolunteerId),
    assignedResourceIds: readStringArray(data, FIELDS.assignedResourceIds),
    assignedAt: readNumber(data, FIELDS.assignedAt),
    assignedBy: readString(data, FIELDS.assignedBy),
    resolvedAt: readNumber(data, FIELDS.resolvedAt),
    resolvedBy: readString(data, FIELDS.resolvedBy),
    resolutionNote: readString(data, FIELDS.resolutionNote),
    extras: Object.keys(extras).length > 0 ? extras : undefined,
  };
}

// ── Public service functions ──────────────────────────────────────────────────

/**
 * Subscribes to the reports collection with real-time updates.
 * Reports are ordered by timestamp descending (newest first).
 *
 * @param onData  - Called whenever the report list changes.
 * @param onError - Called when a Firestore error occurs (e.g. permission denied).
 * @returns Unsubscribe function — MUST be called on component unmount.
 */
export function subscribeToReports(
  onData: (reports: DisasterReport[]) => void,
  onError: (error: FirestoreError) => void
): Unsubscribe {
  const reportsRef = collection(db, REPORTS_COLLECTION);
  // Use string directly to avoid const-narrowing issues with orderBy
  const timestampField: string = FIELDS.timestamp;
  const reportsQuery = query(reportsRef, orderBy(timestampField, 'desc'));

  return onSnapshot(
    reportsQuery,
    (snapshot: QuerySnapshot) => {
      const reports = snapshot.docs.map(mapDocumentToReport);
      onData(reports);
    },
    onError
  );
}

/**
 * Subscribes to a single report document with real-time updates.
 *
 * @param reportId - Firestore document ID (the URL segment, e.g. /reports/:id).
 * @param onData   - Called with the mapped report, or null if the document is deleted.
 * @param onError  - Called when a Firestore error occurs.
 * @returns Unsubscribe function — MUST be called on component unmount.
 */
export function subscribeToReport(
  reportId: string,
  onData: (report: DisasterReport | null) => void,
  onError: (error: FirestoreError) => void
): Unsubscribe {
  const reportRef = doc(db, REPORTS_COLLECTION, reportId);

  return onSnapshot(
    reportRef,
    (snapshot: DocumentSnapshot) => {
      if (!snapshot.exists()) {
        onData(null);
        return;
      }
      onData(mapDocumentToReport(snapshot));
    },
    onError
  );
}

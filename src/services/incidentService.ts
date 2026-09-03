import {
  collection,
  doc,
  writeBatch,
  serverTimestamp,
  getDocs,
  query,
  orderBy,
  onSnapshot
} from 'firebase/firestore';
import { db, REPORTS_COLLECTION } from '../firebase/config';
import type { ActivityRecord, Volunteer, Resource } from '../types/incident';
import type { FirestoreError } from 'firebase/firestore';

const VOLUNTEERS_COLLECTION = 'volunteers';
const RESOURCES_COLLECTION = 'resources';

// ── Private helpers ────────────────────────────────────────────────────────

function getActivityRef(reportId: string) {
  return doc(collection(db, REPORTS_COLLECTION, reportId, 'activity'));
}

// ── Public API ─────────────────────────────────────────────────────────────

export async function assignVolunteer(reportId: string, volunteerId: string, authorityUid: string): Promise<void> {
  const batch = writeBatch(db);
  const reportRef = doc(db, REPORTS_COLLECTION, reportId);
  const volunteerRef = doc(db, VOLUNTEERS_COLLECTION, volunteerId);
  const activityRef = getActivityRef(reportId);

  // 1. Update report
  batch.update(reportRef, {
    assignedVolunteerId: volunteerId,
    assignedAt: serverTimestamp(),
    assignedBy: authorityUid,
    status: 'ASSIGNED' // automatically update status if we want, but let's just update assignment
  });

  // 2. Update volunteer status
  batch.update(volunteerRef, { status: 'ASSIGNED' });

  // 3. Create activity record
  batch.set(activityRef, {
    type: 'VOLUNTEER_ASSIGNED',
    timestamp: serverTimestamp(),
    performedBy: authorityUid,
    metadata: { volunteerId }
  });

  await batch.commit();
}

export async function assignResources(reportId: string, resourceIds: string[], authorityUid: string): Promise<void> {
  if (resourceIds.length === 0) return;

  const batch = writeBatch(db);
  const reportRef = doc(db, REPORTS_COLLECTION, reportId);
  const activityRef = getActivityRef(reportId);

  // 1. Update report
  // Note: Firestore doesn't easily append to arrays without arrayUnion, but replacing is safer here if we assume it overwrites.
  // We should probably use arrayUnion if we are adding, but the UI might just provide the new full list.
  // For simplicity, let's just set the full list.
  batch.update(reportRef, {
    assignedResourceIds: resourceIds,
    assignedAt: serverTimestamp(),
    assignedBy: authorityUid,
  });

  // 2. Update resource statuses
  for (const resId of resourceIds) {
    const resRef = doc(db, RESOURCES_COLLECTION, resId);
    batch.update(resRef, { status: 'ASSIGNED' });
  }

  // 3. Create activity record
  batch.set(activityRef, {
    type: 'RESOURCE_ASSIGNED',
    timestamp: serverTimestamp(),
    performedBy: authorityUid,
    metadata: { resourceIds }
  });

  await batch.commit();
}

export async function changeReportStatus(reportId: string, fromStatus: string, toStatus: string, authorityUid: string): Promise<void> {
  if (fromStatus === toStatus) return;

  const batch = writeBatch(db);
  const reportRef = doc(db, REPORTS_COLLECTION, reportId);
  const activityRef = getActivityRef(reportId);

  batch.update(reportRef, { status: toStatus });

  batch.set(activityRef, {
    type: 'STATUS_CHANGED',
    timestamp: serverTimestamp(),
    performedBy: authorityUid,
    metadata: { from: fromStatus, to: toStatus }
  });

  await batch.commit();
}

export async function resolveIncident(reportId: string, authorityUid: string, resolutionNote: string): Promise<void> {
  const batch = writeBatch(db);
  const reportRef = doc(db, REPORTS_COLLECTION, reportId);
  const activityRef = getActivityRef(reportId);

  batch.update(reportRef, {
    status: 'RESOLVED',
    resolvedAt: serverTimestamp(),
    resolvedBy: authorityUid,
    resolutionNote
  });

  batch.set(activityRef, {
    type: 'CASE_RESOLVED',
    timestamp: serverTimestamp(),
    performedBy: authorityUid,
    metadata: { resolutionNote }
  });

  await batch.commit();
}

// ── Data Fetching Hooks / Functions ────────────────────────────────────────

export async function getVolunteers(): Promise<Volunteer[]> {
  try {
    const snapshot = await getDocs(collection(db, VOLUNTEERS_COLLECTION));
    return snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() } as Volunteer));
  } catch (err) {
    console.warn('Failed to fetch volunteers. Collection might not exist.', err);
    return [];
  }
}

export async function getResources(): Promise<Resource[]> {
  try {
    const snapshot = await getDocs(collection(db, RESOURCES_COLLECTION));
    return snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() } as Resource));
  } catch (err) {
    console.warn('Failed to fetch resources. Collection might not exist.', err);
    return [];
  }
}

export function subscribeToReportActivity(reportId: string, onData: (activities: ActivityRecord[]) => void) {
  const q = query(collection(db, REPORTS_COLLECTION, reportId, 'activity'), orderBy('timestamp', 'desc'));
  
  return onSnapshot(q, (snapshot) => {
    const activities = snapshot.docs.map(doc => {
      const data = doc.data();
      return {
        id: doc.id,
        type: data.type,
        timestamp: data.timestamp?.toMillis() || Date.now(),
        performedBy: data.performedBy,
        metadata: data.metadata,
      } as ActivityRecord;
    });
    onData(activities);
  }, (error) => {
    console.error('Error fetching activity:', error);
    onData([]);
  });
}

export function subscribeToVolunteers(onData: (volunteers: Volunteer[]) => void, onError: (error: FirestoreError) => void) {
  return onSnapshot(
    collection(db, VOLUNTEERS_COLLECTION),
    (snapshot) => {
      const volunteers = snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() } as Volunteer));
      onData(volunteers);
    },
    onError
  );
}

export function subscribeToResources(onData: (resources: Resource[]) => void, onError: (error: FirestoreError) => void) {
  return onSnapshot(
    collection(db, RESOURCES_COLLECTION),
    (snapshot) => {
      const resources = snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() } as Resource));
      onData(resources);
    },
    onError
  );
}

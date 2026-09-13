import {
  collection,
  doc,
  writeBatch,
  serverTimestamp,
  getDocs,
  query,
  orderBy,
  onSnapshot,
  runTransaction
  ,setDoc
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

function asString(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : fallback;
}

function asNumber(value: unknown): number | undefined {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
}

function mapVolunteer(id: string, data: Record<string, unknown>): Volunteer {
  return {
    id,
    name: asString(data.name, 'Unnamed volunteer'),
    // Support existing field aliases without making the UI depend on one client.
    role: asString(data.role, asString(data.skill, 'Not specified')),
    status: asString(data.status, 'UNAVAILABLE').toUpperCase() as Volunteer['status'],
    latitude: asNumber(data.latitude),
    longitude: asNumber(data.longitude),
    lastUpdated: asNumber(data.lastUpdated),
    contact: asString(data.contact, asString(data.phone)),
  };
}

function mapResource(id: string, data: Record<string, unknown>): Resource {
  return {
    id,
    name: asString(data.name, asString(data.item, 'Unnamed resource')),
    type: asString(data.type, asString(data.category, 'OTHER')),
    status: asString(data.status, 'UNAVAILABLE').toUpperCase() as Resource['status'],
    latitude: asNumber(data.latitude),
    longitude: asNumber(data.longitude),
    capacity: asNumber(data.capacity ?? data.quantity),
  };
}

// ── Public API ─────────────────────────────────────────────────────────────

export async function assignVolunteer(reportId: string, volunteerId: string, authorityUid: string): Promise<void> {
  const reportRef = doc(db, REPORTS_COLLECTION, reportId);
  const volunteerRef = doc(db, VOLUNTEERS_COLLECTION, volunteerId);
  const activityRef = getActivityRef(reportId);

  await runTransaction(db, async (transaction) => {
    const volunteerDoc = await transaction.get(volunteerRef);
    if (!volunteerDoc.exists()) {
      throw new Error("Volunteer does not exist!");
    }
    
    if (volunteerDoc.data().status !== 'AVAILABLE') {
      throw new Error("Conflict: Volunteer is no longer available.");
    }

    transaction.update(reportRef, {
      assignedVolunteerId: volunteerId,
      assignedAt: serverTimestamp(),
      assignedBy: authorityUid,
      status: 'ASSIGNED'
    });

    transaction.update(volunteerRef, { status: 'ASSIGNED' });

    transaction.set(activityRef, {
      type: 'VOLUNTEER_ASSIGNED',
      timestamp: serverTimestamp(),
      performedBy: authorityUid,
      metadata: { volunteerId }
    });
  });
}

export async function assignResources(reportId: string, resourceIds: string[], authorityUid: string): Promise<void> {
  if (resourceIds.length === 0) return;

  const reportRef = doc(db, REPORTS_COLLECTION, reportId);
  const activityRef = getActivityRef(reportId);

  await runTransaction(db, async (transaction) => {
    // Check all resources first
    for (const resId of resourceIds) {
      const resRef = doc(db, RESOURCES_COLLECTION, resId);
      const resDoc = await transaction.get(resRef);
      if (!resDoc.exists()) {
        throw new Error(`Resource ${resId} does not exist!`);
      }
      if (resDoc.data().status !== 'AVAILABLE') {
        throw new Error(`Conflict: Resource ${resId} is no longer available.`);
      }
    }

    transaction.update(reportRef, {
      assignedResourceIds: resourceIds,
      assignedAt: serverTimestamp(),
      assignedBy: authorityUid,
    });

    for (const resId of resourceIds) {
      const resRef = doc(db, RESOURCES_COLLECTION, resId);
      transaction.update(resRef, { status: 'ASSIGNED' });
    }

    transaction.set(activityRef, {
      type: 'RESOURCE_ASSIGNED',
      timestamp: serverTimestamp(),
      performedBy: authorityUid,
      metadata: { resourceIds }
    });
  });
}

export async function changeReportStatus(reportId: string, fromStatus: string, toStatus: string, authorityUid: string): Promise<void> {
  if (fromStatus === toStatus) return;

  const batch = writeBatch(db);
  const reportRef = doc(db, REPORTS_COLLECTION, reportId);
  const activityRef = getActivityRef(reportId);

  batch.update(reportRef, { status: toStatus });

  let type = 'STATUS_CHANGED';
  if (fromStatus === 'RESOLVED' && toStatus === 'OPEN') type = 'INCIDENT_REOPENED';
  else if (toStatus === 'ESCALATED') type = 'INCIDENT_ESCALATED';

  batch.set(activityRef, {
    type,
    timestamp: serverTimestamp(),
    performedBy: authorityUid,
    metadata: { from: fromStatus, to: toStatus }
  });

  await batch.commit();
}

export async function resolveIncident(reportId: string, authorityUid: string, resolutionNote: string): Promise<void> {
  const reportRef = doc(db, REPORTS_COLLECTION, reportId);
  const activityRef = getActivityRef(reportId);

  await runTransaction(db, async (transaction) => {
    const reportDoc = await transaction.get(reportRef);
    if (!reportDoc.exists()) throw new Error("Incident not found");

    const reportData = reportDoc.data();

    transaction.update(reportRef, {
      status: 'RESOLVED',
      resolvedAt: serverTimestamp(),
      resolvedBy: authorityUid,
      resolutionNote
    });

    // Release volunteer
    if (reportData.assignedVolunteerId) {
      const volRef = doc(db, VOLUNTEERS_COLLECTION, reportData.assignedVolunteerId);
      const volDoc = await transaction.get(volRef);
      if (volDoc.exists() && volDoc.data().status === 'ASSIGNED') {
        transaction.update(volRef, { status: 'AVAILABLE' });
        const volActivityRef = doc(collection(db, REPORTS_COLLECTION, reportId, 'activity'));
        transaction.set(volActivityRef, {
          type: 'VOLUNTEER_RELEASED',
          timestamp: serverTimestamp(),
          performedBy: authorityUid,
          metadata: { volunteerId: reportData.assignedVolunteerId }
        });
      }
    }

    // Release resources
    if (reportData.assignedResourceIds && Array.isArray(reportData.assignedResourceIds)) {
      for (const resId of reportData.assignedResourceIds) {
        const resRef = doc(db, RESOURCES_COLLECTION, resId);
        const resDoc = await transaction.get(resRef);
        // Only release if they are assigned or in use
        if (resDoc.exists() && (resDoc.data().status === 'ASSIGNED' || resDoc.data().status === 'IN_USE')) {
          transaction.update(resRef, { status: 'AVAILABLE' });
          const resActivityRef = doc(collection(db, REPORTS_COLLECTION, reportId, 'activity'));
          transaction.set(resActivityRef, {
            type: 'RESOURCE_RELEASED',
            timestamp: serverTimestamp(),
            performedBy: authorityUid,
            metadata: { resourceId: resId }
          });
        }
      }
    }

    transaction.set(activityRef, {
      type: 'CASE_RESOLVED',
      timestamp: serverTimestamp(),
      performedBy: authorityUid,
      metadata: { resolutionNote }
    });
  });
}

export async function escalateIncident(reportId: string, authorityUid: string): Promise<void> {
  const reportRef = doc(db, REPORTS_COLLECTION, reportId);
  const activityRef = getActivityRef(reportId);

  await runTransaction(db, async (transaction) => {
    const reportDoc = await transaction.get(reportRef);
    if (!reportDoc.exists()) throw new Error("Incident not found");
    if (reportDoc.data().status === 'ESCALATED') return; // Already escalated

    transaction.update(reportRef, { status: 'ESCALATED' });

    transaction.set(activityRef, {
      type: 'INCIDENT_ESCALATED',
      timestamp: serverTimestamp(),
      performedBy: authorityUid,
      metadata: { from: reportDoc.data().status, to: 'ESCALATED' }
    });
  });
}

// ── Data Fetching Hooks / Functions ────────────────────────────────────────

export async function getVolunteers(): Promise<Volunteer[]> {
  const snapshot = await getDocs(collection(db, VOLUNTEERS_COLLECTION));
  return snapshot.docs.map(snapshot => mapVolunteer(snapshot.id, snapshot.data()));
}

export async function getResources(): Promise<Resource[]> {
  const snapshot = await getDocs(collection(db, RESOURCES_COLLECTION));
  return snapshot.docs.map(snapshot => mapResource(snapshot.id, snapshot.data()));
}

export async function registerVolunteer(input: Pick<Volunteer, 'name' | 'role' | 'contact'>): Promise<string> {
  const volunteerRef = doc(collection(db, VOLUNTEERS_COLLECTION));
  await setDoc(volunteerRef, {
    name: input.name.trim(),
    role: input.role.trim(),
    contact: input.contact?.trim() || '',
    status: 'AVAILABLE',
    lastUpdated: Date.now(),
  });
  return volunteerRef.id;
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
      const volunteers = snapshot.docs.map(snapshot => mapVolunteer(snapshot.id, snapshot.data()));
      onData(volunteers);
    },
    onError
  );
}

export function subscribeToResources(onData: (resources: Resource[]) => void, onError: (error: FirestoreError) => void) {
  return onSnapshot(
    collection(db, RESOURCES_COLLECTION),
    (snapshot) => {
      const resources = snapshot.docs.map(snapshot => mapResource(snapshot.id, snapshot.data()));
      onData(resources);
    },
    onError
  );
}

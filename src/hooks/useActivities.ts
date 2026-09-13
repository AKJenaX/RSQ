import { useState, useEffect } from 'react';
import { collectionGroup, query, orderBy, limit, onSnapshot, Timestamp } from 'firebase/firestore';
import { db } from '../firebase/config';
import type { ActivityRecord } from '../types/incident';

export type ActivityLoadState = 'loading' | 'success' | 'error' | 'permission-denied' | 'empty';

export function useActivities(maxCount = 50) {
  const [activities, setActivities] = useState<(ActivityRecord & { reportId: string })[]>([]);
  const [loadState, setLoadState] = useState<ActivityLoadState>('loading');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    try {
      const q = query(
        collectionGroup(db, 'activity'),
        orderBy('timestamp', 'desc'),
        limit(maxCount)
      );

      const unsubscribe = onSnapshot(q, (snapshot) => {
        const results = snapshot.docs.map(doc => {
          const data = doc.data();
          const pathSegments = doc.ref.path.split('/');
          const reportId = pathSegments.length >= 3 ? pathSegments[pathSegments.length - 3] : 'unknown';
          
          const rawTimestamp = data.timestamp;
          const timestamp = rawTimestamp instanceof Timestamp
            ? rawTimestamp.toMillis()
            : typeof rawTimestamp === 'number'
              ? (rawTimestamp < 1e10 ? rawTimestamp * 1000 : rawTimestamp)
              : 0;

          return {
            id: doc.id,
            type: data.type,
            timestamp,
            performedBy: data.performedBy,
            metadata: data.metadata,
            reportId
          } as ActivityRecord & { reportId: string };
        });
        setActivities(results);
        setLoadState(results.length > 0 ? 'success' : 'empty');
        setError(null);
      }, (err: any) => {
        console.error("[useActivities] Firestore error:", err.code, err.message);
        if (err.code === 'permission-denied') {
          setLoadState('permission-denied');
          setError('You do not have permission to access activity records.');
        } else {
          setLoadState('error');
          setError('Unable to load activity feed. Please try again.');
        }
      });

      return () => unsubscribe();
    } catch (err: any) {
      setLoadState('error');
      setError(err.message);
    }
  }, [maxCount]);

  return { activities, loadState, error, loading: loadState === 'loading' };
}

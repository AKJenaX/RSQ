import { useState, useEffect } from 'react';
import type { FirestoreError } from 'firebase/firestore';
import type { Volunteer } from '../types/incident';
import { subscribeToVolunteers } from '../services/incidentService';
import { isFirebaseConfigured } from '../firebase/config';

export type VolunteersLoadState = 'loading' | 'success' | 'error' | 'permission-denied' | 'not-configured';

export interface UseVolunteersResult {
  volunteers: Volunteer[];
  loadState: VolunteersLoadState;
  error: string | null;
}

export function useVolunteers(): UseVolunteersResult {
  const [volunteers, setVolunteers] = useState<Volunteer[]>([]);
  const [loadState, setLoadState] = useState<VolunteersLoadState>('loading');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isFirebaseConfigured()) {
      setLoadState('not-configured');
      return;
    }

    const unsubscribe = subscribeToVolunteers(
      (data) => {
        setVolunteers(data);
        setLoadState('success');
        setError(null);
      },
      (err: FirestoreError) => {
        console.error('[useVolunteers] Firestore error:', err.code, err.message);
        if (err.code === 'permission-denied') {
          setLoadState('permission-denied');
          setError('You do not have permission to access volunteers.');
        } else {
          setLoadState('error');
          setError('Unable to load volunteers. Please try again.');
        }
      }
    );

    return () => unsubscribe();
  }, []);

  return { volunteers, loadState, error };
}

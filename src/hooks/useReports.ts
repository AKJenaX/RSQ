/**
 * useReports — real-time disaster reports collection hook.
 *
 * Sets up a Firestore onSnapshot listener for the reports collection.
 * The listener is automatically cleaned up when the component unmounts.
 * Only one listener is created per hook instance.
 */

import { useState, useEffect } from 'react';
import type { FirestoreError } from 'firebase/firestore';
import type { DisasterReport } from '../types/report';
import { subscribeToReports } from '../services/reportsService';
import { isFirebaseConfigured } from '../firebase/config';

export type ReportsLoadState = 'loading' | 'success' | 'error' | 'permission-denied' | 'not-configured';

export interface UseReportsResult {
  reports: DisasterReport[];
  loadState: ReportsLoadState;
  error: string | null;
}

export function useReports(): UseReportsResult {
  const [reports, setReports] = useState<DisasterReport[]>([]);
  const [loadState, setLoadState] = useState<ReportsLoadState>('loading');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isFirebaseConfigured()) {
      setLoadState('not-configured');
      return;
    }

    const unsubscribe = subscribeToReports(
      (data) => {
        setReports(data);
        setLoadState('success');
        setError(null);
      },
      (err: FirestoreError) => {
        console.error('[useReports] Firestore error:', err.code, err.message);

        if (err.code === 'permission-denied') {
          setLoadState('permission-denied');
          setError('You do not have permission to access disaster reports.');
        } else {
          setLoadState('error');
          setError('Unable to load disaster reports. Please try again.');
        }
      }
    );

    // Cleanup: stop listening when the component unmounts
    return () => {
      unsubscribe();
    };
  }, []);

  return { reports, loadState, error };
}

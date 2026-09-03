/**
 * useReport — real-time single disaster report hook.
 *
 * Sets up a Firestore onSnapshot listener for a single report document.
 * The listener is automatically cleaned up when the component unmounts
 * or when the reportId changes.
 */

import { useState, useEffect } from 'react';
import type { FirestoreError } from 'firebase/firestore';
import type { DisasterReport } from '../types/report';
import { subscribeToReport } from '../services/reportsService';
import { isFirebaseConfigured } from '../firebase/config';

export type ReportLoadState =
  | 'loading'
  | 'success'
  | 'not-found'
  | 'error'
  | 'permission-denied'
  | 'not-configured';

export interface UseReportResult {
  report: DisasterReport | null;
  loadState: ReportLoadState;
  error: string | null;
}

export function useReport(reportId: string | undefined): UseReportResult {
  const [report, setReport] = useState<DisasterReport | null>(null);
  const [loadState, setLoadState] = useState<ReportLoadState>('loading');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!reportId) {
      setLoadState('not-found');
      return;
    }

    if (!isFirebaseConfigured()) {
      setLoadState('not-configured');
      return;
    }

    // Reset state when reportId changes
    setLoadState('loading');
    setReport(null);
    setError(null);

    const unsubscribe = subscribeToReport(
      reportId,
      (data) => {
        if (data === null) {
          setReport(null);
          setLoadState('not-found');
        } else {
          setReport(data);
          setLoadState('success');
          setError(null);
        }
      },
      (err: FirestoreError) => {
        console.error('[useReport] Firestore error:', err.code, err.message);

        if (err.code === 'permission-denied') {
          setLoadState('permission-denied');
          setError('You do not have permission to view this report.');
        } else {
          setLoadState('error');
          setError('Unable to load report. Please try again.');
        }
      }
    );

    return () => {
      unsubscribe();
    };
  }, [reportId]);

  return { report, loadState, error };
}

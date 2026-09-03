import { useState, useEffect } from 'react';
import type { FirestoreError } from 'firebase/firestore';
import type { Resource } from '../types/incident';
import { subscribeToResources } from '../services/incidentService';
import { isFirebaseConfigured } from '../firebase/config';

export type ResourcesLoadState = 'loading' | 'success' | 'error' | 'permission-denied' | 'not-configured';

export interface UseResourcesResult {
  resources: Resource[];
  loadState: ResourcesLoadState;
  error: string | null;
}

export function useResources(): UseResourcesResult {
  const [resources, setResources] = useState<Resource[]>([]);
  const [loadState, setLoadState] = useState<ResourcesLoadState>('loading');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isFirebaseConfigured()) {
      setLoadState('not-configured');
      return;
    }

    const unsubscribe = subscribeToResources(
      (data) => {
        setResources(data);
        setLoadState('success');
        setError(null);
      },
      (err: FirestoreError) => {
        console.error('[useResources] Firestore error:', err.code, err.message);
        if (err.code === 'permission-denied') {
          setLoadState('permission-denied');
          setError('You do not have permission to access resources.');
        } else {
          setLoadState('error');
          setError('Unable to load resources. Please try again.');
        }
      }
    );

    return () => unsubscribe();
  }, []);

  return { resources, loadState, error };
}

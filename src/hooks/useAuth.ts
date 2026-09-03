/**
 * useAuth — Firebase Authentication state hook.
 *
 * Returns the current Firebase Auth user (or null if not signed in)
 * and a loading flag while the auth state is being determined.
 *
 * The `loading` flag prevents a flash of the login page before Firebase
 * has had a chance to restore the session from local storage.
 */

import { useState, useEffect } from 'react';
import { onAuthStateChanged } from 'firebase/auth';
import type { User } from 'firebase/auth';
import { doc, getDoc } from 'firebase/firestore';
import { auth, db } from '../firebase/config';

export interface AuthState {
  user: User | null;
  loading: boolean;
  isAuthority: boolean;
  authError: Error | null;
}

export function useAuth(): AuthState {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [isAuthority, setIsAuthority] = useState(false);
  const [authError, setAuthError] = useState<Error | null>(null);

  useEffect(() => {
    if (!auth) {
      // Firebase not configured — skip auth listener, surface error via isFirebaseConfigured()
      setLoading(false);
      return;
    }

    const unsubscribe = onAuthStateChanged(auth, async (firebaseUser) => {
      try {
        setUser(firebaseUser);
        
        if (firebaseUser) {
          // Fetch authorization role from users collection
          const userDoc = await getDoc(doc(db, 'users', firebaseUser.uid));
          if (userDoc.exists() && userDoc.data()?.role === 'AUTHORITY' && userDoc.data()?.authorized === true) {
            setIsAuthority(true);
          } else {
            setIsAuthority(false);
          }
        } else {
          setIsAuthority(false);
        }
      } catch (err) {
        console.error('Authorization fetch error:', err);
        setAuthError(err instanceof Error ? err : new Error('Unknown authorization error'));
        setIsAuthority(false);
      } finally {
        setLoading(false);
      }
    });

    return unsubscribe;
  }, []);

  return { user, loading, isAuthority, authError };
}

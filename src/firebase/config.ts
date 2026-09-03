/**
 * Firebase Web SDK configuration.
 *
 * All values are read from environment variables (VITE_FIREBASE_*).
 * Copy .env.example → .env.local and fill in real values from:
 *   Firebase Console → Project Settings → General → Your apps → Web app
 *
 * NEVER put real credentials here or commit .env.local.
 */

import { initializeApp } from 'firebase/app';
import { getAuth } from 'firebase/auth';
import type { Auth } from 'firebase/auth';
import { getFirestore } from 'firebase/firestore';
import type { Firestore } from 'firebase/firestore';
import { getStorage } from 'firebase/storage';
import type { FirebaseStorage } from 'firebase/storage';

// ── Collection names ──────────────────────────────────────────────────────────
// Isolate the collection name here so it can be changed in one place.
// Driven by env var; falls back to "reports" as a safe default.
export const REPORTS_COLLECTION: string =
  (import.meta.env.VITE_REPORTS_COLLECTION as string | undefined) ?? 'reports';

// ── Firebase app config ───────────────────────────────────────────────────────
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY as string | undefined,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN as string | undefined,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID as string | undefined,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET as string | undefined,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID as string | undefined,
  appId: import.meta.env.VITE_FIREBASE_APP_ID as string | undefined,
};

/**
 * Returns true if all required Firebase config values are present.
 * Used to show a configuration-error state rather than crashing.
 */
export function isFirebaseConfigured(): boolean {
  return !!(
    firebaseConfig.apiKey &&
    firebaseConfig.authDomain &&
    firebaseConfig.projectId
  );
}

// ── Service singletons ────────────────────────────────────────────────────────
// These are typed as potentially undefined when Firebase is not configured.
// The isFirebaseConfigured() guard should be checked before using them.
let _auth: Auth | undefined;
let _db: Firestore | undefined;
let _storage: FirebaseStorage | undefined;

if (isFirebaseConfigured()) {
  const app = initializeApp(firebaseConfig as {
    apiKey: string;
    authDomain: string;
    projectId: string;
    storageBucket?: string;
    messagingSenderId?: string;
    appId?: string;
  });
  _auth = getAuth(app);
  _db = getFirestore(app);
  _storage = getStorage(app);
}

// Export as non-null — callers must check isFirebaseConfigured() first.
export const auth = _auth as Auth;
export const db = _db as Firestore;
export const storage = _storage as FirebaseStorage;

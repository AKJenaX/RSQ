import React from 'react';
import { Outlet, Navigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { isFirebaseConfigured } from '../firebase/config';
import { LoadingSpinner } from '../components/LoadingSpinner';
import { Sidebar } from '../components/Sidebar';
import { Header } from '../components/Header';
import { Settings, ShieldAlert, LogOut } from 'lucide-react';
import { signOut } from 'firebase/auth';
import { auth } from '../firebase/config';

interface DashboardLayoutProps {
  title: string;
  subtitle?: string;
}

export function DashboardLayout({ title, subtitle }: DashboardLayoutProps): React.ReactElement {
  const { user, loading, isAuthority, authError } = useAuth();

  if (!isFirebaseConfigured()) {
    return (
      <div style={{ padding: '2rem' }}>
        <div className="config-warning">
          <div className="config-warning-icon">
            <Settings size={24} className="text-secondary" />
          </div>
          <h1 className="config-warning-title">Firebase Configuration Required</h1>
          <p className="config-warning-body">
            The RSQ Authority Dashboard requires Firebase credentials to operate.
            Please copy <code>.env.example</code> to <code>.env.local</code> and
            fill in your Firebase project values from the Firebase Console.
          </p>
          <pre className="config-code">
            {`VITE_FIREBASE_API_KEY=...\nVITE_FIREBASE_AUTH_DOMAIN=...\nVITE_FIREBASE_PROJECT_ID=...\nVITE_FIREBASE_STORAGE_BUCKET=...\nVITE_FIREBASE_MESSAGING_SENDER_ID=...\nVITE_FIREBASE_APP_ID=...`}
          </pre>
        </div>
      </div>
    );
  }

  if (loading) {
    return <LoadingSpinner message="Verifying authority credentials…" />;
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }
  
  if (!isAuthority) {
    return (
      <div className="state-container" style={{ minHeight: '100vh' }}>
        <div className="state-icon mb-4 text-error">
          <ShieldAlert size={48} strokeWidth={1.5} />
        </div>
        <h1 className="state-title">Access restricted</h1>
        <p className="state-message">
          Your account does not have permission to access the RSQ Authority Dashboard.
        </p>
        {authError && (
          <p className="text-sm text-error mt-2">{authError.message}</p>
        )}
        <button
          className="btn-secondary mt-6"
          onClick={() => signOut(auth)}
          style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}
        >
          <LogOut size={16} />
          Sign out
        </button>
      </div>
    );
  }

  return (
    <div className="app-shell">
      <Sidebar />
      <div className="main-content">
        <Header user={user} title={title} subtitle={subtitle} />
        <div className="page-content">
          <Outlet />
        </div>
      </div>
    </div>
  );
}

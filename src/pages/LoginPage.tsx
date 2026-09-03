import React, { useState } from 'react';
import { Navigate } from 'react-router-dom';
import {
  signInWithEmailAndPassword,
} from 'firebase/auth';
import type { AuthError } from 'firebase/auth';
import { auth } from '../firebase/config';
import { useAuth } from '../hooks/useAuth';
import { isFirebaseConfigured } from '../firebase/config';
import { LoadingSpinner } from '../components/LoadingSpinner';
import { Shield } from 'lucide-react';

function getAuthErrorMessage(error: AuthError): string {
  switch (error.code) {
    case 'auth/invalid-email':
      return 'Please enter a valid email address.';
    case 'auth/user-not-found':
    case 'auth/wrong-password':
    case 'auth/invalid-credential':
      return 'Invalid email or password.';
    case 'auth/too-many-requests':
      return 'Too many failed attempts. Please wait before trying again.';
    case 'auth/network-request-failed':
      return 'Network error. Please check your connection.';
    default:
      return 'Sign in failed. Please try again.';
  }
}

export function LoginPage(): React.ReactElement {
  const { user, loading } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Redirect if already authenticated
  if (loading) {
    return <LoadingSpinner message="Verifying credentials…" />;
  }

  if (user) {
    return <Navigate to="/dashboard" replace />;
  }

  if (!isFirebaseConfigured()) {
    return (
      <div className="auth-page">
        <div className="auth-card">
          <div className="state-container">
            <Shield className="state-icon text-warning" size={48} aria-hidden="true" />
            <h1 className="state-title">Firebase Not Configured</h1>
            <p className="state-message">
              Copy <code>.env.example</code> to <code>.env.local</code> and fill
              in your Firebase credentials.
            </p>
          </div>
        </div>
      </div>
    );
  }

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      await signInWithEmailAndPassword(auth, email.trim(), password);
      // onAuthStateChanged in useAuth will handle the redirect via Navigate
    } catch (err) {
      const authError = err as AuthError;
      console.error('[LoginPage] Auth error:', authError.code);
      setError(getAuthErrorMessage(authError));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card" role="main">
        {/* Logo */}
        <div className="auth-logo">
          <Shield className="text-primary" size={24} aria-hidden="true" />
          <div className="flex flex-col">
            <span className="font-semibold text-primary">RSQ</span>
            <span className="text-xs text-tertiary uppercase tracking-wider">Authority Portal</span>
          </div>
        </div>

        <h1 className="auth-heading">Sign In</h1>
        <p className="auth-subheading">
          Enter your credentials to access the emergency operations dashboard.
        </p>

        <form id="login-form" onSubmit={handleSubmit} noValidate>
          <div className="form-group">
            <label htmlFor="login-email" className="form-label">
              Email Address
            </label>
            <input
              id="login-email"
              type="email"
              className="form-input"
              placeholder="authority@rsq.gov"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete="email"
              required
              disabled={submitting}
              aria-required="true"
            />
          </div>

          <div className="form-group">
            <label htmlFor="login-password" className="form-label">
              Password
            </label>
            <input
              id="login-password"
              type="password"
              className="form-input"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
              disabled={submitting}
              aria-required="true"
            />
          </div>

          {error && (
            <div
              className="form-error"
              role="alert"
              aria-live="polite"
              id="login-error"
            >
              {error}
            </div>
          )}

          <div className="mt-4">
            <button
              id="btn-login-submit"
              type="submit"
              className="btn-primary"
              style={{ width: '100%' }}
              disabled={submitting || !email || !password}
              aria-busy={submitting}
            >
              {submitting ? 'Signing in...' : 'Sign In'}
            </button>
          </div>
        </form>

        <p
          style={{
            marginTop: 'var(--space-6)',
            fontSize: '0.75rem',
            color: 'var(--text-tertiary)',
            textAlign: 'center',
            lineHeight: 1.6,
          }}
        >
          Access restricted to authorised personnel only.
        </p>
      </div>
    </div>
  );
}

import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Loader2, Lock, ShieldAlert } from "lucide-react";
import { signInWithEmailAndPassword } from "firebase/auth";
import { auth } from "../firebase/config";
import { useAuth } from "../hooks/useAuth";
import commandCenter from "../assets/hero.png";

export function LoginPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const { user, loading: authLoading } = useAuth();
  useEffect(() => {
    if (user && !authLoading) navigate("/dashboard");
  }, [user, authLoading, navigate]);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.includes("@") || password.length < 6) {
      setError("Enter a valid authority email and a password of at least 6 characters.");
      return;
    }
    setError("");
    setLoading(true);
    signInWithEmailAndPassword(auth, email, password)
      .then(() => {
        navigate("/dashboard");
      })
      .catch((err) => {
        setError(err.message || "Authentication failed. Check credentials.");
        setLoading(false);
      });
  };

  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      <div className="relative hidden lg:block">
        <img
          src={commandCenter}
          alt="Emergency operations command center with wall of monitors"
          width={1280}
          height={1600}
          className="absolute inset-0 h-full w-full object-cover"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-background via-background/70 to-background/30" />
        <div className="absolute inset-x-0 bottom-0 p-10">
          <p className="max-w-md font-display text-3xl font-semibold uppercase leading-tight">
            Coordinated response, accountable relief.
          </p>
          <p className="mt-3 max-w-md text-sm text-muted-foreground">
            RSQ Authority unifies incident dispatch, volunteer deployment, resource logistics and
            donation stewardship in one operational console.
          </p>
          <div className="mt-6 flex flex-wrap gap-6 text-xs uppercase tracking-widest text-muted-foreground">
            <span>24/7 Operations</span>
            <span>ISO 22320 Aligned</span>
            <span>Audit Ready</span>
          </div>
        </div>
      </div>

      <div className="grid-backdrop flex items-center justify-center p-6">
        <div className="w-full max-w-sm">
          <div className="flex items-center gap-3">
            <span className="grid h-11 w-11 shrink-0 place-items-center rounded-md bg-primary text-primary-foreground">
              <ShieldAlert className="h-6 w-6" />
            </span>
            <div className="min-w-0">
              <h1 className="truncate font-display text-2xl font-semibold uppercase tracking-wider">
                RSQ Authority
              </h1>
              <p className="text-xs uppercase tracking-widest text-muted-foreground">
                Resques Authority Management
              </p>
            </div>
          </div>

          <h2 className="mt-8 text-xl font-semibold">Authority sign in</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Restricted to authorised emergency management personnel.
          </p>

          <form onSubmit={submit} className="mt-6 space-y-4">
            <div>
              <label htmlFor="email" className="text-xs font-medium uppercase tracking-widest text-muted-foreground">
                Authority email
              </label>
              <input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="mt-1.5 w-full rounded-md border border-input bg-card px-3 py-2.5 text-sm outline-none focus:border-ring focus:ring-2 focus:ring-ring/30"
                placeholder="Enter email"
              />
            </div>
            <div>
              <label htmlFor="password" className="text-xs font-medium uppercase tracking-widest text-muted-foreground">
                Password
              </label>
              <input
                id="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="mt-1.5 w-full rounded-md border border-input bg-card px-3 py-2.5 text-sm outline-none focus:border-ring focus:ring-2 focus:ring-ring/30"
                placeholder="Enter password"
              />
            </div>

            {error ? (
              <p className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-xs text-destructive">
                {error}
              </p>
            ) : null}

            <div className="flex items-center justify-between text-xs">
              <label className="flex items-center gap-2 text-muted-foreground">
                <input type="checkbox" defaultChecked className="accent-[var(--color-primary)]" />
                Keep me signed in
              </label>
              <a href="#" className="text-primary hover:underline">
                Reset access
              </a>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="flex w-full items-center justify-center gap-2 rounded-md bg-primary px-4 py-2.5 text-sm font-semibold uppercase tracking-wider text-primary-foreground transition-opacity hover:opacity-90 disabled:opacity-60"
            >
              {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Lock className="h-4 w-4" />}
              {loading ? "Authenticating" : "Enter command console"}
            </button>
          </form>

          <p className="mt-6 text-center text-xs text-muted-foreground">
            Sign in with valid Firebase credentials.
          </p>
        </div>
      </div>
    </div>
  );
}

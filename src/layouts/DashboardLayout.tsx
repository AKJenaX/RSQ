import React, { useState } from 'react';
import { Outlet, Navigate, NavLink, useLocation } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { isFirebaseConfigured } from '../firebase/config';
import { signOut } from 'firebase/auth';
import { auth } from '../firebase/config';
import {
  Activity,
  AlertTriangle,
  BarChart3,
  Banknote,
  Bell,
  FileBarChart,
  LayoutDashboard,
  LogOut,
  Map,
  Menu,
  Package,
  PieChart,
  Receipt,
  Settings,
  ShieldAlert,
  Users,
  X,
} from 'lucide-react';
import { cn } from '../lib/utils';

const NAV = [
  {
    group: 'Command Center',
    items: [{ to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard }],
  },
  {
    group: 'Operations',
    items: [
      { to: '/incidents', label: 'Incidents', icon: AlertTriangle },
      { to: '/live-map', label: 'Live Map', icon: Map },
      { to: '/volunteers', label: 'Volunteers', icon: Users },
      { to: '/resources', label: 'Resources', icon: Package },
    ],
  },
  {
    group: 'Coordination',
    items: [
      { to: '/activity', label: 'Activity', icon: Activity },
      { to: '/analytics', label: 'Analytics', icon: BarChart3 },
    ],
  },
  {
    group: 'Financial Management',
    items: [
      { to: '/donations', label: 'Donations', icon: Banknote },
      { to: '/funds', label: 'Funds & Allocation', icon: PieChart },
      { to: '/expenses', label: 'Expenses', icon: Receipt },
      { to: '/financial-reports', label: 'Financial Reports', icon: FileBarChart },
    ],
  },
] as const;

// eslint-disable-next-line @typescript-eslint/no-unused-vars
interface DashboardLayoutProps {
  title?: string;
  subtitle?: string;
}

export function DashboardLayout(_props: DashboardLayoutProps): React.ReactElement {
  const { user, loading, isAuthority, authError } = useAuth();
  const location = useLocation();
  const [open, setOpen] = useState(false);

  React.useEffect(() => {
    window.scrollTo(0, 0);
  }, [location.pathname]);

  if (!isFirebaseConfigured()) {
    return (
      <div className="flex min-h-screen items-center justify-center p-8">
        <div className="max-w-md text-center space-y-4">
          <Settings className="mx-auto h-12 w-12 text-muted-foreground" strokeWidth={1.5} />
          <h1 className="text-xl font-semibold">Firebase Configuration Required</h1>
          <p className="text-sm text-muted-foreground">
            Copy <code className="px-1 py-0.5 bg-secondary rounded text-xs">.env.example</code> to{' '}
            <code className="px-1 py-0.5 bg-secondary rounded text-xs">.env.local</code> and fill in your Firebase credentials.
          </p>
        </div>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">
        Verifying authority credentials…
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  if (!isAuthority) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 p-8 text-center">
        <ShieldAlert className="h-12 w-12 text-destructive" strokeWidth={1.5} />
        <h1 className="text-xl font-semibold">Access Restricted</h1>
        <p className="text-sm text-muted-foreground max-w-sm">
          Your account does not have authority-level permissions.
        </p>
        {authError && <p className="text-xs text-destructive">{authError.message}</p>}
        <button
          className="flex items-center gap-2 rounded-md border border-border px-4 py-2 text-sm hover:bg-secondary"
          onClick={() => signOut(auth)}
        >
          <LogOut className="h-4 w-4" />
          Sign out
        </button>
      </div>
    );
  }

  const initial = 'A';

  const sidebar = (
    <div className="flex h-full flex-col bg-sidebar text-sidebar-foreground">
      <div className="flex items-center gap-3 border-b border-sidebar-border px-4 py-4">
        <span className="grid h-9 w-9 shrink-0 place-items-center rounded-md bg-primary text-primary-foreground">
          <ShieldAlert className="h-5 w-5" />
        </span>
        <div className="min-w-0">
          <p className="truncate font-display text-lg font-semibold uppercase leading-none tracking-wider">
            RSQ Authority
          </p>
          <p className="mt-1 truncate text-[11px] uppercase tracking-widest text-muted-foreground">
            Authority Dashboard
          </p>
        </div>
        <button
          className="ml-auto shrink-0 rounded p-1 text-muted-foreground lg:hidden"
          onClick={() => setOpen(false)}
          aria-label="Close menu"
        >
          <X className="h-5 w-5" />
        </button>
      </div>

      <nav className="flex-1 overflow-y-hidden px-3 py-4">
        {NAV.map((group) => (
          <div key={group.group} className="mb-5">
            <p className="px-2 pb-2 text-[10px] font-semibold uppercase tracking-[0.18em] text-muted-foreground">
              {group.group}
            </p>
            <ul className="space-y-0.5">
              {group.items.map((item) => {
                const Icon = item.icon;
                return (
                  <li key={item.to}>
                    <NavLink
                      to={item.to}
                      className={({ isActive }) => cn(
                        'flex items-center gap-3 rounded-md px-2.5 py-2 text-sm transition-colors',
                        (isActive || location.pathname.startsWith(item.to + '/'))
                          ? 'bg-emerald-500/15 font-medium text-emerald-500'
                          : 'text-sidebar-foreground/80 hover:bg-sidebar-accent hover:text-foreground',
                      )}
                    >
                      <Icon className="h-4 w-4 shrink-0" />
                      <span className="truncate">{item.label}</span>
                    </NavLink>
                  </li>
                );
              })}
            </ul>
          </div>
        ))}
      </nav>

      <div className="border-t border-sidebar-border p-3">
        <button
          onClick={() => signOut(auth)}
          className="flex w-full items-center gap-3 rounded-md px-2.5 py-2 text-sm text-sidebar-foreground/80 transition-colors hover:bg-destructive/15 hover:text-destructive"
        >
          <LogOut className="h-4 w-4 shrink-0" />
          Log out
        </button>
      </div>
    </div>
  );

  return (
    <div className="min-h-screen bg-background">
      <aside className="fixed inset-y-0 left-0 z-40 hidden w-64 border-r border-sidebar-border lg:block">
        {sidebar}
      </aside>

      {open ? (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div className="absolute inset-0 bg-background/80" onClick={() => setOpen(false)} aria-hidden />
          <div className="absolute inset-y-0 left-0 w-72 border-r border-sidebar-border shadow-xl">
            {sidebar}
          </div>
        </div>
      ) : null}

      <div className="lg:pl-64">
        <header className="sticky top-0 z-30 grid grid-cols-[minmax(0,1fr)_auto] items-center gap-3 border-b border-border bg-background/95 px-4 py-3 backdrop-blur sm:flex sm:justify-between">
          <div className="flex min-w-0 items-center gap-3">
            <button
              className="shrink-0 rounded-md border border-border p-2 lg:hidden"
              onClick={() => setOpen(true)}
              aria-label="Open menu"
            >
              <Menu className="h-4 w-4" />
            </button>
            <span className="inline-flex shrink-0 items-center gap-2 rounded-full border border-emerald-500/40 bg-emerald-500/10 px-2.5 py-1 text-xs font-medium text-emerald-500">
              <span className="relative inline-block h-1.5 w-1.5 rounded-full bg-current pulse-dot" />
              RSQ Authority — Live
            </span>
          </div>
          <div className="flex shrink-0 items-center gap-3">
            <button
              className="relative rounded-md border border-border p-2 text-muted-foreground hover:text-foreground"
              aria-label="Notifications"
            >
              <Bell className="h-4 w-4" />
            </button>
            <span className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-secondary text-sm font-semibold">
              {initial}
            </span>
          </div>
        </header>

        <main className="mx-auto max-w-[1400px] space-y-6 p-4 sm:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

import React from 'react';
import { NavLink } from 'react-router-dom';
import { signOut } from 'firebase/auth';
import { Activity, LayoutDashboard, FileText, LogOut, Users, Box, History, Map, HeartHandshake, Briefcase, Receipt, FileBarChart, ServerCrash } from 'lucide-react';
import { auth } from '../firebase/config';

export function Sidebar(): React.ReactElement {
  const handleSignOut = async () => {
    try {
      await signOut(auth);
    } catch (err) {
      console.error('[Sidebar] Sign out error:', err);
    }
  };

  return (
    <aside className="sidebar" role="navigation" aria-label="Main navigation">
      {/* Logo */}
      <div className="sidebar-logo">
        <Activity className="sidebar-logo-icon" />
        <div className="sidebar-logo-text">
          <span className="sidebar-logo-title">RSQ</span>
          <span className="sidebar-logo-subtitle">Authority</span>
        </div>
      </div>

      {/* Navigation */}
      <nav className="sidebar-nav">
        {/* OVERVIEW */}
        <div className="sidebar-nav-label">COMMAND CENTER</div>
        <NavLink
          id="nav-dashboard"
          to="/dashboard"
          className={({ isActive }) =>
            `sidebar-nav-item${isActive ? ' active' : ''}`
          }
        >
          <LayoutDashboard className="sidebar-nav-icon" size={16} />
          Dashboard
        </NavLink>

        {/* OPERATIONS */}
        <div className="sidebar-nav-label mt-4">OPERATIONS</div>
        <NavLink
          id="nav-reports"
          to="/reports"
          className={({ isActive }) =>
            `sidebar-nav-item${isActive ? ' active' : ''}`
          }
        >
          <FileText className="sidebar-nav-icon" size={16} />
          Incidents
        </NavLink>
        <NavLink
          id="nav-map"
          to="/live-map"
          className={({ isActive }) =>
            `sidebar-nav-item${isActive ? ' active' : ''}`
          }
        >
          <Map className="sidebar-nav-icon" size={16} />
          Live Map
        </NavLink>
        <NavLink
          id="nav-volunteers"
          to="/volunteers"
          className={({ isActive }) =>
            `sidebar-nav-item${isActive ? ' active' : ''}`
          }
        >
          <Users className="sidebar-nav-icon" size={16} />
          Volunteers
        </NavLink>
        <NavLink
          id="nav-resources"
          to="/resources"
          className={({ isActive }) =>
            `sidebar-nav-item${isActive ? ' active' : ''}`
          }
        >
          <Box className="sidebar-nav-icon" size={16} />
          Resources
        </NavLink>

        {/* COORDINATION */}
        <div className="sidebar-nav-label mt-4">COORDINATION</div>
        <NavLink
          id="nav-activity"
          to="/activity"
          className={({ isActive }) =>
            `sidebar-nav-item${isActive ? ' active' : ''}`
          }
        >
          <History className="sidebar-nav-icon" size={16} />
          Activity
        </NavLink>
        <NavLink
          id="nav-analytics"
          to="/analytics"
          className={({ isActive }) =>
            `sidebar-nav-item${isActive ? ' active' : ''}`
          }
        >
          <Activity className="sidebar-nav-icon" size={16} />
          Analytics
        </NavLink>

        {/* FINANCIAL MANAGEMENT */}
        <div className="sidebar-nav-label mt-4">FINANCIAL MANAGEMENT</div>
        <NavLink
          id="nav-donations"
          to="/donations"
          className={({ isActive }) =>
            `sidebar-nav-item${isActive ? ' active' : ''}`
          }
        >
          <HeartHandshake className="sidebar-nav-icon" size={16} />
          Donations
        </NavLink>
        <NavLink
          id="nav-funds"
          to="/funds"
          className={({ isActive }) =>
            `sidebar-nav-item${isActive ? ' active' : ''}`
          }
        >
          <Briefcase className="sidebar-nav-icon" size={16} />
          Funds & Allocation
        </NavLink>
        <NavLink
          id="nav-expenses"
          to="/expenses"
          className={({ isActive }) =>
            `sidebar-nav-item${isActive ? ' active' : ''}`
          }
        >
          <Receipt className="sidebar-nav-icon" size={16} />
          Expenses
        </NavLink>
        <NavLink
          id="nav-fin-reports"
          to="/financial-reports"
          className={({ isActive }) =>
            `sidebar-nav-item${isActive ? ' active' : ''}`
          }
        >
          <FileBarChart className="sidebar-nav-icon" size={16} />
          Financial Reports
        </NavLink>

        {/* SYSTEM */}
        <div className="sidebar-nav-label mt-4">SYSTEM</div>
        <NavLink
          id="nav-system"
          to="/system"
          className={({ isActive }) =>
            `sidebar-nav-item${isActive ? ' active' : ''}`
          }
        >
          <ServerCrash className="sidebar-nav-icon" size={16} />
          System Health
        </NavLink>
      </nav>

      {/* Footer */}
      <div className="sidebar-footer">
        <button
          id="btn-sidebar-signout"
          className="btn-signout"
          onClick={handleSignOut}
          aria-label="Sign out of RSQ Authority Dashboard"
        >
          <LogOut size={14} />
          Sign Out
        </button>
      </div>
    </aside>
  );
}

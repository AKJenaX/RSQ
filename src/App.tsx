import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { DashboardLayout } from './layouts/DashboardLayout';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { ReportsPage } from './pages/ReportsPage';
import { ReportDetailPage } from './pages/ReportDetailPage';
import { VolunteersPage } from './pages/VolunteersPage';
import { ResourcesPage } from './pages/ResourcesPage';
import { ActivityPage } from './pages/ActivityPage';
import { AnalyticsPage } from './pages/AnalyticsPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { DonationsPage } from './pages/DonationsPage';
import { FundsPage } from './pages/FundsPage';
import { ExpensesPage } from './pages/ExpensesPage';
import { FinancialReportsPage } from './pages/FinancialReportsPage';
import { SystemHealthPage } from './pages/SystemHealthPage';
import { LiveMapPage } from './pages/LiveMapPage';

/**
 * RSQ Authority Dashboard â€” Application Router
 *
 * Route structure:
 *   /                â†’ redirect to /dashboard
 *   /login           â†’ LoginPage (unauthenticated only)
 *   /dashboard       â†’ DashboardPage (protected)
 *   /reports         â†’ ReportsPage (protected)
 *   /reports/:id     â†’ ReportDetailPage (protected)
 *   *                â†’ 404 NotFoundPage
 *
 * Authentication protection is implemented in DashboardLayout,
 * which redirects unauthenticated users to /login.
 */
export default function App(): React.ReactElement {
  return (
    <BrowserRouter>
      <Routes>
        {/* Default redirect */}
        <Route path="/" element={<Navigate to="/dashboard" replace />} />

        {/* Authentication */}
        <Route path="/login" element={<LoginPage />} />

        {/* Protected â€” Dashboard overview */}
        <Route
          path="/dashboard"
          element={
            <DashboardLayout
              title="Operations Overview"
              subtitle="RSQ Authority Dashboard"
            />
          }
        >
          <Route index element={<DashboardPage />} />
        </Route>

        {/* Protected â€” Reports / Incidents */}
        <Route
          path="/reports"
          element={
            <DashboardLayout
              title="Incident Workspace"
              subtitle="Real-time incident management queue"
            />
          }
        >
          <Route index element={<ReportsPage />} />
          <Route path=":id" element={<ReportDetailPage />} />
        </Route>

        {/* Alias routes for /incidents */}
        <Route
          path="/incidents"
          element={
            <DashboardLayout
              title="Incident Workspace"
              subtitle="Real-time incident management queue"
            />
          }
        >
          <Route index element={<ReportsPage />} />
          <Route path=":id" element={<ReportDetailPage />} />
        </Route>

        {/* Protected â€” Volunteers */}
        <Route
          path="/volunteers"
          element={
            <DashboardLayout
              title="Volunteer Operations"
              subtitle="Response personnel capacity & assignment"
            />
          }
        >
          <Route index element={<VolunteersPage />} />
        </Route>

        {/* Protected â€” Resources */}
        <Route
          path="/resources"
          element={
            <DashboardLayout
              title="Resource Operations"
              subtitle="Physical response assets capacity & assignment"
            />
          }
        >
          <Route index element={<ResourcesPage />} />
        </Route>

        {/* Protected â€” Activity */}
        <Route
          path="/activity"
          element={
            <DashboardLayout
              title="Activity Monitoring"
              subtitle="System-wide operational timeline"
            />
          }
        >
          <Route index element={<ActivityPage />} />
        </Route>

        {/* Protected â€” Analytics */}
        <Route
          path="/analytics"
          element={
            <DashboardLayout
              title="Analytics & Reporting"
              subtitle="Historical operational metrics"
            />
          }
        >
          <Route index element={<AnalyticsPage />} />
        </Route>

        {/* Protected — Financial Management */}
        <Route path="/donations" element={<DashboardLayout title="Donations" subtitle="Financial contributions tracking" />}>
          <Route index element={<DonationsPage />} />
        </Route>
        <Route path="/funds" element={<DashboardLayout title="Funds & Allocation" subtitle="Fund pools and allocation management" />}>
          <Route index element={<FundsPage />} />
        </Route>
        <Route path="/expenses" element={<DashboardLayout title="Expenses" subtitle="Operational expense tracking" />}>
          <Route index element={<ExpensesPage />} />
        </Route>
        <Route path="/financial-reports" element={<DashboardLayout title="Financial Reports" subtitle="Financial analytics and summaries" />}>
          <Route index element={<FinancialReportsPage />} />
        </Route>

        {/* Protected — System Health */}
        <Route path="/system" element={<DashboardLayout title="System Health" subtitle="System metrics and status" />}>
          <Route index element={<SystemHealthPage />} />
        </Route>

        {/* Protected — Live Map */}
        <Route path="/live-map" element={<DashboardLayout title="Live Incident Map" subtitle="Geospatial situational awareness" />}>
          <Route index element={<LiveMapPage />} />
        </Route>

        {/* Alias routes for /live-map */}
        <Route path="/map" element={<Navigate to="/live-map" replace />} />

        {/* 404 */}
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </BrowserRouter>
  );
}


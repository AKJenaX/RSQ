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
import { NotFoundPage } from './pages/NotFoundPage';

/**
 * RSQ Authority Dashboard — Application Router
 *
 * Route structure:
 *   /                → redirect to /dashboard
 *   /login           → LoginPage (unauthenticated only)
 *   /dashboard       → DashboardPage (protected)
 *   /reports         → ReportsPage (protected)
 *   /reports/:id     → ReportDetailPage (protected)
 *   *                → 404 NotFoundPage
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

        {/* Protected — Dashboard overview */}
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

        {/* Protected — Reports / Incidents */}
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

        {/* Protected — Volunteers */}
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

        {/* Protected — Resources */}
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

        {/* Protected — Activity */}
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

        {/* 404 */}
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </BrowserRouter>
  );
}

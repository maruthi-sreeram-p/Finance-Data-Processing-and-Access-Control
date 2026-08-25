import { Route, Routes } from 'react-router-dom';
import { AppLayout } from '@/layouts/AppLayout';
import { AdminRoute, ProtectedRoute, PublicOnlyRoute } from '@/components/routing/ProtectedRoute';
import { LandingPage } from '@/pages/LandingPage';
import { LoginPage } from '@/pages/LoginPage';
import { RegisterPage } from '@/pages/RegisterPage';
import { DashboardPage } from '@/pages/DashboardPage';
import { TransactionsPage } from '@/pages/TransactionsPage';
import { TransactionFormPage } from '@/pages/TransactionFormPage';
import { TransactionDetailPage } from '@/pages/TransactionDetailPage';
import { CategoriesPage } from '@/pages/CategoriesPage';
import { ReportsPage } from '@/pages/ReportsPage';
import { ProfilePage } from '@/pages/ProfilePage';
import { AdminUsersPage } from '@/pages/AdminUsersPage';
import { SystemDashboardPage } from '@/pages/SystemDashboardPage';
import { ForbiddenPage, NotFoundPage } from '@/pages/ErrorPages';

/**
 * The route table.
 *
 * Grouped by access level rather than by feature, so the privilege boundaries are
 * visible in the structure: anything nested under ProtectedRoute needs a session,
 * anything under AdminRoute additionally needs the ADMIN role. Reading this file
 * should make it obvious which pages are reachable by whom.
 *
 * These guards shape navigation only. Every route below is independently
 * authorized by the backend.
 */
export default function App() {
  return (
    <Routes>
      {/* Public marketing page. Redirects signed-in users straight to their dashboard. */}
      <Route path="/" element={<LandingPage />} />

      {/* Reachable only while signed out; a signed-in visitor is sent to the dashboard. */}
      <Route element={<PublicOnlyRoute />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
      </Route>

      {/* Everything inside the application shell requires a session. */}
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route path="/dashboard" element={<DashboardPage />} />

          <Route path="/transactions" element={<TransactionsPage />} />
          {/* Declared before ":id" so "new" is not captured as an id. */}
          <Route path="/transactions/new" element={<TransactionFormPage mode="create" />} />
          <Route path="/transactions/:id" element={<TransactionDetailPage />} />
          <Route path="/transactions/:id/edit" element={<TransactionFormPage mode="edit" />} />

          <Route path="/categories" element={<CategoriesPage />} />
          <Route path="/reports" element={<ReportsPage />} />
          <Route path="/profile" element={<ProfilePage />} />

          {/* Organization-wide financial data: ADMIN and MANAGER. The backend
              restricts GET /api/dashboard/system to the same two roles, so this
              guard only avoids showing a page that would return 403. */}
          <Route element={<ProtectedRoute roles={['ADMIN', 'MANAGER']} />}>
            <Route path="/organization" element={<SystemDashboardPage />} />
          </Route>

          {/* Account administration: ADMIN only. */}
          <Route element={<AdminRoute />}>
            <Route path="/admin/users" element={<AdminUsersPage />} />
          </Route>
        </Route>
      </Route>

      <Route path="/forbidden" element={<ForbiddenPage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}

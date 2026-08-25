import { api, buildParams } from './api';
import type { DashboardQuery, DashboardResponse, SystemDashboardResponse } from '@/types/api';

/**
 * The financial overview.
 *
 * Every figure in the response is computed by a database aggregate, which is why
 * the client never sums transactions itself: doing so would both duplicate
 * backend logic and give a different answer, since the client only ever holds one
 * page of the ledger.
 */
export const dashboardService = {
  /** `GET /api/dashboard` -- unbounded unless `from`/`to` narrow the period. */
  async get(query: DashboardQuery = {}): Promise<DashboardResponse> {
    const { data } = await api.get<DashboardResponse>('/dashboard', {
      params: buildParams({ ...query }),
    });
    return data;
  },

  /**
   * `GET /api/dashboard/system` -- organization-wide totals.
   *
   * Restricted to ADMIN and MANAGER by the backend. A USER calling this receives
   * 403, which is the intended behaviour rather than an error to design around:
   * the UI simply does not offer the route to roles that cannot use it.
   */
  async getSystem(query: Omit<DashboardQuery, 'userId'> = {}): Promise<SystemDashboardResponse> {
    const { data } = await api.get<SystemDashboardResponse>('/dashboard/system', {
      params: buildParams({ ...query }),
    });
    return data;
  },
};

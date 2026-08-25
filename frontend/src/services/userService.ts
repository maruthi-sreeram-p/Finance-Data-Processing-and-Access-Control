import { api, buildParams } from './api';
import type {
  AdminUpdateUserRequest,
  ChangePasswordRequest,
  PageRequest,
  PagedResponse,
  UpdateUserRequest,
  UserFilters,
  UserResponse,
} from '@/types/api';

/**
 * Accounts.
 *
 * The `/me` calls and the id-addressed calls are kept as separate methods because
 * the difference between them is a privilege boundary, not a parameter. Merging
 * them behind one function with an optional id is how a self-service screen ends
 * up quietly calling an administrative endpoint.
 */
export const userService = {
  async getCurrent(): Promise<UserResponse> {
    const { data } = await api.get<UserResponse>('/users/me');
    return data;
  },

  /** Name and email only -- the backend does not accept role or status here. */
  async updateCurrent(request: UpdateUserRequest): Promise<UserResponse> {
    const { data } = await api.put<UserResponse>('/users/me', request);
    return data;
  },

  /** Requires the current password, so a stolen token alone cannot seize the account. */
  async changePassword(request: ChangePasswordRequest): Promise<void> {
    await api.put('/users/me/password', request);
  },

  /** Soft delete: the account becomes INACTIVE and the ledger is retained. */
  async deactivateCurrent(): Promise<void> {
    await api.delete('/users/me');
  },

  // --- Administrative ------------------------------------------------------

  async list(filters: UserFilters = {}, page: PageRequest = {}): Promise<PagedResponse<UserResponse>> {
    const params = buildParams({
      ...filters,
      page: page.page,
      size: page.size,
    });
    if (page.sort) {
      params.append('sort', `${page.sort.field},${page.sort.direction}`);
    }

    const { data } = await api.get<PagedResponse<UserResponse>>('/users', { params });
    return data;
  },

  async getById(id: number): Promise<UserResponse> {
    const { data } = await api.get<UserResponse>(`/users/${id}`);
    return data;
  },

  /** The only route through which a role or status can change. */
  async update(id: number, request: AdminUpdateUserRequest): Promise<UserResponse> {
    const { data } = await api.put<UserResponse>(`/users/${id}`, request);
    return data;
  },

  async deactivate(id: number): Promise<void> {
    await api.delete(`/users/${id}`);
  },
};

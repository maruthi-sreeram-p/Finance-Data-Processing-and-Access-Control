import { api, buildParams } from './api';
import type { CategoryRequest, CategoryResponse, TransactionType } from '@/types/api';

/**
 * Reference categories: readable by any signed-in user, writable by administrators.
 *
 * The listing is unpaginated because the backend returns the full set -- it is a
 * small bounded lookup table that clients need in its entirety to render a picker.
 */
export const categoryService = {
  /** `GET /api/categories` -- optionally narrowed to those valid for one direction. */
  async list(type?: TransactionType): Promise<CategoryResponse[]> {
    const { data } = await api.get<CategoryResponse[]>('/categories', {
      params: buildParams({ type }),
    });
    return data;
  },

  async getById(id: number): Promise<CategoryResponse> {
    const { data } = await api.get<CategoryResponse>(`/categories/${id}`);
    return data;
  },

  /** Administrator only; a non-admin caller receives 403 from the backend. */
  async create(request: CategoryRequest): Promise<CategoryResponse> {
    const { data } = await api.post<CategoryResponse>('/categories', request);
    return data;
  },

  async update(id: number, request: CategoryRequest): Promise<CategoryResponse> {
    const { data } = await api.put<CategoryResponse>(`/categories/${id}`, request);
    return data;
  },

  /** Refused with 403 while any transaction still references the category. */
  async remove(id: number): Promise<void> {
    await api.delete(`/categories/${id}`);
  },
};

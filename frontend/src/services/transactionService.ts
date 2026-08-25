import { api, buildParams } from './api';
import type {
  PageRequest,
  PagedResponse,
  TransactionFilters,
  TransactionRequest,
  TransactionResponse,
} from '@/types/api';

/**
 * The transaction ledger.
 *
 * Note that no method takes an owner. The backend resolves the owner from the
 * authenticated principal for writes, and pins ordinary callers to their own
 * ledger for reads, so there is nothing here for a client to get wrong.
 */
export const transactionService = {
  /**
   * `GET /api/transactions`
   *
   * Sort is sent as Spring Data expects it, `sort=field,direction`. The backend
   * whitelists the property and appends an id tiebreaker, so an unrecognised
   * value degrades to the default ordering rather than failing the request.
   */
  async list(
    filters: TransactionFilters = {},
    page: PageRequest = {},
  ): Promise<PagedResponse<TransactionResponse>> {
    const params = buildParams({
      ...filters,
      page: page.page,
      size: page.size,
    });

    if (page.sort) {
      params.append('sort', `${page.sort.field},${page.sort.direction}`);
    }

    const { data } = await api.get<PagedResponse<TransactionResponse>>('/transactions', { params });
    return data;
  },

  /**
   * `GET /api/transactions/{id}`
   *
   * Answers 404 both for a transaction that does not exist and for one belonging
   * to another user -- indistinguishable on purpose, so the endpoint cannot be
   * walked to discover which ids are real. The UI therefore shows one "not found"
   * state for both and must not imply the record exists.
   */
  async getById(id: number): Promise<TransactionResponse> {
    const { data } = await api.get<TransactionResponse>(`/transactions/${id}`);
    return data;
  },

  async create(request: TransactionRequest): Promise<TransactionResponse> {
    const { data } = await api.post<TransactionResponse>('/transactions', request);
    return data;
  },

  /** Owner only. An administrator editing someone else's entry receives 404. */
  async update(id: number, request: TransactionRequest): Promise<TransactionResponse> {
    const { data } = await api.put<TransactionResponse>(`/transactions/${id}`, request);
    return data;
  },

  /** Owner only. */
  async remove(id: number): Promise<void> {
    await api.delete(`/transactions/${id}`);
  },
};

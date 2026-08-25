/**
 * TypeScript mirror of the FinTrack API contract.
 *
 * Transcribed from the OpenAPI document the backend generates, not from
 * guesswork: `GET /v3/api-docs` is the source of truth, and every shape below was
 * additionally verified against live responses.
 *
 * Two properties of the backend's serialization shape these types:
 *
 *  1. Jackson runs with `default-property-inclusion: non_null`, so a null field is
 *     *absent* from the payload rather than present as null. Optional fields are
 *     therefore marked `?`, and code must handle `undefined` -- not `null`.
 *
 *  2. Money crosses the wire as a JSON number. JavaScript has no decimal type, so
 *     these values are IEEE doubles by the time they reach us. They are safe to
 *     format and display, but the client never does arithmetic on them: every
 *     total, balance and percentage is computed by the database and read from the
 *     dashboard response.
 */

// --- Enums -----------------------------------------------------------------
// Union types rather than TS `enum`: they erase to plain strings, compare
// directly against API values, and need no runtime import.

export type Role = 'ADMIN' | 'MANAGER' | 'USER';

export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED';

export type TransactionType = 'INCOME' | 'EXPENSE';

/** `BOTH` categories accept either direction, e.g. Investment. */
export type CategoryType = 'INCOME' | 'EXPENSE' | 'BOTH';

export const ROLES: readonly Role[] = ['ADMIN', 'MANAGER', 'USER'];
export const USER_STATUSES: readonly UserStatus[] = ['ACTIVE', 'INACTIVE', 'SUSPENDED'];
export const TRANSACTION_TYPES: readonly TransactionType[] = ['INCOME', 'EXPENSE'];

// --- Shared ----------------------------------------------------------------

/**
 * The paging envelope returned by every paginated endpoint.
 *
 * Note this is the backend's own `PagedResponse`, not Spring Data's serialized
 * `Page` -- the field names are stable and will not shift with a Spring upgrade.
 */
export interface PagedResponse<T> {
  content: T[];
  /** Zero-based. */
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

/** One rejected field of a request body. Present only on validation failures. */
export interface ApiFieldError {
  field: string;
  message: string;
}

/**
 * The single error shape returned by every failing request, from the security
 * filter chain through to Spring MVC.
 *
 * `error` is the stable machine-readable code and is what client logic should
 * branch on; `message` is written for humans and may change.
 */
export interface ApiError {
  /** ISO-8601 instant. */
  timestamp: string;
  status: number;
  error: ApiErrorCode;
  message: string;
  path: string;
  /** Present only when `error` is `VALIDATION_FAILED`. */
  fieldErrors?: ApiFieldError[];
}

export type ApiErrorCode =
  | 'VALIDATION_FAILED'
  | 'INVALID_TRANSACTION'
  | 'BAD_REQUEST'
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'RESOURCE_NOT_FOUND'
  | 'DUPLICATE_RESOURCE'
  | 'INTERNAL_ERROR';

// --- Auth ------------------------------------------------------------------

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface AuthResponse {
  accessToken: string;
  /** Always `"Bearer"`. */
  tokenType: string;
  /** Token lifetime in seconds. */
  expiresIn: number;
  user: UserResponse;
}

// --- Users -----------------------------------------------------------------

export interface UserResponse {
  id: number;
  name: string;
  email: string;
  role: Role;
  status: UserStatus;
  /** ISO-8601 instant. */
  createdAt: string;
  updatedAt: string;
}

/**
 * Self-service profile update. Deliberately carries no role or status: the
 * backend does not accept them here, and the omission is the reason a user
 * cannot promote themselves.
 */
export interface UpdateUserRequest {
  name?: string;
  email?: string;
}

/** Administrative update. Role and status are reachable only through this shape. */
export interface AdminUpdateUserRequest {
  name?: string;
  email?: string;
  role?: Role;
  status?: UserStatus;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

// --- Categories ------------------------------------------------------------

export interface CategoryResponse {
  id: number;
  name: string;
  /** Absent when the category has no description. */
  description?: string;
  type: CategoryType;
}

export interface CategoryRequest {
  name: string;
  description?: string;
  type: CategoryType;
}

// --- Transactions ----------------------------------------------------------

export interface TransactionResponse {
  id: number;
  /** Owner. Present so an admin or manager viewing another ledger can tell whose it is. */
  userId: number;
  type: TransactionType;
  /** Always positive; `type` carries the direction. */
  amount: number;
  category: CategoryResponse;
  /** Absent when the transaction has no note. */
  description?: string;
  /** Calendar date, `YYYY-MM-DD`. Not a timestamp -- see `utils/format`. */
  transactionDate: string;
  createdAt: string;
  updatedAt: string;
}

/**
 * Create/replace payload.
 *
 * There is no owner field, and that is the point: the backend takes the owner
 * from the authenticated principal, so a client cannot file an entry into someone
 * else's ledger even by adding the property.
 */
export interface TransactionRequest {
  type: TransactionType;
  amount: number;
  categoryId: number;
  description?: string;
  /** `YYYY-MM-DD`. The backend rejects future dates. */
  transactionDate: string;
}

/**
 * Query parameters for the transaction listing. Every field is optional; omitting
 * one leaves that dimension unfiltered.
 */
export interface TransactionFilters {
  /** Another user's ledger. Honoured only for ADMIN and MANAGER; ignored otherwise. */
  userId?: number;
  type?: TransactionType;
  categoryId?: number;
  /** Category by name, case-insensitive. Ignored when `categoryId` is supplied. */
  category?: string;
  /** Inclusive lower bound, `YYYY-MM-DD`. */
  from?: string;
  /** Inclusive upper bound, `YYYY-MM-DD`. */
  to?: string;
  minAmount?: number;
  maxAmount?: number;
}

/** Properties the backend permits sorting by; anything else is silently ignored. */
export type TransactionSortField =
  | 'transactionDate'
  | 'amount'
  | 'type'
  | 'createdAt'
  | 'updatedAt'
  | 'id';

export interface SortSpec {
  field: TransactionSortField;
  direction: 'asc' | 'desc';
}

export interface PageRequest {
  /** Zero-based. */
  page?: number;
  size?: number;
  sort?: SortSpec;
}

// --- Dashboard -------------------------------------------------------------

export interface CategoryBreakdown {
  categoryId: number;
  categoryName: string;
  type: TransactionType;
  total: number;
  transactionCount: number;
  /** Share of all activity in the same direction, so income and expense shares each total 100. */
  percentage: number;
}

export interface MonthlySummary {
  /** `YYYY-MM`. */
  month: string;
  income: number;
  expenses: number;
  /** `income - expenses`; negative for a month that ran at a loss. */
  net: number;
}

export interface DashboardResponse {
  userId: number;
  /** Absent when the period is unbounded. */
  periodFrom?: string;
  periodTo?: string;
  totalIncome: number;
  totalExpenses: number;
  /** `totalIncome - totalExpenses`; may be negative. */
  balance: number;
  transactionCount: number;
  /** Largest first. */
  categoryBreakdown: CategoryBreakdown[];
  /** Oldest first. */
  monthlySummary: MonthlySummary[];
  /** Newest first, capped at 5 by the backend. */
  recentTransactions: TransactionResponse[];
}

/**
 * Organization-wide overview, returned by `GET /api/dashboard/system`.
 *
 * A separate type from DashboardResponse because the two describe different
 * things: one account versus every account. Deliberately carries no per-user
 * breakdown -- it reports what the organization spent, not what each person did.
 */
export interface SystemDashboardResponse {
  /** Always `"SYSTEM"`, so a personal payload cannot be mistaken for this one. */
  scope: 'SYSTEM';
  periodFrom?: string;
  periodTo?: string;
  totalIncome: number;
  totalExpenses: number;
  netPosition: number;
  transactionCount: number;
  /** Distinct accounts that recorded at least one transaction in the period. */
  activeLedgerCount: number;
  categoryBreakdown: CategoryBreakdown[];
  monthlySummary: MonthlySummary[];
}

export interface DashboardQuery {
  userId?: number;
  from?: string;
  to?: string;
}

// --- Admin user listing ----------------------------------------------------

export interface UserFilters {
  role?: Role;
  status?: UserStatus;
}

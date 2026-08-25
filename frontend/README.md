# FinTrack — Frontend

React + TypeScript client for the [FinTrack API](../backend). It talks to the real
Spring Boot backend on every screen; there are no mocked responses and no
hard-coded financial figures anywhere in the application.

---

## Stack

| | | Why |
|---|---|---|
| **React 19** + **TypeScript** | UI | Contract types catch a renamed API field at compile time |
| **Vite 6** | Build | Fast dev server, native CSS Modules |
| **React Router 7** | Routing | Nested routes express the privilege boundaries |
| **Axios** | HTTP | One instance, one interceptor, one place for auth |
| **Vitest** + **Testing Library** | Tests | Queries by role and label, so tests exercise accessibility too |

**Four runtime dependencies in total:** `react`, `react-dom`, `react-router-dom`,
`axios`.

### Deliberate omissions

- **No CSS framework.** Styling is CSS Modules over a design-token layer
  (`src/styles/tokens.css`). Every colour, space and type step resolves to a
  variable, which is what makes the dark theme ~20 redefinitions rather than an
  audit of every stylesheet — and keeps the result from looking like a default
  template.
- **No chart library.** The three charts are hand-written SVG rendering directly
  from `/api/dashboard`. A charting package would add far more weight than three
  chart types justify.
- **No state-management library.** Server data is fetched per screen through
  `useApiData`; the only genuinely global state is the session and the theme, which
  are two small contexts.
- **No icon package.** ~20 inline SVGs that inherit `currentColor`, so they follow
  the theme for free.

---

## Setup

```bash
cd frontend && npm install
```

```bash
cp .env.example .env
```

| Variable | Default | Purpose |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080/api` | Base URL of the API, including `/api` |

Only `VITE_`-prefixed variables reach the bundle — Vite's guard against a server
secret being inlined into shipped JavaScript. **Nothing in `.env` is secret**: it
is all publicly readable once built.

---

## Running

The backend must be running first. From the repository root:

```bash
cd backend && ./mvnw spring-boot:run -Plocal -Dspring-boot.run.profiles=local
```

Then, in a second terminal:

```bash
cd frontend && npm run dev
```

The app is at **http://localhost:5173**.

The dev-server port is pinned with `strictPort`. If Vite silently moved to 5174 the
browser origin would no longer match the backend's CORS allow-list, and every
request would fail as an opaque CORS error rather than something diagnosable.

| Script | Does |
|---|---|
| `npm run dev` | Dev server with HMR |
| `npm run build` | Type-check then production build |
| `npm run preview` | Serve the built bundle |
| `npm run typecheck` | Types only |
| `npm test` | Run the test suite |

---

## Architecture

```
src/
├── components/
│   ├── ui/            Button, Field, Card, Badge, Pagination, ConfirmDialog,
│   │                  Toaster, States (empty/error/skeleton), Spinner, Icons
│   ├── charts/        MonthlyTrendChart, IncomeExpenseDonut, CategoryBreakdownChart
│   ├── transactions/  TransactionTable, FilterBar
│   └── routing/       ProtectedRoute, AdminRoute, PublicOnlyRoute
├── context/           AuthContext, ThemeContext, ToastContext
├── hooks/             useAuth, useTheme, useToast, useApiData,
│                      useDebouncedValue, useDocumentTitle
├── layouts/           AppLayout (sidebar + top bar + routed outlet)
├── pages/             One file per route
├── services/          api (axios instance), apiError, tokenStorage,
│                      authService, userService, transactionService,
│                      categoryService, dashboardService
├── styles/            tokens.css, global.css
├── types/             api.ts — the contract, mirrored from the OpenAPI document
└── test/              Vitest suites
```

### The API layer

Every HTTP call goes through `services/api.ts`. Components never import `axios`,
and never see an `AxiosError`.

```
component → service → axios instance → interceptors → backend
                                            │
                    request: attach bearer token (skipped for login/register)
                    response: normalize to AppError; on 401, end the session
```

`AppError` is the single error type the UI deals with. It carries the backend's
stable `error` code, its human-readable message, and `fieldErrors` keyed by
property so a form can mark the exact inputs that were rejected.

### Session handling

The token lives in `localStorage`. **The honest trade-off**: that makes it readable
by any script on the page, so an XSS flaw becomes account takeover. The usual
recommendation — an httpOnly cookie — is not available here, because the API is
deliberately stateless and issues a single bearer token with no refresh endpoint.
Holding it only in memory would close the hole but sign the user out on every
reload.

What bounds the exposure: tokens last one hour, the backend re-reads the account on
every request (so a suspension takes effect immediately, not at expiry), and the
app renders no user content as raw HTML. A refresh-token endpoint plus httpOnly
cookies is the documented upgrade path.

On load, a stored token is treated as a **claim to verify**: the app calls
`GET /api/users/me` and uses the response. It does not decode the JWT's `role`
claim, because a token is a snapshot from when it was issued — if an administrator
changed the role since, the claim is stale.

### Frontend authorization is not security

`ProtectedRoute`, `AdminRoute` and every hidden nav link exist to keep people out of
screens that would only fail. They protect nothing. Anyone can edit local state or
call the API directly with curl.

The backend enforces the real thing, and this was verified against the running
server rather than assumed:

| Attempt as a plain USER | Result |
|---|---|
| `GET /api/users` | **403** |
| `GET /api/transactions/{someone else's}` | **404** — identical to a missing record |
| `DELETE /api/transactions/{someone else's}` | **404** |
| `PUT /api/users/{id}` to self-promote | **403** |
| `PUT /api/users/me` with `"role":"ADMIN"` | **200**, role unchanged — the field is not bound |

---

## Pages

| Route | Access | Notes |
|---|---|---|
| `/` | Public | Landing page; redirects to dashboard if signed in |
| `/login`, `/register` | Signed out only | Redirect to dashboard if already signed in |
| `/dashboard` | Authenticated | Totals, charts, recent activity |
| `/transactions` | Authenticated | Filter, sort, paginate |
| `/transactions/new` | Authenticated | Create |
| `/transactions/:id` | Authenticated | Detail |
| `/transactions/:id/edit` | Owner | Edit |
| `/categories` | Authenticated | Read-only; full CRUD for ADMIN |
| `/reports` | Authenticated | Period analysis over `/api/dashboard?from=&to=` |
| `/profile` | Authenticated | Profile, password, deactivation |
| `/admin/users` | **ADMIN** | Roles, status, deactivation |
| `/forbidden`, `*` | — | 403 and 404 |

---

## Contract details that shape the client

Learned from the generated OpenAPI document and from calling the running API — not
assumed:

- **Null fields are absent, not null.** The backend serializes with
  `non_null`, so `description`, `periodFrom`/`periodTo` and `fieldErrors` are simply
  missing when empty. The types mark them optional and the UI checks for `undefined`.
- **Category ids are not ordered by name** (`Bills=8`, `Rent=3`). Every picker loads
  them from the API; no id is ever hard-coded.
- **`transactionDate` is a plain `YYYY-MM-DD`.** `new Date('2026-08-01')` parses as
  UTC midnight and renders as 31 July anywhere west of Greenwich. `parseDateOnly`
  splits the parts and builds a local date instead — there is a test for exactly this.
- **Money arrives as a JSON number.** JavaScript has no decimal type, so the client
  formats amounts but never does arithmetic on them. Every total, balance and
  percentage shown comes from a database aggregate.
- **Sorting is whitelisted server-side** to six properties, with an `id` tiebreaker
  appended so paging cannot repeat or skip rows.

---

## Testing

```bash
npm test
```

**53 tests**, all passing.

| Suite | Covers |
|---|---|
| `ProtectedRoute` | Anonymous redirect, in-flight session check, role gates, revoked token, expired token short-circuit |
| `TransactionFormPage` | The 0.01 floor, future dates, required category, no `userId` in the payload, backend field errors |
| `TransactionTable` | Signed amounts, missing description fallback, date rendering, `aria-sort`, write actions hidden for others' rows |
| `LoginPage` | Label association, submit, token storage, backend error surfaced, double-submit guard, password never persisted |
| `apiError` | Backend message preferred, field-error mapping, network vs. server failure, no internals leaked |
| `format` | Date-only parsing including the timezone trap and invalid dates |

Tests query by **role and label**, so a control that is unreachable to a screen
reader fails the test too.

---

## Accessibility

- Semantic HTML — a real `<table>` for tabular data, `<dl>` for record details,
  `<nav>`/`<main>`/`<aside>` landmarks.
- Every input is programmatically associated with its label via `useId`, with
  `aria-describedby` for hints and errors and `aria-invalid` on failure.
- `ConfirmDialog` is a proper modal: focus moves in and is restored on close, Tab is
  trapped, Escape cancels, and **Cancel** takes initial focus so a stray Enter never
  deletes.
- Visible `:focus-visible` rings throughout; a skip link ahead of the navigation.
- Status is never carried by colour alone — income/expense and account status all
  pair colour with a word.
- `prefers-reduced-motion` is honoured.
- Toasts announce politely; errors use `role="alert"`.

## Responsive

Verified with no horizontal page overflow at 375px.

- **≤ 960px** — sidebar becomes an overlay drawer with a scrim; it closes on
  navigation, on Escape, and on scrim click.
- **≤ 820px** — the transaction table becomes a card list. Six columns cannot be made
  readable at phone width, and dropping columns would hide information.
- Wide content scrolls inside its own container, never the page.

## Theming

Light and dark, chosen from `prefers-color-scheme` and persisted to `localStorage`.
An inline script in `index.html` applies the theme **before first paint**, so there
is no flash of the wrong theme on reload. Dark mode is not an inversion: surfaces
lift as they come forward, because elevation reads through lightness when shadows
are invisible against a dark ground.
